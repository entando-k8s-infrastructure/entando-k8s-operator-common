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

package org.entando.kubernetes.controller.spi.container;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Secret;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.common.ServerStatus;

public class ProvidedSsoCapability implements SsoConnectionInfo {

    public static final String DEFAULT_REALM_PARAMETER = "defaultRealm";

    private final CapabilityProvisioningResult capabilityResult;
    private final ExposedService exposedService;

    public ProvidedSsoCapability(CapabilityProvisioningResult capabilityResult) {
        super();
        this.capabilityResult = capabilityResult;
        this.exposedService = new ExposedService(capabilityResult.getService(), capabilityResult.getIngress().orElse(null));
    }

    @Override
    public Secret getAdminSecret() {
        return capabilityResult.getAdminSecret().orElseThrow(IllegalStateException::new);
    }

    @Override
    public String getBaseUrlToUse() {
        if (useExternalService() || EntandoOperatorSpiConfig.forceExternalAccessToKeycloak()) {
            return getExternalBaseUrl();
        } else {
            return exposedService.getInternalBaseUrl();
        }
    }

    private boolean useExternalService() {
        return capabilityResult.getProvidedCapability().getSpec().getProvisioningStrategy()
                .map(CapabilityProvisioningStrategy.USE_EXTERNAL::equals)
                .orElse(false);
    }

    @Override
    public String getExternalBaseUrl() {
        return capabilityResult.getProvidedCapability().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER)
                .flatMap(ServerStatus::getExternalBaseUrl).orElseThrow(IllegalStateException::new);
    }

    @Override
    public Optional<String> getDefaultRealm() {
        return ofNullable(
                capabilityResult.getProvidedCapability().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER)
                        .orElseThrow(IllegalStateException::new)
                        .getDerivedDeploymentParameters()).flatMap(map -> ofNullable(map.get(DEFAULT_REALM_PARAMETER)));
    }

    @Override
    public Optional<String> getInternalBaseUrl() {
        if (exposedService.getService() == null) {
            return Optional.empty();
        } else {
            return of(exposedService.getInternalBaseUrl());
        }
    }

}
