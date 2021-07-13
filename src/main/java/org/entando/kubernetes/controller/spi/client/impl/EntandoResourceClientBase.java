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

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.interruptionSafe;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListOptions;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;

public class EntandoResourceClientBase {

    protected final KubernetesClient client;
    private ConfigMap crdNameMap;

    public EntandoResourceClientBase(KubernetesClient client) {
        this.client = client;
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T waitForCompletion(T customResource, int timeoutSeconds) throws TimeoutException {
        return waitForPhase(customResource, timeoutSeconds, EntandoDeploymentPhase.IGNORED, EntandoDeploymentPhase.FAILED,
                EntandoDeploymentPhase.SUCCESSFUL);
    }

    protected <T extends EntandoCustomResource> T waitForPhase(T customResource, int timeoutSeconds, EntandoDeploymentPhase... phases)
            throws TimeoutException {
        Predicate<EntandoCustomResource> predicate = resource -> resource.getStatus().getPhase() != null && Set
                .of(phases).contains(resource.getStatus().getPhase());
        final T reloaded = reload(customResource);
        if (predicate.test(reloaded)) {
            return reloaded;
        }
        final CompletableFuture<T> future = new CompletableFuture<>();
        final CustomResourceDefinitionContext definition;
        if (customResource instanceof SerializedEntandoResource) {
            definition = ((SerializedEntandoResource) customResource).getDefinition();
        } else {
            definition = CustomResourceDefinitionContext.fromCustomResourceType(((CustomResource<?, ?>) customResource).getClass());
        }
        try (Watch ignore = ioSafe(() -> client.customResource(definition)
                .watch(customResource.getMetadata().getNamespace(), customResource.getMetadata().getName(), null,
                        (ListOptions) null, new Watcher<>() {
                            final ObjectMapper objectMapper = new ObjectMapper();

                            @Override
                            public void eventReceived(Action action, String s) {
                                final T resource = ioSafe(() -> objectMapper.readValue(s, (Class<T>) customResource.getClass()));
                                if (resource instanceof SerializedEntandoResource) {
                                    ((SerializedEntandoResource) resource).setDefinition(definition);
                                }
                                if (predicate.test(resource)) {
                                    future.complete(resource);
                                }
                            }

                            @Override
                            public void onClose(WatcherException cause) {
                                if (cause.getMessage().contains("resourceVersion") && cause.getMessage().contains("too old")) {
                                    //reconnect - resource went out of sync. happens on occasion.
                                    ioSafe(() -> client.customResource(definition)
                                            .watch(customResource.getMetadata().getNamespace(),
                                                    customResource.getMetadata().getName(),
                                                    null,
                                                    (ListOptions) null, this));
                                } else {
                                    future.completeExceptionally(cause);
                                }
                            }
                        }))) {
            return interruptionSafe(() -> future.get(timeoutSeconds, TimeUnit.SECONDS));
        }
    }

    protected SerializedEntandoResource loadCustomResource(String apiVersion, String kind, String namespace, String name) {
        return ioSafe(() -> {
            final CustomResourceDefinitionContext context = resolveDefinitionContext(kind, apiVersion);
            final Map<String, Object> crMap = client.customResource(context).get(namespace, name);
            final ObjectMapper objectMapper = new ObjectMapper();
            final SerializedEntandoResource serializedEntandoResource = objectMapper
                    .readValue(objectMapper.writeValueAsString(crMap), SerializedEntandoResource.class);
            serializedEntandoResource.setDefinition(context);
            return serializedEntandoResource;
        });
    }

    protected <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName) {
        return getOperations(clzz).inNamespace(resourceNamespace).withName(resourceName).fromServer().get();
    }

    @SuppressWarnings("unchecked")
    protected <T extends EntandoCustomResource> MixedOperation<T, KubernetesResourceList<T>, Resource<T>> getOperations(Class<T> c) {
        return client.customResources((Class) c);
    }

    @SuppressWarnings({"unchecked"})
    protected <T extends EntandoCustomResource> T reload(T customResource) {
        if (customResource instanceof SerializedEntandoResource) {
            return (T) loadCustomResource(
                    customResource.getApiVersion(),
                    customResource.getKind(),
                    customResource.getMetadata().getNamespace(),
                    customResource.getMetadata().getName());
        } else {
            return (T) load(customResource.getClass(), customResource.getMetadata().getNamespace(), customResource.getMetadata().getName());
        }
    }

    protected CustomResourceDefinitionContext resolveDefinitionContext(String kind, String apiVersion) {
        final String key = kind + "." + apiVersion.substring(0, apiVersion.indexOf("/"));
        final String name = getCrdNameMap().getData().get(key);
        return CustomResourceDefinitionContext.fromCrd(client.apiextensions().v1beta1().customResourceDefinitions()
                .withName(name).get());
    }

    protected ConfigMap getCrdNameMap() {
        crdNameMap = Objects.requireNonNullElseGet(crdNameMap,
                () -> Objects.requireNonNullElseGet(
                        client.configMaps().inNamespace(client.getNamespace()).withName(
                                KubernetesClientForControllers.ENTANDO_CRD_NAMES_CONFIG_MAP).fromServer().get(),
                        this::createConfigMap));

        return crdNameMap;
    }

    private ConfigMap createConfigMap() {
        try {
            return client.configMaps().inNamespace(client.getNamespace())
                    .create(new ConfigMapBuilder()
                            .withNewMetadata()
                            .withNamespace(client.getNamespace())
                            .withName(KubernetesClientForControllers.ENTANDO_CRD_NAMES_CONFIG_MAP)
                            .endMetadata()
                            .addToData(client.apiextensions().v1beta1().customResourceDefinitions()
                                    .withLabel(LabelNames.CRD_OF_INTEREST.getName())
                                    .list()
                                    .getItems()
                                    .stream()
                                    .collect(Collectors
                                            .toMap(crd -> crd.getSpec().getNames().getKind() + "." + crd.getSpec()
                                                            .getGroup(),
                                                    crd -> crd.getMetadata().getName())))
                            .build());
        } catch (KubernetesClientException e) {
            if (e.getStatus().getCode() == HttpURLConnection.HTTP_CONFLICT) {
                return client.configMaps().inNamespace(client.getNamespace()).withName(
                        KubernetesClientForControllers.ENTANDO_CRD_NAMES_CONFIG_MAP).fromServer().get();

            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r) {
        final MixedOperation<T, KubernetesResourceList<T>, Resource<T>> operations = getOperations((Class<T>) r.getClass());
        if (operations.inNamespace(r.getMetadata().getNamespace()).withName(r.getMetadata().getName()).get() == null) {
            return operations.inNamespace(r.getMetadata().getNamespace()).create(r);
        } else {
            return operations.inNamespace(r.getMetadata().getNamespace()).withName(r.getMetadata().getName()).patch(r);
        }
    }
}
