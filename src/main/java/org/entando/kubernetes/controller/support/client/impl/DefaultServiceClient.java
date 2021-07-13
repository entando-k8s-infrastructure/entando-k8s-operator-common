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

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.retry;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DefaultServiceClient implements ServiceClient {

    private final KubernetesClient client;

    public DefaultServiceClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Endpoints createOrReplaceEndpoints(EntandoCustomResource peerInNamespace, Endpoints endpoints) {
        return createOrReplace(peerInNamespace, endpoints, client.endpoints());
    }

    @Override
    public Endpoints createOrReplaceDelegateEndpoints(Endpoints endpoints) {
        return createOrReplace(endpoints, client.endpoints());
    }

    @Override
    public Endpoints loadEndpoints(EntandoCustomResource peerInNamespace, String endpointsName) {
        return client.endpoints().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(endpointsName).fromServer().get();
    }

    @Override
    public Service createOrReplaceDelegateService(Service service) {
        return createOrReplace(service, client.services());
    }

    @Override
    public Service loadService(EntandoCustomResource peerInNamespace, String name) {
        return client.services().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name).get();
    }

    @Override
    public Service loadControllerService(String name) {
        return client.services().inNamespace(client.getNamespace()).withName(name).fromServer().get();
    }

    @Override
    public Service createOrReplaceService(EntandoCustomResource peerInNamespace, Service service) {
        return createOrReplace(peerInNamespace, service, client.services());
    }

    private <T extends HasMetadata, L extends KubernetesResourceList<T>, R extends Resource<T>> T createOrReplace(
            EntandoCustomResource peerInNamespace, T resource,
            MixedOperation<T, L, R> oper) {
        resource.getMetadata().setNamespace(peerInNamespace.getMetadata().getNamespace());
        return createOrReplace(resource, oper);
    }

    private <T extends HasMetadata, L extends KubernetesResourceList<T>, R extends Resource<T>> T createOrReplace(T resource,
            MixedOperation<T, L, R> oper) {
        if (oper.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).get() != null) {
            oper.inNamespace(resource.getMetadata().getNamespace()).withName(resource.getMetadata().getName()).delete();
        }
        resource.getMetadata().setResourceVersion(null);
        //retry because it may still be in the process of being deleted
        return retry(() -> oper.inNamespace(resource.getMetadata().getNamespace()).create(resource), KubernetesClient.class::isInstance, 5);
    }
}
