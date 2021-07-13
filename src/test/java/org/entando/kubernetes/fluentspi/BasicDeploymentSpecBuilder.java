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

import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoDeploymentSpecFluent;

public class BasicDeploymentSpecBuilder extends EntandoDeploymentSpecFluent<BasicDeploymentSpecBuilder> {

    private CapabilityProvisioningStrategy provisioningStrategy;
    private DbmsVendor dbms;
    private String externalHostName;
    private String adminSecretName;

    public BasicDeploymentSpec build() {
        return new BasicDeploymentSpec(provisioningStrategy, dbms, externalHostName, replicas, serviceAccountToUse, environmentVariables,
                resourceRequirements,
                storageClass, adminSecretName);
    }

    public BasicDeploymentSpecBuilder withDbms(DbmsVendor dbms) {
        this.dbms = dbms;
        return thisAsF();
    }

    public BasicDeploymentSpecBuilder withProvisioningStrategy(CapabilityProvisioningStrategy provisioningStrategy) {
        this.provisioningStrategy = provisioningStrategy;
        return thisAsF();
    }

    public BasicDeploymentSpecBuilder withExternalHostName(String externalHostName) {
        this.externalHostName = externalHostName;
        return thisAsF();
    }

    public BasicDeploymentSpecBuilder withAdminSecretName(String adminSecretName) {
        this.adminSecretName = adminSecretName;
        return thisAsF();
    }

}
