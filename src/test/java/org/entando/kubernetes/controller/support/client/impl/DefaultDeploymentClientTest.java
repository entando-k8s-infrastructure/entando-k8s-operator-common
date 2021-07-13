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

package org.entando.kubernetes.controller.support.client.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.client.AbstractSupportK8SIntegrationTest;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.fluentspi.TestResource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultDeploymentClientTest extends AbstractSupportK8SIntegrationTest {

    private final TestResource customResource = newTestResource();

    @Test
    void shouldReflectChangesThatWerePatchedAfterInitialCreation() throws TimeoutException {
        Deployment firstDeployment = getSimpleK8SClient().deployments().createOrPatchDeployment(
                customResource, new DeploymentBuilder().withNewMetadata()
                        .withName("my-deployment")
                        .withNamespace(customResource.getMetadata().getNamespace())
                        .endMetadata()
                        .withNewSpec()
                        .withNewSelector()
                        .addToMatchLabels("my-test-label", "bla-bla")
                        .endSelector()
                        .withNewTemplate()
                        .withNewMetadata()
                        .addToLabels("my-test-label", "bla-bla")
                        .endMetadata()
                        .withNewSpec()
                        .addNewContainer()
                        .withImage("centos/nginx-116-centos7")
                        .withName("nginx")
                        .withCommand("/usr/libexec/s2i/run")
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .build(), EntandoOperatorSpiConfig.getPodShutdownTimeoutSeconds());
        thePrimaryContainerOn(firstDeployment).getEnv().add(new EnvVar("MY_VAR", "myvalue", null));

        getSimpleK8SClient().deployments().createOrPatchDeployment(customResource, firstDeployment,
                EntandoOperatorSpiConfig.getPodShutdownTimeoutSeconds());
        final Deployment secondDeployment = getSimpleK8SClient().deployments().loadDeployment(customResource, "my-deployment");
        assertThat(theVariableNamed("MY_VAR").on(thePrimaryContainerOn(secondDeployment)), is("myvalue"));

    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE_1};
    }
}
