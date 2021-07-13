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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class ServiceClientDouble extends AbstractK8SClientDouble implements ServiceClient {

    public ServiceClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
    }

    @Override
    public Service createOrReplaceService(EntandoCustomResource peerInNamespace, Service service) {
        if (peerInNamespace == null) {
            return null;
        }
        service.getSpec().setClusterIP("10.0.0." + (byte) Math.abs(new Random().nextInt()));
        getNamespace(peerInNamespace).putService(service);
        return service;
    }

    @Override
    public Endpoints createOrReplaceEndpoints(EntandoCustomResource peerInNamespace, Endpoints endpoints) {
        getNamespace(peerInNamespace).putEndpoints(endpoints);
        return getNamespace(peerInNamespace).getEndpoints(endpoints.getMetadata().getName());
    }

    @Override
    public Service createOrReplaceDelegateService(Service service) {
        getNamespace(service).putService(service);
        return getNamespace(service).getService(service.getMetadata().getName());
    }

    @Override
    public Endpoints createOrReplaceDelegateEndpoints(Endpoints endpoints) {
        getNamespace(endpoints).putEndpoints(endpoints);
        return getNamespace(endpoints).getEndpoints(endpoints.getMetadata().getName());
    }

    @Override
    public Endpoints loadEndpoints(EntandoCustomResource peerInNamespace, String endpointsName) {
        return getNamespace(peerInNamespace).getEndpoints(endpointsName);
    }

    @Override
    public Service loadService(EntandoCustomResource peerInNamespace, String name) {
        if (peerInNamespace == null) {
            return null;
        }
        return getNamespace(peerInNamespace).getService(name);
    }

    @Override
    public Service loadControllerService(String name) {
        return getNamespace(CONTROLLER_NAMESPACE).getService(name);
    }
}
