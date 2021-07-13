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

package org.entando.kubernetes.controller.support.client.doubles;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClusterDouble {

    private final Map<String, CustomResourceDefinition> customResourceDefinitions = new ConcurrentHashMap<>();
    protected KubernetesResourceProcessor resourceProcessor = new KubernetesResourceProcessor();

    public Map<String, Collection<? extends HasMetadata>> getKubernetesState() {
        return Map.of("customResourceDefinitions", customResourceDefinitions.values());
    }

    public CustomResourceDefinition getCustomResourceDefinition(String name) {
        return this.customResourceDefinitions.get(name);
    }

    public CustomResourceDefinition putCustomResourceDefinition(CustomResourceDefinition customResourceDefinition) {
        return resourceProcessor.processResource(this.customResourceDefinitions, customResourceDefinition);
    }

    public Map<String, CustomResourceDefinition> getCustomResourceDefinitions() {
        return customResourceDefinitions;
    }

    public KubernetesResourceProcessor getResourceProcessor() {
        return resourceProcessor;
    }
}
