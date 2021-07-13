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

package org.entando.kubernetes.fluentspi;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer.SpringProperty;
import org.entando.kubernetes.controller.spi.container.SsoAwareContainer;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;

public abstract class SsoAwareContainerFluent<N extends SsoAwareContainerFluent<N>> extends DeployableContainerFluent<N> implements
        SsoAwareContainer {

    private SsoConnectionInfo ssoConnectionInfo;
    private SsoClientConfig ssoClientConfig;

    public N withSsoConnectionInfo(SsoConnectionInfo ssoConnectionInfo) {
        this.ssoConnectionInfo = ssoConnectionInfo;
        return thisAsN();
    }

    public N withSsoClientConfig(SsoClientConfig ssoClientConfig) {
        this.ssoClientConfig = ssoClientConfig;
        return thisAsN();
    }

    @Override
    public List<EnvVar> getSsoVariables() {
        List<EnvVar> vars = new ArrayList<>();
        ofNullable(ssoConnectionInfo).ifPresent(si -> {
            vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name(),
                    ssoConnectionInfo.getExternalBaseUrl() + "/realms/" + ssoClientConfig.getRealm(),
                    null));
        });

        String keycloakSecretName = KeycloakName.forTheClientSecret(ssoClientConfig);
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name(), null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name(), null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_ID_KEY)));
        return vars;
    }
}
