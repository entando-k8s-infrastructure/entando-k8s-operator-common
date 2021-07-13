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

import static java.util.Optional.ofNullable;

import java.util.Collections;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.ServerStatus;
import picocli.CommandLine;

public class ControllerFluent<N extends ControllerFluent<N>> implements Runnable {

    protected final KubernetesClientForControllers k8sClient;
    protected final DeploymentProcessor deploymentProcessor;
    protected DeployableFluent<?> deployable;
    protected Class<? extends EntandoCustomResource> supportedClass;
    protected Integer timeoutSeconds;

    public ControllerFluent(KubernetesClientForControllers k8sClient, DeploymentProcessor deploymentProcessor) {
        this.k8sClient = k8sClient;
        this.deploymentProcessor = deploymentProcessor;
    }

    @Override
    public void run() {
        EntandoCustomResource resourceToProcess = k8sClient.resolveCustomResourceToProcess(Collections.singleton(supportedClass));
        try {
            resourceToProcess = k8sClient.deploymentStarted(resourceToProcess);
            final DefaultExposedDeploymentResult result = deploymentProcessor
                    .processDeployable(deployable.withCustomResource(resourceToProcess), ofNullable(timeoutSeconds).orElse(60));
            //The result may be used here, but its latest state already sits on the entandoCustomResource.status.serverStatuses
            //At this point the status on our local resourceToProcess is out of sync and needs to be reloaded

            //This call implicitly reloads the custom resource, calcualates the correct final phase and applies it to the status
            resourceToProcess = k8sClient.deploymentEnded(resourceToProcess);
        } catch (Exception e) {
            e.printStackTrace();
            //This call implicitly reloads the custom resource and applies the failedStatus to it
            resourceToProcess = k8sClient.deploymentFailed(resourceToProcess, e, NameUtils.MAIN_QUALIFIER);
        }
        if (resourceToProcess.getStatus().hasFailed()) {
            throw new CommandLine.ExecutionException(new CommandLine(this), resourceToProcess.getStatus().findFailedServerStatus()
                    .flatMap(ServerStatus::getEntandoControllerFailure)
                    .flatMap(f -> ofNullable(f.getDetailMessage()).or(() -> ofNullable(f.getMessage())))
                    .orElse("Deployment Failed"));
        }

    }

    public N withTimeoutSeconds(int i) {
        this.timeoutSeconds = i;
        return thisAsN();
    }

    public N withDeployable(DeployableFluent<?> deployable) {
        this.deployable = deployable;
        return thisAsN();
    }

    public N withSupportedClass(Class<? extends EntandoCustomResource> supportedClass) {
        this.supportedClass = supportedClass;
        return thisAsN();
    }

    @SuppressWarnings("unchecked")
    protected N thisAsN() {
        return (N) this;
    }
}
