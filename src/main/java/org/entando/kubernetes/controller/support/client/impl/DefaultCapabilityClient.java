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

package org.entando.kubernetes.controller.support.client.impl;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.capability.SerializedCapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.client.impl.EntandoResourceClientBase;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.client.CapabilityClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;

public class DefaultCapabilityClient extends EntandoResourceClientBase implements CapabilityClient {

    public DefaultCapabilityClient(KubernetesClient client) {
        super(client);
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByName(String namespace, String name) {
        return ofNullable(client.customResources(ProvidedCapability.class).inNamespace(namespace).withName(name).fromServer().get());
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(Map<String, String> labels) {
        if (EntandoOperatorConfig.isClusterScopedDeployment()) {
            return client.customResources(ProvidedCapability.class).inAnyNamespace().withLabels(labels).list().getItems().stream()
                    .findFirst();
        } else {
            for (String namespace : EntandoOperatorConfig.getAllAccessibleNamespaces()) {
                try {
                    final Optional<ProvidedCapability> providedCapability = client.customResources(ProvidedCapability.class)
                            .inNamespace(namespace)
                            .withLabels(labels).list().getItems().stream()
                            .findFirst();
                    if (providedCapability.isPresent()) {
                        return providedCapability;
                    }
                } catch (KubernetesClientException e) {
                    if (e.getCode() != HttpURLConnection.HTTP_FORBIDDEN) {
                        throw e;
                    }
                }
            }

        }
        return Optional.empty();
    }

    @Override
    public Optional<ProvidedCapability> providedCapabilityByLabels(String namespace, Map<String, String> labels) {
        return client.customResources(ProvidedCapability.class).inNamespace(namespace).withLabels(labels).list().getItems().stream()
                .findFirst();
    }

    @Override
    public ProvidedCapability createOrPatchCapability(ProvidedCapability capability) {
        return super.createOrPatchEntandoResource(capability);
    }

    public ProvidedCapability waitForCapabilityCommencement(ProvidedCapability capability, int timeoutSeconds) throws TimeoutException {
        return waitForPhase(capability, timeoutSeconds, EntandoDeploymentPhase.STARTED, EntandoDeploymentPhase.REQUESTED);
    }

    @Override
    public ProvidedCapability waitForCapabilityCompletion(ProvidedCapability capability, int timeoutSeconds) throws TimeoutException {
        return waitForCompletion(capability, timeoutSeconds);
    }

    @Override
    public String getNamespace() {
        return client.getNamespace();
    }

    @Override
    public SerializedCapabilityProvisioningResult buildCapabilityProvisioningResult(ProvidedCapability providedCapability) {
        final ServerStatus serverStatus = providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER)
                .orElseThrow(IllegalStateException::new);
        Service service = serverStatus.getServiceName()
                .map(s -> client.services().inNamespace(providedCapability.getMetadata().getNamespace())
                        .withName(s).get()).orElse(null);
        Secret adminSecret = serverStatus.getAdminSecretName()
                .map(s -> client.secrets().inNamespace(providedCapability.getMetadata().getNamespace()).withName(s).get())
                .orElse(null);
        Ingress ingress = serverStatus.getIngressName()
                .map(s -> client.extensions().ingresses().inNamespace(providedCapability.getMetadata().getNamespace())
                        .withName(s).get()).orElse(null);
        return new SerializedCapabilityProvisioningResult(providedCapability, service, ingress, adminSecret);
    }
}
