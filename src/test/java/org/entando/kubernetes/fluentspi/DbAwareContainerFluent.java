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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.DatabasePopulator;
import org.entando.kubernetes.controller.spi.container.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.spi.container.DbAwareContainer;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer.SpringProperty;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;

public class DbAwareContainerFluent<N extends DbAwareContainerFluent<N>> extends PersistentContainerFluent<N> implements DbAwareContainer {

    private DatabasePopulator databasePopulator;
    private final List<EnvVar> databaseConnectionVariables = new ArrayList<>();
    private final List<DatabaseSchemaConnectionInfo> schemaConnectionInfo = new ArrayList<>();

    @Override
    public Optional<DatabasePopulator> getDatabasePopulator() {
        return Optional.ofNullable(databasePopulator);
    }

    @Override
    public List<EnvVar> getDatabaseConnectionVariables() {
        return this.databaseConnectionVariables;
    }

    @Override
    public List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo() {
        return this.schemaConnectionInfo;
    }

    public void determineDatabaseSchemaInfo(DatabaseConnectionInfo dbConnectionInfo) {
        this.schemaConnectionInfo.clear();
        this.databaseConnectionVariables.clear();
        this.schemaConnectionInfo.addAll(DbAwareContainer
                .buildDatabaseSchemaConnectionInfo(customResource, dbConnectionInfo, Collections.singletonList(getNameQualifier())));
        final DatabaseSchemaConnectionInfo databaseSchema = schemaConnectionInfo.get(0);
        databaseConnectionVariables
                .add(new EnvVar(SpringProperty.SPRING_DATASOURCE_USERNAME.name(), null, databaseSchema.getUsernameRef()));
        databaseConnectionVariables
                .add(new EnvVar(SpringProperty.SPRING_DATASOURCE_PASSWORD.name(), null, databaseSchema.getPasswordRef()));
        databaseConnectionVariables.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_URL.name(), databaseSchema.getJdbcUrl(), null));
        databaseConnectionVariables.add(new EnvVar(SpringProperty.SPRING_JPA_DATABASE_PLATFORM.name(),
                databaseSchema.getDatabaseServiceResult().getVendor().getHibernateDialect(), null));
        this.databasePopulator = new BasicDatabasePopulator().withCommand("java", "-jar", "/deployments/myapp.jar", "--prepare-db")
                .withDockerImageInfo(getDockerImageInfo()).withEnvironmentVariables(getDatabaseConnectionVariables());

    }
}
