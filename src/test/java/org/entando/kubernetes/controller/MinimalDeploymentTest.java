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

package org.entando.kubernetes.controller;

import static io.qameta.allure.Allure.step;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractThrowableAssert;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.creators.DeploymentCreator;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpec;
import org.entando.kubernetes.fluentspi.ControllerFluent;
import org.entando.kubernetes.fluentspi.DeployableContainerFluent;
import org.entando.kubernetes.fluentspi.DeployableFluent;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.PodBehavior;
import org.entando.kubernetes.test.common.SourceLink;
import org.entando.kubernetes.test.common.ValueHolder;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("inner-hexagon"), @Tag("in-process"), @Tag("allure"), @Tag("pre-deployment")})
@Feature(
        "As a controller developer, I would like to specify how my image gets deployed providing only the essential configuration so that "
                + " I can focus on my development tasks")
@Issue("ENG-2284")
@SourceLink("MinimalDeploymentTest.java")
class MinimalDeploymentTest extends ControllerTestBase implements FluentTraversals, VariableReferenceAssertions, PodBehavior {

    private TestResource entandoCustomResource;
    private Integer timeoutSeconds = 60;

    /*
    Classes to be implemented by the controller provider
     */
    @CommandLine.Command()
    public static class BasicController extends ControllerFluent<BasicController> {

        public BasicController(KubernetesClientForControllers k8sClient,
                DeploymentProcessor deploymentProcessor) {
            super(k8sClient, deploymentProcessor);
        }
    }

    public static class BasicDeployable extends DeployableFluent<BasicDeployable> {

    }

    public static class BasicDeployableContainer extends DeployableContainerFluent<BasicDeployableContainer> {

    }

    private BasicDeployable deployable;

