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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.spi.container.DbAwareContainer;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer;
import org.entando.kubernetes.controller.spi.container.TrustStoreAwareContainer;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.support.common.FluentTernary;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentSpec;
import org.entando.kubernetes.model.common.EntandoIngressingDeploymentSpec;

public class SampleDeployableContainer<S extends EntandoDeploymentSpec> implements IngressingContainer, DbAwareContainer,
        TrustStoreAwareContainer,
        PersistentVolumeAwareContainer, ParameterizableContainer {

    public static final String DEFAULT_IMAGE_NAME = "entando/entando-keycloak:6.0.0-SNAPSHOT";
    public static final String VAR_LIB_MYDATA = "/var/lib/mydata";

    private final EntandoBaseCustomResource<S, EntandoCustomResourceStatus> entandoResource;
    private final List<DatabaseSchemaConnectionInfo> databaseSchemaInfo;

    public SampleDeployableContainer(EntandoBaseCustomResource<S, EntandoCustomResourceStatus> entandoResource,
            DatabaseConnectionInfo databaseConnectionInfo) {
        this.entandoResource = entandoResource;
        if (databaseConnectionInfo == null) {
            this.databaseSchemaInfo = Collections.emptyList();
        } else {
            this.databaseSchemaInfo = DbAwareContainer
                    .buildDatabaseSchemaConnectionInfo(entandoResource, databaseConnectionInfo, Collections.singletonList("db"));
        }
    }

    public static String secretName(EntandoCustomResource entandoResource) {
        return entandoResource.getMetadata().getName() + "-admin-secret";
    }

    @Override
    public String determineImageToUse() {
        return DEFAULT_IMAGE_NAME;
    }

    @Override
    public String getNameQualifier() {
        return NameUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public int getPrimaryPort() {
        return 8080;
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar("KEYCLOAK_USER", null, SecretUtils.secretKeyRef(secretName(entandoResource), SecretUtils.USERNAME_KEY)));
        vars.add(new EnvVar("KEYCLOAK_PASSWORD", null, SecretUtils.secretKeyRef(secretName(entandoResource), SecretUtils.PASSSWORD_KEY)));
        vars.add(new EnvVar("PROXY_ADDRESS_FORWARDING", "true", null));
        return vars;
    }

    @Override
    public List<EnvVar> getDatabaseConnectionVariables() {
        List<EnvVar> vars = new ArrayList<>();
        DatabaseSchemaConnectionInfo databseSchemaConnectionInfo = databaseSchemaInfo.get(0);
        vars.add(new EnvVar("DB_ADDR", databseSchemaConnectionInfo.getDatabaseServiceResult().getInternalServiceHostname(), null));
        vars.add(new EnvVar("DB_PORT", databseSchemaConnectionInfo.getDatabaseServiceResult().getPort(), null));
        vars.add(new EnvVar("DB_DATABASE", databseSchemaConnectionInfo.getDatabaseServiceResult().getDatabaseName(), null));
        vars.add(new EnvVar("DB_PASSWORD", null, databseSchemaConnectionInfo.getPasswordRef()));
        vars.add(new EnvVar("DB_USER", null, databseSchemaConnectionInfo.getUsernameRef()));
        vars.add(new EnvVar("DB_VENDOR", determineKeycloaksNonStandardDbVendorName(databseSchemaConnectionInfo), null));
        vars.add(new EnvVar("DB_SCHEMA", databseSchemaConnectionInfo.getSchemaName(), null));
        return vars;
    }

    @Override
    public List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo() {
        return this.databaseSchemaInfo;
    }

    private String determineKeycloaksNonStandardDbVendorName(DatabaseSchemaConnectionInfo databseSchemaConnectionInfo) {
        return FluentTernary.use("postgres")
                .when(databseSchemaConnectionInfo.getDatabaseServiceResult().getVendor() == DbmsVendorConfig.POSTGRESQL)
                .orElse(databseSchemaConnectionInfo.getDatabaseServiceResult().getVendor().getName());
    }

    @Override
    public String getWebContextPath() {
        return "/auth";
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath());
    }

    @Override
    public String getVolumeMountPath() {
        return VAR_LIB_MYDATA;
    }

    protected EntandoIngressingDeploymentSpec getCustomResourceSpec() {
        return (EntandoIngressingDeploymentSpec) entandoResource.getSpec();
    }

    @Override
    public List<EnvVar> getEnvironmentVariableOverrides() {
        return getCustomResourceSpec().getEnvironmentVariables();
    }
}
