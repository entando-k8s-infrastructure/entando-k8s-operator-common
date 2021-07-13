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

package org.entando.kubernetes.controller.spi.examples;

import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public abstract class SampleController<R extends ServiceDeploymentResult<R>> implements Runnable {

    private final SimpleK8SClient<?> k8sClient;
    private final SimpleKeycloakClient keycloakClient;

    public SampleController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient) {
        this.k8sClient = k8sClient;
        this.keycloakClient = keycloakClient;
    }

    @Override
    public void run() {
    }

    protected abstract Deployable<R> createDeployable(EntandoCustomResource newEntandoKeycloakServer,
            DatabaseConnectionInfo databaseConnectionInfo,
            SsoConnectionInfo ssoConnectionInfo);

}
