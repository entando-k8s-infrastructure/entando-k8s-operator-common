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

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.container.HasHealthCommand;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer;
import org.entando.kubernetes.controller.spi.container.ServiceBackingContainer;

public class TestResourceContainer implements ServiceBackingContainer, HasHealthCommand, PersistentVolumeAwareContainer {

    private final DbmsDockerVendorStrategy dbmsStrategy;

    public TestResourceContainer(DbmsDockerVendorStrategy dbmsStrategy) {
        this.dbmsStrategy = dbmsStrategy;
    }

    @Override
    public String getNameQualifier() {
        return "db";
    }

    @Override
    public int getPrimaryPort() {
        return dbmsStrategy.getPort();
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        return Arrays.asList(new EnvVar("DB_ADMIN", dbmsStrategy.getDefaultAdminUsername(), null));
    }

    @Override
    public String determineImageToUse() {
        return dbmsStrategy.getOrganization() + "/" + dbmsStrategy.getImageRepository();
    }

    @Override
    public String getHealthCheckCommand() {
        return dbmsStrategy.getHealthCheck();
    }

    @Override
    public String getVolumeMountPath() {
        return dbmsStrategy.getVolumeMountPath();
    }

}
