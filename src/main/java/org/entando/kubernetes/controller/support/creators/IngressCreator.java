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

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressTLS;
import io.fabric8.kubernetes.api.model.extensions.IngressTLSBuilder;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.IngressingPathOnPort;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.ServerStatus;

public class IngressCreator extends AbstractK8SResourceCreator {

    private final IngressPathCreator ingressPathCreator;
    private Ingress ingress;

    public IngressCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
        this.ingressPathCreator = new IngressPathCreator(entandoCustomResource);
    }

    public static String getIngressServerUrl(Ingress ingress) {
        return (ingress.getSpec().getTls().isEmpty() ? "http" : "https") + "://" + ingress.getSpec().getRules().get(0)
                .getHost();
    }

    public static String determineRoutingSuffix(String masterNodeHost) {
        return EntandoOperatorConfig.getDefaultRoutingSuffix()
                .orElse(determineViableRoutingSuffix(masterNodeHost));
    }

    private static String determineViableRoutingSuffix(String masterNodeHost) {
        if (isTopLevelDomain(masterNodeHost)) {
            return masterNodeHost;
        }
        return masterNodeHost + ".nip.io";
    }

    private static boolean isTopLevelDomain(String host) {
        return ioSafe(() -> {
            //loaded from https://data.iana.org/TLD/tlds-alpha-by-domain.txt
            try (InputStream stream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("top-level-domains.txt");
                    BufferedReader r = new BufferedReader(new InputStreamReader(stream))) {

                String line = r.readLine();
                while (line != null) {
                    if (host.toLowerCase(Locale.getDefault()).endsWith("." + line.toLowerCase(Locale.getDefault()))) {
                        return true;
                    }
                    line = r.readLine();
                }
            }
            return false;
        });
    }

    public boolean requiresDelegatingService(Service service, IngressingDeployable<?> ingressingDeployable) {
        return !service.getMetadata().getNamespace().equals(ingressingDeployable.getIngressNamespace());
    }

    public void createIngress(IngressClient ingressClient, IngressingDeployable<?> ingressingDeployable,
            Service service, ServerStatus status) {
        this.ingress = ingressClient.loadIngress(ingressingDeployable.getIngressNamespace(), ingressingDeployable.getIngressName());
        if (this.ingress == null) {
            Ingress newIngress = newIngress(ingressClient, ingressPathCreator.buildPaths(ingressingDeployable, service, status),
                    ingressingDeployable);
            this.ingress = withDiagnostics(() -> ingressClient.createIngress(entandoCustomResource, newIngress), () -> newIngress);
        } else {
            if (ResourceUtils.customResourceOwns(entandoCustomResource, ingress)) {
                final String host = determineIngressHost(ingressClient, ingressingDeployable);
                final List<IngressTLS> tls = maybeBuildTls(ingressClient, ingressingDeployable);
                this.ingress = ingressClient.editIngress(entandoCustomResource, ingressingDeployable.getIngressName())
                        .editSpec().editFirstRule().withHost(host).endRule()
                        .withTls(tls).endSpec().done();
            }
            List<IngressingPathOnPort> ingressingContainers = ingressingDeployable.getContainers().stream()
                    .filter(IngressingContainer.class::isInstance).map(IngressingContainer.class::cast).collect(Collectors.toList());

            this.ingress = ingressPathCreator.addMissingHttpPaths(ingressClient, ingressingContainers, ingress, service, status);
        }
    }

    public Ingress getIngress() {
        return ingress;
    }

    private Ingress newIngress(IngressClient ingressClient, Map<String, HTTPIngressPath> paths,
            IngressingDeployable<?> deployable) {
        return new IngressBuilder()
                .withNewMetadata()
                .addToAnnotations(forNginxIngress(deployable))
                .addToAnnotations(toPathAnnotations(paths))
                .withName(deployable.getIngressName())
                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                .addToLabels(entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getName())
                .withOwnerReferences(ResourceUtils.buildOwnerReference(entandoCustomResource))
                .endMetadata()
                .withNewSpec()
                .withIngressClassName(EntandoOperatorConfig.getIngressClass().orElse(null))
                .withTls(maybeBuildTls(ingressClient, deployable))
                .addNewRule()
                .withHost(determineIngressHost(ingressClient, deployable))
                .withNewHttp()
                .withPaths(new ArrayList<>(paths.values()))
                .endHttp()
                .endRule().endSpec().build();
    }

    private Map<String, String> toPathAnnotations(Map<String, HTTPIngressPath> paths) {
        return paths.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().getPath()));
    }

    private Map<String, String> forNginxIngress(IngressingDeployable<?> deployable) {
        Map<String, String> result = new HashMap<>();
        EntandoOperatorConfig.getIngressClass().ifPresent(s -> result.put("kubernetes.io/ingress.class", s));
        if (EntandoOperatorConfig.getTlsSecretName().isPresent() || EntandoOperatorConfig.useAutoCertGeneration()) {

            //for cases where we have https available but the Keycloak redirect was specified as http
            result.put("nginx.ingress.kubernetes.io/force-ssl-redirect", "true");
        }
        deployable.getFileUploadLimit().ifPresent(s -> result.put("nginx.ingress.kubernetes.io/proxy-body-size", s));
        return result;
    }

    private List<IngressTLS> maybeBuildTls(IngressClient ingressClient, IngressingDeployable<?> deployable) {
        List<IngressTLS> result = new ArrayList<>();

        deployable.getTlsSecretName().ifPresent(s ->
                result.add(new IngressTLSBuilder().withSecretName(s).withHosts(determineIngressHost(ingressClient, deployable)).build()));
        if (result.isEmpty()) {
            EntandoOperatorConfig.getTlsSecretName().ifPresent(s ->
                    result.add(new IngressTLSBuilder().withHosts(determineIngressHost(ingressClient, deployable))
                            .withSecretName(s).build()));
        }
        if (result.isEmpty() && EntandoOperatorConfig.useAutoCertGeneration()) {
            result.add(new IngressTLSBuilder().withHosts(determineIngressHost(ingressClient, deployable))
                    .withSecretName(SecretUtils.EMPTY_TLS_SECRET_NAME).build());
        }
        return result;
    }

    private String determineIngressHost(IngressClient ingressClient, IngressingDeployable<?> deployable) {
        return deployable.getIngressHostName()
                .orElse(entandoCustomResource.getMetadata().getName() + "-" + entandoCustomResource.getMetadata().getNamespace() + "."
                        + determineRoutingSuffix(
                        ingressClient.getMasterUrlHost()));
    }

}
