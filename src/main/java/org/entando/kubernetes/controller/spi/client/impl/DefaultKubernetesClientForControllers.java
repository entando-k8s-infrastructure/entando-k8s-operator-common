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

package org.entando.kubernetes.controller.spi.client.impl;

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.entando.kubernetes.controller.spi.client.ExecutionResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DefaultKubernetesClientForControllers extends EntandoResourceClientBase implements KubernetesClientForControllers {

    public DefaultKubernetesClientForControllers(KubernetesClient client) {
        super(client);

    }

    @Override
    public String getNamespace() {
        return client.getNamespace();
    }

    @Override
    public Service loadControllerService(String name) {
        return client.services().inNamespace(client.getNamespace()).withName(name).get();
    }

    @Override
    public <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        return super.createOrPatchEntandoResource(r);
    }

    @Override
    public <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName) {
        return super.load(clzz, resourceNamespace, resourceName);
    }

    @Override
    public SerializedEntandoResource loadCustomResource(String apiVersion, String kind, String namespace, String name) {
        return super.loadCustomResource(apiVersion, kind, namespace, name);
    }

    @Override
    public void prepareConfig() {
        EntandoOperatorConfigBase.setConfigMap(loadOperatorConfig());
        EntandoOperatorSpiConfig.getCertificateAuthoritySecretName()
                .ifPresent(s -> TrustStoreHelper
                        .trustCertificateAuthoritiesIn(client.secrets().inNamespace(client.getNamespace()).withName(s).fromServer().get()));

    }

    public ConfigMap loadOperatorConfig() {
        return client.configMaps().inNamespace(client.getNamespace())
                .withName(KubernetesClientForControllers.ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME).fromServer().get();
    }

    @Override
    public ExecutionResult executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) throws TimeoutException {
        PodResource<Pod> podResource = this.client.pods().inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName());
        return executeAndWait(podResource, containerName, timeoutSeconds, commands);
    }

    @Override
    public HasMetadata loadStandardResource(String kind, String namespace, String name) {
        return SupportedStandardResourceKind.resolveFromKind(kind)
                .map(k -> k.getOperation(client)
                        .inNamespace(namespace)
                        .withName(name)
                        .fromServer()
                        .get())
                .orElseThrow(() -> new IllegalStateException(
                        "Resource kind '" + kind + "' not supported."));
    }

    @Override
    public List<Event> listEventsFor(EntandoCustomResource resource) {
        return client.v1().events().inAnyNamespace().withLabels(ResourceUtils.labelsFromResource(resource)).list().getItems();
    }

    public <T extends EntandoCustomResource> void issueEvent(T customResource, Event event) {
        client.v1().events().inNamespace(customResource.getMetadata().getNamespace())
                .create(event);
    }

    @SuppressWarnings({"unchecked", "java:S1905", "java:S1874"})
    //These casts are necessary to circumvent our "inaccurate" use of type parameters for our generic Serializable resources
    //We have to use the deprecated methods in question to "generically" resolve our Serializable resources
    @Override
    public <T extends EntandoCustomResource> T performStatusUpdate(T customResource, Consumer<T> consumer) {
        if (customResource instanceof SerializedEntandoResource) {
            return ioSafe(() -> {
                SerializedEntandoResource ser = (SerializedEntandoResource) customResource;
                CustomResourceDefinitionContext definition = Optional.ofNullable(ser.getDefinition()).orElse(
                        resolveDefinitionContext(ser.getKind(), ser.getApiVersion()));
                ser.setDefinition(definition);
                RawCustomResourceOperationsImpl resource = client.customResource(definition)
                        .inNamespace(customResource.getMetadata().getNamespace())
                        .withName(customResource.getMetadata().getName());
                final ObjectMapper objectMapper = new ObjectMapper();
                ser = objectMapper.readValue(objectMapper.writeValueAsString(resource.get()), SerializedEntandoResource.class);
                ser.setDefinition(definition);
                T latest = (T) ser;
                consumer.accept(latest);
                return (T) objectMapper.readValue(
                        objectMapper.writeValueAsString(resource.updateStatus(objectMapper.writeValueAsString(latest))),
                        SerializedEntandoResource.class);
            });
        } else {
            MixedOperation<T, KubernetesResourceList<T>, Resource<T>> operations = getOperations(
                    (Class<T>) customResource.getClass());
            Resource<T> resource = operations
                    .inNamespace(customResource.getMetadata().getNamespace())
                    .withName(customResource.getMetadata().getName());
            T latest = resource.fromServer().get();
            consumer.accept(latest);
            return resource.updateStatus(latest);
        }
    }

}
