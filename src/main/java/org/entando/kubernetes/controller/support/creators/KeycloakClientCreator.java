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

package org.entando.kubernetes.controller.support.creators;

import static java.util.Optional.ofNullable;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;
import static org.entando.kubernetes.controller.support.creators.IngressCreator.getIngressServerUrl;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.container.HasWebContext;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.SsoAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class KeycloakClientCreator {

    private final EntandoCustomResource entandoCustomResource;
    private String realm;
    private String ssoClientId;

    public KeycloakClientCreator(EntandoCustomResource entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    public void createKeycloakClients(SecretClient secrets, SimpleKeycloakClient keycloak, SsoAwareDeployable<?> deployable,
            Ingress ingress) {
        login(keycloak, deployable);
        if (deployable instanceof PublicIngressingDeployable) {
            //Create a public keycloak client
            PublicIngressingDeployable<?> publicIngressingDeployable = (PublicIngressingDeployable<?>) deployable;
            keycloak.createPublicClient(publicIngressingDeployable.getSsoClientConfig().getRealm(),
                    publicIngressingDeployable.getPublicClientId().orElse(KeycloakName.PUBLIC_CLIENT_ID),
                    getIngressServerUrl(ingress));

        }
        createClient(secrets, keycloak, deployable, ingress);
    }

    private void login(SimpleKeycloakClient client, SsoAwareDeployable<?> deployable) {
        client.login(deployable.getSsoConnectionInfo().getBaseUrlToUse(), deployable.getSsoConnectionInfo().getUsername(),
                deployable.getSsoConnectionInfo().getPassword());
    }

    private void createClient(SecretClient secrets, SimpleKeycloakClient client, SsoAwareDeployable<?> deployable,
            Ingress optionalIngress) {
        SsoClientConfig ssoClientConfig = ofNullable(optionalIngress)
                .map(ingress -> augmentClientConfigWithExternalUris(deployable, ingress))
                .orElse(deployable.getSsoClientConfig());
        String keycloakClientSecret = client.prepareClientAndReturnSecret(ssoClientConfig);
        String secretName = KeycloakName.forTheClientSecret(ssoClientConfig);
        final Secret builtSecret = new SecretBuilder()
                .withNewMetadata()
                .withOwnerReferences(ResourceUtils.buildOwnerReference(entandoCustomResource))
                .withName(secretName)
                .endMetadata()
                .addToStringData(KeycloakName.CLIENT_ID_KEY, ssoClientConfig.getClientId())
                .addToStringData(KeycloakName.CLIENT_SECRET_KEY, keycloakClientSecret)
                .build();
        ssoClientId = ssoClientConfig.getClientId();
        //TODO there can only be one realm per deployment. This needs to be carried at the deployable level
        //In fact, the entire ssoClientConfig should probably be at the deployable level.
        this.realm = ssoClientConfig.getRealm();
        withDiagnostics(() -> {
            secrets.createSecretIfAbsent(entandoCustomResource, builtSecret);
            return null;
        }, () -> builtSecret);
    }

    private SsoClientConfig augmentClientConfigWithExternalUris(SsoAwareDeployable<?> deployable, Ingress ingress) {
        SsoClientConfig ssoClientConfig = deployable.getSsoClientConfig();
        for (HasWebContext container : deployable.getContainers().stream()
                .filter(HasWebContext.class::isInstance)
                .map(HasWebContext.class::cast)
                .collect(Collectors.toList())) {
            ssoClientConfig = ssoClientConfig
                    .withRedirectUri(getIngressServerUrl(ingress) + container.getWebContextPath() + "/*");
            if (ingress.getSpec().getTls().size() == 1) {
                //Also support redirecting to http for http services that don't have knowledge that they are exposed as https
                //TODO revisit this. This could be a Java only problem that we may be able to fix with X-Forwarded-* headers
                ssoClientConfig = ssoClientConfig.withRedirectUri(
                        "http://" + ingress.getSpec().getRules().get(0).getHost() + container.getWebContextPath() + "/*");
            }
        }
        return ssoClientConfig.withWebOrigin(getIngressServerUrl(ingress));

    }

    public String getRealm() {
        return realm;
    }

    public String getSsoClientId() {
        return this.ssoClientId;
    }
}
