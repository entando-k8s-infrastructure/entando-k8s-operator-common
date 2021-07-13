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

package org.entando.kubernetes.controller.spi.container;

import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.Secret;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;

public class DefaultDatabaseSchemaConnectionInfo implements DatabaseSchemaConnectionInfo {

    private final DatabaseConnectionInfo databaseConnectionInfo;
    private final String schemaName;
    private final Secret schemaSecret;

    public DefaultDatabaseSchemaConnectionInfo(DatabaseConnectionInfo databaseConnectionInfo, String schemaName, Secret schemaSecret) {
        this.databaseConnectionInfo = databaseConnectionInfo;
        this.schemaName = schemaName;
        this.schemaSecret = schemaSecret;
    }

    @Override
    public String getSchemaSecretName() {
        return schemaSecret.getMetadata().getName();
    }

    @Override
    public String getJdbcUrl() {
        return getDatabaseServiceResult().getVendor().getConnectionStringBuilder()
                .toHost(databaseConnectionInfo.getInternalServiceHostname())
                .onPort(databaseConnectionInfo.getPort())
                .usingDatabase(
                        getDatabaseServiceResult().getDatabaseName()).usingSchema(schemaName)
                .usingParameters(this.databaseConnectionInfo.getJdbcParameters())
                .buildJdbcConnectionString();
    }

    public String getDatabase() {
        if (getDatabaseServiceResult().getVendor().schemaIsDatabase()) {
            return getSchemaName();
        } else {
            return this.databaseConnectionInfo.getDatabaseName();
        }
    }

    public DatabaseConnectionInfo getDatabaseServiceResult() {
        return databaseConnectionInfo;
    }

    @Override
    public String getSchemaName() {
        return schemaName;
    }

    @Override
    public EnvVarSource getPasswordRef() {
        return SecretUtils.secretKeyRef(getSchemaSecretName(), SecretUtils.PASSSWORD_KEY);
    }

    @Override
    public EnvVarSource getUsernameRef() {
        return SecretUtils.secretKeyRef(getSchemaSecretName(), SecretUtils.USERNAME_KEY);
    }

    public Secret getSchemaSecret() {
        return this.schemaSecret;
    }

}
