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

package org.entando.kubernetes.controller.support.creators;

import static java.util.Optional.ofNullable;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.IngressingPathOnPort;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.ServerStatus;

public class IngressPathCreator {

    private final EntandoCustomResource entandoCustomResource;

    public IngressPathCreator(EntandoCustomResource entandoCustomResource) {
        this.entandoCustomResource = entandoCustomResource;
    }

    public Map<String, HTTPIngressPath> buildPaths(IngressingDeployable<?> ingressingDeployable, Service service,
            ServerStatus status) {
        Map<String, HTTPIngressPath> result = new HashMap<>();
        ingressingDeployable.getContainers().stream()
                .filter(IngressingContainer.class::isInstance)
                .map(IngressingContainer.class::cast)
                .forEach(ingressingContainer -> {
                    result.put(pathAnnotationName(ingressingContainer.getNameQualifier()), newHttpPath(ingressingContainer, service));
                    status.addToWebContexts(ingressingContainer.getNameQualifier(), ingressingContainer.getWebContextPath());
                });
        return result;
    }

    public Ingress addMissingHttpPaths(IngressClient ingressClient, List<IngressingPathOnPort> ingressingDeployable, final Ingress ingress,
            Service service, ServerStatus status) {
        List<IngressingPathOnPort> ingressingContainers = ingressingDeployable.stream()
                .filter(path -> this.httpPathIsAbsent(ingress, path))
                .collect(Collectors.toList());
        for (IngressingPathOnPort ingressingContainer : ingressingContainers) {
            String qualifier = ingressingContainer.getNameQualifier();
            withDiagnostics(() -> {
                ingressClient.addHttpPath(ingress, newHttpPath(ingressingContainer, service), Collections
                        .singletonMap(pathAnnotationName(qualifier),
                                ingressingContainer.getWebContextPath()));
                return status.addToWebContexts(ingressingContainer.getNameQualifier(), ingressingContainer.getWebContextPath());
            }, () -> ingress);

        }
        return ingressClient.loadIngress(ingress.getMetadata().getNamespace(), ingress.getMetadata().getName());
    }

    private String pathAnnotationName(String qualifier) {
        return "entando.org/" + entandoCustomResource.getMetadata().getName() + "-" + qualifier + "-path";
    }

    private HTTPIngressPath newHttpPath(IngressingPathOnPort ingressingPathOnPort, Service service) {
        //A null path causes NPE down the line
        return new HTTPIngressPathBuilder()
                .withPath(ofNullable(ingressingPathOnPort.getWebContextPath()).orElse("/" + entandoCustomResource.getMetadata().getName()))
                .withNewBackend()
                .withServiceName(service.getMetadata().getName())
                .withNewServicePort(ingressingPathOnPort.getPortForIngressPath())
                .endBackend()
                .build();
    }

    private boolean httpPathIsAbsent(Ingress ingress, IngressingPathOnPort ingressingContainer) {
        return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                .noneMatch(httpIngressPath -> httpIngressPath.getPath().equals(ingressingContainer.getWebContextPath()));
    }

}
