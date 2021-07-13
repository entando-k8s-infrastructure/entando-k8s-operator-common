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

package org.entando.kubernetes.fluentspi;

import java.util.Arrays;
import javax.inject.Inject;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ExternallyProvidedService;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.ProvidedCapabilityBuilder;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import picocli.CommandLine;

@CommandLine.Command()
public class TestResourceController implements Runnable {

    private final KubernetesClientForControllers k8sClient;
    private final DeploymentProcessor deploymentProcessor;
    private TestResource testResource;
    private ProvidedCapability providedCapability;

    @Inject
    public TestResourceController(KubernetesClientForControllers k8sClient, DeploymentProcessor deploymentProcessor) {
        this.k8sClient = k8sClient;
        this.deploymentProcessor = deploymentProcessor;
    }

    @Override
    public void run() {
        final EntandoCustomResource resourceToProcess = k8sClient.resolveCustomResourceToProcess(
                Arrays.asList(TestResource.class, ProvidedCapability.class));
        int port = 0;
        try {
            k8sClient.updatePhase(resourceToProcess, EntandoDeploymentPhase.STARTED);
            if (resourceToProcess instanceof ProvidedCapability) {
                providedCapability = (ProvidedCapability) resourceToProcess;
                testResource = new TestResource()
                        .withNames(resourceToProcess.getMetadata().getNamespace(), resourceToProcess.getMetadata().getName());
                testResource.setSpec(new BasicDeploymentSpecBuilder()
                        .withDbms(DbmsVendor.valueOf(
                                providedCapability.getSpec().getImplementation().orElse(StandardCapabilityImplementation.POSTGRESQL)
                                        .name()))
                        .withProvisioningStrategy(providedCapability.getSpec().getProvisioningStrategy().orElse(
                                CapabilityProvisioningStrategy.DEPLOY_DIRECTLY))
                        .withExternalHostName(
                                providedCapability.getSpec().getExternallyProvisionedService().map(ExternallyProvidedService::getHost)
                                        .orElse(null))
                        .withAdminSecretName(providedCapability.getSpec().getExternallyProvisionedService()
                                .map(ExternallyProvidedService::getAdminSecretName)
                                .orElse(null))
                        .build());
                port = providedCapability.getSpec().getExternallyProvisionedService().flatMap(ExternallyProvidedService::getPort).orElse(
                        DbmsVendorConfig.valueOf(
                                providedCapability.getSpec().getImplementation().orElse(StandardCapabilityImplementation.POSTGRESQL).name())
                                .getDefaultPort());
                testResource = k8sClient.createOrPatchEntandoResource(testResource);
            } else {
                testResource = (TestResource) resourceToProcess;
                providedCapability = new ProvidedCapabilityBuilder()
                        .withNewMetadata()
                        .withName(testResource.getMetadata().getName())
                        .withNamespace(testResource.getMetadata().getNamespace())
                        .endMetadata()
                        .withNewSpec()
                        .withCapability(StandardCapability.DBMS)
                        .withImplementation(StandardCapabilityImplementation.valueOf(testResource.getSpec().getDbms().name()))
                        .withProvisioningStrategy(testResource.getSpec().getProvisioningStrategy())
                        .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                        .endSpec().build();
                ResourceUtils.addCapabilityLabels(providedCapability);
                providedCapability = k8sClient.createOrPatchEntandoResource(providedCapability);
            }
            final TestResourceDeploymentResult result = deploymentProcessor
                    .processDeployable(new TestResourceDeployable(testResource, port), 60);
            k8sClient.updateStatus(providedCapability, result.getStatus().withOriginatingCustomResource(providedCapability));
            k8sClient.updateStatus(testResource, result.getStatus().withOriginatingCustomResource(testResource));
            k8sClient.updatePhase(providedCapability, EntandoDeploymentPhase.SUCCESSFUL);
            k8sClient.updatePhase(testResource, EntandoDeploymentPhase.SUCCESSFUL);
        } catch (Exception e) {
            e.printStackTrace();
            k8sClient.deploymentFailed(providedCapability, e, NameUtils.MAIN_QUALIFIER);
            k8sClient.deploymentFailed(testResource, e, NameUtils.MAIN_QUALIFIER);
            throw new CommandLine.ExecutionException(new CommandLine(this), e.getMessage());
        }

    }
}
