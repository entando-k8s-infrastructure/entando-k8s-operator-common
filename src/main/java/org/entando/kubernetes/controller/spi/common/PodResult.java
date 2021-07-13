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

package org.entando.kubernetes.controller.spi.common;

import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

//TODO move to org.entando.kubernetes.controller.support
public class PodResult {

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal
            .withInitial(() -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()));
    public static final String RUNNING_PHASE = "Running";
    public static final String SUCCEEDED_PHASE = "Succeeded";
    public static final String FAILED_PHASE = "Failed";
    private static final String PENDING_PHASE = "Pending";
    private boolean someContainersNotDidNotRun;

    private State state;
    private String failReason;

    @SuppressWarnings("PMD.ShortMethodName")
    //This is perfectly fine for fluent
    public static PodResult of(Pod pod) {
        PodResult podResult = new PodResult();
        podResult.init(pod);
        return podResult;
    }

    public void init(Pod pod) {
        //TODO put more diagnostic info in this
        if (pod.getStatus().getPhase().equals(FAILED_PHASE)) {
            failReason = Optional.ofNullable(pod.getStatus().getReason()).orElse(FAILED_PHASE);
            setState(State.COMPLETED);
        } else if (findFailedContainer(pod).isPresent()) {
            failReason = Optional
                    .ofNullable(findFailedContainer(pod).orElseThrow(IllegalStateException::new).getState().getTerminated().getReason())
                    .orElse(FAILED_PHASE);
            setState(State.COMPLETED);
        } else if (pod.getStatus().getPhase().equals(PENDING_PHASE)) {
            setState(State.CREATING);
        } else if (pod.getStatus().getPhase().equals(RUNNING_PHASE)) {
            if (hasCondition(pod, "Ready") && hasCondition(pod, "ContainersReady")) {
                setState(State.READY);
            } else {
                setState(State.RUNNING);
            }
        } else if (pod.getStatus().getPhase().equals(SUCCEEDED_PHASE)) {
            setState(State.COMPLETED);
        }
        this.someContainersNotDidNotRun = didSomeContainersNotRun(pod);
    }

    private boolean hasCondition(Pod pod, String condition) {
        return pod.getStatus().getConditions().stream()
                .anyMatch(podCondition -> condition.equals(podCondition.getType()) && "True".equals(podCondition.getStatus()));
    }

    protected boolean didSomeContainersNotRun(Pod pod) {
        return pod.getSpec().getContainers().size() > pod.getStatus().getContainerStatuses().size()
                || pod.getSpec().getInitContainers().size() > pod.getStatus().getInitContainerStatuses().size();
    }

    public boolean hasFailed() {
        return failReason != null || someContainersNotDidNotRun;
    }

    public State getState() {
        return state;
    }

    private void setState(State state) {
        this.state = state;
    }

    public String getFailReason() {
        if (failReason == null && someContainersNotDidNotRun) {
            return "SomeContainersNotRun";
        }
        return failReason;
    }

    private Optional<ContainerStatus> findFailedContainer(Pod pod) {
        List<ContainerStatus> containerStatuses = new ArrayList<>();
        Optional.ofNullable(pod.getStatus().getInitContainerStatuses()).ifPresent(containerStatuses::addAll);
        Optional.ofNullable(pod.getStatus().getContainerStatuses()).ifPresent(containerStatuses::addAll);
        return containerStatuses.stream().filter(this::failed).findFirst();
    }

    private boolean failed(ContainerStatus status) {
        return Optional.ofNullable(status.getState().getTerminated()).map(this::hasStateFailed).orElse(false);
    }

    private boolean hasStateFailed(ContainerStateTerminated state) {
        return hasFailReason(state) || hasFailureExitCode(state);
    }

    private boolean hasFailReason(ContainerStateTerminated state) {
        return "Error".equals(state.getReason());
    }

    private boolean hasFailureExitCode(ContainerStateTerminated state) {
        return Optional.ofNullable(state.getExitCode()).map(exitCode -> exitCode != 0).orElse(false);
    }

    public enum State {
        CREATING, RUNNING, READY, COMPLETED
    }
}
