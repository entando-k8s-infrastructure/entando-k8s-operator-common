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

package org.entando.kubernetes.controller.spi.common;

import java.util.Locale;
import java.util.Optional;
import org.entando.kubernetes.model.common.DbmsVendor;

public enum DbmsDockerVendorStrategy {
    CENTOS_MYSQL(DbmsVendorConfig.MYSQL, DockerRegistry.DOCKER_IO, "centos", "mysql-80-centos7", "/var/lib/mysql/data", 27L, 512),

    CENTOS_POSTGRESQL(DbmsVendorConfig.POSTGRESQL, DockerRegistry.DOCKER_IO, "centos", "postgresql-12-centos7", "/var/lib/pgsql/data", 26L,
            256),

    RHEL_MYSQL(DbmsVendorConfig.MYSQL, DockerRegistry.REGISTRY_REDHAT_IO, "rhel8", "mysql-80", "/var/lib/mysql/data", 27L, 512),

    RHEL_POSTGRESQL(DbmsVendorConfig.POSTGRESQL, DockerRegistry.REGISTRY_REDHAT_IO, "rhel8", "postgresql-12", "/var/lib/pgsql/data", 26L,
            256),

    ORACLE(DbmsVendorConfig.ORACLE, DockerRegistry.DOCKER_IO, "store/oracle", "database-enterprise:12.2.0.1", "/ORCL", null, 4096);

    private final String imageRepository;
    private final String organization;
    private final DockerRegistry registry;
    private final String volumeMountPath;
    private final DbmsVendorConfig vendorConfig;
    private final Long fsUserGroupId;
    private final Integer defaultMemoryLimit;

    DbmsDockerVendorStrategy(DbmsVendorConfig vendorConfig,
            DockerRegistry registry,
            String organization,
            String imageRepository,
            String volumeMountPath,
            Long fsUserGroupId,
            Integer defaultMemoryLimit) {
        this.registry = registry;
        this.organization = organization;
        this.imageRepository = imageRepository;
        this.volumeMountPath = volumeMountPath;
        this.vendorConfig = vendorConfig;
        this.fsUserGroupId = fsUserGroupId;
        this.defaultMemoryLimit = defaultMemoryLimit;
    }

    public Optional<Long> getFileSystemUserGroupid() {
        return Optional.ofNullable(fsUserGroupId);
    }

    public DbmsVendorConfig getVendorConfig() {
        return vendorConfig;
    }

    public String getHealthCheck() {
        return this.getVendorConfig().getHealthCheck();
    }

    public String getImageRepository() {
        return this.imageRepository;
    }

    public String getOrganization() {
        return organization;
    }

    public String getRegistry() {
        return registry.registry;
    }

    public int getPort() {
        return this.getVendorConfig().getDefaultPort();
    }

    public String getVolumeMountPath() {
        return this.volumeMountPath;
    }

    public String getName() {
        return this.name().toLowerCase(Locale.getDefault());
    }

    public Integer getDefaultMemoryLimitMebibytes() {
        return defaultMemoryLimit;
    }

    public String getDefaultAdminUsername() {
        return this.getVendorConfig().getDefaultAdminUsername();
    }

    public static DbmsDockerVendorStrategy forVendor(DbmsVendor vendor, EntandoOperatorComplianceMode complianceMode) {
        if (complianceMode == EntandoOperatorComplianceMode.COMMUNITY) {
            if (vendor == DbmsVendor.POSTGRESQL) {
                return CENTOS_POSTGRESQL;
            } else if (vendor == DbmsVendor.MYSQL) {
                return CENTOS_MYSQL;
            }
        } else if (complianceMode == EntandoOperatorComplianceMode.REDHAT) {
            if (vendor == DbmsVendor.POSTGRESQL) {
                return RHEL_POSTGRESQL;
            } else if (vendor == DbmsVendor.MYSQL) {
                return RHEL_MYSQL;
            }
        }
        return valueOf(vendor.name());
    }

    public enum DockerRegistry {
        REGISTRY_REDHAT_IO("registry.redhat.io"),
        DOCKER_IO("docker.io");
        private String registry;

        DockerRegistry(String registry) {
            this.registry = registry;
        }
    }
}