    @AfterEach
    @BeforeEach
    void resetSystemPropertiesUsed() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty());
    }

    @Override
    public Runnable createController(
            KubernetesClientForControllers kubernetesClientForControllers,
            DeploymentProcessor deploymentProcessor, CapabilityProvider capabilityProvider) {
        return new BasicController(kubernetesClientForControllers, deploymentProcessor)
                .withDeployable(deployable)
                .withTimeoutSeconds(timeoutSeconds)
                .withSupportedClass(TestResource.class);
    }

    @Test
    @AllureId("test11")
    @Description("Should deploy successfully even if only the image and port are specified")
    void absoluteMinimalDeployment() {
        step("Given I have a basic Deployable", () -> this.deployable = new BasicDeployable());
        step("and a basic DeployableContainer qualified as 'server' that uses the image test/my-image:6.3.2 and exports port 8081",
                () -> {
                    deployable.withContainer(
                            new BasicDeployableContainer().withDockerImageInfo("test/my-image:6.3.2").withNameQualifier("server")
                                    .withPrimaryPort(8081));
                    attachSpiResource("DeployableContainer", SerializationHelper.serialize(deployable.getContainers().get(0)));
                });
        final EntandoCustomResource entandoCustomResource = new TestResource()
                .withNames(MY_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpec());
        step("When the controller processes a new TestResource", () -> {
            runControllerAgainstCustomResource(entandoCustomResource);
        });
        step(format("Then a Deployment was created reflecting the name of the TestResource and the suffix '%s'",
                NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                () -> {
                    final Deployment deployment = getClient().deployments()
                            .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                    step("and it has a single container with a name reflecting the qualifier 'server'", () -> {
                        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().size()).isEqualTo(1);
                        assertThat(thePrimaryContainerOn(deployment).getName()).isEqualTo("server-container");
                    });
                    step("and this container exports port 8081 with a name that reflects the qualifier 'server'", () ->
                            assertThat(thePortNamed("server-port").on(thePrimaryContainerOn(deployment)).getContainerPort())
                                    .isEqualTo(8081));
                    step("and the image of this container is the previously specified image test/my-image:6.3.2 but with the default "
                                    + "registry "
                                    + "'docker.io' specified",
                            () -> assertThat(thePrimaryContainerOn(deployment).getImage()).isEqualTo("docker.io/test/my-image:6.3.2"));
                    step("And the default resource limits of 256Mi of Memory and 0.5 CPU were specified", () -> {
                        assertThat(thePrimaryContainerOn(deployment).getResources().getLimits().get("memory")).hasToString("256Mi");
                        assertThat(thePrimaryContainerOn(deployment).getResources().getLimits().get("cpu")).hasToString("500m");
                    });
                    step("And all the startupProbe, readinessProbe and livenessProve all verify that port 8081 is receiving connections",
                            () -> {
                                assertThat(thePrimaryContainerOn(deployment).getStartupProbe().getTcpSocket().getPort().getIntVal())
                                        .isEqualTo(8081);
                                assertThat(thePrimaryContainerOn(deployment).getLivenessProbe().getTcpSocket().getPort().getIntVal())
                                        .isEqualTo(8081);
                                assertThat(thePrimaryContainerOn(deployment).getReadinessProbe().getTcpSocket().getPort().getIntVal())
                                        .isEqualTo(8081);
                            });
                    step("And the startupProbe is guaranteed to allow the maximum boot time required by the container", () -> {
                        final Probe startupProbe = thePrimaryContainerOn(deployment).getStartupProbe();
                        assertThat(startupProbe.getPeriodSeconds() * startupProbe.getFailureThreshold())
                                .isBetween(DeploymentCreator.DEFAULT_STARTUP_TIME,
                                        (int) Math.round(DeploymentCreator.DEFAULT_STARTUP_TIME * 1.1));

                    });
                });
        attachKubernetesState();
    }

    @Test
    @Description("Should deploy successfully with resource limits disabled")
    void minimalDeploymentWithoutResourceLimits() {
        step("Given I have a basic Deployable", () -> this.deployable = new BasicDeployable());
        step("and a basic DeployableContainer qualified as 'server' that uses the image test/my-image:6.3.2 and exports port 8081",
                () -> {
                    deployable.withContainer(
                            new BasicDeployableContainer().withDockerImageInfo("test/my-image:6.3.2").withNameQualifier("server")
                                    .withPrimaryPort(8081));
                    attachSpiResource("DeployableContainer", SerializationHelper.serialize(deployable.getContainers().get(0)));
                });
        step("But I have switched off the limits for environments where resource use is not a concern", () -> System.setProperty(
                EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty(), "false"));
        final EntandoCustomResource entandoCustomResource = new TestResource()
                .withNames(MY_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpec());
        step("When the controller processes a new TestResource", () -> {
            runControllerAgainstCustomResource(entandoCustomResource);
        });
        final Deployment deployment = getClient().deployments()
                .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
        step(format("Then a Deployment was created reflecting the name of the TestResource and the suffix '%s'",
                NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                () -> {
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                });
        step("But the default resource limits were left empty", () -> {
            assertThat(thePrimaryContainerOn(deployment).getResources().getLimits()).doesNotContainKey("memory");
            assertThat(thePrimaryContainerOn(deployment).getResources().getLimits()).doesNotContainKey("cpu");
        });
        attachKubernetesState();
    }

    @Test
    @Description("Should track failed deployments on the status of the EntandoCustomResource")
    void minimalDeploymentThatFailsToStartSuccessfully() {
        step("Given I have a basic Deployable", () -> this.deployable = new BasicDeployable());
        step("and a basic DeployableContainer qualified as 'server' that uses the image test/my-image:6.3.2 and exports port 8081",
                () -> {
                    deployable.withContainer(
                            new BasicDeployableContainer().withDockerImageInfo("test/my-image:6.3.2").withNameQualifier("server")
                                    .withPrimaryPort(8081));
                    attachSpiResource("DeployableContainer", SerializationHelper.serialize(deployable.getContainers().get(0)));
                });
        step("But the pod of the deployment is going to fail to start up successfully", () ->
                when(getClient().pods().waitForPod(eq(MY_NAMESPACE), eq(LabelNames.DEPLOYMENT.getName()), eq("my-app"), anyInt()))
                        .thenAnswer(inv -> {
                            Pod pod = (Pod) inv.callRealMethod();
                            return podWithFailedStatus(pod);
                        }));
        final EntandoCustomResource entandoCustomResource = new TestResource()
                .withNames(MY_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpec());
        ValueHolder<AbstractThrowableAssert<?, ? extends Throwable>> thrown = new ValueHolder<>();
        step("When the controller processes a new TestResource", () -> {
            thrown.set(assertThatThrownBy(
                    () -> runControllerAgainstCustomResource(entandoCustomResource)));
        });
        final Deployment deployment = getClient().deployments()
                .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
        step(format("Then a Deployment was created reflecting the name of the TestResource and the suffix '%s'",
                NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                () -> {
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                });
        step("But the controller throws a CommandLine.ExecutionException", () -> {
            thrown.get().isInstanceOf(CommandLine.ExecutionException.class);
            thrown.get().hasMessageContaining("Deployment failed. Please inspect the logs of the pod my-namespace/my-app-deployment");
        });

        step("And the status on the TestResource indicates that the deployment has failed and makes some diagnostic info available", () -> {
            final EntandoCustomResource resource = getClient().entandoResources().reload(entandoCustomResource);
            assertThat(resource.getStatus().hasFailed()).isTrue();
            assertThat(resource.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
            assertThat(resource.getStatus().findFailedServerStatus().flatMap(ServerStatus::getEntandoControllerFailure).get().getMessage())
                    .isEqualTo("Deployment failed. Please inspect the logs of the pod my-namespace/my-app-deployment");
        });
        attachKubernetesState();
    }

    @Test
    @Disabled("Low priority unlikely scenario")
    @Description("Should track failures even when a status update on the EntandoCustomResource itself failed (Unlikely but possible)")
    void minimalDeploymentThatFailsOnStatusUpdate() {
        fail();
    }

    @Test
    @Description("Should track failures when the deployment of the EntandoCustomResource times out")
    void minimalDeploymentThatTimesOut() {
        step("Given I have a basic Deployable", () -> this.deployable = new BasicDeployable());
        step("and a basic DeployableContainer qualified as 'server' that uses the image test/my-image:6.3.2 and exports port 8081",
                () -> {
                    deployable.withContainer(
                            new BasicDeployableContainer().withDockerImageInfo("test/my-image:6.3.2").withNameQualifier("server")
                                    .withPrimaryPort(8081));
                    attachSpiResource("DeployableContainer", SerializationHelper.serialize(deployable.getContainers().get(0)));
                });
        step("But the timeout for the deployment is set unrealistically low to 1 second", () -> this.timeoutSeconds = 1);
        step("And there is a 3 second delay when waiting for the pod", () ->
                when(getClient().pods().waitForPod(eq(MY_NAMESPACE), eq(LabelNames.DEPLOYMENT.getName()), eq("my-app"), anyInt()))
                        .thenAnswer(
                                inv -> {
                                    long start = System.currentTimeMillis();
                                    await().until(() -> TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start) >= 3);
                                    return inv.callRealMethod();
                                }));
        final EntandoCustomResource entandoCustomResource = new TestResource()
                .withNames(MY_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpec());
        ValueHolder<AbstractThrowableAssert<?, ? extends Throwable>> thrown = new ValueHolder<>();
        step("When the controller processes a new TestResource", () -> {
            thrown.set(assertThatThrownBy(
                    () -> runControllerAgainstCustomResource(entandoCustomResource)));
        });
        final Deployment deployment = getClient().deployments()
                .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
        step(format("Then a Deployment was created reflecting the name of the TestResource and the suffix '%s'",
                NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                () -> {
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                });
        step("But the controller throws a CommandLine.ExecutionException", () -> {
            thrown.get().isInstanceOf(CommandLine.ExecutionException.class);
            thrown.get().hasMessageContaining(
                    "java.util.concurrent.TimeoutException");
        });

        step("And the status on the TestResource indicates that the deployment has failed and makes some diagnostic info "
                        + "available",
                () -> {
                    final EntandoCustomResource resource = getClient().entandoResources().reload(entandoCustomResource);
                    assertThat(resource.getStatus().hasFailed()).isTrue();
                    assertThat(resource.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
                    assertThat(resource.getStatus().findFailedServerStatus().get().getEntandoControllerFailure().get().getDetailMessage())
                            .contains("java.util.concurrent.TimeoutException");
                });
        attachKubernetesState();
    }

    @Test
    @Description("Should reflect both direct custom environment variables and environment variables referring to Secret keys")
    void minimalDeploymentWithEnvironmentVariables() {
        step("Given I have a basic Deployable that specifies a Secret to be created", () -> {
            this.deployable = new BasicDeployable();
            attachSpiResource("Deployable", deployable);
        });
        step("And I have a custom resource of kind TestResource with name", () -> {
            this.entandoCustomResource = new TestResource()
                    .withNames(MY_NAMESPACE, MY_APP)
                    .withSpec(new BasicDeploymentSpec());
            attachKubernetesResource("TestResource", entandoCustomResource);
        });
        step("And I have created a Secret named 'my-secret' with the key 'my.secret.key' in the same namespace as the "
                        + "TestResource",
                () -> {
                    getClient().secrets().createSecretIfAbsent(entandoCustomResource, new SecretBuilder().withNewMetadata()
                            .withName("my-secret")
                            .endMetadata().addToStringData("my.secret.key", "my.value").build());
                    attachKubernetesResource("Secret", getClient().secrets().loadSecret(entandoCustomResource, "my-secret"));
                });
        step("And I have created a ConfigMap named 'my-config' with the key 'my.config.key' in the same namespace as the "
                        + "TestResource",
                () -> {
                    getClient().secrets().createConfigMapIfAbsent(entandoCustomResource, new ConfigMapBuilder().withNewMetadata()
                            .withName("my-config")
                            .endMetadata().addToData("my.config.key", "my.value").build());
                    attachKubernetesResource("Secret", getClient().secrets().loadSecret(entandoCustomResource, "my-secret"));
                });
        final BasicDeployableContainer container = deployable
                .withContainer(new BasicDeployableContainer().withDockerImageInfo("test/my-image:6.3.2")
                        .withPrimaryPort(8081)
                        .withNameQualifier("server"));
        step("and a basic DeployableContainer with the some custom environment variables set:", () -> {
            step("the environment variable MY_VAR=my-val",
                    () -> container.withEnvVar("MY_VAR", "my-val")
            );
            step("environment variable reference MY_SECRET_VAR from the Secret named 'my-secret' using the key 'my.secret.key'",
                    () -> container.withEnvVarFromSecret("MY_SECRET_VAR", "my-secret", "my.secret.key")
            );
            step("and environment variable reference MY_CONFIG_VAR from the Secret named 'my-config' using the key 'my.config.key'",
                    () -> container.withEnvVarFromConfigMap("MY_CONFIG_VAR", "my-config", "my.config.key")
            );
            attachSpiResource("Container", container);
        });
        step("When the controller processes a new TestResource", () -> {
            attachKubernetesResource("TestResource", entandoCustomResource);
            runControllerAgainstCustomResource(entandoCustomResource);
        });
        step("Then a Deployment was created with a Container that reflects both the environment variable and the environment variable "
                        + "reference",
                () -> {
                    final Deployment deployment = getClient().deployments()
                            .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                    step("the value of MY_VAR is 'my-val'", () ->
                            assertThat(theVariableNamed("MY_VAR").on(thePrimaryContainerOn(deployment))).isEqualTo("my-val"));
                    step("and the variable 'MY_SECRET_VAR' refers to the key 'my.key' on the Secret 'my-secret'", () ->
                            assertThat(theVariableReferenceNamed("MY_SECRET_VAR").on(thePrimaryContainerOn(deployment)))
                                    .matches(theSecretKey("my-secret", "my.secret.key")));
                    step("and the variable 'MY_CONFIG_VAR' refers to the key 'my.config.key' on the Secret 'my-config'", () ->
                            assertThat(theVariableReferenceNamed("MY_CONFIG_VAR").on(thePrimaryContainerOn(deployment)))
                                    .matches(theConfigMapKey("my-config", "my.config.key")));
                });
        step("And all the environment variables referring to Secrets are resolved",
                () -> verifyThatAllVariablesAreMapped(entandoCustomResource, getClient(), getClient().deployments()
                        .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource))));

    }
}
