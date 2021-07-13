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

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.interruptionSafe;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DefaultDeploymentClient implements DeploymentClient {

    private final KubernetesClient client;

    public DefaultDeploymentClient(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public boolean supportsStartupProbes() {
        try {
            final VersionInfo version = client.getVersion();
            //Is null when using the MockServer. Return true because that is the most common scenario we want to test
            return version == null || parseVersion(version) >= 16;
        } catch (NullPointerException e) {
            //Happens on the mock server
            return true;
        }
    }

    private int parseVersion(VersionInfo version) {
        StringBuilder sb = new StringBuilder();
        //Some versions have trailing non-digit characters
        for (char current : version.getMinor().toCharArray()) {
            if (Character.isDigit(current)) {
                sb.append(current);
            } else {
                break;
            }
        }
        return Integer.parseInt(sb.toString());
    }

    @Override
    public Deployment createOrPatchDeployment(EntandoCustomResource peerInNamespace, Deployment deployment, int timeoutSeconds)
            throws TimeoutException {
        Deployment existingDeployment = getDeploymenResourceFor(peerInNamespace, deployment).get();
        if (existingDeployment == null) {
            return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(deployment);
        } else {
            //Don't wait because the polling in Fabric8 is dodge
            getDeploymenResourceFor(peerInNamespace, deployment).scale(0, true);
            FilterWatchListDeletable<Pod, PodList> podResource = client.pods()
                    .inNamespace(existingDeployment.getMetadata().getNamespace())
                    .withLabelSelector(existingDeployment.getSpec().getSelector());
            interruptionSafe(() -> podResource.waitUntilCondition(pod -> podResource.list().getItems().isEmpty(),
                    timeoutSeconds,
                    TimeUnit.SECONDS));
            //Create the deployment with the correct replicas now. We don't support 0 because we will be waiting for the pod
            return getDeploymenResourceFor(peerInNamespace, deployment).patch(deployment);
        }
    }

    private RollableScalableResource<Deployment> getDeploymenResourceFor(
            EntandoCustomResource peerInNamespace,
            Deployment deployment) {
        return client.apps()
                .deployments()
                .inNamespace(peerInNamespace.getMetadata().getNamespace())
                .withName(deployment.getMetadata().getName());
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        return client.apps().deployments().inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(name)
                .get();
    }

}
