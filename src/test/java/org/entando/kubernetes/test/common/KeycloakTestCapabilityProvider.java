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

package org.entando.kubernetes.test.common;

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import javax.ws.rs.NotFoundException;
import org.entando.kubernetes.controller.spi.capability.SerializedCapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedSsoCapability;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.impl.DefaultKeycloakClient;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.ProvidedCapabilityBuilder;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;

public class KeycloakTestCapabilityProvider {

    private Secret entandoBuildSecret;
    private final SimpleK8SClient<?> client;
    private final String targetNamespace;
    private URL baseUrl;

    public KeycloakTestCapabilityProvider(SimpleK8SClient<?> client, String targetNamespace) {
        this.client = client;
        this.targetNamespace = targetNamespace;
    }

    public void deleteTestRealms(ProvidedCapability providedCapability, String... names) {
        final ProvidedCapability reloaded = client.entandoResources().reload(providedCapability);
        final ProvidedSsoCapability sso = new ProvidedSsoCapability(
                new SerializedCapabilityProvisioningResult(reloaded, null, null, client.secrets()
                        .loadSecret(reloaded,
                                reloaded.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getAdminSecretName().get())));
        final DefaultKeycloakClient keycloak = new DefaultKeycloakClient();
        keycloak.login(sso.getExternalBaseUrl(), sso.getUsername(), sso.getPassword());
        Arrays.stream(names)
                .forEach(realm -> {
                    try {
                        keycloak.getKeycloak().realm(realm).remove();
                    } catch (NotFoundException e) {
                        //This is OK
                    }
                });
    }

    public ProvidedCapability provideKeycloakCapability() {
        final ProvidedCapability providedCapability = createKeycloakCapability();
        return forceSuccessfulStatus(providedCapability);
    }

    public ProvidedCapability createKeycloakCapability() {
        final ProvidedCapability requiredCapability = new ProvidedCapabilityBuilder()
                .withNewMetadata()
                .withNamespace(targetNamespace)
                .withName("default-sso-in-namespace")
                .addToLabels(LabelNames.CAPABILITY.getName(), StandardCapability.SSO.getCamelCaseName())
                .addToLabels(LabelNames.CAPABILITY_PROVISION_SCOPE.getName(), CapabilityScope.NAMESPACE.getCamelCaseName())
                .endMetadata().build();
        client.secrets().createSecretIfAbsent(requiredCapability, new SecretBuilder()
                .withNewMetadata()
                .withName(NameUtils.standardAdminSecretName(requiredCapability))
                .withNamespace(targetNamespace)
                .endMetadata()
                .addToStringData(SecretUtils.USERNAME_KEY, getUsername())
                .addToStringData(SecretUtils.PASSSWORD_KEY, getPassword())
                .build());
        final ProvidedCapability providedCapability = client.entandoResources()
                .createOrPatchEntandoResource(new ProvidedCapabilityBuilder(requiredCapability)
                        .withNewSpec()
                        .withCapability(StandardCapability.SSO)
                        .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                        .withProvisioningStrategy(CapabilityProvisioningStrategy.USE_EXTERNAL)
                        .withNewExternallyProvidedService()
                        .withAdminSecretName(NameUtils.standardAdminSecretName(requiredCapability))
                        .withHost(getBaseUrl().getHost())
                        .withPort(getPort())
                        .withPath(getBaseUrl().getPath())
                        .endExternallyProvidedService()
                        .addAllToCapabilityParameters(Map.of(ProvidedSsoCapability.DEFAULT_REALM_PARAMETER, targetNamespace))
                        .endSpec()
                        .build());
        return providedCapability;
    }

    private ProvidedCapability forceSuccessfulStatus(ProvidedCapability providedCapability) {
        final ServerStatus serverStatus = new ServerStatus(NameUtils.MAIN_QUALIFIER)
                .withOriginatingCustomResource(providedCapability)
                .withOriginatingControllerPod(client.entandoResources().getNamespace(), EntandoOperatorSpiConfig.getControllerPodName());
        serverStatus.setExternalBaseUrl(getBaseUrlString());

        serverStatus.setAdminSecretName(NameUtils.standardAdminSecretName(providedCapability));
        client.entandoResources().updateStatus(providedCapability, serverStatus);
        return client.entandoResources().updatePhase(providedCapability, EntandoDeploymentPhase.SUCCESSFUL);
    }

    private int getPort() {
        if (getBaseUrl().getPort() == -1) {
            return getBaseUrl().getDefaultPort();
        } else {
            return getBaseUrl().getPort();
        }
    }

    private URL getBaseUrl() {
        this.baseUrl = Objects.requireNonNullElseGet(this.baseUrl, () -> ioSafe(() -> new URL(getBaseUrlString())));
        return this.baseUrl;
    }

    private String getUsername() {
        if (EntandoOperatorTestConfig.lookupProperty(EntandoOperatorTestConfig.ENTANDO_TEST_KEYCLOAK_BASE_URL).isPresent()) {
            return EntandoOperatorTestConfig.getKeycloakUser();
        } else {
            return new String(Base64.getDecoder().decode(getEntandoBuildSecret().getData().get("keycloak.admin.user")),
                    StandardCharsets.UTF_8);
        }
    }

    private String getPassword() {
        if (EntandoOperatorTestConfig.lookupProperty(EntandoOperatorTestConfig.ENTANDO_TEST_KEYCLOAK_BASE_URL).isPresent()) {
            return EntandoOperatorTestConfig.getKeycloakPassword();
        } else {
            return new String(Base64.getDecoder().decode(getEntandoBuildSecret().getData().get("keycloak.admin.password")),
                    StandardCharsets.UTF_8);
        }
    }

    private String getBaseUrlString() {
        if (EntandoOperatorTestConfig.lookupProperty(EntandoOperatorTestConfig.ENTANDO_TEST_KEYCLOAK_BASE_URL).isPresent()) {
            return EntandoOperatorTestConfig.getKeycloakBaseUrl();
        } else {
            return new String(Base64.getDecoder().decode(getEntandoBuildSecret().getData().get("keycloak.base.url")),
                    StandardCharsets.UTF_8);
        }
    }

    private Secret getEntandoBuildSecret() {
        this.entandoBuildSecret = Objects
                .requireNonNullElseGet(this.entandoBuildSecret, () -> client.secrets().loadControllerSecret("entando-jx-common-secret"));
        return this.entandoBuildSecret;
    }
}
