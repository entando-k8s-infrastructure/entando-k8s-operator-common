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

package org.entando.kubernetes.controller.spi.deployable;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.SerializeByReference;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public interface Deployable<T extends ServiceDeploymentResult<T>> {

    /**
     * NB!!! Implementations need to implement this as a non-modifiable list with the exact same instances returned consistently.
     */
    List<DeployableContainer> getContainers();

    default Optional<ExternalService> getExternalService() {
        return Optional.empty();
    }

    /**
     * Optional qualifying string for scenarios where a single Custom Resource results in more than one deployment.
     */
    Optional<String> getQualifier();

    @SerializeByReference
    EntandoCustomResource getCustomResource();

    T createResult(Deployment deployment, Service service, Ingress ingress, Pod pod);

    String getServiceAccountToUse();

    default String getDefaultServiceAccountName() {
        return "default";
    }

    default int getReplicas() {
        return 1;
    }

    default Optional<Long> getFileSystemUserAndGroupId() {
        return Optional.empty();
    }
}
