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

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.Collections;
import java.util.List;
import org.entando.kubernetes.controller.spi.container.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.spi.container.DbAwareContainer;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;

public class DbAwareKeycloakContainer extends MinimalKeycloakContainer implements DbAwareContainer {

    private final List<DatabaseSchemaConnectionInfo> dbSchemaInfo;

    public DbAwareKeycloakContainer(EntandoKeycloakServer entandoResource, DatabaseConnectionInfo databaseConnectionInfo) {
        super(entandoResource);
        this.dbSchemaInfo = DbAwareContainer
                .buildDatabaseSchemaConnectionInfo(entandoResource, databaseConnectionInfo, Collections.singletonList("db"));

    }

    @Override
    public List<EnvVar> getDatabaseConnectionVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo() {
        return dbSchemaInfo;
    }

}
