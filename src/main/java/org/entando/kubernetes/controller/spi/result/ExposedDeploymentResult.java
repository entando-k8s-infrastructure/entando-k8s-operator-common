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

package org.entando.kubernetes.controller.spi.result;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import org.entando.kubernetes.controller.spi.common.SerializeByReference;
import org.entando.kubernetes.model.common.ServerStatus;

public class ExposedDeploymentResult<T extends ExposedDeploymentResult<T>> extends ExposedService implements ServiceDeploymentResult<T> {

    private ServerStatus status;
    private final Pod pod;

    public ExposedDeploymentResult(Pod pod, Service service, Ingress ingress) {
        super(service, ingress);
        this.pod = pod;
    }

    @Override
    public T withStatus(ServerStatus status) {
        this.status = status;
        return thisAsT();
    }

    @SuppressWarnings("unchecked")
    private T thisAsT() {
        return (T) this;
    }

    @Override
    public ServerStatus getStatus() {
        return status;
    }

    @Override
    @SerializeByReference
    public Pod getPod() {
        return pod;
    }
}
