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

package org.entando.kubernetes.controller.spi.command;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.result.ExposedDeploymentResult;

public class DefaultSerializableDeploymentResult
        extends ExposedDeploymentResult<DefaultSerializableDeploymentResult>
        implements SerializableDeploymentResult<DefaultSerializableDeploymentResult> {

    private final Deployment deployment;

    public DefaultSerializableDeploymentResult(Deployment deployment, Pod pod, Service service, Ingress ingress) {
        super(pod, service, ingress);
        this.deployment = deployment;
    }

    @Override
    public Deployment getDeployment() {
        return deployment;
    }

    @Override
    public String getExternalHostUrl() {
        return Optional.ofNullable(getIngress()).map(i -> super.getExternalHostUrl()).orElse(null);
    }

    @Override
    public String getExternalBaseUrl() {
        return Optional.ofNullable(getIngress()).map(i -> super.getExternalBaseUrl()).orElse(null);
    }

    @Override
    public String getInternalBaseUrl() {
        return Optional.ofNullable(getIngress()).map(i -> super.getInternalBaseUrl()).orElse(null);
    }

    @Override
    protected boolean isTlsEnabled() {
        return Optional.ofNullable(getIngress()).map(i -> super.isTlsEnabled()).orElse(false);
    }
}
