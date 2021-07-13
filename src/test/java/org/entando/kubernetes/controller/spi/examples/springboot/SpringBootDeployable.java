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

package org.entando.kubernetes.controller.spi.examples.springboot;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.controller.support.spibase.IngressingDeployableBase;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.KeycloakAwareSpec;

public class SpringBootDeployable<S extends KeycloakAwareSpec> implements
        IngressingDeployableBase<DefaultExposedDeploymentResult>,
        DbAwareDeployable<DefaultExposedDeploymentResult> {

    private final EntandoBaseCustomResource<S, EntandoCustomResourceStatus> customResource;
    private final SsoConnectionInfo ssoConnectionInfo;
    private final DeployableContainer container;

    public SpringBootDeployable(EntandoBaseCustomResource<S, EntandoCustomResourceStatus> customResource,
            SsoConnectionInfo ssoConnectionInfo,
            DatabaseConnectionInfo databaseConnectionInfo) {
        this.customResource = customResource;
        this.ssoConnectionInfo = ssoConnectionInfo;
        container = new SampleSpringBootDeployableContainer<>(customResource, databaseConnectionInfo, ssoConnectionInfo,
                new SsoClientConfig("entando", "asdf", "asdf"));
    }

    /**
     * NB!!! Implementations need to implement this as a non-modifiable list with the exact same instances returned consistently.
     */
    @Override
    public List<DeployableContainer> getContainers() {
        return Arrays.asList(container);
    }

    @Override
    public String getIngressName() {
        return customResource.getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX;
    }

    @Override
    public String getIngressNamespace() {
        return customResource.getMetadata().getNamespace();
    }

    @Override
    public Optional<String> getQualifier() {
        return Optional.of(NameUtils.DEFAULT_SERVER_QUALIFIER);
    }

    @Override
    public EntandoBaseCustomResource<S, EntandoCustomResourceStatus> getCustomResource() {
        return customResource;
    }

    @Override
    public DefaultExposedDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new DefaultExposedDeploymentResult(pod, service, ingress);
    }

    @Override
    public String getServiceAccountToUse() {
        return this.customResource.getSpec().getServiceAccountToUse().orElse(getDefaultServiceAccountName());
    }

    @Override
    public boolean isIngressRequired() {
        return true;
    }

}
