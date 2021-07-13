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

package org.entando.kubernetes.controller.spi.examples.barebones;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentSpec;

public class BareBonesDeployable<S extends EntandoDeploymentSpec> implements Deployable<BarebonesDeploymentResult>, Secretive {

    public static final String MY_SERVICE_ACCOUNT = "my-service-account";
    public static final String NAME_QUALIFIER = "db";
    private final List<DeployableContainer> containers;
    private EntandoBaseCustomResource<S, EntandoCustomResourceStatus> customResource;
    private DbmsDockerVendorStrategy dbmsVendor = DbmsDockerVendorStrategy.CENTOS_POSTGRESQL;

    public BareBonesDeployable(EntandoBaseCustomResource<S, EntandoCustomResourceStatus> customResource) {
        this(customResource, new BareBonesContainer());
    }

    public BareBonesDeployable(EntandoBaseCustomResource<S, EntandoCustomResourceStatus> customResource,
            DeployableContainer... containers) {
        this.customResource = customResource;
        this.containers = Arrays.asList(containers);
    }

    @Override
    public List<Secret> getSecrets() {
        Secret secret = SecretUtils.generateSecret(customResource, BareBonesContainer.getDatabaseAdminSecretName(),
                dbmsVendor.getDefaultAdminUsername());
        return Arrays.asList(secret);
    }

    /**
     * NB!!! Implementations need to implement this as a non-modifiable list with the exact same instances returned consistently.
     */
    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public Optional<String> getQualifier() {
        return Optional.of(NAME_QUALIFIER);
    }

    @Override
    public EntandoBaseCustomResource<S, EntandoCustomResourceStatus> getCustomResource() {
        return customResource;
    }

    @Override
    public BarebonesDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new BarebonesDeploymentResult(service, pod);
    }

    @Override
    public String getServiceAccountToUse() {
        return customResource.getSpec().getServiceAccountToUse().orElse(getDefaultServiceAccountName());
    }

    @Override
    public String getDefaultServiceAccountName() {
        return MY_SERVICE_ACCOUNT;
    }
}
