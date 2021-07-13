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

package org.entando.kubernetes.controller.spi.capability;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Optional;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.model.common.ServerStatus;

public class SerializedCapabilityProvisioningResult implements CapabilityProvisioningResult {

    private ProvidedCapability providedCapability;
    private Service service;
    private Ingress ingress;
    private Secret adminSecret;
    private EntandoControllerFailure controllerFailure;

    public SerializedCapabilityProvisioningResult(ProvidedCapability providedCapability, Service service, Ingress ingress,
            Secret adminSecret) {
        this.providedCapability = providedCapability;
        this.service = service;
        this.ingress = ingress;
        this.adminSecret = adminSecret;
    }

    public SerializedCapabilityProvisioningResult(EntandoControllerFailure controllerFailure) {
        this.controllerFailure = controllerFailure;
    }

    @Override
    public ProvidedCapability getProvidedCapability() {
        return providedCapability;
    }

    @Override
    public Service getService() {
        return service;
    }

    @Override
    public Optional<Ingress> getIngress() {
        return Optional.ofNullable(ingress);
    }

    @Override
    public Optional<Secret> getAdminSecret() {
        return Optional.ofNullable(adminSecret);
    }

    @Override
    public Optional<EntandoControllerFailure> getControllerFailure() {
        return Optional.ofNullable(controllerFailure)
                .or(() -> providedCapability.getStatus().findFailedServerStatus().flatMap(ServerStatus::getEntandoControllerFailure));
    }
}
