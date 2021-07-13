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

package org.entando.kubernetes.controller.spi.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorComplianceMode;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class DbmsDockerVendorStrategyTest {

    @Test
    void testResolve() {
        assertThat(DbmsDockerVendorStrategy.forVendor(DbmsVendor.MYSQL, EntandoOperatorComplianceMode.COMMUNITY),
                is(DbmsDockerVendorStrategy.CENTOS_MYSQL));
        assertThat(DbmsDockerVendorStrategy.forVendor(DbmsVendor.MYSQL, EntandoOperatorComplianceMode.REDHAT),
                is(DbmsDockerVendorStrategy.RHEL_MYSQL));
        assertThat(DbmsDockerVendorStrategy.forVendor(DbmsVendor.POSTGRESQL, EntandoOperatorComplianceMode.COMMUNITY),
                is(DbmsDockerVendorStrategy.CENTOS_POSTGRESQL));
        assertThat(DbmsDockerVendorStrategy.forVendor(DbmsVendor.POSTGRESQL, EntandoOperatorComplianceMode.REDHAT),
                is(DbmsDockerVendorStrategy.RHEL_POSTGRESQL));
    }
}
