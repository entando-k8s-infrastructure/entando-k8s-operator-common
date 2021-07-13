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

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.ForInternalUseOnly;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.model.common.DbmsVendor;

public interface SpringBootDeployableContainer extends DbAwareContainer, SsoAwareContainer, IngressingContainer,
        TrustStoreAwareContainer {

    Optional<DatabaseSchemaConnectionInfo> getDatabaseSchema();

    @Override
    default int getCpuLimitMillicores() {
        return 1000;
    }

    @Override
    default int getMemoryLimitMebibytes() {
        return 1024;
    }

    @Override
    default List<EnvVar> getDatabaseConnectionVariables() {
        List<EnvVar> vars = new ArrayList<>();
        getDatabaseSchema().ifPresent(databaseSchema -> {
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_USERNAME.name(), null, databaseSchema.getUsernameRef()));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_PASSWORD.name(), null, databaseSchema.getPasswordRef()));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_URL.name(), databaseSchema.getJdbcUrl(), null));
            vars.add(new EnvVar(SpringProperty.SPRING_JPA_DATABASE_PLATFORM.name(),
                    databaseSchema.getDatabaseServiceResult().getVendor().getHibernateDialect(), null));
            /*
            TODO: Set SPRING_JPA_PROPERTIES_HIBERNATE_ID_NEW_GENERATOR_MAPPINGS to 'false' if we ever run into issues with ID Generation
            */
        });
        if (getDatabaseSchema().isEmpty() && getDbms().orElse(DbmsVendor.NONE) == DbmsVendor.EMBEDDED) {
            DbmsVendorConfig defaultEmbeddedVendor = DbmsVendorConfig.H2;

            vars.add(new EnvVar(SpringProperty.SPRING_JPA_DATABASE_PLATFORM.name(), defaultEmbeddedVendor.getHibernateDialect(), null));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_USERNAME.name(), defaultEmbeddedVendor.getDefaultAdminUsername(), null));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_PASSWORD.name(), defaultEmbeddedVendor.getDefaultAdminPassword(), null));
            String rootFolder = "/entando-data";
            if (this instanceof PersistentVolumeAwareContainer) {
                rootFolder = ((PersistentVolumeAwareContainer) this).getVolumeMountPath();
            }
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_URL.name(), defaultEmbeddedVendor.getConnectionStringBuilder()
                    .inFolder(rootFolder + "/databases")
                    .usingDatabase(NameUtils.snakeCaseOf(getNameQualifier()) + ".db")
                    .buildConnectionString(), null));
        }
        return vars;
    }

    Optional<DbmsVendor> getDbms();

    @Override
    default List<EnvVar> getSsoVariables() {
        List<EnvVar> vars = new ArrayList<>();
        ofNullable(getSsoConnectionInfo()).ifPresent(info ->
                vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name(),
                        info.getExternalBaseUrl() + "/realms/" + getSsoClientConfig().getRealm(),
                        null)));

        String keycloakSecretName = KeycloakName.forTheClientSecret(getSsoClientConfig());
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name(), null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name(), null,
                SecretUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_ID_KEY)));
        return vars;
    }

    @ForInternalUseOnly
    SsoClientConfig getSsoClientConfig();

    @ForInternalUseOnly
    SsoConnectionInfo getSsoConnectionInfo();

    enum SpringProperty {
        SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI,
        SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET,
        SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID,
        SPRING_DATASOURCE_USERNAME,
        SPRING_DATASOURCE_PASSWORD,
        SPRING_DATASOURCE_URL,
        SPRING_JPA_DATABASE_PLATFORM;
    }

}
