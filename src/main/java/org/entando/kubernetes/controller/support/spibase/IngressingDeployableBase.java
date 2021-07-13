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

package org.entando.kubernetes.controller.support.spibase;

import io.fabric8.kubernetes.client.CustomResource;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.deployable.IngressingDeployable;
import org.entando.kubernetes.controller.spi.result.ExposedDeploymentResult;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentSpec;
import org.entando.kubernetes.model.common.EntandoIngressingDeploymentSpec;
import org.entando.kubernetes.model.common.EntandoResourceRequirements;

public interface IngressingDeployableBase<T extends ExposedDeploymentResult<T>> extends IngressingDeployable<T> {

    @Override
    default boolean isIngressRequired() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    default Optional<String> getFileUploadLimit() {
        return ((CustomResource<EntandoDeploymentSpec, EntandoCustomResourceStatus>) getCustomResource()).getSpec()
                .getResourceRequirements().flatMap(EntandoResourceRequirements::getFileUploadLimit);
    }

    @Override
    @SuppressWarnings("unchecked")
    default Optional<String> getTlsSecretName() {
        return ((CustomResource<EntandoIngressingDeploymentSpec, EntandoCustomResourceStatus>) getCustomResource()).getSpec()
                .getTlsSecretName();
    }

    @Override
    @SuppressWarnings("unchecked")
    default Optional<String> getIngressHostName() {
        return ((CustomResource<EntandoIngressingDeploymentSpec, EntandoCustomResourceStatus>) getCustomResource()).getSpec()
                .getIngressHostName();
    }

}
