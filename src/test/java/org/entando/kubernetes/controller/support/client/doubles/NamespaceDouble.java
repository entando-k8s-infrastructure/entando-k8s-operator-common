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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class NamespaceDouble {

    private final Map<String, Service> services = new ConcurrentHashMap<>();
    private final Map<String, Ingress> ingresses = new ConcurrentHashMap<>();
    private final Map<String, Deployment> deployments = new ConcurrentHashMap<>();
    private final Map<String, Event> events = new ConcurrentHashMap<>();
    private final Map<String, Pod> pods = new ConcurrentHashMap<>();
    private final Map<String, PersistentVolumeClaim> persistentVolumeClaims = new ConcurrentHashMap<>();
    private final Map<String, Endpoints> endpointsMap = new ConcurrentHashMap<>();
    private final Map<String, Secret> secrets = new ConcurrentHashMap<>();
    private final Map<String, ConfigMap> configMaps = new ConcurrentHashMap<>();
    private final Map<String, ServiceAccount> serviceAccounts = new ConcurrentHashMap<>();
    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    private final Map<String, RoleBinding> roleBindings = new ConcurrentHashMap<>();
    private final Map<String, Map<String, EntandoCustomResource>> customResources = new
            ConcurrentHashMap<>();
    private final String name;

    public NamespaceDouble(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Service putService(Service service) {
        services.put(service.getMetadata().getName(), service);
        return service;
    }

    public Ingress putIngress(Ingress ingress) {
        ingresses.put(ingress.getMetadata().getName(), ingress);
        return ingress;
    }

    public Ingress getIngress(String name) {
        return ingresses.get(name);
    }

    public Service getService(String name) {
        return services.get(name);
    }

    public Deployment putDeployment(Deployment deployment) {
        this.deployments.put(deployment.getMetadata().getName(), deployment);
        return deployment;
    }

    public Deployment getDeployment(String name) {
        return deployments.get(name);
    }

    public Map<String, Deployment> getDeployments() {
        return deployments;
    }

    public Pod getPod(String name) {
        return this.pods.get(name);
    }

    public void putPod(Pod pod) {
        this.pods.put(pod.getMetadata().getName(), pod);
    }

    public Map<String, Pod> getPods() {
        return pods;
    }

    public void putPersistentVolumeClaim(PersistentVolumeClaim persistentVolumeClaim) {
        this.persistentVolumeClaims.put(persistentVolumeClaim.getMetadata().getName(), persistentVolumeClaim);
    }

    public PersistentVolumeClaim getPersistentVolumeClaim(String name) {
        return persistentVolumeClaims.get(name);
    }

    public Map<String, PersistentVolumeClaim> getPersistentVolumeClaims() {
        return persistentVolumeClaims;
    }

    public void putEndpoints(Endpoints endpoints) {
        this.endpointsMap.put(endpoints.getMetadata().getName(), endpoints);

    }

    public Endpoints getEndpoints(String name) {
        return endpointsMap.get(name);
    }

    public void putSecret(Secret secret) {
        this.secrets.put(secret.getMetadata().getName(), secret);
    }

    public Secret getSecret(String secretName) {
        return this.secrets.get(secretName);
    }

    public Map<String, Secret> getSecrets() {
        return secrets;
    }

    public void putServiceAccount(ServiceAccount serviceAccount) {
        this.serviceAccounts.put(serviceAccount.getMetadata().getName(), serviceAccount);
    }

    public ServiceAccount getServiceAccount(String name) {
        return this.serviceAccounts.get(name);
    }

    public Map<String, ServiceAccount> getServiceAccounts() {
        return serviceAccounts;
    }

    public void putRole(Role role) {
        this.roles.put(role.getMetadata().getName(), role);
    }

    public Role getRole(String name) {
        return this.roles.get(name);
    }

    public Map<String, Role> getRoles() {
        return roles;
    }

    public void putRoleBinding(RoleBinding roleBinding) {
        this.roleBindings.put(roleBinding.getMetadata().getName(), roleBinding);
    }

    public RoleBinding getRoleBinding(String name) {
        return this.roleBindings.get(name);
    }

    public Map<String, RoleBinding> getRoleBindings() {
        return roleBindings;
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> Map<String, T> getCustomResources(Class<T> customResource) {
        return (Map<String, T>) customResources.computeIfAbsent(customResource.getSimpleName(), kind -> new ConcurrentHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> Map<String, T> getCustomResources(String kind) {
        return (Map<String, T>) customResources.computeIfAbsent(kind, s -> new ConcurrentHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T getCustomResource(String kind, String name) {
        return (T) getCustomResources(kind).get(name);
    }

    public void putCustomResource(EntandoCustomResource resource) {
        getCustomResources(resource.getKind()).put(resource.getMetadata().getName(), resource);
    }

    public ConfigMap getConfigMap(String configMapName) {
        return configMaps.get(configMapName);
    }

    public Map<String, Collection<? extends HasMetadata>> getKubernetesState() {
        Map<String, Collection<? extends HasMetadata>> result = new ConcurrentHashMap<>();
        result.put("services", services.values());
        result.put("ingresses", ingresses.values());
        result.put("deployments", deployments.values());
        result.put("events", events.values());
        result.put("pods", pods.values());
        result.put("persistentVolumeClaims", persistentVolumeClaims.values());
        result.put("endpointsMap", endpointsMap.values());
        result.put("secrets", secrets.values());
        result.put("configMaps", configMaps.values());
        result.put("serviceAccounts", serviceAccounts.values());
        result.put("roles", roles.values());
        result.put("roleBinding", roleBindings.values());
        this.customResources.forEach(
                (kind, stringEntandoCustomResourceMap) -> result.put(kind, stringEntandoCustomResourceMap.values())
        );
        return result;
    }

    public Map<String, ConfigMap> getConfigMaps() {
        return configMaps;
    }

    public void putConfigMap(ConfigMap configMap) {
        this.configMaps.put(configMap.getMetadata().getName(), configMap);
    }

    public Event putEvent(Event event) {
        this.events.put(event.getMetadata().getName(), event);
        return event;
    }
}
