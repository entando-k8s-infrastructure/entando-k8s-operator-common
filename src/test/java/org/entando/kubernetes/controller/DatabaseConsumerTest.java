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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.util.Map;
import org.assertj.core.api.AbstractThrowableAssert;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer.SpringProperty;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpec;
import org.entando.kubernetes.fluentspi.DatabaseConsumingControllerFluent;
import org.entando.kubernetes.fluentspi.DbAwareContainerFluent;
import org.entando.kubernetes.fluentspi.DbAwareDeployableFluent;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.common.PodBehavior;
import org.entando.kubernetes.test.common.SourceLink;
import org.entando.kubernetes.test.common.ValueHolder;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("inner-hexagon"), @Tag("in-process"), @Tag("allure"), @Tag("pre-deployment")})
@Feature("As a controller developer, I would like to request the Database capability so that I can deploy containers that use the database")
@Issue("ENG-2284")
@SourceLink("DatabaseConsumerTest.java")
class DatabaseConsumerTest extends ControllerTestBase implements VariableReferenceAssertions, CommonLabels, PodBehavior {

    public static final String MY_APP_SERVER_SECRET = "my-app-server-secret";

    /*
                Classes to be implemented by the controller provider
                 */
    @CommandLine.Command()
    public static class BasicDatabaseConsumingController extends DatabaseConsumingControllerFluent<BasicDatabaseConsumingController> {

        public BasicDatabaseConsumingController(KubernetesClientForControllers k8sClient,
                DeploymentProcessor deploymentProcessor,
                CapabilityProvider capabilityProvider) {
            super(k8sClient, deploymentProcessor, capabilityProvider);
        }
    }

    public static class BasicDbAwareDeployable extends DbAwareDeployableFluent<BasicDbAwareDeployable> {

    }

    public static class BasicDbAwareContainer extends DbAwareContainerFluent<BasicDbAwareContainer> {

    }

    private CapabilityRequirement databaseRequirement;
    private BasicDbAwareDeployable deployable;
    private TestResource entandoCustomResource;
    private CapabilityProvisioningResult capabilityProvisioningResult;

    @Override
    public Runnable createController(KubernetesClientForControllers entandoResourceClientDouble,
            DeploymentProcessor deploymentProcessor,
            CapabilityProvider capabilityProvider) {
        return new BasicDatabaseConsumingController(entandoResourceClientDouble, deploymentProcessor, capabilityProvider)
                .withDeployable(this.deployable)
                .withDatabaseRequirement(this.databaseRequirement)
                .withSupportedClass(TestResource.class);
    }

