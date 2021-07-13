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

import io.fabric8.kubernetes.api.model.Service;

public abstract class AbstractServiceResult implements ServiceResult {

    protected final Service service;
    protected String adminSecretName;

    protected AbstractServiceResult(Service service) {
        this(service, null);
    }

    protected AbstractServiceResult(Service service, String adminSecretName) {
        this.service = service;
        this.adminSecretName = adminSecretName;
    }

    @Override
    public String getAdminSecretName() {
        return adminSecretName;
    }

    @Override
    public String getInternalServiceHostname() {
        if (service == null) {
            return null;
        }
        return service.getMetadata().getName() + "." + service.getMetadata().getNamespace() + ".svc.cluster.local";
    }

    @Override
    public String getPort() {
        if (service == null) {
            return null;
        }
        return service.getSpec().getPorts().get(0).getPort().toString();
    }

    @Override
    public Service getService() {
        return service;
    }

}
