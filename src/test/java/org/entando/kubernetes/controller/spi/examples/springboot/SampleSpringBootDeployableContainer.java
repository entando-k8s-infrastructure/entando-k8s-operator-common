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

package org.entando.kubernetes.controller.spi.examples.springboot;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.container.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.spi.container.DbAwareContainer;
import org.entando.kubernetes.controller.spi.container.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer;
import org.entando.kubernetes.controller.spi.container.SsoAwareContainer;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoResourceRequirements;
import org.entando.kubernetes.model.common.KeycloakAwareSpec;

public class SampleSpringBootDeployableContainer<T extends EntandoBaseCustomResource<? extends KeycloakAwareSpec,
        EntandoCustomResourceStatus>> implements
        SpringBootDeployableContainer, SsoAwareContainer,
        ParameterizableContainer, PersistentVolumeAwareContainer, ConfigurableResourceContainer {

    public static final String MY_IMAGE = "entando/entando-k8s-service";
    public static final String MY_WEB_CONTEXT = "/k8s";
    private final T customResource;
    private final List<DatabaseSchemaConnectionInfo> dbSchemaInfo;
    private final SsoConnectionInfo ssoConnectionInfo;
    private final SsoClientConfig ssoClientConfig;

    public SampleSpringBootDeployableContainer(T customResource, DatabaseConnectionInfo databaseConnectionInfo,
            SsoConnectionInfo ssoConnectionInfo, SsoClientConfig ssoClientConfig) {
        this.customResource = customResource;
        this.ssoConnectionInfo = ssoConnectionInfo;
        this.ssoClientConfig = ssoClientConfig;
        if (databaseConnectionInfo == null) {
            this.dbSchemaInfo = Collections.emptyList();
        } else {
            this.dbSchemaInfo = DbAwareContainer
                    .buildDatabaseSchemaConnectionInfo(customResource, databaseConnectionInfo, Collections.singletonList("serverdb"));
        }
    }

    @Override
    public Optional<EntandoResourceRequirements> getResourceRequirementsOverride() {
        return customResource.getSpec().getResourceRequirements();
    }

    @Override
    public String getWebContextPath() {
        return MY_WEB_CONTEXT;
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath() + "/actuator/health");
    }

    @Override
    public String determineImageToUse() {
        return MY_IMAGE;
    }

    @Override
    public String getNameQualifier() {
        return NameUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public int getPrimaryPort() {
        return 8084;
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        return new ArrayList<>();
    }

    @Override
    public Optional<DatabaseSchemaConnectionInfo> getDatabaseSchema() {
        return Optional.of(this.dbSchemaInfo.get(0));
    }

    @Override
    public Optional<DbmsVendor> getDbms() {
        return dbSchemaInfo.stream().findFirst().map(i -> i.getDatabaseServiceResult().getVendor().getDbms());
    }

    @Override
    public SsoClientConfig getSsoClientConfig() {
        return this.ssoClientConfig;
    }

    @Override
    public SsoConnectionInfo getSsoConnectionInfo() {
        return this.ssoConnectionInfo;
    }

    @Override
    public String getVolumeMountPath() {
        return "/entando-data";
    }

    @Override
    public List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo() {
        return this.dbSchemaInfo;
    }

    @Override
    public List<EnvVar> getEnvironmentVariableOverrides() {
        return customResource.getSpec().getEnvironmentVariables();
    }
}
