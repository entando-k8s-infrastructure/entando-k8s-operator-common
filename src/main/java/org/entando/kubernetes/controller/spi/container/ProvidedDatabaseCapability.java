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

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Service;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.result.AbstractServiceResult;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.model.common.ServerStatus;

public class ProvidedDatabaseCapability extends AbstractServiceResult implements DatabaseConnectionInfo {

    public static final String DATABASE_NAME_PARAMETER = "databaseName";
    public static final String JDBC_PARAMETER_PREFIX = "jdbc-";
    public static final String DBMS_VENDOR_PARAMETER = "dbmsVendor";
    public static final String TABLESPACE_PARAMETER = "tablespace";
    private final ServerStatus status;
    private final Map<String, String> capabilityParameters;

    public ProvidedDatabaseCapability(CapabilityProvisioningResult serializedCapabilityProvisioningResult) {
        this(serializedCapabilityProvisioningResult.getService(),
                serializedCapabilityProvisioningResult.getProvidedCapability().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER)
                        .orElseThrow(IllegalStateException::new),
                serializedCapabilityProvisioningResult.getProvidedCapability().getSpec().getCapabilityParameters());
    }

    public ProvidedDatabaseCapability(Service service, ServerStatus status, Map<String, String> capabilityParameters) {
        super(service, status.getAdminSecretName().orElse(null));
        this.status = status;
        this.capabilityParameters = ofNullable(capabilityParameters).orElse(Collections.emptyMap());
    }

    @Override
    public Map<String, String> getJdbcParameters() {
        Map<String, String> result = new HashMap<>();
        capabilityParameters.forEach((key, value) -> {
            if (key.startsWith(JDBC_PARAMETER_PREFIX)) {
                result.put(key.substring(JDBC_PARAMETER_PREFIX.length()), value);
            }
        });
        return result;
    }

    @Override
    public String getDatabaseName() {
        return ofNullable(findStatus().getDerivedDeploymentParameters()).map(m -> m.get(DATABASE_NAME_PARAMETER)).orElse(null);
    }

    @Override
    public DbmsVendorConfig getVendor() {
        return DbmsVendorConfig.valueOf(findStatus().getDerivedDeploymentParameters().get(DBMS_VENDOR_PARAMETER).toUpperCase(Locale.ROOT));
    }

    private ServerStatus findStatus() {
        return status;
    }

    @Override
    public Optional<String> getTablespace() {
        return ofNullable(capabilityParameters.get(TABLESPACE_PARAMETER));
    }
}
