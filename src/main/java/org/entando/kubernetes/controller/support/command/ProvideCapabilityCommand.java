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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.capability.SerializedCapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.EntandoControllerException;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.client.CapabilityClient;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ResourceReference;

//TODO Think of how to use with CommandStream
public class ProvideCapabilityCommand {

    private final CapabilityClient client;

    public ProvideCapabilityCommand(CapabilityClient client) {
        this.client = client;
    }

    public SerializedCapabilityProvisioningResult execute(HasMetadata forResource, CapabilityRequirement capabilityRequirement,
            int timeoutSeconds) throws TimeoutException {
        try {
            final CapabilityScope requirementScope = capabilityRequirement.getScope().orElse(CapabilityScope.NAMESPACE);
            Optional<ProvidedCapability> match = findCapability(forResource, capabilityRequirement, requirementScope);
            match.ifPresent(c -> validateCapabilityCriteria(capabilityRequirement, requirementScope, c));
            return loadProvisioningResult(
                    match.orElseGet(
                            () -> makeNewCapabilityAvailable(forResource, capabilityRequirement, requirementScope, timeoutSeconds)));
        } catch (TimeoutHolderException e) {
            throw e.getCause();
        }
    }

    private SerializedCapabilityProvisioningResult loadProvisioningResult(ProvidedCapability providedCapability) {
        return client.buildCapabilityProvisioningResult(providedCapability);
    }

    private void validateCapabilityCriteria(CapabilityRequirement capabilityRequirement, CapabilityScope requirementScope,
            ProvidedCapability c) {
        if (!requirementScope.getCamelCaseName()
                .equals(c.getMetadata().getLabels().get(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME))) {
            throw new IllegalArgumentException(
                    format("The capability %s was found, but its provision scope is %s instead of the requested %s scope",
                            capabilityRequirement.getCapability().getCamelCaseName(),
                            c.getMetadata().getLabels().get(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME),
                            requirementScope.getCamelCaseName()
                    ));
        }
        if (!capabilityRequirement.getImplementation().map(i -> i.getCamelCaseName()
                .equals(c.getMetadata().getLabels().get(ProvidedCapability.IMPLEMENTATION_LABEL_NAME))).orElse(true)) {
            throw new IllegalArgumentException(
                    format("The capability %s was found, but its implementation is %s instead of the requested %s scope",
                            capabilityRequirement.getCapability().getCamelCaseName(),
                            c.getMetadata().getLabels().get(ProvidedCapability.IMPLEMENTATION_LABEL_NAME),
                            requirementScope.getCamelCaseName()
                    ));
        }
    }

    private Optional<ProvidedCapability> findCapability(HasMetadata forResource, CapabilityRequirement capabilityRequirement,
            CapabilityScope requirementScope) {
        switch (requirementScope) {
            case DEDICATED:
                return client.providedCapabilityByName(forResource.getMetadata().getNamespace(),
                        forResource.getMetadata().getName() + "-" + capabilityRequirement.getCapability().getSuffix());
            case SPECIFIED:
                final ResourceReference resourceReference = capabilityRequirement.getSpecifiedCapability()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "A requirement for a specified capability needs a valid name and optional namespace to resolve "
                                        + "the required capability."));
                return client.providedCapabilityByName(resourceReference.getNamespace().orElse(forResource.getMetadata().getNamespace()),
                        resourceReference.getName());
            case LABELED:
                if (capabilityRequirement.getSelector() == null || capabilityRequirement.getSelector().isEmpty()) {
                    throw new IllegalArgumentException("A requirement for a labeled capability needs at least one label to resolve "
                            + "the required capability.");
                }

                return client.providedCapabilityByLabels(capabilityRequirement.getSelector());
            case NAMESPACE:
                return client
                        .providedCapabilityByLabels(forResource.getMetadata().getNamespace(),
                                determineCapabilityLabels(capabilityRequirement));
            case CLUSTER:
            default:
                return client.providedCapabilityByLabels(determineCapabilityLabels(capabilityRequirement));
        }
    }

    private Map<String, String> determineCapabilityLabels(CapabilityRequirement capabilityRequirement) {
        Map<String, String> result = new HashMap<>();
        result.put(ProvidedCapability.CAPABILITY_LABEL_NAME, capabilityRequirement.getCapability().getCamelCaseName());
        //In the absence of implementation and scope, it is the Controller's responsibility to make the decions and populate the
        // ProvidedCapability's labels
        capabilityRequirement.getImplementation().ifPresent(impl -> result.put(ProvidedCapability.IMPLEMENTATION_LABEL_NAME,
                impl.getCamelCaseName()));
        capabilityRequirement.getScope().ifPresent(scope -> result.put(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME,
                scope.getCamelCaseName()));
        return result;
    }

    private ProvidedCapability makeNewCapabilityAvailable(HasMetadata forResource, CapabilityRequirement requiredCapability,
            CapabilityScope requirementScope, int timeoutSeconds) throws TimeoutHolderException {
        try {
            final ProvidedCapability capabilityRequirement = buildCapabilityRequirementFor(forResource, requiredCapability);
            final ProvidedCapability providedCapability = client.createAndWaitForCapability(capabilityRequirement, timeoutSeconds);
            if (providedCapability.getStatus().getPhase() == EntandoDeploymentPhase.FAILED) {
                throw new EntandoControllerException("Could not provide capability");
            }
            return findCapability(forResource, requiredCapability, requirementScope).orElseThrow(IllegalStateException::new);
        } catch (TimeoutException e) {
            throw new TimeoutHolderException(e);
        }
    }

    private ProvidedCapability buildCapabilityRequirementFor(HasMetadata forResource, CapabilityRequirement capabilityRequirement) {
        ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder();
        switch (capabilityRequirement.getScope().orElse(CapabilityScope.NAMESPACE)) {
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
                                capabilityRequirement.getImplementation().map(i -> i.getHypenatedName() + "-").orElse("")
                                        + capabilityRequirement
                                        .getCapability().getHypenatedName() + "-" + NameUtils
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
        metaBuilder.addToLabels(determineCapabilityLabels(capabilityRequirement));
        return new ProvidedCapability(metaBuilder.build(), capabilityRequirement);
    }

    private String calculateDefaultName(CapabilityRequirement capabilityRequirement) {
        return "default" + capabilityRequirement.getImplementation()
                .map(i -> "-" + i.getHypenatedName()).orElse("") + "-" + capabilityRequirement.getCapability()
                .getHypenatedName();
    }

    private static class TimeoutHolderException extends RuntimeException {

        public TimeoutHolderException(TimeoutException timeoutException) {
            super(timeoutException);
        }

        @Override
        public synchronized TimeoutException getCause() {
            return (TimeoutException) super.getCause();
        }
    }
}