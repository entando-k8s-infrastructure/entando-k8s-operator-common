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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.KeycloakPreference;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.database.ExternalDatabaseDeployment;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.KeycloakToUse;
import org.entando.kubernetes.model.common.ResourceReference;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public interface EntandoResourceClient {

    String getNamespace();

    void updateStatus(EntandoCustomResource customResource, AbstractServerStatus status);

    <T extends EntandoCustomResource> T reload(T customResource);

    <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r);

    void updatePhase(EntandoCustomResource customResource, EntandoDeploymentPhase phase);

    void deploymentFailed(EntandoCustomResource entandoCustomResource, Exception reason);

    Optional<ExternalDatabaseDeployment> findExternalDatabase(EntandoCustomResource peerInNamespace, DbmsVendor vendor);

    KeycloakConnectionConfig findKeycloak(EntandoCustomResource resource, KeycloakPreference keycloakPreference);

    Optional<EntandoKeycloakServer> findKeycloakInNamespace(EntandoCustomResource peerInNamespace);

    ExposedService loadExposedService(EntandoCustomResource resource);

    DoneableConfigMap loadDefaultCapabilitiesConfigMap();

    ConfigMap loadDockerImageInfoConfigMap();

    ConfigMap loadOperatorConfig();

    default Optional<ResourceReference> determineKeycloakToUse(EntandoCustomResource resource,
            KeycloakPreference keycloakPreference) {
        ResourceReference resourceReference = null;
        Optional<KeycloakToUse> keycloakToUse = keycloakPreference.getPreferredKeycloakToUse();
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
                DoneableConfigMap configMapResource = loadDefaultCapabilitiesConfigMap();
                resourceReference = new ResourceReference(
                        configMapResource.getData().get(KeycloakName.DEFAULT_KEYCLOAK_NAMESPACE_KEY),
                        configMapResource.getData().get(KeycloakName.DEFAULT_KEYCLOAK_NAME_KEY));

            }
        }
        return refineResourceReference(resourceReference, resource.getMetadata());
    }

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
