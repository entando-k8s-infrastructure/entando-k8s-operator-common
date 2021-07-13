/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.support.command;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.capability.SerializedCapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.EntandoControllerException;
import org.entando.kubernetes.controller.spi.common.ExceptionUtils;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.client.CapabilityClient;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.ProvidedCapabilityBuilder;
import org.entando.kubernetes.model.common.ResourceReference;

public class ProvideCapabilityCommand {

    private final CapabilityClient client;

    public ProvideCapabilityCommand(CapabilityClient client) {
        this.client = client;
    }

    /**
     * This method is not allowed to throw any exceptions. Any error conditions associated with the execution of this method should be
     * attached to the CapabilityProvisioningResult, or if a ProvidedCapability was created, it will be attached to this
     * ProvidedCapability.
     */

    public SerializedCapabilityProvisioningResult execute(HasMetadata forResource, CapabilityRequirement requirement, int timeoutSeconds) {
        try {
            final List<CapabilityScope> resolutionScopePreference = determineResolutionScopePreference(requirement);
            Optional<ProvidedCapability> match = findCapability(forResource, requirement, resolutionScopePreference);
            match.ifPresent(c -> validateCapabilityCriteria(requirement, resolutionScopePreference, c));
            ProvidedCapability providedCapability = match.map(pc -> syncIfRequired(forResource, pc, requirement)).orElseGet(
                    () -> makeNewCapabilityAvailable(forResource, requirement, resolutionScopePreference.get(0)));
            providedCapability = client.waitForCapabilityCompletion(providedCapability, timeoutSeconds);
            return loadProvisioningResult(providedCapability);
        } catch (Exception e) {
            return new SerializedCapabilityProvisioningResult(ExceptionUtils.failureOf(forResource, e));
        }
    }

    private ProvidedCapability syncIfRequired(HasMetadata forResource, ProvidedCapability providedCapability,
            CapabilityRequirement requirement) {
        if (ofNullable(providedCapability.getMetadata().getAnnotations())
                .map(m -> forResource.getMetadata().getUid().equals(m.get(CapabilityProvider.ORIGIN_UUID_ANNOTATION_NAME)))
                .orElse(false)) {
            final CapabilityRequirement requiredState = new CapabilityRequirementBuilder(providedCapability.getSpec())
                    .addAllToCapabilityParameters(requirement.getCapabilityParameters()).build();
            if (!providedCapability.getSpec().getCapabilityParameters().equals(requiredState.getCapabilityParameters())) {
                ProvidedCapability newCapability = client
                        .createOrPatchCapability(new ProvidedCapabilityBuilder(providedCapability).withSpec(requiredState).build());
                try {
                    return client.waitForCapabilityCommencement(newCapability, 3);
                } catch (TimeoutException e) {
                    throw new EntandoControllerException(newCapability, e);
                }
            }
        }
        return providedCapability;
    }

    private List<CapabilityScope> determineResolutionScopePreference(CapabilityRequirement capabilityRequirement) {
        List<CapabilityScope> requirementScopes = capabilityRequirement.getResolutionScopePreference();
        if (requirementScopes.isEmpty()) {
            requirementScopes = Collections.singletonList(CapabilityScope.NAMESPACE);
        }
        return requirementScopes;
    }

    private SerializedCapabilityProvisioningResult loadProvisioningResult(ProvidedCapability providedCapability) {
        return client.buildCapabilityProvisioningResult(providedCapability);
    }

    private void validateCapabilityCriteria(CapabilityRequirement capabilityRequirement, List<CapabilityScope> requirementScope,
            ProvidedCapability c) {
        Set<CapabilityScope> required = new HashSet<>(requirementScope);
        Set<CapabilityScope> provided = new HashSet<>(c.getSpec().getResolutionScopePreference());
        provided.add(CapabilityScope.forValue(c.getMetadata().getLabels().get(LabelNames.CAPABILITY_PROVISION_SCOPE.getName())));
        required.retainAll(provided);
        if (required.isEmpty()) {
            throw new EntandoControllerException(c,
                    format("The capability %s was found, but its supported provisioning scopes are '%s' instead of the requested '%s' "
                                    + "scopes",
                            capabilityRequirement.getCapability().getCamelCaseName(),
                            c.getSpec().getResolutionScopePreference().stream()
                                    .map(CapabilityScope::getCamelCaseName)
                                    .collect(Collectors.joining(",")),
                            requirementScope.stream()
                                    .map(CapabilityScope::getCamelCaseName)
                                    .collect(Collectors.joining(","))));
        }
        if (!capabilityRequirement.getImplementation().map(i -> i.getCamelCaseName()
                .equals(c.getMetadata().getLabels().get(LabelNames.CAPABILITY_IMPLEMENTATION.getName()))).orElse(true)) {
            throw new EntandoControllerException(c,
                    format("The capability %s was found, but its implementation is %s instead of the requested %s",
                            capabilityRequirement.getCapability().getCamelCaseName(),
                            c.getMetadata().getLabels().get(LabelNames.CAPABILITY_IMPLEMENTATION.getName()),
                            capabilityRequirement.getImplementation().orElseThrow(IllegalStateException::new).getCamelCaseName()
                    ));
        }
    }

