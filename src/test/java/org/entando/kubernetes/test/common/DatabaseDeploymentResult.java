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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.result.AbstractServiceResult;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.spi.result.ServiceDeploymentResult;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.ServerStatus;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

//TODO move to org.entando.kubernetes.controller.support
public class DatabaseDeploymentResult extends AbstractServiceResult implements DatabaseConnectionInfo,
        ServiceDeploymentResult<DatabaseDeploymentResult> {

    /*migrate to DbmsVendorConfig*/
    private final DbmsVendorConfig vendor;
    private final String databaseName;
    private final Pod pod;
    private ServerStatus status;

    public DatabaseDeploymentResult(Service service, DbmsVendorConfig vendor, String databaseName, String databaseSecretName,
            Pod pod) {
        super(service, databaseSecretName);
        this.pod = pod;
        this.vendor = vendor;
        this.databaseName = databaseName;
    }

    public DatabaseDeploymentResult(Service service, EntandoDatabaseService entandoDatabaseService) {
        super(service, entandoDatabaseService.getSpec().getSecretName()
                .orElse(entandoDatabaseService.getMetadata().getName() + "-db-admin-secret"));
        this.pod = null;
        this.vendor = DbmsVendorConfig.valueOf(entandoDatabaseService.getSpec().getDbms().orElse(DbmsVendor.POSTGRESQL).name());
        this.databaseName = entandoDatabaseService.getSpec().getDatabaseName()
                .orElse(NameUtils.databaseCompliantName(entandoDatabaseService, NameUtils.DB_NAME_QUALIFIER, vendor));
    }

    @Override
    public Optional<String> getTablespace() {
        return Optional.empty();
    }

    @Override
    public Map<String, String> getJdbcParameters() {
        return Collections.emptyMap();
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public DbmsVendorConfig getVendor() {
        return vendor;
    }

    public boolean hasFailed() {
        return Optional.ofNullable(pod).map(existingPod -> PodResult.of(existingPod).hasFailed()).orElse(false);
    }

    @Override
    public DatabaseDeploymentResult withStatus(ServerStatus status) {
        status.setAdminSecretName(getAdminSecretName());
        status.putDerivedDeploymentParameter(ProvidedDatabaseCapability.DBMS_VENDOR_PARAMETER, getVendor().name().toLowerCase(Locale.ROOT));
        status.putDerivedDeploymentParameter(ProvidedDatabaseCapability.DATABASE_NAME_PARAMETER, getDatabaseName());
        this.status = status;
        return this;
    }

    public ServerStatus getStatus() {
        return status;
    }

    @Override
    public Pod getPod() {
        return pod;
    }
}