    @Test
    @Description("Should request a required database capability on-demand and connect to the service using the resulting "
            + "DatabaseConnectionInfo ")
    void requestDatabaseCapabilityOnDemandAndConnectToIt() {
        step("Given I have a basic DbAwareDeployable", () -> {
            this.deployable = new BasicDbAwareDeployable();
            attachSpiResource("Deployable", deployable);
        });
        step(format("And I have a custom resource of kind TestResource with name '%s'", MY_APP), () -> {
            this.entandoCustomResource = new TestResource()
                    .withNames(MY_NAMESPACE, MY_APP)
                    .withSpec(new BasicDeploymentSpec());
            attachKubernetesResource("TestResource", entandoCustomResource);
            this.deployable.withCustomResource(this.entandoCustomResource);
        });
        step("And I have requested a requirement for a Database capability",
                () -> {
                    this.databaseRequirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS)
                            .withImplementation(StandardCapabilityImplementation.POSTGRESQL)
                            .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                            .addAllToCapabilityParameters(Map.of(ProvidedDatabaseCapability.DATABASE_NAME_PARAMETER, "my_db"))
                            .addAllToCapabilityParameters(
                                    Map.of(ProvidedDatabaseCapability.JDBC_PARAMETER_PREFIX + "connectionTimeout", "300"))
                            .build();
                });
        final BasicDbAwareContainer container = deployable
                .withContainer(new BasicDbAwareContainer().withDockerImageInfo("test/my-spring-boot-image:6.3.2")
                        .withPrimaryPort(8081)
                        .withNameQualifier("server"));
        step("and a basic DbAwareContainer using the image 'test/my-spring-boot-image:6.3.2'", () -> {
            attachSpiResource("Container", container);
        });
        step("And there is a controller to process requests for the DBMS capability requested",
                () -> doAnswer(withADatabaseCapabilityStatus(DbmsVendor.POSTGRESQL, "my_db")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt()));
        step("When the controller processes a new TestResource", () -> {
            runControllerAgainstCustomResource(entandoCustomResource);
        });
        step("Then a database capability was provided for the DbAwareDeployable with a name reflecting that it is the default PostgreSQL "
                        + "DBMS in the namespace",
                () -> {
                    this.capabilityProvisioningResult = getClient().entandoResources().loadCapabilityProvisioningResult(
                            getClient().capabilities().providedCapabilityByName(MY_NAMESPACE, "default-postgresql-dbms-in-namespace")
                                    .get().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get());
                });
        step("And a database preparation Pod was run for the DbAwareDeployable", () -> {
            step("identified by the labels + " + dbPreparationJobLabels(entandoCustomResource, NameUtils.MAIN_QUALIFIER));
            Pod dbPreparationPod = getClient().pods()
                    .loadPod(MY_NAMESPACE, dbPreparationJobLabels(entandoCustomResource, NameUtils.MAIN_QUALIFIER));
            attachKubernetesResource("DbPreparationPod", dbPreparationPod);
            step("with 2 initContainers, the first to create the schema and the second to populate it", () -> {
                assertThat(dbPreparationPod.getSpec().getInitContainers().size()).isEqualTo(2);
                assertThat(dbPreparationPod.getSpec().getInitContainers().get(0).getImage()).contains("entando/entando-k8s-dbjob");
                assertThat(dbPreparationPod.getSpec().getInitContainers().get(1).getImage()).contains("test/my-spring-boot-image:6.3.2");
            });
            step("and the first container defines all the EnvironmentVariables required by the dbjob container", () -> {
                final String adminSecret = NameUtils.standardAdminSecretName(this.capabilityProvisioningResult.getProvidedCapability());
                final Container schemaCreationContainer = dbPreparationPod.getSpec().getInitContainers().get(0);
                verifyDbJobConnectionVariables(schemaCreationContainer, DbmsVendor.POSTGRESQL,
                        NameUtils.standardServiceName(this.capabilityProvisioningResult.getProvidedCapability())
                                + ".my-namespace.svc.cluster.local");
                verifyDbJobAdminCredentials(adminSecret, schemaCreationContainer);
                verifyDbJobSchemaCredentials(MY_APP_SERVER_SECRET, schemaCreationContainer);
                assertThat(theVariableNamed("JDBC_PARAMETERS").on(schemaCreationContainer)).isEqualTo("connectionTimeout=300");
            });
            step("and the second container populates the database with some initial state", () -> {
                final Container populator = dbPreparationPod.getSpec().getInitContainers().get(1);
                step("using a hypothetical command 'java -jar /deployments/myapp.jar --prepare-db',", () ->
                        assertThat(populator.getCommand()).contains("java", "-jar", "/deployments/myapp.jar", "--prepare-db"));
                verifySpringJdbcVars(MY_APP_SERVER_SECRET, populator);
            });
        });
        step("Then a Deployment was created with a Container that reflects all the environment variables required to connect to the "
                        + "database",
                () -> {
                    final Deployment deployment = getClient().deployments()
                            .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
                    attachKubernetesResource("Deployment", deployment);
                    verifySpringJdbcVars(MY_APP_SERVER_SECRET, thePrimaryContainerOn(deployment));
                });
        step("And all the environment variables referring to Secrets are resolved",
                () -> verifyThatAllVariablesAreMapped(entandoCustomResource, getClient(), getClient().deployments()
                        .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource))));
        attachKubernetesState();

    }

    @Test
    @Description("Should track failure when the database preparation Pod failed on the status of the EntandoCustomResource and NOT create"
            + " a deployment")
    void dbawareDeploymentFailsWhenDatabasePreparationFailed() {
        step("Given I have a basic Deployable", () -> this.deployable = new BasicDbAwareDeployable());
        step("Given I have a basic DbAwareDeployable", () -> {
            this.deployable = new BasicDbAwareDeployable();
            attachSpiResource("Deployable", deployable);
        });
        step(format("And I have a custom resource of kind TestResource with name '%s'", MY_APP), () -> {
            this.entandoCustomResource = new TestResource()
                    .withNames(MY_NAMESPACE, MY_APP)
                    .withSpec(new BasicDeploymentSpec());
            attachKubernetesResource("TestResource", entandoCustomResource);
            this.deployable.withCustomResource(this.entandoCustomResource);
        });
        step("And I have requested a requirement for a Database capability",
                () -> {
                    this.databaseRequirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS)
                            .withImplementation(StandardCapabilityImplementation.POSTGRESQL)
                            .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                            .addAllToCapabilityParameters(Map.of(ProvidedDatabaseCapability.DATABASE_NAME_PARAMETER, "my_db"))
                            .build();
                });
        final BasicDbAwareContainer container = deployable
                .withContainer(new BasicDbAwareContainer().withDockerImageInfo("test/my-spring-boot-image:6.3.2")
                        .withPrimaryPort(8081)
                        .withNameQualifier("server"));
        step("and a basic DbAwareContainer using the image 'test/my-spring-boot-image:6.3.2'", () -> {
            attachSpiResource("Container", container);
        });
        step("And there is a controller to process requests for the DBMS capability requested",
                () -> doAnswer(withADatabaseCapabilityStatus(DbmsVendor.POSTGRESQL, "my_db")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt()));
        step("But the database preparation pod is going to fail", () ->
                when(getClient().pods().runToCompletion(any(), anyInt())).thenAnswer(inv -> {
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
        step(format("Then no Deployment was created reflecting the name of the TestResource and the suffix '%s'",
                NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                () -> {
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNull();
                });
        step("And the controller throws a CommandLine.ExecutionException", () -> {
            thrown.get().isInstanceOf(CommandLine.ExecutionException.class);
            thrown.get().hasMessageContaining("Database preparation failed. Please inspect the logs of the pod ");
        });

        step("And the status on the TestResource indicates that the deployment has failed and makes some diagnostic info available", () -> {
            final EntandoCustomResource resource = getClient().entandoResources().reload(entandoCustomResource);
            assertThat(resource.getStatus().hasFailed()).isTrue();
            assertThat(resource.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
            assertThat(resource.getStatus().findFailedServerStatus().flatMap(ServerStatus::getEntandoControllerFailure).map(
                    EntandoControllerFailure::getMessage).get())
                    .contains("Database preparation failed. Please inspect the logs of the pod ");
        });
        attachKubernetesState();
    }

    private void verifySpringJdbcVars(String schemaSecret, Container populator) {
        step(format("the DB schema credentials from the Secret '%s'", schemaSecret), () -> {
            assertThat(theVariableReferenceNamed(SpringProperty.SPRING_DATASOURCE_USERNAME.name()).on(populator))
                    .matches(theSecretKey(schemaSecret,
                            SecretUtils.USERNAME_KEY));
            assertThat(theVariableReferenceNamed(SpringProperty.SPRING_DATASOURCE_PASSWORD.name()).on(populator))
                    .matches(theSecretKey(schemaSecret,
                            SecretUtils.PASSSWORD_KEY));
        });

        final String jdbcUrl = "jdbc:postgresql://" + NameUtils
                .standardServiceName(this.capabilityProvisioningResult.getProvidedCapability())
                + ".my-namespace.svc.cluster.local:5432/my_db?connectionTimeout=300";
        step(format("and the JDBC connection string '%s'", jdbcUrl),
                () -> assertThat(theVariableNamed(SpringProperty.SPRING_DATASOURCE_URL.name()).on(populator)).isEqualTo(
                        jdbcUrl));
    }

}
