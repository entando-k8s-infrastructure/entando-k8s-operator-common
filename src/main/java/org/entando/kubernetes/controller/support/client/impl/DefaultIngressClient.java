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

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.support.client.DoneableIngress;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DefaultIngressClient implements IngressClient {

    private final KubernetesClient client;

    public DefaultIngressClient(KubernetesClient client) {
        this.client = client;
    }

    public static String resolveMasterHostname(KubernetesClient client) {
        String host = client.getMasterUrl().getHost();
        if ("127.0.0.1".equals(host)) {
            //This will only happen on single node installations. Generally it will return some ip/domain name that resolves to the master
            // Retrieve IP from node API and discard local IP
            Node masterNode = client.nodes().list().getItems().get(0);
            Optional<NodeAddress> masterNodeAddress = masterNode.getStatus().getAddresses().stream()
                    .filter(na -> na.getType().equalsIgnoreCase("InternalIP")).findFirst();
            host = masterNodeAddress.orElseThrow(() -> new RuntimeException("Impossible to retrieve node internal IP address"))
                    .getAddress();
        }
        return host;
    }

    @Override
    public Ingress addHttpPath(Ingress ingress, HTTPIngressPath httpIngressPath, Map<String, String> annotations) {
        return edit(ingress.getMetadata(), ingress.getMetadata().getName())
                .editSpec().editFirstRule().editHttp()
                .addNewPathLike(httpIngressPath)
                .endPath().endHttp().endRule().endSpec()
                .editMetadata().addToAnnotations(annotations).endMetadata()
                .done();
    }

    private DoneableIngress edit(ObjectMeta metadata, String name) {
        return new DoneableIngress(client.extensions().ingresses().inNamespace(metadata.getNamespace())
                .withName(name).fromServer().get(), client.extensions().ingresses().inNamespace(metadata.getNamespace())
                .withName(name)::patch);
    }

    @Override
    public Ingress removeHttpPath(Ingress ingress, HTTPIngressPath path) {
        return edit(ingress.getMetadata(), ingress.getMetadata().getName())
                .editSpec().editFirstRule().editHttp()
                .removeFromPaths(path)
                .endHttp()
                .endRule()
                .endSpec()
                .done();

    }

    @Override
    public String getMasterUrlHost() {
        return resolveMasterHostname(this.client);
    }

    @Override
    public Ingress createIngress(EntandoCustomResource peerInNamespace, Ingress ingress) {
        try {
            return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(ingress);
        } catch (KubernetesClientException e) {
            if (e.getCode() == HttpURLConnection.HTTP_CONFLICT) {
                return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace())
                        .withName(ingress.getMetadata().getName())
                        .edit(existing -> {
                            existing.getSpec().getRules().get(0).getHttp().getPaths()
                                    .addAll(ingress.getSpec().getRules().get(0).getHttp().getPaths());
                            return existing;
                        });
            } else {
                throw e;
            }
        }
    }

    @Override
    public DoneableIngress editIngress(EntandoCustomResource peerInNamespace, String name) {
        return edit(peerInNamespace.getMetadata(), name);
    }

    @Override
    public Ingress loadIngress(String namespace, String name) {
        return client.extensions().ingresses().inNamespace(namespace).withName(name).get();
    }
}
