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

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.interruptionSafe;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.entando.kubernetes.controller.spi.client.ExecutionResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.client.impl.SupportedStandardResourceKind;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;

public class EntandoResourceClientDouble extends EntandoResourceClientDoubleBase implements EntandoResourceClient {

    private ConfigMap crdNameMap;

    public EntandoResourceClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
        final ConfigMapBuilder builder = new ConfigMapBuilder(
                Objects.requireNonNullElseGet(getNamespace(CONTROLLER_NAMESPACE).getConfigMap(ENTANDO_CRD_NAMES_CONFIG_MAP), ConfigMap::new)
        );
        crdNameMap = getCluster().getResourceProcessor()
                .processResource(getNamespace(CONTROLLER_NAMESPACE).getConfigMaps(), builder.withNewMetadata()
                        .withName(ENTANDO_CRD_NAMES_CONFIG_MAP)
                        .withNamespace(CONTROLLER_NAMESPACE).endMetadata().build());

    }

    public <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        if (r != null) {
            return this.getCluster().getResourceProcessor().processResource(getNamespace(r).getCustomResources(r.getKind()), r);
        }
        return null;
    }

    @Override
    public ExecutionResult executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) throws TimeoutException {
        if (pod != null) {
            PodResource<Pod> podResource = new PodResourceDouble();
            return executeAndWait(podResource, containerName, timeoutSeconds, commands);
        }
        return null;
    }

    @Override
    public String getNamespace() {
        return CONTROLLER_NAMESPACE;
    }

    @Override
    public Service loadControllerService(String name) {
        return getNamespace(CONTROLLER_NAMESPACE).getService(name);
    }

    @Override
    public void prepareConfig() {
        EntandoOperatorConfigBase.setConfigMap(loadOperatorConfig());
    }

    @Override
    public <T extends EntandoCustomResource> T load(Class<T> clzz, String namespace, String name) {
        Map<String, T> customResources = getNamespace(namespace).getCustomResources(clzz);
        T t = customResources.get(name);
        if (clzz.isInstance(t)) {
            return t;
        } else {
            ObjectMapper objectMapper = new ObjectMapper();
            return ioSafe(() -> objectMapper.readValue(objectMapper.writeValueAsString(t), clzz));
        }
    }

    @Override
    public ConfigMap loadDockerImageInfoConfigMap() {
        return getNamespace(EntandoOperatorConfig.getEntandoDockerImageInfoNamespace().orElse(CONTROLLER_NAMESPACE))
                .getConfigMap(EntandoOperatorConfig.getEntandoDockerImageInfoConfigMap());
    }

    @Override
    public ConfigMap loadOperatorConfig() {
        return getNamespace(CONTROLLER_NAMESPACE).getConfigMap(KubernetesClientForControllers.ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME);
    }

    @Override
    public SerializedEntandoResource loadCustomResource(String apiVersion, String kind, String namespace, String name) {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            final SerializedEntandoResource resource = objectMapper
                    .readValue(objectMapper.writeValueAsString(getNamespace(namespace).getCustomResources(kind).get(name)),
                            SerializedEntandoResource.class);
            final String group = kind + "." + apiVersion.substring(0, apiVersion.indexOf("/"));
            resource.setDefinition(CustomResourceDefinitionContext.fromCrd(getCluster()
                    .getCustomResourceDefinition(crdNameMap.getData().get(group))));
            return resource;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public HasMetadata loadStandardResource(String kind, String namespace, String name) {
        switch (SupportedStandardResourceKind.resolveFromKind(kind).get()) {
            case DEPLOYMENT:
                return getNamespace(namespace).getDeployment(name);
            case INGRESS:
                return getNamespace(namespace).getIngress(name);
            case SERVICE:
                return getNamespace(namespace).getService(name);
            case SECRET:
                return getNamespace(namespace).getSecret(name);
            case POD:
                return getNamespace(namespace).getPod(name);
            case PERSISTENT_VOLUME_CLAIM:
                return getNamespace(namespace).getPersistentVolumeClaim(name);
            default:
                return null;
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T extends EntandoCustomResource> T performStatusUpdate(T customResource, Consumer<T> consumer) {
        final T reloaded = (T) getNamespace(customResource.getMetadata().getNamespace()).getCustomResources(customResource.getKind())
                .get(customResource.getMetadata().getName());
        consumer.accept(reloaded);
        return getCluster().getResourceProcessor()
                .processResource(getNamespace(customResource).getCustomResources(customResource.getKind()), reloaded);
    }

    @Override
    public <T extends EntandoCustomResource> void issueEvent(T customResource, Event event) {
        getNamespace(customResource).putEvent(event);
    }

    @Override
    public List<Event> listEventsFor(EntandoCustomResource resource) {
        return Collections.emptyList();
    }

    public void registerCustomResourceDefinition(String resourceName) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        final CustomResourceDefinition crd = objectMapper
                .readValue(Thread.currentThread().getContextClassLoader().getResource(resourceName),
                        CustomResourceDefinition.class);
        getCluster().putCustomResourceDefinition(crd);
        crdNameMap = getCluster().getResourceProcessor().processResource(getNamespace(CONTROLLER_NAMESPACE).getConfigMaps(),
                new ConfigMapBuilder(getNamespace(CONTROLLER_NAMESPACE).getConfigMap(ENTANDO_CRD_NAMES_CONFIG_MAP))
                        .addToData(crd.getSpec().getNames().getKind() + "." + crd.getSpec().getGroup(), crd.getMetadata().getName())
                        .build());

    }

}
