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
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractK8SClientDouble {

    protected static boolean matchesSelector(Map<String, String> selector, HasMetadata resource) {
        return selector.entrySet().stream().allMatch(
                entry -> resource.getMetadata().getLabels() != null
                        && resource.getMetadata().getLabels().containsKey(entry.getKey())
                        && (entry.getValue() == null || entry.getValue().equals(resource.getMetadata().getLabels().get(entry.getKey()))));
    }

    public static final String CONTROLLER_NAMESPACE = "controller-namespace";
    private final ConcurrentHashMap<String, NamespaceDouble> namespaces;
    private final ClusterDouble cluster;

    public AbstractK8SClientDouble() {
        this(new ConcurrentHashMap<>(), new ClusterDouble());
    }

    public AbstractK8SClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        this.namespaces = namespaces;
        this.cluster = cluster;
        getNamespace(CONTROLLER_NAMESPACE);
    }

    public ClusterDouble getCluster() {
        return cluster;
    }

    public Map<String, Map<String, Collection<? extends HasMetadata>>> getKubernetesState() {
        Map<String, Map<String, Collection<? extends HasMetadata>>> result = new ConcurrentHashMap<>();
        namespaces.forEach((namespaceName, namespaceDouble) -> result.put(namespaceName, namespaceDouble.getKubernetesState()));
        result.put("ClusterScopedResource", this.cluster.getKubernetesState());
        return result;
    }

    protected NamespaceDouble getNamespace(HasMetadata customResource) {
        return getNamespace(customResource.getMetadata().getNamespace());
    }

    protected synchronized NamespaceDouble getNamespace(String namespace) {
        return this.namespaces.computeIfAbsent(namespace, NamespaceDouble::new);
    }

    public ConcurrentHashMap<String, NamespaceDouble> getNamespaces() {
        return namespaces;
    }
}
