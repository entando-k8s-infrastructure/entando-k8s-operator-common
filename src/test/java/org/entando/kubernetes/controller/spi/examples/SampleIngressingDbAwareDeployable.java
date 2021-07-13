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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.controller.support.spibase.IngressingDeployableBase;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoIngressingDeploymentSpec;

public abstract class SampleIngressingDbAwareDeployable<S extends EntandoIngressingDeploymentSpec> implements
        IngressingDeployableBase<DefaultExposedDeploymentResult>, DbAwareDeployable<DefaultExposedDeploymentResult> {

    protected final EntandoBaseCustomResource<S, EntandoCustomResourceStatus> entandoResource;
    protected final List<DeployableContainer> containers;
    protected final DatabaseConnectionInfo databaseConnectionInfo;

    public SampleIngressingDbAwareDeployable(EntandoBaseCustomResource<S, EntandoCustomResourceStatus> entandoResource,
            DatabaseConnectionInfo databaseConnectionInfo) {
        this.entandoResource = entandoResource;
        this.databaseConnectionInfo = databaseConnectionInfo;
        this.containers = createContainers(entandoResource);
    }

    @Override
    public String getServiceAccountToUse() {
        return entandoResource.getSpec().getServiceAccountToUse().orElse(getDefaultServiceAccountName());
    }

    protected abstract List<DeployableContainer> createContainers(
            EntandoBaseCustomResource<S, EntandoCustomResourceStatus> entandoResource);

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public Optional<String> getQualifier() {
        return Optional.of(NameUtils.DEFAULT_SERVER_QUALIFIER);
    }

    @Override
    public EntandoBaseCustomResource<S, EntandoCustomResourceStatus> getCustomResource() {
        return entandoResource;
    }

    @Override
    public DefaultExposedDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new DefaultExposedDeploymentResult(pod, service, ingress);
    }

    @Override
    public String getDefaultServiceAccountName() {
        return "plugin";
    }

    @Override
    public String getIngressName() {
        return ((EntandoCustomResource) entandoResource).getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX;
    }

    @Override
    public String getIngressNamespace() {
        return entandoResource.getMetadata().getNamespace();
    }

    @Override
    public boolean isIngressRequired() {
        return true;
    }
}
