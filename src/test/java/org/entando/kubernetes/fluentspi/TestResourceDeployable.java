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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorComplianceMode;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.deployable.ExternalService;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class TestResourceDeployable implements Deployable<TestResourceDeploymentResult>, Secretive {

    private final TestResource testResource;
    private final DbmsDockerVendorStrategy dbmsStrategy;
    private final List<DeployableContainer> containers;
    private final List<Secret> secrets;
    private int port;

    public TestResourceDeployable(TestResource testResource, int port) {
        this.dbmsStrategy = DbmsDockerVendorStrategy.forVendor(testResource.getSpec().getDbms(), EntandoOperatorComplianceMode.COMMUNITY);
        this.testResource = testResource;
        this.port = port;
        containers = Collections.singletonList(new TestResourceContainer(dbmsStrategy));
        secrets = Collections.singletonList(
                SecretUtils.generateSecret(testResource, NameUtils.standardAdminSecretName(testResource),
                        dbmsStrategy.getDefaultAdminUsername()));

    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public Optional<ExternalService> getExternalService() {
        if (testResource.getSpec().getProvisioningStrategy() == CapabilityProvisioningStrategy.DEPLOY_DIRECTLY) {
            return Optional.empty();
        } else {
            return Optional.of(new BasicExternalService(testResource.getSpec().getExternalHostName(), port));
        }
    }

    @Override
    public Optional<String> getQualifier() {
        return Optional.empty();
    }

    @Override
    public EntandoCustomResource getCustomResource() {
        return testResource;
    }

    @Override
    public TestResourceDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new TestResourceDeploymentResult(service, testResource.getSpec().getDbms().toValue(),
                NameUtils.databaseCompliantName(testResource, null,
                        dbmsStrategy.getVendorConfig()), testResource.getSpec().getAdminSecretName());
    }

    @Override
    public String getServiceAccountToUse() {
        return "default";
    }

    @Override
    public List<Secret> getSecrets() {
        return this.secrets;
    }
}
