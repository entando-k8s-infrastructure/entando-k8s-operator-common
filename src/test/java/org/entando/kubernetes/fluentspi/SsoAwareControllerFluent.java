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

import java.util.Collections;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedSsoCapability;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;
import picocli.CommandLine;

public class SsoAwareControllerFluent<N extends SsoAwareControllerFluent<N>> extends ControllerFluent<N> {

    private final CapabilityProvider capabilityProvider;
    private CapabilityRequirement ssoRequirement;
    private SsoAwareDeployableFluent<?> deployable;

    public SsoAwareControllerFluent(KubernetesClientForControllers k8sClient,
            DeploymentProcessor deploymentProcessor, CapabilityProvider capabilityProvider) {
        super(k8sClient, deploymentProcessor);
        this.capabilityProvider = capabilityProvider;
    }

    @Override
    public N withDeployable(DeployableFluent<?> deployable) {
        this.deployable = (SsoAwareDeployableFluent<?>) deployable;
        return super.withDeployable(deployable);
    }

    public N withSsoRequirement(CapabilityRequirement ssoRequirement) {
        this.ssoRequirement = ssoRequirement;
        return thisAsN();
    }

    @Override
    public void run() {
        EntandoCustomResource resourceToProcess = k8sClient.resolveCustomResourceToProcess(Collections.singleton(supportedClass));
        try {
            k8sClient.updatePhase(resourceToProcess, EntandoDeploymentPhase.STARTED);
            final ProvidedSsoCapability ssoCapability = new ProvidedSsoCapability(
                    this.capabilityProvider.provideCapability(resourceToProcess, ssoRequirement, 30)
            );
            final DefaultExposedDeploymentResult result = deploymentProcessor
                    .processDeployable(deployable.withSsoConnectionInfo(ssoCapability)
                                    .withCustomResource(resourceToProcess),
                            60);
            resourceToProcess = k8sClient.updateStatus(resourceToProcess, result.getStatus());
            k8sClient.deploymentEnded(resourceToProcess);
        } catch (Exception e) {
            e.printStackTrace();
            k8sClient.deploymentFailed(resourceToProcess, e, NameUtils.MAIN_QUALIFIER);
        }
        resourceToProcess.getStatus().findFailedServerStatus().flatMap(ServerStatus::getEntandoControllerFailure).ifPresent(f -> {
                    throw new CommandLine.ExecutionException(new CommandLine(this), f.getDetailMessage());
                }
        );
    }

}
