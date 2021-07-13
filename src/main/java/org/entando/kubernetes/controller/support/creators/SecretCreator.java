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

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class SecretCreator extends AbstractK8SResourceCreator {

    public SecretCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public void createSecrets(SecretClient client, Deployable<?> deployable) {
        EntandoOperatorSpiConfig.getCertificateAuthoritySecretName().ifPresent(s -> {
            cloneControllerSecret(client, s);
            cloneControllerSecret(client, TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET);
        });
        EntandoOperatorConfig.getTlsSecretName().ifPresent(s -> cloneControllerSecret(client, s));
        if (EntandoOperatorConfig.useAutoCertGeneration()) {
            cloneControllerSecret(client, SecretUtils.EMPTY_TLS_SECRET_NAME);
        }
        if (deployable instanceof Secretive) {
            for (Secret secret : ((Secretive) deployable).getSecrets()) {
                createSecret(client, secret);
            }
        }
        EntandoOperatorConfig.getImagePullSecrets().forEach(s -> ofNullable(client.loadControllerSecret(s))
                .ifPresent(secret -> client.createSecretIfAbsent(entandoCustomResource,
                        new SecretBuilder()
                                .withNewMetadata()
                                .withName(secret.getMetadata().getName())
                                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                                .withLabels(secret.getMetadata().getLabels())
                                .withAnnotations(secret.getMetadata().getAnnotations())
                                .endMetadata()
                                .withType(secret.getType())
                                .withData(secret.getData())
                                .withStringData(secret.getStringData())
                                .build())));
    }

    private void cloneControllerSecret(SecretClient client, String name) {
        final Secret secret = client.loadControllerSecret(name);
        if (secret != null) {
            withDiagnostics(() -> {
                client.createSecretIfAbsent(entandoCustomResource, new SecretBuilder()
                        .withNewMetadata()
                        .withLabels(secret.getMetadata().getLabels())
                        .withAnnotations(secret.getMetadata().getAnnotations())
                        .withName(name)
                        .endMetadata()
                        .withType(secret.getType())
                        .withData(secret.getData())
                        .withStringData(secret.getStringData())
                        .build());
                return null;
            }, () -> secret);
        }
    }

    private void createSecret(SecretClient client, Secret secret) {
        ObjectMeta metadata = fromCustomResource(secret.getMetadata().getName());
        ofNullable(secret.getMetadata().getLabels()).ifPresent(map -> metadata.getLabels().putAll(map));
        secret.setMetadata(metadata);
        client.createSecretIfAbsent(entandoCustomResource, secret);
    }
}
