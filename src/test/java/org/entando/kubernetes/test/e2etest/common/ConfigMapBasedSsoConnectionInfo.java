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

package org.entando.kubernetes.test.e2etest.common;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;

//TODO get rid of this class
public class ConfigMapBasedSsoConnectionInfo implements SsoConnectionInfo {

    private final Secret adminSecret;
    private final ConfigMap configMap;

    public ConfigMapBasedSsoConnectionInfo(Secret adminSecret, ConfigMap configMap) {
        this.adminSecret = adminSecret;
        this.configMap = configMap;
    }

    @Override
    public String getBaseUrlToUse() {
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

    @Override
    public String getExternalBaseUrl() {
        return configMap.getData().get(NameUtils.URL_KEY);
    }

    @Override
    public Optional<String> getDefaultRealm() {
        return Optional.of(KeycloakName.ENTANDO_DEFAULT_KEYCLOAK_REALM);
    }

    @Override
    public Optional<String> getInternalBaseUrl() {
        return Optional.ofNullable(configMap.getData().get(NameUtils.INTERNAL_URL_KEY));
    }
}
