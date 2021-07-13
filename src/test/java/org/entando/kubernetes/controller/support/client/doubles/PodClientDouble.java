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

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodConditionBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.controller.support.client.PodClient;

public class PodClientDouble extends AbstractK8SClientDouble implements PodClient {

    public PodClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
    }

    @Override
    public void removeSuccessfullyCompletedPods(String namespace, Map<String, String> labels) {
        getNamespace(namespace).getPods().values().removeIf(
                pod -> labels.entrySet().stream()
                        .allMatch(labelEntry ->
                                !PodResult.of(pod).hasFailed()
                                        && PodResult.of(pod).getState() == State.COMPLETED
                                        && pod.getMetadata().getLabels().containsKey(labelEntry.getKey())
                                        && pod.getMetadata().getLabels().get(
                                        labelEntry.getKey()).equals(labelEntry.getValue())));
    }

    @Override
    public void removeAndWait(String namespace, Map<String, String> labels, int timeoutSeconds) {
        getNamespace(namespace).getPods().values().removeIf(
                pod -> labels.entrySet().stream()
                        .allMatch(labelEntry -> pod.getMetadata().getLabels().containsKey(labelEntry.getKey()) && pod.getMetadata()
                                .getLabels().get(
                                        labelEntry.getKey()).equals(labelEntry.getValue())));
    }

    @Override
    public void deletePod(Pod pod) {
        getNamespace(pod.getMetadata().getNamespace()).getPods().remove(pod.getMetadata().getName());
    }

    @Override
    public Pod runToCompletion(Pod pod, int timeoutSeconds) {
        if (pod != null) {
            getNamespace(pod).putPod(pod);
            pod.setStatus(new PodStatusBuilder().withPhase("Complete").build());
            pod.getSpec().getInitContainers()
                    .forEach(container -> pod.getStatus().getInitContainerStatuses().add(new ContainerStatusBuilder()
                            .withNewState().withNewTerminated().withReason("Complete").withExitCode(0).endTerminated().endState()
                            .build()));
            pod.getSpec().getContainers().forEach(container -> pod.getStatus().getContainerStatuses().add(new ContainerStatusBuilder()
                    .withNewState().withNewTerminated().withReason("Complete").withExitCode(0).endTerminated().endState()
                    .build()));
        }
        return pod;
    }

    @Override
    public Pod start(Pod pod) {
        getNamespace(pod).putPod(pod);
        return pod;
    }

    @Override
    public Pod loadPod(String namespace, Map<String, String> labels) {
        return getNamespace(namespace).getPods().values().stream()
                .filter(pod -> labels.entrySet().stream().allMatch(stringStringEntry -> stringStringEntry.getValue()
                        .equals(pod.getMetadata().getLabels().get(stringStringEntry.getKey())))).findFirst().orElse(null);
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue, int timeoutSeconds) throws TimeoutException {
        if (namespace != null && !getNamespace(namespace).getPods().isEmpty()) {
            Pod result = getNamespace(namespace).getPods().values().stream()
                    .filter(pod -> labelValue.equals(pod.getMetadata().getLabels().get(labelName))).findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException(format("Could not find pod with label %s=%s", labelName, labelValue)));
            result.setStatus(new PodStatusBuilder().withPhase("Running").build());
            if (result.getSpec() == null) {
                result.setSpec(new PodSpec());
                result.getSpec().getContainers().add(new Container());
            }
            result.getSpec().getInitContainers()
                    .forEach(container -> result.getStatus().getInitContainerStatuses().add(new ContainerStatusBuilder()
                            .withNewState().withNewRunning().endRunning().endState()
                            .build()));
            result.getSpec().getContainers()
                    .forEach(container -> result.getStatus().getContainerStatuses().add(new ContainerStatusBuilder()
                            .withNewState().withNewRunning().endRunning().endState()
                            .build()));
            result.getStatus().getConditions()
                    .add(new PodConditionBuilder().withType("Ready")
                            .withLastTransitionTime(PodResult.DATE_FORMAT.get().format(new Date()))
                            .build());
            return result;
        }
        return null;
    }

}
