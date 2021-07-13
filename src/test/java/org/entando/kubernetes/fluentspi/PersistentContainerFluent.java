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

import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer;

public class PersistentContainerFluent<N extends PersistentContainerFluent<N>> extends
        DeployableContainerFluent<N> implements
        PersistentVolumeAwareContainer {

    private String accessMode;
    private String storageClass;
    private int storageLimitMebibytes;
    private String volumeMountPath;

    @Override
    public String getVolumeMountPath() {
        return this.volumeMountPath;
    }

    public N withVolumeMountPath(String volumeMountPath) {
        this.volumeMountPath = volumeMountPath;
        return thisAsN();
    }

    @Override
    public int getStorageLimitMebibytes() {
        return this.storageLimitMebibytes;
    }

    public N withStorageLimitMebibytes(int storageLimitMebibytes) {
        this.storageLimitMebibytes = storageLimitMebibytes;
        return thisAsN();
    }

    @Override
    public Optional<String> getStorageClass() {
        return Optional.ofNullable(this.storageClass);
    }

    public N withStorageClass(String storageClass) {
        this.storageClass = storageClass;
        return thisAsN();
    }

    @Override
    public Optional<String> getAccessMode() {
        return Optional.ofNullable(this.accessMode);
    }

    public N withAccessMode(String accessMode) {
        this.accessMode = accessMode;
        return thisAsN();
    }

}
