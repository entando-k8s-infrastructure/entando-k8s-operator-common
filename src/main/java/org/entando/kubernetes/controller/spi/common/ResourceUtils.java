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

package org.entando.kubernetes.controller.spi.common;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class ResourceUtils {

    private ResourceUtils() {

    }

    public static OwnerReference buildOwnerReference(HasMetadata entandoCustomResource) {
        return new OwnerReferenceBuilder()
                .withApiVersion(entandoCustomResource.getApiVersion())
                .withBlockOwnerDeletion(true)
                .withController(true)
                .withKind(entandoCustomResource.getKind())
                .withName(entandoCustomResource.getMetadata().getName())
                .withUid(entandoCustomResource.getMetadata().getUid()).build();
    }

    public static boolean customResourceOwns(EntandoCustomResource owner, HasMetadata owned) {
        return owned.getMetadata().getOwnerReferences().stream()
                .anyMatch(ownerReference -> owner.getMetadata().getName().equals(ownerReference.getName())
                        && owner.getKind().equals(ownerReference.getKind()));
    }

    public static Map<String, String> labelsFromResource(EntandoCustomResource entandoCustomResource) {
        Map<String, String> labels = new ConcurrentHashMap<>();
        labels.put(entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getName());
        labels.put(LabelNames.RESOURCE_KIND.getName(), entandoCustomResource.getKind());
        labels.put(LabelNames.RESOURCE_NAMESPACE.getName(), entandoCustomResource.getMetadata().getNamespace());
        labels.putAll(ofNullable(entandoCustomResource.getMetadata().getLabels()).orElse(Collections.emptyMap()));
        return labels;
    }

    public static boolean addCapabilityLabels(ProvidedCapability providedCapability) {
        boolean changed = false;
        if (providedCapability.getMetadata().getLabels() == null) {
            providedCapability.getMetadata().setLabels(new HashMap<>());
        }
        Map<String, String> labels = providedCapability.getMetadata().getLabels();
        CapabilityRequirement spec = providedCapability.getSpec();
        if (!labels.containsKey(LabelNames.CAPABILITY.getName())) {
            changed = true;
            labels.put(LabelNames.CAPABILITY.getName(), spec.getCapability().getCamelCaseName());
            spec.getImplementation().ifPresent(standardCapabilityImplementation -> labels
                    .put(LabelNames.CAPABILITY_IMPLEMENTATION.getName(), standardCapabilityImplementation.getCamelCaseName()));
        }
        if (!(labels.containsKey(LabelNames.CAPABILITY_PROVISION_SCOPE.getName()) || spec.getResolutionScopePreference().isEmpty())) {
            changed = true;
            labels.put(LabelNames.CAPABILITY_PROVISION_SCOPE.getName(), spec.getResolutionScopePreference().get(0).getCamelCaseName());
        }
        return changed;
    }
}
