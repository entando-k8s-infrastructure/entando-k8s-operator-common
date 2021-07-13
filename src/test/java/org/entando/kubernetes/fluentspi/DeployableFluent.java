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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public abstract class DeployableFluent<N extends DeployableFluent<N>> implements Deployable<DefaultExposedDeploymentResult> {

    protected List<DeployableContainerFluent<?>> containers = new ArrayList<>();
    protected String qualifier;
    protected EntandoCustomResource customResource;
    private String serviceAccountToUse = "default";
    private Long fileSystemUserAndGroupId;

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<DeployableContainer> getContainers() {
        return (List) this.containers;
    }

    public <C extends DeployableContainerFluent<C>> C withContainer(C container) {
        this.containers.add(container);
        ofNullable(customResource).ifPresent(container::withCustomResource);
        return container;
    }

    @SuppressWarnings("unchecked")
    protected N thisAsN() {
        return (N) this;
    }

    @Override
    public Optional<String> getQualifier() {
        return ofNullable(this.qualifier);
    }

    public N withQualifier(String qualifier) {
        this.qualifier = qualifier;
        return thisAsN();
    }

    @Override
    public EntandoCustomResource getCustomResource() {
        return this.customResource;
    }

    public N withCustomResource(EntandoCustomResource customResource) {
        this.customResource = customResource;
        this.containers.forEach(c -> c.withCustomResource(customResource));
        return thisAsN();
    }

    @Override
    public DefaultExposedDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new DefaultExposedDeploymentResult(pod, service, ingress);
    }

    @Override
    public String getServiceAccountToUse() {
        return this.serviceAccountToUse;
    }

    public N withServiceAccountToUse(String serviceAccountToUse) {
        this.serviceAccountToUse = serviceAccountToUse;
        return thisAsN();
    }

    @Override
    public Optional<Long> getFileSystemUserAndGroupId() {
        return ofNullable(fileSystemUserAndGroupId);
    }

    public N withFileSystemUserAndGroupId(Long fileSystemUserAndGroupId) {
        this.fileSystemUserAndGroupId = fileSystemUserAndGroupId;
        return thisAsN();
    }
}
