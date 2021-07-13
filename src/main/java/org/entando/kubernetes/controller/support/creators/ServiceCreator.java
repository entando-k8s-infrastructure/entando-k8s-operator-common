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

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.MayRequireDelegateService;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.container.ServiceBackingContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.ExternalService;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.common.FluentTernary;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class ServiceCreator extends AbstractK8SResourceCreator {

    private static final int TCP4_NUMBER_OF_BYTES = 4;
    private static final int TCP6_NUMBER_OF_SEGMENTS = 8;

    private Service primaryService;

    public ServiceCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public ServiceCreator(EntandoCustomResource entandoCustomResource, Service primaryService) {
        super(entandoCustomResource);
        this.primaryService = primaryService;
    }

    public void createService(ServiceClient services, Deployable<?> deployable) {
        final Service service = newService(deployable);
        primaryService = withDiagnostics(() -> services.createOrReplaceService(entandoCustomResource, service), () -> service);
    }

    public Service newDelegatingService(ServiceClient services, MayRequireDelegateService ingressingDeployable) {
        ObjectMeta metaData = new ObjectMetaBuilder()
                .withLabels(labelsFromResource(ingressingDeployable.getQualifier().orElse(null)))
                .withName(ingressingDeployable.getIngressName() + "-to-" + primaryService.getMetadata().getName())
                .withNamespace(ingressingDeployable.getIngressNamespace())
                .withOwnerReferences(ResourceUtils.buildOwnerReference(this.entandoCustomResource)).build();
        final Service builtService = new ServiceBuilder()
                .withMetadata(metaData)
                .withNewSpec()
                .withPorts(new ArrayList<>(primaryService.getSpec().getPorts()))
                .endSpec()
                .build();

        Service delegatingService = withDiagnostics(() -> services.createOrReplaceDelegateService(builtService), () -> builtService);
        //This is just a workaround for Openshift where the DNS is not shared across namespaces. Joining the networks is an alternative
        // solution
        final Endpoints builtEndpoints = new EndpointsBuilder()
                .withMetadata(metaData)
                .addNewSubset()
                .addNewAddress().withIp(primaryService.getSpec().getClusterIP()).endAddress()
                .withPorts(toEndpointPorts(primaryService.getSpec().getPorts()))
                .endSubset()
                .build();
        withDiagnostics(() -> services.createOrReplaceDelegateEndpoints(builtEndpoints), () -> builtEndpoints);
        return delegatingService;
    }

    public void createExternalService(SimpleK8SClient<?> k8sClient, ExternalService externalService) {
        final Service newExternalService = newExternalService(externalService);
        this.primaryService = withDiagnostics(() -> k8sClient.services().createOrReplaceService(entandoCustomResource, newExternalService),
                () -> newExternalService);
        if (isIpAddress(externalService)) {
            final Endpoints newEndpoints = newEndpoints(externalService);
            withDiagnostics(() -> k8sClient.services().createOrReplaceEndpoints(entandoCustomResource, newEndpoints), () -> newEndpoints);
        }
    }

    private Service newService(Deployable<?> deployable) {
        final String nameQualifier = deployable.getQualifier().orElse(null);
        ObjectMeta objectMeta = fromCustomResource(true, resolveName(nameQualifier, NameUtils.DEFAULT_SERVICE_SUFFIX), nameQualifier);
        return new ServiceBuilder()
                .withMetadata(objectMeta)
                .withNewSpec()
                .withSelector(labelsFromResource(nameQualifier))
                .withType("ClusterIP")
                .withPorts(buildPorts(deployable))
                .endSpec()
                .build();
    }

    private List<ServicePort> buildPorts(Deployable<?> deployable) {
        return deployable.getContainers().stream().filter(ServiceBackingContainer.class::isInstance)
                .map(ServiceBackingContainer.class::cast)
                .map(this::newServicePort).collect(Collectors.toList());
    }

    private ServicePort newServicePort(ServiceBackingContainer deployableContainer) {
        return new ServicePortBuilder()
                .withName(deployableContainer.getNameQualifier() + "-port")
                .withPort(deployableContainer.getPrimaryPort())
                .withProtocol("TCP")
                .withTargetPort(new IntOrString(deployableContainer.getPrimaryPort()))
                .build();
    }

    private List<EndpointPort> toEndpointPorts(List<ServicePort> ports) {
        return ports.stream()
                .map(servicePort -> new EndpointPort(servicePort.getAppProtocol(), servicePort.getName(), servicePort.getPort(),
                        servicePort.getProtocol()))
                .collect(Collectors.toList());

    }

    private Endpoints newEndpoints(ExternalService externalService) {
        return new EndpointsBuilder()
                //Needs to match the service name exactly
                .withMetadata(fromCustomResource(true, resolveName(null, NameUtils.DEFAULT_SERVICE_SUFFIX), null))
                .addNewSubset()
                .addNewAddress()
                .withIp(externalService.getHost())
                .endAddress()
                .addNewPort()
                .withPort(externalService.getPort())
                .endPort()
                .endSubset()
                .build();
    }

    private Service newExternalService(ExternalService externalService) {
        return new ServiceBuilder()
                .withMetadata(fromCustomResource(true, resolveName(null, NameUtils.DEFAULT_SERVICE_SUFFIX), null))
                .withNewSpec()
                .withExternalName(FluentTernary.useNull(String.class).when(isIpAddress(externalService))
                        .orElse(externalService.getHost()))
                .withType(FluentTernary.use("ClusterIP").when(isIpAddress(externalService)).orElse("ExternalName"))
                .addNewPort()
                .withNewTargetPort(
                        externalService.getPort())
                .withPort(externalService.getPort())
                .endPort()
                .endSpec()
                .build();
    }

    private boolean isIpAddress(ExternalService externalService) {
        String host = externalService.getHost();
        try {
            String[] split = host.split("\\.");
            if (split.length == TCP4_NUMBER_OF_BYTES) {
                for (String s : split) {
                    int i = Integer.parseInt(s);
                    if (i > 255 || i < 0) {
                        return false;
                    }
                }
                return true;
            } else {
                split = host.split("\\:");

                if (split.length == TCP6_NUMBER_OF_SEGMENTS) {
                    for (String s : split) {
                        Integer.parseInt(s, 176);
                    }
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    public Service getService() {
        return primaryService;
    }
}
