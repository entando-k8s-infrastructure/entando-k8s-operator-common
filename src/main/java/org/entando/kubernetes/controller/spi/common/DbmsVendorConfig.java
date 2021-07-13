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
import org.entando.kubernetes.model.common.DbmsVendor;

public enum DbmsVendorConfig {
    MYSQL("org.hibernate.dialect.MySQL5InnoDBDialect", 3306, "root",
            "MYSQL_PWD=${MYSQL_ROOT_PASSWORD} mysql -h 127.0.0.1 -u root -e 'SELECT 1'", 32, true) {
        public JdbcConnectionStringBuilder getConnectionStringBuilder() {
            return new JdbcConnectionStringBuilder() {
                public String buildConnectionString() {
                    return String.format("jdbc:mysql://%s:%s/%s", this.getHost(), this.getPort(), this.getSchema());
                }
            };
        }
    },
    POSTGRESQL("org.hibernate.dialect.PostgreSQLDialect", 5432, "postgres",
            "/usr/libexec/check-container", 64, false) {
        public JdbcConnectionStringBuilder getConnectionStringBuilder() {
            return new JdbcConnectionStringBuilder() {
                public String buildConnectionString() {
                    return String.format("jdbc:postgresql://%s:%s/%s", this.getHost(), this.getPort(), this.getDatabase());
                }
            };
        }
    },
    ORACLE("org.hibernate.dialect.Oracle10gDialect", 1521, "sys", "sqlplus sys/Oradoc_db1:${DB_SID}", 128, false) {
        public JdbcConnectionStringBuilder getConnectionStringBuilder() {
            return new JdbcConnectionStringBuilder() {
                public String buildConnectionString() {
                    return String.format("jdbc:oracle:thin:@//%s:%s/%s", this.getHost(), this.getPort(), this.getDatabase());
                }
            };
        }
    },
    DERBY("org.hibernate.dialect.DerbyDialect", "agile", "agile") {
        @Override
        public JdbcConnectionStringBuilder getConnectionStringBuilder() {
            return new JdbcConnectionStringBuilder() {
                public String buildConnectionString() {
                    return String.format("jdbc:derby:%s/%s;create=true", this.getDataFolder(), this.getDatabase());
                }
            };
        }
    },
    H2("org.hibernate.dialect.H2Dialect", "sa", "") {
        @Override
        public JdbcConnectionStringBuilder getConnectionStringBuilder() {
            return new JdbcConnectionStringBuilder() {
                public String buildConnectionString() {
                    return String.format("jdbc:h2:file:%s/%s;DB_CLOSE_ON_EXIT=FALSE", this.getDataFolder(), this.getDatabase());
                }
            };
        }
    };

    private final String hibernateDialect;
    private final String defaultAdminUsername;
    private String defaultAdminPassword;
    private int defaultPort;
    private boolean schemaIsDatabase;
    private String healthCheck;
    private int maxNameLength;

    DbmsVendorConfig(String hibernateDialect, String defaultAdminUsername, String defaultAdminPassword) {
        this.hibernateDialect = hibernateDialect;
        this.defaultAdminUsername = defaultAdminUsername;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    DbmsVendorConfig(String hibernateDialect, int port, String user, String healthCheck, int maxNameLength, boolean schemaIsDatabase) {
        this.hibernateDialect = hibernateDialect;
        this.defaultAdminUsername = user;
        this.defaultPort = port;
        this.healthCheck = healthCheck;
        this.maxNameLength = maxNameLength;
        this.schemaIsDatabase = schemaIsDatabase;
    }

    public String getDefaultAdminPassword() {
        return defaultAdminPassword;
    }

    public boolean schemaIsDatabase() {
        return this.schemaIsDatabase;
    }

    public int getMaxNameLength() {
        return maxNameLength;
    }

    public abstract JdbcConnectionStringBuilder getConnectionStringBuilder();

    public int getDefaultPort() {
        return defaultPort;
    }

    public String getDefaultAdminUsername() {
        return defaultAdminUsername;
    }

    public String getHealthCheck() {
        return healthCheck;
    }

    public String getHibernateDialect() {
        return hibernateDialect;
    }

    public String getName() {
        return name().toLowerCase(Locale.getDefault());
    }

    public DbmsVendor getDbms() {
        try {
            return DbmsVendor.valueOf(name());
        } catch (IllegalArgumentException e) {
            return DbmsVendor.EMBEDDED;
        }

    }
}
