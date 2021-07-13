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

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.Assertions;
import org.entando.kubernetes.controller.spi.client.AbstractSupportK8SIntegrationTest;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.fluentspi.TestResource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultPodClientTest extends AbstractSupportK8SIntegrationTest {

    private final TestResource testResource = newTestResource();

    @Test
    void shouldWaitForPreviouslyStartedPod() throws TimeoutException {
        //Given I have started a new Pod
        awaitDefaultToken(testResource.getMetadata().getNamespace());
        final Pod startedPod = getSimpleK8SClient().pods().start(new PodBuilder()
                .withNewMetadata()
                .withName("my-pod")
                .withNamespace(testResource.getMetadata().getNamespace())
                .addToLabels("pod-label", "123")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withImage("centos/nginx-116-centos7")
                .withName("nginx")
                .withCommand("/usr/libexec/s2i/run")
                .endContainer()
                .endSpec()
                .build());
        //When I wait for the pod
        final Pod pod = getSimpleK8SClient().pods().waitForPod(testResource.getMetadata().getNamespace(), "pod-label", "123",
                EntandoOperatorSpiConfig.getPodReadinessTimeoutSeconds());
        //Then the current thread only proceeds once the pod is ready
        assertThat(PodResult.of(pod).getState(), is(State.READY));
        assertThat(PodResult.of(pod).hasFailed(), is(false));
    }

    @Test
    void shouldRemoveAndWait() throws TimeoutException {
        awaitDefaultToken(testResource.getMetadata().getNamespace());
        //Given I have started a new Pod
        final Pod startedPod = getSimpleK8SClient().pods().start(new PodBuilder()
                .withNewMetadata()
                .withName("my-pod")
                .withNamespace(testResource.getMetadata().getNamespace())
                .addToLabels("pod-label", "123")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withImage("centos/nginx-116-centos7")
                .withName("nginx")
                .withCommand("/usr/libexec/s2i/run")
                .endContainer()
                .endSpec()
                .build());
        //When I wait for the pod
        getSimpleK8SClient().pods().removeAndWait(testResource.getMetadata().getNamespace(), Collections.singletonMap("pod-label", "123"),
                EntandoOperatorSpiConfig
                        .getPodShutdownTimeoutSeconds());
        //Then the current thread only proceeds once the pod is ready
        assertThat(
                getSimpleK8SClient().pods()
                        .loadPod(testResource.getMetadata().getNamespace(), Collections.singletonMap("pod-label", "123")),
                is(nullValue()));
    }

    @Test
    void shouldRemoveSuccessfullyCompletedPods() throws TimeoutException {
        awaitDefaultToken(testResource.getMetadata().getNamespace());
        //Given I have started a new Pod
        getSimpleK8SClient().pods().runToCompletion(new PodBuilder()
                .withNewMetadata()
                .withName("successful-pod")
                .withNamespace(testResource.getMetadata().getNamespace())
                .addToLabels("pod-label", "successful-pod")
                .addToLabels("label", "value")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withImage("busybox")
                .withName("sleep")
                .withCommand("sleep")
                .withArgs("1")
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .build(), EntandoOperatorSpiConfig.getPodCompletionTimeoutSeconds());
        getSimpleK8SClient().pods().runToCompletion(new PodBuilder()
                .withNewMetadata()
                .withName("failed-pod")
                .withNamespace(testResource.getMetadata().getNamespace())
                .addToLabels("label", "value")
                .addToLabels("pod-label", "failed-pod")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withImage("busybox")
                .withName("exit")
                .withCommand("exit")
                .withArgs("128")
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .build(), EntandoOperatorSpiConfig.getPodCompletionTimeoutSeconds());
        //When I wait for the pod
        getSimpleK8SClient().pods()
                .removeSuccessfullyCompletedPods(testResource.getMetadata().getNamespace(), Collections.singletonMap("label", "value"));
        //Then the current thread only proceeds once the pod is ready
        await().atMost(10, TimeUnit.SECONDS).until(() -> getSimpleK8SClient().pods()
                .loadPod(testResource.getMetadata().getNamespace(), Collections.singletonMap("pod-label", "successful-pod")) == null);
        assertThat(
                getSimpleK8SClient().pods()
                        .loadPod(testResource.getMetadata().getNamespace(), Collections.singletonMap("pod-label", "failed-pod")),
                is(notNullValue()));
    }

    @Test
    void shouldRunPodToCompletion() throws TimeoutException {
        awaitDefaultToken(testResource.getMetadata().getNamespace());

        //Given I have started a new Pod
        final Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("my-pod")
                .withNamespace(testResource.getMetadata().getNamespace())
                .addToLabels("pod-label", "123")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withImage("busybox")
                .withName("sleep")
                .withCommand("sleep")
                .withArgs("5")
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .build();
        //When I wait for the pod
        final Pod actual = getSimpleK8SClient().pods().runToCompletion(pod, EntandoOperatorSpiConfig.getPodCompletionTimeoutSeconds());
        //Then the current thread only proceeds once the pod has completed
        assertThat(PodResult.of(actual).getState(), is(State.COMPLETED));
        assertThat(PodResult.of(actual).hasFailed(), is(false));
    }

    @Test
    @Disabled("Currently used for optimization only")
    void testProbes() throws TimeoutException {
        awaitDefaultToken(testResource.getMetadata().getNamespace());
        step("Given I have started a new Pod");
        final Pod startedPod = this.fabric8Client.pods().inNamespace(newTestResource().getMetadata().getNamespace())
                .createOrReplace(new PodBuilder()
                        .withNewMetadata()
                        .withName("my-pod")
                        .withNamespace(newTestResource().getMetadata().getNamespace())
                        .addToLabels("pod-label", "123")
                        .endMetadata()
                        .withNewSpec()
                        .addNewContainer()
                        .withImage("centos/nginx-116-centos7")
                        .withName("nginx")
                        .withCommand("/usr/libexec/s2i/run")
                        //                .withNewStartupProbe()
                        //                .withNewExec()
                        //                .withCommand("cat", "/tmp/started")
                        //                .endExec()
                        //                .withPeriodSeconds(5)
                        //                .withFailureThreshold(10)
                        //                .endStartupProbe()
                        .withNewLivenessProbe()
                        .withNewExec()
                        .withCommand("cat", "/tmp/live")
                        .endExec()
                        .withInitialDelaySeconds(120)
                        .withPeriodSeconds(5)
                        .withFailureThreshold(1)
                        .withSuccessThreshold(1)
                        .endLivenessProbe()
                        .withNewReadinessProbe()
                        .withNewExec()
                        .withCommand("cat", "/tmp/ready")
                        .endExec()
                        .withPeriodSeconds(5)
                        .endReadinessProbe()
                        .endContainer()
                        .endSpec()
                        .build());
        //        getKubernetesClientForControllers().executeOnPod(startedPod, "nginx", 5, "touch /tmp/started");
        getSimpleK8SClient().entandoResources().executeOnPod(startedPod, "nginx", 5, "touch /tmp/ready");
        getSimpleK8SClient().entandoResources().executeOnPod(startedPod, "nginx", 5, "touch /tmp/live");
        getSimpleK8SClient().entandoResources().executeOnPod(startedPod, "nginx", 5, "rm /tmp/live");
        Assertions.assertThat(startedPod).isNotNull();
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE_1};
    }
}
