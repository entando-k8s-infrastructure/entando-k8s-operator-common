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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class ResourceUtils {

    public static final String ENTANDO_RESOURCE_KIND_LABEL_NAME = "EntandoResourceKind";
    public static final String ENTANDO_RESOURCE_NAMESPACE_LABEL_NAME = "EntandoResourceNamespace";

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
        labels.put(ENTANDO_RESOURCE_KIND_LABEL_NAME, entandoCustomResource.getKind());
        labels.put(ENTANDO_RESOURCE_NAMESPACE_LABEL_NAME, entandoCustomResource.getMetadata().getNamespace());
        labels.putAll(ofNullable(entandoCustomResource.getMetadata().getLabels()).orElse(Collections.emptyMap()));
        return labels;
    }
}
