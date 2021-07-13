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

package org.entando.kubernetes.test.common;

import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.deployable.ExternalService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class ExternalDatabaseService implements ExternalService {

    private final EntandoDatabaseService databaseService;

    public ExternalDatabaseService(EntandoDatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public String getHost() {
        return databaseService.getSpec().getHost().orElseThrow(IllegalStateException::new);
    }

    @Override
    public int getPort() {
        return databaseService.getSpec().getPort()
                .orElse(DbmsVendorConfig.valueOf(databaseService.getSpec().getDbms().orElseThrow(IllegalStateException::new).name())
                        .getDefaultPort());
    }

    @Override
    public boolean getCreateDelegateService() {
        return true;
    }
}
