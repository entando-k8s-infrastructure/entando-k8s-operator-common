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

import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.support.client.DoneableIngress;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class IngressClientDouble extends AbstractK8SClientDouble implements IngressClient {

    public IngressClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
    }

    @Override
    public synchronized Ingress createIngress(EntandoCustomResource peerInNamespace, Ingress ingress) {
        if (peerInNamespace == null) {
            return null;
        }
        ingress.getMetadata().getName();
        getNamespace(peerInNamespace).putIngress(ingress);
        return ingress;
    }

    @Override
    public DoneableIngress editIngress(EntandoCustomResource peerInNamespace, String name) {
        return new DoneableIngress(getNamespace(peerInNamespace).getIngress(name), item -> {
            item.getMetadata().getName();
            getNamespace(peerInNamespace).putIngress(item);
            return item;
        });
    }

    @Override
    public Ingress loadIngress(String namespace, String name) {
        if (namespace == null) {
            return null;
        }
        return getNamespace(namespace).getIngress(name);
    }

    @Override
    public synchronized Ingress addHttpPath(Ingress ingress, HTTPIngressPath httpIngressPath, Map<String, String> annotations) {
        if (ingress == null) {
            return null;
        }
        final Ingress ingressToUpdate = getNamespace(ingress).getIngress(ingress.getMetadata().getName());
        ingressToUpdate.getSpec().getRules().get(0).getHttp().getPaths().add(httpIngressPath);
        if (ingressToUpdate.getMetadata().getAnnotations() == null) {
            ingressToUpdate.getMetadata().setAnnotations(annotations);
        } else {
            ingressToUpdate.getMetadata().getAnnotations().putAll(annotations);
        }
        return ingressToUpdate;
    }

    @Override
    public Ingress removeHttpPath(Ingress ingress, HTTPIngressPath path) {
        if (ingress == null) {
            return null;
        }
        ingress.getSpec().getRules().get(0).getHttp().getPaths().removeIf(p -> p.equals(path));
        return ingress;
    }

    @Override
    public String getMasterUrlHost() {
        return "somehost.com";
    }
}
