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

package org.entando.kubernetes.controller.spi.examples.barebones;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.entando.kubernetes.controller.spi.result.AbstractServiceResult;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.model.common.ServerStatus;

public class BarebonesDeploymentResult extends AbstractServiceResult implements ServiceDeploymentResult<BarebonesDeploymentResult> {

    private final Pod pod;
    private ServerStatus status;

    public BarebonesDeploymentResult(Service service, Pod pod) {
        super(service);
        this.pod = pod;
    }

    @Override
    public String getInternalServiceHostname() {
        return pod.getStatus().getPodIP();
    }

    @Override
    public String getPort() {
        return pod.getSpec().getContainers().get(0).getPorts().get(0).getContainerPort().toString();
    }

    @Override
    public BarebonesDeploymentResult withStatus(ServerStatus status) {
        this.status = status;
        return this;
    }

    @Override
    public Pod getPod() {
        return pod;
    }

    @Override
    public ServerStatus getStatus() {
        return status;
    }
}
