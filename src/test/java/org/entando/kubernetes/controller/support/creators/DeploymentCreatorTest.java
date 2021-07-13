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

package org.entando.kubernetes.controller.support.creators;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.entando.kubernetes.controller.spi.examples.barebones.BareBonesDeployable;
import org.entando.kubernetes.controller.spi.examples.springboot.SpringBootDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.doubles.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.common.EntandoImageResolver;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class DeploymentCreatorTest implements InProcessTestData, FluentTraversals {

    @AfterEach
    @BeforeEach
    void cleanUp() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty());
    }

    @Test
    void shouldCreateDeploymentWithStartupProbeOnK8S16() {
        //Given I am running on K8S 16
        SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble(16);
        EntandoApp testEntandoApp = newTestEntandoApp();
        DeploymentCreator deploymentCreator = new DeploymentCreator(testEntandoApp);
        DatabaseConnectionInfo databaseConnectionInfo = emulateDatabasDeployment(client);
        //When I create a deployment
        Deployment actual = deploymentCreator.createDeployment(
                new EntandoImageResolver(null),
                client.deployments(),
                new SpringBootDeployable<>(testEntandoApp, emulateKeycloakDeployment(client), databaseConnectionInfo));
        final Container thePrimaryContainer = thePrimaryContainerOn(actual);
        //Then I expect a startupProbe
        final Probe startupProbe = thePrimaryContainer.getStartupProbe();
        assertThat(startupProbe.getHttpGet().getPort().getIntVal(), is(8084));
        assertThat(startupProbe.getHttpGet().getPath(), is("/k8s/actuator/health"));
        assertThat(startupProbe.getInitialDelaySeconds(), is(nullValue()));
        assertThat(startupProbe.getSuccessThreshold(), is(nullValue()));
        assertThat(startupProbe.getFailureThreshold(), is(11));
        //That executes 10 times in the allowed maximumStartupTime (120)
        assertThat(startupProbe.getPeriodSeconds(), is(120 / 10));
        assertThat(startupProbe.getTimeoutSeconds(), is(5));
        //And a ReadinessProbe
        final Probe readinessProbe = thePrimaryContainer.getReadinessProbe();
        assertThat(readinessProbe.getHttpGet().getPort().getIntVal(), is(8084));
        assertThat(readinessProbe.getHttpGet().getPath(), is("/k8s/actuator/health"));
        //That executes immediately after the first succeeding startupProbe
        assertThat(readinessProbe.getInitialDelaySeconds(), is(nullValue()));
        assertThat(readinessProbe.getSuccessThreshold(), is(nullValue()));
        assertThat(readinessProbe.getFailureThreshold(), is(1));
        assertThat(readinessProbe.getPeriodSeconds(), is(10));
        assertThat(readinessProbe.getTimeoutSeconds(), is(5));
        //And a livenessprbe
        final Probe livenessProbe = thePrimaryContainer.getLivenessProbe();
        assertThat(livenessProbe.getHttpGet().getPort().getIntVal(), is(8084));
        assertThat(livenessProbe.getHttpGet().getPath(), is("/k8s/actuator/health"));
        //That executes immediately after the first succeeding startupProbe
        assertThat(livenessProbe.getInitialDelaySeconds(), is(nullValue()));
        assertThat(livenessProbe.getSuccessThreshold(), is(nullValue()));
        assertThat(livenessProbe.getFailureThreshold(), is(1));
        assertThat(livenessProbe.getPeriodSeconds(), is(10));
        assertThat(livenessProbe.getTimeoutSeconds(), is(3));
    }

    @Test
    void shouldCreateDeploymentWithoutStartupProbeOnK8S15() {
        //Given I am running on K8S 16
        SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble(15);
        EntandoApp testEntandoApp = newTestEntandoApp();
        DeploymentCreator deploymentCreator = new DeploymentCreator(testEntandoApp);
        DatabaseConnectionInfo databaseConnectionInfo = emulateDatabasDeployment(client);
        //When I create a deployment
        Deployment actual = deploymentCreator.createDeployment(
                new EntandoImageResolver(null),
                client.deployments(),
                new SpringBootDeployable<>(testEntandoApp, emulateKeycloakDeployment(client), databaseConnectionInfo));
        final Container thePrimaryContainer = thePrimaryContainerOn(actual);
        //Then I expect no startupProbe
        assertThat(thePrimaryContainer.getStartupProbe(), is(nullValue()));
        //And a ReadinessProbe
        final Probe readinessProbe = thePrimaryContainer.getReadinessProbe();
        assertThat(readinessProbe.getHttpGet().getPort().getIntVal(), is(8084));
        assertThat(readinessProbe.getHttpGet().getPath(), is("/k8s/actuator/health"));
        //That is delayed by a sixth the maxStartupTime
        assertThat(readinessProbe.getInitialDelaySeconds(), is(120 / 6));
        assertThat(readinessProbe.getSuccessThreshold(), is(nullValue()));
        //is allowed to fail 6 - 1 times during startup
        assertThat(readinessProbe.getFailureThreshold(), is(5));
        assertThat(readinessProbe.getPeriodSeconds(), is(120 / 6));
        assertThat(readinessProbe.getTimeoutSeconds(), is(5));
        //And a livenessprobe
        final Probe livenessProbe = thePrimaryContainer.getLivenessProbe();
        assertThat(livenessProbe.getHttpGet().getPort().getIntVal(), is(8084));
        assertThat(livenessProbe.getHttpGet().getPath(), is("/k8s/actuator/health"));
        //That executes only after the allowed maximumStartupTime
        assertThat(livenessProbe.getInitialDelaySeconds(), is(Math.round(120 * 1.2F)));
        assertThat(livenessProbe.getSuccessThreshold(), is(nullValue()));
        //Fails immediately on error
        assertThat(livenessProbe.getFailureThreshold(), is(1));
        assertThat(livenessProbe.getPeriodSeconds(), is(10));
        assertThat(livenessProbe.getTimeoutSeconds(), is(3));
    }

    @Test
    void shouldCreateDeploymentWithTrueImposeResourceLimitsWillSetResourceLimitsOnCreatedDeployment() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty(), "true");

        ResourceRequirements resources = executeCreateDeploymentTest();
        assertThat(resources.getLimits().get("cpu").getAmount(), is("800"));
        assertThat(resources.getLimits().get("memory").getAmount(), is("256"));
        assertThat(resources.getRequests().get("cpu").getAmount(), is("200"));
        //Rounded
        assertThat(resources.getRequests().get("memory").getAmount(), is("64"));
    }

    @Test
    void shouldCreateDeploymentWithFalseImposeResourceLimitsWillNotSetResourceLimitsOnCreatedDeployment() {

        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty(), "false");

        ResourceRequirements resources = executeCreateDeploymentTest();

        assertTrue(resources.getLimits().isEmpty());
        assertTrue(resources.getRequests().isEmpty());
    }

    /**
     * executes tests of types CreateDeploymentTest.
     *
     * @return the ResourceRequirements of the first container of the resulting Deployment
     */
    private ResourceRequirements executeCreateDeploymentTest() {

        DeploymentClient deploymentClientDouble = new SimpleK8SClientDouble().deployments();
        EntandoApp testEntandoApp = newTestEntandoApp();
        DeploymentCreator deploymentCreator = new DeploymentCreator(testEntandoApp);

        Deployment actual = deploymentCreator.createDeployment(
                new EntandoImageResolver(null),
                deploymentClientDouble,
                new BareBonesDeployable<>(testEntandoApp));

        return actual.getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
    }
}
