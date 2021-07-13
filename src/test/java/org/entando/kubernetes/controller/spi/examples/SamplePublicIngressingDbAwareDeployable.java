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

import static org.entando.kubernetes.controller.spi.common.SecretUtils.generateSecret;

import io.fabric8.kubernetes.api.model.Secret;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.KeycloakAwareSpec;

public class SamplePublicIngressingDbAwareDeployable<S extends KeycloakAwareSpec> extends
        SampleIngressingDbAwareDeployable<S> implements PublicIngressingDeployable<DefaultExposedDeploymentResult>,
        Secretive {

    private final Secret sampleSecret;
    private final SsoConnectionInfo ssoConnectionInfo;

    public SamplePublicIngressingDbAwareDeployable(EntandoBaseCustomResource<S, EntandoCustomResourceStatus> entandoResource,
            DatabaseConnectionInfo databaseConnectionInfo,
            SsoConnectionInfo ssoConnectionInfo) {
        super(entandoResource, databaseConnectionInfo);
        this.ssoConnectionInfo = ssoConnectionInfo;
        sampleSecret = generateSecret(this.entandoResource, secretName(this.entandoResource),
                "entando_keycloak_admin");

    }

    public static <T extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>> String secretName(T resource) {
        return resource.getMetadata().getName() + "-admin-secret";
    }

    protected List<DeployableContainer> createContainers(EntandoBaseCustomResource<S, EntandoCustomResourceStatus> entandoResource) {
        return Collections.singletonList(new SampleDeployableContainer<>(entandoResource, databaseConnectionInfo));
    }

    @Override
    public List<Secret> getSecrets() {
        return Collections.singletonList(sampleSecret);
    }

    @Override
    public String getServiceAccountToUse() {
        return this.entandoResource.getSpec().getServiceAccountToUse().orElse(getDefaultServiceAccountName());
    }

    @Override
    public boolean isIngressRequired() {
        return true;
    }

    @Override
    public SsoConnectionInfo getSsoConnectionInfo() {
        return ssoConnectionInfo;
    }

    @Override
    public SsoClientConfig getSsoClientConfig() {
        return new SsoClientConfig(KeycloakName.ENTANDO_DEFAULT_KEYCLOAK_REALM,
                entandoResource.getMetadata().getName() + "-" + NameUtils.DEFAULT_SERVER_QUALIFIER,
                entandoResource.getMetadata().getName() + "-" + NameUtils.DEFAULT_SERVER_QUALIFIER);
    }

    @Override
    public Optional<String> getPublicClientId() {
        return Optional.of(KeycloakName.PUBLIC_CLIENT_ID);
    }
}
