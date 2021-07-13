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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoDeploymentSpec;
import org.entando.kubernetes.model.common.EntandoResourceRequirements;

@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(
        fieldVisibility = Visibility.ANY,
        isGetterVisibility = Visibility.NONE,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE
)
@RegisterForReflection
@JsonIgnoreProperties(
        ignoreUnknown = true
)
public class BasicDeploymentSpec extends EntandoDeploymentSpec {

    private CapabilityProvisioningStrategy provisioningStrategy;
    private DbmsVendor dbms;
    private String externalHostName;
    private String adminSecretName;

    public BasicDeploymentSpec() {
    }

    public BasicDeploymentSpec(CapabilityProvisioningStrategy provisioningStrategy,
            DbmsVendor dbms, String externalHostName, Integer replicas, String serviceAccountToUse,
            List<EnvVar> environmentVariables,
            EntandoResourceRequirements resourceRequirements, String storageClass, String adminSecretName) {
        super(replicas, serviceAccountToUse, environmentVariables, resourceRequirements, storageClass);
        this.provisioningStrategy = provisioningStrategy;
        this.dbms = dbms;
        this.externalHostName = externalHostName;
        this.adminSecretName = adminSecretName;
    }

    public CapabilityProvisioningStrategy getProvisioningStrategy() {
        return provisioningStrategy;
    }

    public DbmsVendor getDbms() {
        return dbms;
    }

    public String getExternalHostName() {
        return this.externalHostName;
    }

    public String getAdminSecretName() {
        return adminSecretName;
    }

}
