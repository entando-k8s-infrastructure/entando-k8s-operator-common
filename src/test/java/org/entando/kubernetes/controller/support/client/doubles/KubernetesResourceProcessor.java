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

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;

public class KubernetesResourceProcessor {

    private final Map<String, Set<WatcherHolder<?>>> watcherHolders = new ConcurrentHashMap<>();

    public <T extends HasMetadata> T processResource(Map<String, T> existingMap, T newResourceState) {
        populateUid(newResourceState);
        updateGeneration(existingMap, newResourceState);
        T clone = validateResourceVersion(existingMap, newResourceState);
        putClone(existingMap, clone);
        fireEvents(newResourceState, clone);
        return clone;
    }

    private <T extends HasMetadata> void updateGeneration(Map<String, T> existingMap, T newResourceState) {
        HasMetadata existingResource = existingMap.get(newResourceState.getMetadata().getName());
        if (existingResource == null) {
            if (newResourceState.getMetadata().getGeneration() == null) {
                //To allow consuming code to artificially manipulate the metadata.generation for testing purposes
                newResourceState.getMetadata().setGeneration(1L);
            }
        } else if (existingResource != newResourceState) {
            Object existingSpec = null;
            Object newSpec = null;
            try {
                final ObjectMapper objectMapper = new ObjectMapper();
                final Method getSpec = newResourceState.getClass().getMethod("getSpec");
                if (!newResourceState.getClass().isInstance(existingResource)) {
                    System.out.println();
                }
                existingSpec = getSpec.invoke(existingResource);
                newSpec = getSpec.invoke(newResourceState);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                if (newResourceState instanceof ConfigMap) {
                    existingSpec = ((ConfigMap) existingResource).getData();
                    newSpec = ((ConfigMap) newResourceState).getData();
                } else if (newResourceState instanceof Secret) {
                    existingSpec = ((Secret) existingResource).getData();
                    newSpec = ((Secret) newResourceState).getData();
                    if (newSpec == null) {
                        existingSpec = ((Secret) existingResource).getStringData();
                        newSpec = ((Secret) newResourceState).getStringData();
                    }
                }
            }
            if (newSpec != null && !newSpec.equals(existingSpec)) {
                newResourceState.getMetadata().setGeneration(ofNullable(existingResource.getMetadata().getGeneration()).orElse(0L) + 1);
            }
        }
    }

    private <T extends HasMetadata> void putClone(Map<String, T> existingMap, T clone) {
        existingMap.put(clone.getMetadata().getName(), clone);
    }

    private <T extends HasMetadata> void fireEvents(T newResourceState, T clone) {
        final Set<WatcherHolder<HasMetadata>> watcherHoldersFor = getWatcherHoldersFor(newResourceState.getKind());
        watcherHoldersFor.stream()
                .filter(h -> h.matches(newResourceState))
                .forEach(h -> h.processEvent(Action.MODIFIED, clone));
    }

