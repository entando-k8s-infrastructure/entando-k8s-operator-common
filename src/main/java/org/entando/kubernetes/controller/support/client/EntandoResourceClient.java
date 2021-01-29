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

package org.entando.kubernetes.controller.support.client;

import io.fabric8.kubernetes.api.model.DoneableConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.io.Serializable;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.ClusterInfrastructureAwareSpec;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.KeycloakAwareSpec;
import org.entando.kubernetes.model.KeycloakToUse;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.plugin.EntandoPlugin;

public interface EntandoResourceClient {

    String getNamespace();

    <S extends Serializable, T extends EntandoBaseCustomResource<S>> void updateStatus(T customResource, AbstractServerStatus status);

    <S extends Serializable, T extends EntandoBaseCustomResource<S>> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    <S extends Serializable, T extends EntandoBaseCustomResource<S>> T createOrPatchEntandoResource(T r);

    <S extends Serializable, T extends EntandoBaseCustomResource<S>> void updatePhase(T customResource, EntandoDeploymentPhase phase);

    <S extends Serializable, T extends EntandoBaseCustomResource<S>> void deploymentFailed(T entandoCustomResource, Exception reason);

    <S extends Serializable, T extends EntandoBaseCustomResource<S>> Optional<ExternalDatabaseDeployment> findExternalDatabase(T resource,
            DbmsVendor vendor);

    <T extends KeycloakAwareSpec> KeycloakConnectionConfig findKeycloak(EntandoBaseCustomResource<T> resource);

    Optional<EntandoKeycloakServer> findKeycloakInNamespace(EntandoBaseCustomResource<?> peerInNamespace);

    <T extends ClusterInfrastructureAwareSpec> Optional<InfrastructureConfig> findInfrastructureConfig(
            EntandoBaseCustomResource<T> resource);

    <S extends Serializable, T extends EntandoBaseCustomResource<S>> ExposedService loadExposedService(T resource);

    default EntandoApp loadEntandoApp(String namespace, String name) {
        return load(EntandoApp.class, namespace, name);
    }

    default EntandoPlugin loadEntandoPlugin(String namespace, String name) {
        return load(EntandoPlugin.class, namespace, name);
    }

    DoneableConfigMap loadDefaultConfigMap();

    default <T extends KeycloakAwareSpec> Optional<ResourceReference> determineKeycloakToUse(EntandoBaseCustomResource<T> resource) {
        ResourceReference resourceReference = null;
        Optional<KeycloakToUse> keycloakToUse = resource.getSpec().getKeycloakToUse();
        if (keycloakToUse.isPresent()) {
            resourceReference = new ResourceReference(
                    keycloakToUse.get().getNamespace().orElse(null),
                    keycloakToUse.get().getName());
        } else {
            Optional<EntandoKeycloakServer> keycloak = findKeycloakInNamespace(resource);
            if (keycloak.isPresent()) {
                resourceReference = new ResourceReference(
                        keycloak.get().getMetadata().getNamespace(),
                        keycloak.get().getMetadata().getName());
            } else {
                DoneableConfigMap configMapResource = loadDefaultConfigMap();
                resourceReference = new ResourceReference(
                        configMapResource.getData().get(KeycloakName.DEFAULT_KEYCLOAK_NAMESPACE_KEY),
                        configMapResource.getData().get(KeycloakName.DEFAULT_KEYCLOAK_NAME_KEY));

            }
        }
        return refineResourceReference(resourceReference, resource.getMetadata());
    }

    default <T extends ClusterInfrastructureAwareSpec> Optional<ResourceReference> determineClusterInfrastructureToUse(
            EntandoBaseCustomResource<T> resource) {
        ResourceReference resourceReference = null;
        Optional<ResourceReference> clusterInfrastructureToUse = resource.getSpec().getClusterInfrastructureToUse();
        if (clusterInfrastructureToUse.isPresent()) {
            //Not ideal. GetName should not return null.
            resourceReference = new ResourceReference(
                    clusterInfrastructureToUse.get().getNamespace().orElse(null),
                    clusterInfrastructureToUse.get().getName());
        } else {
            Optional<EntandoClusterInfrastructure> clusterInfrastructure = findClusterInfrastructureInNamespace(resource);
            if (clusterInfrastructure.isPresent()) {
                resourceReference = new ResourceReference(
                        clusterInfrastructure.get().getMetadata().getNamespace(),
                        clusterInfrastructure.get().getMetadata().getName());
            } else {
                DoneableConfigMap configMapResource = loadDefaultConfigMap();
                resourceReference = new ResourceReference(
                        configMapResource.getData().get(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAMESPACE_KEY),
                        configMapResource.getData().get(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAME_KEY));

            }
        }
        return refineResourceReference(resourceReference, resource.getMetadata());
    }

    <T extends ClusterInfrastructureAwareSpec> Optional<EntandoClusterInfrastructure> findClusterInfrastructureInNamespace(
            EntandoBaseCustomResource<T> resource);

    default Optional<ResourceReference> refineResourceReference(ResourceReference resourceReference, ObjectMeta metadata) {
        if (resourceReference.getName() == null) {
            //no valid resource reference in any config anywhere. Return empty
            return Optional.empty();
        } else {
            //Default an empty namespace to the resource's own namespace
            return Optional.of(new ResourceReference(
                    resourceReference.getNamespace().orElse(metadata.getNamespace()),
                    resourceReference.getName()));
        }
    }

}