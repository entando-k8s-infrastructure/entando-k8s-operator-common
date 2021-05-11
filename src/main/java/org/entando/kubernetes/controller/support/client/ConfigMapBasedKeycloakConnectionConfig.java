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

package org.entando.kubernetes.controller.support.client;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;

public class ConfigMapBasedKeycloakConnectionConfig implements KeycloakConnectionConfig {

    private final Secret adminSecret;
    private final ConfigMap configMap;

    public ConfigMapBasedKeycloakConnectionConfig(Secret adminSecret, ConfigMap configMap) {
        this.adminSecret = adminSecret;
        this.configMap = configMap;
    }

    @Override
    public String determineBaseUrl() {
        if (EntandoOperatorSpiConfig.forceExternalAccessToKeycloak()) {
            return getExternalBaseUrl();
        } else {
            return getInternalBaseUrl().orElse(getExternalBaseUrl());
        }
    }

    @Override
    public Secret getAdminSecret() {
        return adminSecret;
    }

    //TODO put these in this in the ProvidedCapability ConfigMap
    @Override
    public String getExternalBaseUrl() {
        return configMap.getData().get(NameUtils.URL_KEY);
    }

    //TODO derive thi from the ProvidedCapability service
    @Override
    public Optional<String> getInternalBaseUrl() {
        return Optional.ofNullable(configMap.getData().get(NameUtils.INTERNAL_URL_KEY));
    }
}
