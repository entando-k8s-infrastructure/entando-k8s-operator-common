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

package org.entando.kubernetes.test.common;

import static org.entando.kubernetes.controller.spi.common.PodResult.SUCCEEDED_PHASE;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.support.client.PodClient;

public interface PodBehavior {

    default Pod podWithReadyStatus(Deployment deployment) {
        return podWithReadyStatus(podFrom(deployment));
    }

    default Pod podWithReadyStatus(Pod pod) {
        PodStatus status = new PodStatusBuilder().withPhase("Running")
                .withContainerStatuses(statusesFor(pod.getSpec().getContainers()))
                .withInitContainerStatuses(statusesFor(pod.getSpec().getInitContainers()))
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build();
        pod.setStatus(status);
        return pod;
    }

    default Pod podWithSucceededStatus(Pod pod) {
        PodStatus status = new PodStatusBuilder().withPhase(SUCCEEDED_PHASE)
                .withContainerStatuses(statusesFor(pod.getSpec().getContainers()))
                .withInitContainerStatuses(statusesFor(pod.getSpec().getInitContainers()))
                .addNewCondition().withType("ContainersReady").withStatus("True").endCondition()
                .addNewCondition().withType("Ready").withStatus("True").endCondition().build();
        pod.setStatus(status);
        return pod;
    }

    default Pod podWithSucceededStatus(Deployment deployment) {
        return podWithSucceededStatus(podFrom(deployment));
    }

    default Pod podWithFailedStatus(Pod pod) {
        PodStatus status = new PodStatusBuilder().withPhase(PodResult.FAILED_PHASE)
                .withContainerStatuses(statusesFor(pod.getSpec().getContainers()))
                .withInitContainerStatuses(statusesFor(pod.getSpec().getInitContainers())).build();
        pod.setStatus(status);
        return pod;
    }

    default Pod podFrom(Deployment deployment) {
        PodTemplateSpec template = deployment.getSpec().getTemplate();
        return new PodBuilder()
                .withNewMetadata()
                .withLabels(template.getMetadata().getLabels())
                .withNamespace(deployment.getMetadata().getNamespace())
                .withName(deployment.getMetadata().getName() + "-" + SecretUtils.randomAlphanumeric(8))
                .endMetadata()
                .withNewSpec()
                .withContainers(template.getSpec().getContainers())
                .withInitContainers(template.getSpec().getInitContainers())
                .endSpec()
                .build();
    }

    default List<ContainerStatus> statusesFor(List<Container> initContainers) {
        return initContainers.stream()
                .map(container -> new ContainerStatusBuilder().withNewState().withNewTerminated().withExitCode(0).endTerminated()
                        .endState().build()).collect(Collectors.toList());
    }

    default Pod podWithStatus(PodClient client, PodStatus status, String namespace) {
        return client.start(new PodBuilder().withNewMetadata()
                .withName("pod-" + RandomStringUtils.randomAlphanumeric(8))
                .withNamespace(namespace)
                .endMetadata()
                .editOrNewSpec().addNewContainer().endContainer().endSpec()
                .editOrNewSpec().addNewInitContainer().endInitContainer().endSpec()
                .withStatus(status).build());
    }

}
