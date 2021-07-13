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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.controller.support.client.PodClient;

public class DefaultPodClient implements PodClient {

    private final KubernetesClient client;

    public DefaultPodClient(KubernetesClient client) {
        this.client = client;
        //HACK for GraalVM
        KubernetesDeserializer.registerCustomKind("v1", "Pod", Pod.class);
    }

    @Override
    public void removeSuccessfullyCompletedPods(String namespace, Map<String, String> labels) {
        client.pods().inNamespace(namespace).withLabels(labels).list().getItems().stream()
                .filter(pod -> PodResult.of(pod).getState() == State.COMPLETED && !PodResult.of(pod).hasFailed())
                .forEach(client.pods().inNamespace(namespace)::delete);
    }

    @Override
    public void removeAndWait(String namespace, Map<String, String> labels, int timeoutSeconds) throws TimeoutException {
        interruptionSafe(() -> {
            FilterWatchListDeletable<Pod, PodList> podResource = client.pods().inNamespace(namespace).withLabels(labels);
            podResource.delete();
            return podResource.waitUntilCondition(pod -> podResource.list().getItems().isEmpty(),
                    timeoutSeconds,
                    TimeUnit.SECONDS);
        });
    }

    @Override
    public Pod runToCompletion(Pod pod, int timeoutSeconds) throws TimeoutException {
        return interruptionSafe(() -> {
            this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
            return this.client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName())
                    .waitUntilCondition(got -> PodResult.of(got).getState() == State.COMPLETED,
                            timeoutSeconds, TimeUnit.SECONDS);
        });
    }

    @Override
    public void deletePod(Pod pod) {
        this.client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).delete();
    }

    @Override
    public Pod start(Pod pod) {
        return this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue, int timeoutSeconds) throws TimeoutException {
        return interruptionSafe(() ->
                client.pods().inNamespace(namespace).withLabel(labelName, labelValue).waitUntilCondition(
                        got -> got != null && got.getStatus() != null && (PodResult.of(got).getState() == State.READY
                                || PodResult.of(got).getState() == State.COMPLETED),
                        EntandoOperatorSpiConfig.getPodReadinessTimeoutSeconds(),
                        TimeUnit.SECONDS));

    }

    @Override
    public Pod loadPod(String namespace, Map<String, String> labels) {
        return client.pods().inNamespace(namespace).withLabels(labels).list().getItems().stream().findFirst().orElse(null);
    }

}

