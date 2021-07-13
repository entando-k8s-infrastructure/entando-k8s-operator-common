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

package org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import org.entando.kubernetes.controller.support.client.impl.DefaultKeycloakClient;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

public interface KeycloakTestHelper {

    default void deleteRealm(String realm) {
        try {
            getKeycloak().realm(realm).remove();
        } catch (NotFoundException e) {
            //can ignore this
        }
    }

    default List<RoleRepresentation> retrieveServiceAccountRolesInRealm(String realmName, String serviceAccountClientId,
            String targetClientId) {
        RealmResource realm = getKeycloak().realm(realmName);
        ClientsResource clients = realm.clients();
        ClientRepresentation serviceAccountClient = clients.findByClientId(serviceAccountClientId).get(0);
        ClientRepresentation targetClient = clients.findByClientId(targetClientId).get(0);
        UserRepresentation serviceAccountUser = clients.get(serviceAccountClient.getId()).getServiceAccountUser();
        return realm.users().get(serviceAccountUser.getId()).roles().clientLevel(targetClient.getId()).listAll();
    }

    default Optional<ClientRepresentation> findClientInRealm(String realmName, String clientId) {
        try {
            RealmResource realm = getKeycloak().realm(realmName);
            ClientsResource clients = realm.clients();
            return clients.findByClientId(clientId).stream().findFirst();
        } catch (ClientErrorException e) {
            if (e.getResponse() != null && e.getResponse().getEntity() != null) {
                System.out.println(e.toString());
                System.out.println(e.getResponse().getEntity());
            }
            throw e;
        }
    }

    default DefaultKeycloakClient connectToExistingKeycloak() {
        DefaultKeycloakClient keycloakClient = new DefaultKeycloakClient();
        if (EntandoOperatorTestConfig.lookupProperty(EntandoOperatorTestConfig.ENTANDO_TEST_KEYCLOAK_BASE_URL).isEmpty()) {
            try (DefaultKubernetesClient defaultKubernetesClient = new DefaultKubernetesClient()) {
                final Secret secret = defaultKubernetesClient.secrets().inNamespace("jx").withName("entando-jx-common-secret").get();
                keycloakClient.login(decodeData(secret, "keycloak.base.url"),
                        decodeData(secret, "keycloak.admin.user"),
                        decodeData(secret, "keycloak.admin.password"));
            }
        } else {
            keycloakClient.login(EntandoOperatorTestConfig.getKeycloakBaseUrl(),
                    EntandoOperatorTestConfig.getKeycloakUser(),
                    EntandoOperatorTestConfig.getKeycloakPassword());
        }
        return keycloakClient;
    }

    private String decodeData(Secret secret, String o) {
        return new String(Base64.getDecoder().decode(secret.getData().get(o)), StandardCharsets.UTF_8);
    }

    Keycloak getKeycloak();
}
