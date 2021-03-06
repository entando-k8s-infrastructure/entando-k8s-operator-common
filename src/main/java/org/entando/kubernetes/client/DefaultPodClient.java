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

package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Watchable;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.controller.support.client.PodClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;

public class DefaultPodClient implements PodClient {

    private final KubernetesClient client;
    private BlockingQueue<PodWatcher> podWatcherHolder = new ArrayBlockingQueue<>(15);
    private BlockingQueue<EntandoExecListener> execListenerHolder = new ArrayBlockingQueue<>(15);

    public DefaultPodClient(KubernetesClient client) {
        this.client = client;
        //HACK for GraalVM
        KubernetesDeserializer.registerCustomKind("v1", "Pod", Pod.class);
    }

    public BlockingQueue<EntandoExecListener> getExecListenerHolder() {
        return execListenerHolder;
    }

    @Override
    public void removeSuccessfullyCompletedPods(String namespace, Map<String, String> labels) {
        client.pods().inNamespace(namespace).withLabels(labels).list().getItems().stream()
                .filter(pod -> PodResult.of(pod).getState() == State.COMPLETED && !PodResult.of(pod).hasFailed())
                .forEach(client.pods().inNamespace(namespace)::delete);
    }

    @Override
    public void removeAndWait(String namespace, Map<String, String> labels) {
        FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> podResource = client
                .pods().inNamespace(namespace).withLabels(labels);
        podResource.delete();
        watchPod(
                pod -> podResource.list().getItems().isEmpty(),
                EntandoOperatorConfig.getPodShutdownTimeoutSeconds(), podResource);
    }

    @Override
    public BlockingQueue<PodWatcher> getPodWatcherQueue() {
        return podWatcherHolder;
    }

    @Override
    public Pod runToCompletion(Pod pod) {
        Pod running = this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
        return waitFor(running, got -> PodResult.of(got).getState() == State.COMPLETED,
                EntandoOperatorConfig.getPodCompletionTimeoutSeconds());
    }

    @Override
    public void deletePod(Pod pod) {
        this.client.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).delete();
    }

    @Override
    public EntandoExecListener executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) {
        PodResource<Pod, DoneablePod> podResource = this.client.pods().inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName());
        return executeAndWait(podResource, containerName, timeoutSeconds, commands);
    }

    @Override
    public Pod start(Pod pod) {
        return this.client.pods().inNamespace(pod.getMetadata().getNamespace()).create(pod);
    }

    @Override
    public Pod waitForPod(String namespace, String labelName, String labelValue) {
        Watchable<Watch, Watcher<Pod>> watchable = client.pods().inNamespace(namespace).withLabel(labelName, labelValue);
        return watchPod(got -> PodResult.of(got).getState() == State.READY || PodResult.of(got).getState() == State.COMPLETED,
                EntandoOperatorConfig.getPodReadinessTimeoutSeconds(),
                watchable);
    }

    @Override
    public Pod loadPod(String namespace, Map<String, String> labels) {
        return client.pods().inNamespace(namespace).withLabels(labels).list().getItems().stream().findFirst().orElse(null);
    }

    /**
     * For some reason a local Openshift consistently resulted in timeouts on pod.waitUntilCondition(), so we had to implement our own
     * logic. waituntilCondition also polls which is nasty.
     */
    private Pod waitFor(Pod pod, Predicate<Pod> podPredicate, long timeoutSeconds) {
        Watchable<Watch, Watcher<Pod>> watchable = client.pods().inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName());
        return watchPod(podPredicate, timeoutSeconds, watchable);

    }

}