    @SuppressWarnings("unchecked")
    private <T extends HasMetadata> T clone(T newResourceState) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            final T result = (T) objectMapper.readValue(objectMapper.writeValueAsString(newResourceState), newResourceState.getClass());
            if (newResourceState instanceof SerializedEntandoResource) {
                ((SerializedEntandoResource) result).setDefinition(((SerializedEntandoResource) newResourceState).getDefinition());
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T extends HasMetadata> T validateResourceVersion(Map<String, T> existingMap, T newResourceState) {
        HasMetadata existingResource = existingMap.get(newResourceState.getMetadata().getName());
        if (existingResource != null && existingResource != newResourceState) {
            if (!existingResource.getMetadata().getResourceVersion().equals(newResourceState.getMetadata().getResourceVersion())) {
                throw new KubernetesClientException(
                        format("Resource version mismatch for %s %s/%s: %s != %s",
                                newResourceState.getKind(),
                                newResourceState.getMetadata().getNamespace(),
                                newResourceState.getMetadata().getName(),
                                newResourceState.getMetadata().getResourceVersion(),
                                existingResource.getMetadata().getResourceVersion()));
            }
        }
        T clone = clone(newResourceState);
        clone.getMetadata().setResourceVersion(String.valueOf((long) (Math.random() * 10000000L)));
        return clone;

    }

    public Collection<Watcher<?>> getAllWatchers() {
        return this.watcherHolders.values().stream().flatMap(Collection::stream).map(WatcherHolder::getWatcher)
                .collect(Collectors.toList());
    }

    private <T extends HasMetadata> void populateUid(T newResourceState) {
        if (newResourceState.getMetadata().getUid() == null) {
            newResourceState.getMetadata().setUid(UUID.randomUUID().toString());
        }
    }

    public <T extends HasMetadata> Watch watch(Watcher<T> watcher, String namespace, String name) {
        return registerWatcherHolder(new WatcherHolder<T>(watcher, namespace, name));
    }

    public <T extends HasMetadata> Watch watch(Watcher<T> watcher, String namespace, Map<String, String> selector) {
        return registerWatcherHolder(new WatcherHolder<T>(watcher, namespace, selector));
    }

    public <T extends HasMetadata> Watch watch(Watcher<T> watcher, String namespace) {
        return registerWatcherHolder(new WatcherHolder<T>(watcher, namespace));
    }

    public <T extends HasMetadata> Watch watch(Watcher<T> watcher) {
        return registerWatcherHolder(new WatcherHolder<T>(watcher));
    }

    public <T extends HasMetadata> Watch watch(Watcher<T> watcher, CustomResourceDefinitionContext context, String namespace) {
        return registerWatcherHolder(new WatcherHolder<T>(watcher, context, namespace));
    }

    public <T extends HasMetadata> Watch watch(Watcher<T> watcher, CustomResourceDefinitionContext context, String namespace, String name) {
        return registerWatcherHolder(new WatcherHolder<T>(watcher, context, namespace, name));
    }

    public <T extends HasMetadata> Watch watch(Watcher<T> watcher, CustomResourceDefinitionContext context) {
        return registerWatcherHolder(new WatcherHolder<T>(watcher, context));
    }

    private <T extends HasMetadata> Watch registerWatcherHolder(WatcherHolder<T> holder) {
        final Set<WatcherHolder<T>> holders = getWatcherHoldersFor(holder.getKind());
        holders.add(holder);
        return () -> holders.remove(holder);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T extends HasMetadata> Set<WatcherHolder<T>> getWatcherHoldersFor(String kind) {
        return (Set<WatcherHolder<T>>) (Set) this.watcherHolders.computeIfAbsent(kind, key -> new HashSet<>());
    }

    private static class WatcherHolder<T extends HasMetadata> {

        private final Watcher<T> watcher;
        private final String namespace;
        private final String name;
        private final Map<String, String> selector;
        private final CustomResourceDefinitionContext context;

        public WatcherHolder(Watcher<T> watcher) {
            this(watcher, null, null, null, null);
        }

        public WatcherHolder(Watcher<T> watcher, String namespace, Map<String, String> selector) {
            this(watcher, null, namespace, null, selector);
        }

        public WatcherHolder(Watcher<T> watcher, String namespace, String name) {
            this(watcher, null, namespace, name, null);
        }

        private WatcherHolder(Watcher<T> watcher, CustomResourceDefinitionContext context, String namespace, String name,
                Map<String, String> selector) {
            this.watcher = watcher;
            this.context = context;
            this.namespace = namespace;
            this.name = name;
            this.selector = selector;
        }

        public WatcherHolder(Watcher<T> watcher, String namespace) {
            this(watcher, null, namespace, null, null);
        }

        public WatcherHolder(Watcher<T> watcher, CustomResourceDefinitionContext context, String namespace) {
            this(watcher, context, namespace, null, null);
        }

        public WatcherHolder(Watcher<T> watcher, CustomResourceDefinitionContext context) {
            this(watcher, context, null, null, null);
        }

        public WatcherHolder(Watcher<T> watcher, CustomResourceDefinitionContext context, String namespace, String name) {
            this(watcher, context, namespace, name, null);
        }

        public String getKind() {
            return ofNullable(this.context).map(CustomResourceDefinitionContext::getKind).orElseGet(() -> {
                final Class<?> actualTypeArgument = (Class<?>) ((ParameterizedType) watcher.getClass().getGenericInterfaces()[0])
                        .getActualTypeArguments()[0];
                return actualTypeArgument.getSimpleName();
            });
        }

        public void processEvent(Action action, T resource) {
            watcher.eventReceived(action, (T) resource);
        }

        public boolean matches(HasMetadata resource) {
            return matchesNamespace(resource) && matchesName(resource) && matchesLabels(resource);
        }

        private boolean matchesLabels(HasMetadata resource) {
            return selector == null || AbstractK8SClientDouble.matchesSelector(selector, resource);
        }

        private boolean matchesName(HasMetadata resource) {
            return this.name == null || this.name.equals(resource.getMetadata().getName());
        }

        private boolean matchesNamespace(HasMetadata resource) {
            return this.namespace == null || this.namespace.equals(resource.getMetadata().getNamespace());
        }

        public Watcher<T> getWatcher() {
            return watcher;
        }
    }

}