    private Optional<ProvidedCapability> findCapability(HasMetadata forResource, CapabilityRequirement capabilityRequirement,
            List<CapabilityScope> resolutionScopePreference) {
        for (CapabilityScope capabilityScope : resolutionScopePreference) {
            final Optional<ProvidedCapability> found = resolveCapabilityByScope(forResource, capabilityRequirement, capabilityScope);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<ProvidedCapability> resolveCapabilityByScope(HasMetadata forResource, CapabilityRequirement capabilityRequirement,
            CapabilityScope capabilityScope) {
        final Map<String, String> baseLabels = determineCapabilityLabels(capabilityRequirement, capabilityScope);
        switch (capabilityScope) {
            case DEDICATED:
                return client.providedCapabilityByName(forResource.getMetadata().getNamespace(),
                        forResource.getMetadata().getName() + "-" + capabilityRequirement.getCapability().getSuffix());
            case SPECIFIED:
                final ResourceReference resourceReference = capabilityRequirement.getSpecifiedCapability()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "A requirement for a specified capability needs a valid name and optional namespace to resolve "
                                        + "the required capability."));
                return client
                        .providedCapabilityByName(resourceReference.getNamespace().orElse(forResource.getMetadata().getNamespace()),
                                resourceReference.getName());
            case LABELED:
                if (capabilityRequirement.getSelector() == null || capabilityRequirement.getSelector().isEmpty()) {
                    throw new EntandoControllerException("A requirement for a labeled capability needs at least one label to resolve "
                            + "the required capability.");
                }
                baseLabels.putAll(capabilityRequirement.getSelector());
                return client.providedCapabilityByLabels(baseLabels);
            case NAMESPACE:
                return client
                        .providedCapabilityByLabels(forResource.getMetadata().getNamespace(),
                                baseLabels);
            case CLUSTER:
            default:
                return client.providedCapabilityByLabels(baseLabels);
        }
    }

    private Map<String, String> determineCapabilityLabels(CapabilityRequirement capabilityRequirement, CapabilityScope scope) {
        Map<String, String> result = new HashMap<>();
        result.put(LabelNames.CAPABILITY.getName(), capabilityRequirement.getCapability().getCamelCaseName());
        //In the absence of implementation and scope, it is the Controller's responsibility to make the decisions and populate the
        // ProvidedCapability's labels
        capabilityRequirement.getImplementation().ifPresent(impl -> result.put(LabelNames.CAPABILITY_IMPLEMENTATION.getName(),
                impl.getCamelCaseName()));
        result.put(LabelNames.CAPABILITY_PROVISION_SCOPE.getName(), scope.getCamelCaseName());
        return result;
    }

    private ProvidedCapability makeNewCapabilityAvailable(HasMetadata forResource, CapabilityRequirement requiredCapability,
            CapabilityScope requirementScope) {
        final ProvidedCapability capabilityRequirement = buildProvidedCapabilityFor(forResource, requiredCapability);
        client.createOrPatchCapability(capabilityRequirement);
        return findCapability(forResource, requiredCapability, Collections.singletonList(requirementScope))
                .orElseThrow(IllegalStateException::new);
    }

    private ProvidedCapability buildProvidedCapabilityFor(HasMetadata forResource, CapabilityRequirement capabilityRequirement) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder();
        switch (determineResolutionScopePreference(capabilityRequirement).get(0)) {
            case DEDICATED:
                metaBuilder = metaBuilder.withNamespace(forResource.getMetadata().getNamespace())
                        .withName(forResource.getMetadata().getName() + "-" + capabilityRequirement.getCapability().getSuffix());
                break;
            case SPECIFIED:
                final ResourceReference resourceReference = capabilityRequirement.getSpecifiedCapability()
                        .orElseThrow(IllegalStateException::new);
                metaBuilder = metaBuilder.withNamespace(resourceReference.getNamespace().orElse(forResource.getMetadata().getNamespace()))
                        .withName(resourceReference.getName());
                break;
            case LABELED:
                metaBuilder = metaBuilder.withNamespace(forResource.getMetadata().getNamespace())
                        .withName(
                                capabilityRequirement.getImplementation().map(i -> i.getHyphenatedName() + "-").orElse("")
                                        + capabilityRequirement
                                        .getCapability().getHyphenatedName() + "-" + NameUtils
                                        .randomNumeric(4))
                        .addToLabels(capabilityRequirement.getSelector());
                break;
            case NAMESPACE:
                metaBuilder = metaBuilder.withNamespace(forResource.getMetadata().getNamespace())
                        .withName(calculateDefaultName(capabilityRequirement) + "-in-namespace");
                break;
            case CLUSTER:
            default:
                metaBuilder = metaBuilder.withNamespace(client.getNamespace())
                        .withName(calculateDefaultName(capabilityRequirement) + "-in-cluster");
        }
        metaBuilder.addToLabels(
                determineCapabilityLabels(capabilityRequirement, determineResolutionScopePreference(capabilityRequirement).get(0)))
                .addToAnnotations(CapabilityProvider.ORIGIN_UUID_ANNOTATION_NAME, forResource.getMetadata().getUid());
        return new ProvidedCapability(metaBuilder.build(), capabilityRequirement);
    }

    private String calculateDefaultName(CapabilityRequirement capabilityRequirement) {
        return "default" + capabilityRequirement.getImplementation()
                .map(i -> "-" + i.getHyphenatedName()).orElse("") + "-" + capabilityRequirement.getCapability()
                .getHyphenatedName();
    }

}
