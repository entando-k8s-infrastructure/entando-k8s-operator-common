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

import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;

public class DbAwareDeployableFluent<N extends DbAwareDeployableFluent<N>> extends SecretiveDeployableFluent<N> implements
        DbAwareDeployable<DefaultExposedDeploymentResult> {

    public N withProvidedDatabase(ProvidedDatabaseCapability databaseCapability) {
        this.getContainers().stream().filter(DbAwareContainerFluent.class::isInstance).map(DbAwareContainerFluent.class::cast).forEach(
                dbAwareContainerFluent -> dbAwareContainerFluent.determineDatabaseSchemaInfo(databaseCapability));
        return thisAsN();
    }
}
