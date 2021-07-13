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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;

public class EntandoResourceClientDoubleBase extends AbstractK8SClientDouble {

    public EntandoResourceClientDoubleBase(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T reload(T customResource) {
        if (customResource instanceof SerializedEntandoResource) {
            return (T) reloadAsOpaqueResource(customResource);
        } else {
            return (T) getNamespace(customResource.getMetadata().getNamespace()).getCustomResources(customResource.getKind())
                    .get(customResource.getMetadata().getName());
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends EntandoCustomResource> T waitForCompletion(T customResource, int timeoutSeconds)
            throws TimeoutException {
        return waitForPhase(customResource, timeoutSeconds, EntandoDeploymentPhase.IGNORED, EntandoDeploymentPhase.FAILED,
                EntandoDeploymentPhase.SUCCESSFUL);
    }

    protected <T extends EntandoCustomResource> T waitForPhase(T customResource, int timeoutSeconds, EntandoDeploymentPhase... phases)
            throws TimeoutException {
        Predicate<EntandoCustomResource> predicate = resource -> resource.getStatus().getPhase() != null
                && Set.of(phases).contains(resource.getStatus().getPhase());
        final T reloaded = reload(customResource);
        if (predicate.test(reloaded)) {
            return reloaded;
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        CustomResourceDefinitionContext definition;
        if (customResource instanceof SerializedEntandoResource) {
            definition = ((SerializedEntandoResource) customResource).getDefinition();
        } else {
            definition = CustomResourceDefinitionContext
                    .fromCustomResourceType((Class<? extends CustomResource<?, ?>>) customResource.getClass());
        }
        getCluster().getResourceProcessor().watch(new Watcher<EntandoCustomResource>() {
            @Override
            public void eventReceived(Action action, EntandoCustomResource resource) {
                //NB we may not know which the type of 'resource' is here.
                if (predicate.test(resource)) {
                    //but we need to reload it using the original type requested
                    future.complete(reload(customResource));
                }
            }

            @Override
            public void onClose(WatcherException e) {

            }
        }, definition, customResource.getMetadata().getNamespace(), customResource.getMetadata().getName());
        return interruptionSafe(() -> future.get(timeoutSeconds, TimeUnit.SECONDS));
    }

    private SerializedEntandoResource reloadAsOpaqueResource(EntandoCustomResource customResource) {
        return ioSafe(() -> {
            final ObjectMapper objectMapper = new ObjectMapper();
            final EntandoCustomResource currentState = getNamespace(customResource.getMetadata().getNamespace())
                    .getCustomResources(customResource.getKind())
                    .get(customResource.getMetadata().getName());
            if (currentState instanceof SerializedEntandoResource) {
                return (SerializedEntandoResource) currentState;
            } else {
                return objectMapper.readValue(objectMapper.writeValueAsString(currentState), SerializedEntandoResource.class);
            }
        });
    }
}
