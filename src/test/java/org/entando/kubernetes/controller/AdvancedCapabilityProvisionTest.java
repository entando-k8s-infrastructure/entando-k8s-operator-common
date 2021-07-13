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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.capability.SerializingCapabilityProvider;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.command.InProcessCommandStream;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ResourceReference;
import org.entando.kubernetes.model.common.ServerStatus;
import org.entando.kubernetes.test.common.CustomResourceStatusEmulator;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.entando.kubernetes.test.common.SourceLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

/**
 * This test focuses on the implementation of the CapabilityProvider interface. Scope:
 *
 * <p>From the interface  org.entando.kubernetes.controller.spi.capability.CapabilityProvider which will be used in the controllers
 * consuming capabilities</p>
 *
 * <p>through the serialization layer which in future may involve spawning a process to execute a CLI,</p>
 *
 * <p>terminating in the org.entando.kubernetes.controller.support.command.ProvideCapabilityCommand</p>
 *
 * <p>And then we mock out the behaviour of the controller providing the capability</p>
 */

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("inner-hexagon"), @Tag("in-process"), @Tag("allure"), @Tag("pre-deployment")})
@Feature("As a controller developer, I would like to have be able to resolve ProvidedCapabilities at different levels of scope so that we"
        + " can find the optimal balance of reuse of our Capabilities")
@Issue("ENG-2284")
@SourceLink("AdvancedCapabilityProvisionTest.java")
class AdvancedCapabilityProvisionTest implements InProcessTestData, CustomResourceStatusEmulator<SimpleK8SClientDouble> {

    public static final int TIMEOUT_SECONDS = 30;
    private TestResource forResource;
    private CapabilityRequirement theCapabilityRequirement;
    private final SimpleK8SClientDouble clientDouble = new SimpleK8SClientDouble();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final CapabilityProvider capabilityProvider = new SerializingCapabilityProvider(clientDouble.entandoResources(),
            new InProcessCommandStream(clientDouble, null));
    private CapabilityProvisioningResult capabilityResult;

    @BeforeEach
    final void beforeEach() {
        step("Given I have registered a CustomResourceDefinition for the resource kind 'TestResource'", () -> {
            getClient().entandoResources().registerCustomResourceDefinition("testresources.test.org.crd.yaml");

        });
    }

    @Override
    public final SimpleK8SClientDouble getClient() {
        return this.clientDouble;
    }

    @Override
    public final ScheduledExecutorService getScheduler() {
        return this.scheduler;
    }

    @Test
    @Description("Should provide a MySQL DBMS capability at cluster scope")
    void shouldProvideClusterScopeCapability() {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", forResource);
        });

        step("And it requires a MySQL DBMS Capability at the Cluster level", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(
                            StandardCapabilityImplementation.MYSQL).withResolutionScopePreference(CapabilityScope.CLUSTER)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        });
        step("And there is a controller to process requests for the DBMS capability requested",
                () -> doAnswer(withADatabaseCapabilityStatus(DbmsVendor.MYSQL, "my_db")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt()));
        step("When I request for the CapabilityRequirement to be fulfilled", () -> {
            this.capabilityResult = capabilityProvider.provideCapability(forResource, theCapabilityRequirement, 10);
            attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
        });
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        step("Then the resulting capability matching my requirements is created in the controller's namespace", () -> {
            assertThat(providedCapability).isNotNull();
            assertThat(providedCapability.getSpec().getCapability()).isEqualTo(StandardCapability.DBMS);
            assertThat(providedCapability.getSpec().getImplementation()).contains(StandardCapabilityImplementation.MYSQL);
            assertThat(providedCapability.getSpec().getResolutionScopePreference()).contains(CapabilityScope.CLUSTER);
        });
        step("And its name 'default-mysql-dbms-in-cluster' reflects the fact that it is the default MySQL DBMS capability in the cluster",
                () -> {
                    assertThat(capabilityResult.getProvidedCapability().getMetadata().getName()).isEqualTo("default-mysql-dbms-in-cluster");
                });
        step("And the associated Service has the same name but with the suffix '-service'", () -> {
            assertThat(capabilityResult.getService().getMetadata().getName()).isEqualTo("default-mysql-dbms-in-cluster-service");
        });
        step("And the associated admin Secret has the same name but with the suffix '-admin-secret'", () -> {
            assertThat(capabilityResult.getAdminSecret().get().getMetadata().getName())
                    .isEqualTo("default-mysql-dbms-in-cluster-admin-secret");
        });
        step("And subsequent requests for a DBMS Capability at the Cluster level without specifying the implementation result in the same"
                        + " ProvidedCapability",
                () -> {
                    final CapabilityRequirement requirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS).withResolutionScopePreference(CapabilityScope.CLUSTER)
                            .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
                    this.capabilityResult = capabilityProvider.provideCapability(forResource, requirement, 10);
                    assertThat(capabilityResult.getProvidedCapability().getMetadata().getUid())
                            .isEqualTo(providedCapability.getMetadata().getUid());
                    attachKubernetesResource("Capability Requirement", requirement);
                });
    }

    @Test
    @Description("The request for a new required Capability should fail if its deployment process ended with the 'FAILED' phase")
    void shouldFailWhenTheCapabilityStatusPhaseIsFailed() {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", forResource);
        });
        step("With a cluster scoped capability requirement for a MYSQL server", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL)
                    .withResolutionScopePreference(CapabilityScope.CLUSTER)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", theCapabilityRequirement);
        });
        step("But the controller that processes this capability updates the Phase on its status to 'FAILED'", () ->
                doAnswer(withFailedServerStatus(NameUtils.MAIN_QUALIFIER, new IllegalStateException()))
                        .when(clientDouble.capabilities()).waitForCapabilityCompletion(any(), anyInt()));
        step("Expect a request to fulfil this capabilityRequirement to result in an EntandoControllerFailure", () -> {
            final CapabilityProvisioningResult result = capabilityProvider
                    .provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            final Optional<EntandoControllerFailure> controllerFailure = result.getControllerFailure();
            assertThat(controllerFailure).isPresent();
            assertThat(controllerFailure.get().getDetailMessage()).contains("java.lang.IllegalStateException");

        });
    }

    @Test
    @Description("The request for a new required Capability should fail if times out")
    void shouldFailWhenTimingOut() {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", forResource);
        });
        step("With a cluster scoped capability requirement for a MYSQL server", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL)
                    .withResolutionScopePreference(CapabilityScope.CLUSTER)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", theCapabilityRequirement);
        });
        step("But the controller that processes this capability updates the Phase on its status to 'FAILED'",
                () -> doAnswer((Answer<?>) invocationOnMock -> {
                    throw new TimeoutException();
                }).when(clientDouble.capabilities()).waitForCapabilityCompletion(any(), anyInt()));
        step("Expect a request to fulfil this capabilityRequirement to result in an EntandoControllerFailure", () -> {
            final CapabilityProvisioningResult result = capabilityProvider
                    .provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            final Optional<EntandoControllerFailure> controllerFailure = result.getControllerFailure();
            assertThat(controllerFailure).isPresent();
            assertThat(controllerFailure.get().getDetailMessage()).contains("java.util.concurrent.TimeoutException");

        });
    }

    @Test
    @Description("Should fail when the a specified capability match but there is a scope mismatch")
    void shouldFailWhenThereIsAScopeMismatch() {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });
        step("And I have already created a capability with the 'dedicated' scope", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL)
                    .withResolutionScopePreference(CapabilityScope.DEDICATED)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            doAnswer(withADatabaseCapabilityStatus(DbmsVendor.MYSQL, "my_db")).when(getClient().capabilities())
                    .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt());
            this.capabilityResult = capabilityProvider
                    .provideCapability(forResource, this.theCapabilityRequirement, 10);
        });
        step("And I have requirement for a Labeled capability", () -> {
            //with a cluster scoped capability requirement for a MYSQL server
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL)
                    .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                    .withSpecifiedCapability(new ResourceReference(MY_APP_NAMESPACE, "my-app-db"))
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", this.theCapabilityRequirement);
        });
        step("Expect my request for the CapabilityRequirement to be fulfilled to result in an IllegalArgumentException",
                () -> {
                    final CapabilityProvisioningResult result = capabilityProvider
                            .provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
                    final Optional<EntandoControllerFailure> controllerFailure = result.getControllerFailure();
                    assertThat(controllerFailure.get().getMessage()).isEqualTo(
                            "The capability Dbms was found, but its supported provisioning scopes are 'Dedicated' instead of the "
                                    + "requested 'Specified' scopes");
                });
    }

    @Test
    @Description("Should fail when a matching ProvidedCapability is found but there is an implementation mismatch")
    void shouldFailWhenThereIsAnImplementationMismatch() {
        //TODO rethink this. Should it not just create a new capability with the correct implementation?
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });
        step("And I have already created a capability at the cluster level but it specifies PostgreSQL as implementation",
                () -> {
                    this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS)
                            .withImplementation(StandardCapabilityImplementation.POSTGRESQL)
                            .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                            .withSpecifiedCapability(new ResourceReference(MY_APP_NAMESPACE, "my-db"))
                            .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
                    doAnswer(withADatabaseCapabilityStatus(DbmsVendor.MYSQL, "my_db")).when(getClient().capabilities())
                            .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt());
                    this.capabilityResult = capabilityProvider
                            .provideCapability(forResource, this.theCapabilityRequirement, 10);
                });
        step("But now I have requirement for a MySQL implementation using the same Labels", () -> {
            //with a cluster scoped capability requirement for a MYSQL server
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL)
                    .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                    .withSpecifiedCapability(new ResourceReference(MY_APP_NAMESPACE, "my-db"))
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", this.theCapabilityRequirement);
        });

        step("Expect a request to fulfil this capabilityRequirement to result in an EntandoControllerFailure indicating that there was an"
                        + " implementation mismatch",
                () -> {
                    final CapabilityProvisioningResult result = capabilityProvider
                            .provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
                    final Optional<EntandoControllerFailure> controllerFailure = result.getControllerFailure();
                    assertThat(controllerFailure).isPresent();
                    assertThat(controllerFailure.get().getMessage())
                            .isEqualTo(
                                    "The capability Dbms was found, but its implementation is Postgresql instead of the requested Mysql");
                    attachKubernetesResource("EntandoControllerFailure", controllerFailure.get());
                });
    }

    @Test
    @Description("Should provide a capability resolved at Cluster level using a Label Selector")
    void shouldProvideLabeledCapability() throws TimeoutException {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });

        step("With capability requirement that should be match using a Label Selector", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL).withResolutionScopePreference(CapabilityScope.LABELED)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                    .withSelector(Collections.singletonMap("Environment", "Stage")).build();
            attachSpiResource("CapabilityRequirement", theCapabilityRequirement);
        });
        step("And there is a controller to process requests for the DBMS capability requested",
                () -> doAnswer(withADatabaseCapabilityStatus(DbmsVendor.MYSQL, "my_db")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt()));

        step("When I request for the CapabilityRequirement to be fulfilled", () -> {
            this.capabilityResult = capabilityProvider.provideCapability(forResource, theCapabilityRequirement, 10);
            attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
        });
        step("Then I receive a capability that meets my requirements", () -> {
            final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
            assertThat(providedCapability).isNotNull();
            assertThat(providedCapability.getSpec().getCapability()).isEqualTo(StandardCapability.DBMS);
            assertThat(providedCapability.getSpec().getImplementation()).contains(StandardCapabilityImplementation.MYSQL);
            assertThat(providedCapability.getSpec().getResolutionScopePreference()).contains(CapabilityScope.LABELED);
            assertThat(providedCapability.getMetadata().getLabels()).containsEntry("Environment", "Stage");
        });
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        step("In the TestResource's namespace with a name that reflects the fact that it is a MySQL DBMS Capability", () -> {
            assertThat(providedCapability.getMetadata().getNamespace()).isEqualTo(forResource.getMetadata().getNamespace());
            assertThat(providedCapability.getMetadata().getName()).startsWith("mysql-dbms");
        });
        step("And a subsequent request for any DBMS Capability with the same Labels returns the same capability", () -> {
            this.capabilityResult = capabilityProvider.provideCapability(forResource, new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withResolutionScopePreference(CapabilityScope.LABELED)
                    .withSelector(Collections.singletonMap("Environment", "Stage")).build(), 10);
            assertThat(
                    this.capabilityResult.getProvidedCapability().getMetadata().getUid().equals(providedCapability.getMetadata().getUid()));
            attachSpiResource("Second CapabilityProvisioningResult", this.capabilityResult);
        });
    }

    @Test
    @Description("Should failed when a Labeled Capability was requested, but no Label Selector was provided")
    void shouldFailWhenNoLabelsProvidedForLabeledCapability() {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });

        step("With a labeled scoped capability requirement for a MYSQL server, but without a Selector specified", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL).withResolutionScopePreference(CapabilityScope.LABELED)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        });
        step("Expect a request to fulfil this CapabilityRequirement to result in an IllegalArgumentException", () -> {
            final CapabilityProvisioningResult result = capabilityProvider
                    .provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            final Optional<EntandoControllerFailure> controllerFailure = result.getControllerFailure();
            assertThat(controllerFailure).isPresent();
            assertThat(controllerFailure.get().getMessage())
                    .isEqualTo("A requirement for a labeled capability needs at least one label to resolve the required capability.");
        });
    }

    @Test
    @Description("Should provide a capability with the specified name and namespace when the CapabilityRequirement specifies a scope of "
            + "'SPECIFIED'")
    void shouldProvideSpecifiedCapability() throws TimeoutException {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", forResource);
        });

        step("And I have a requirement for a Capability with an explicit name 'my-db' and namespace 'my-db-namespace' specified for it",
                () -> {
                    this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS)
                            .withImplementation(StandardCapabilityImplementation.MYSQL)
                            .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                            .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                            .withSpecifiedCapability(
                                    new ResourceReference("my-db-namespace", "my-db")).build();
                    attachKubernetesResource("CapabilityRequirement", theCapabilityRequirement);
                });
        step("And there is a controller to process requests for the DBMS capability requested",
                () -> doAnswer(withADatabaseCapabilityStatus(DbmsVendor.MYSQL, "my_db")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt()));
        step("When I request for the CapabilityRequirement to fulfilled", () -> {
            this.capabilityResult = this.capabilityProvider.provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
        });
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        step("Then I receive a ProvidedCapability matching my requirements, with the namespace 'my-db-namespace and the name 'my-db'",
                () -> {
                    assertThat(providedCapability).isNotNull();
                    assertThat(providedCapability.getSpec().getCapability()).isEqualTo(StandardCapability.DBMS);
                    assertThat(providedCapability.getSpec().getImplementation()).contains(StandardCapabilityImplementation.MYSQL);
                    assertThat(providedCapability.getSpec().getResolutionScopePreference()).contains(CapabilityScope.SPECIFIED);
                    assertThat(providedCapability.getMetadata().getNamespace()).isEqualTo("my-db-namespace");
                    assertThat(
                            providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).flatMap(ServerStatus::getServiceName))
                            .contains("my-db-service");
                });
        step("And subsequent requests for a DBMS Capability with the same name and namespace specified result in the same"
                        + " ProvidedCapability",
                () -> {
                    this.capabilityResult = capabilityProvider.provideCapability(forResource, new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS)
                            .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                            .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                            .withSpecifiedCapability(new ResourceReference("my-db-namespace", "my-db"))
                            .build(), 10);
                    assertThat(capabilityResult.getProvidedCapability().getMetadata().getUid())
                            .isEqualTo(providedCapability.getMetadata().getUid());
                    attachKubernetesResource("CapabilityResult", this.capabilityResult);
                });
    }

    @Test
    void shouldFailWhenNoReferenceSpecifiedForSpecifiedCapability() {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });
        step("And a 'SPECIFIED' CapabilityRequirement but with no specifiedCapability set", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL).withResolutionScopePreference(CapabilityScope.SPECIFIED)
                    .withProvisioningStrategy(
                            CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachSpiResource("CapabilityRequirement", this.theCapabilityRequirement);
        });
        step("Expect a request to fulfil this CapabilityRequirement to result in an EntandoControllerFailure", () -> {
            final CapabilityProvisioningResult result = capabilityProvider
                    .provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            final Optional<EntandoControllerFailure> controllerFailure = result.getControllerFailure();
            assertThat(controllerFailure).isPresent();
            assertThat(controllerFailure.get().getMessage()).isEqualTo(
                    "A requirement for a specified capability needs a valid name and optional namespace to resolve the required "
                            + "capability.");
        });
    }

    @Test
    @Description("Should provide a Capability in the same namespace when it is share with others in the namespace")
    void shouldProvideNamespaceScopedCapability() throws TimeoutException {

        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });
        step("And I require a Capability to be usable by others in the same namespace", () -> {
            //with a cluster scoped capability requirement for a MYSQL server
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL).withResolutionScopePreference(CapabilityScope.NAMESPACE)
                    .withProvisioningStrategy(
                            CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", this.theCapabilityRequirement);
        });
        step("And there is a controller to process requests for the DBMS capability requested",
                () -> doAnswer(withADatabaseCapabilityStatus(DbmsVendor.MYSQL, "my_db")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt()));
        step("When I request for my CapabilityRequirement to be fulfilled", () -> {
            this.capabilityResult = this.capabilityProvider.provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
        });
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        step("Then I receive a capability that meets my requirements", () -> {
            assertThat(providedCapability).isNotNull();
            assertThat(providedCapability.getSpec().getCapability()).isEqualTo(StandardCapability.DBMS);
            assertThat(providedCapability.getSpec().getImplementation()).contains(StandardCapabilityImplementation.MYSQL);
            assertThat(providedCapability.getSpec().getResolutionScopePreference()).contains(CapabilityScope.NAMESPACE);
            assertTrue(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).flatMap(ServerStatus::getIngressName)
                    .isEmpty());
        });
        step("With the name 'default-mysql-dbms-in-namespace-service' indicating that it is the default MySQL DBMS in the namespace", () ->
                assertThat(providedCapability.getMetadata().getName()).isEqualTo("default-mysql-dbms-in-namespace"));
        step("And subsequent requests for a DBMS Capability at the Namespace level without specifying the implementation result in the same"
                        + " ProvidedCapability",
                () -> {
                    final CapabilityRequirement requirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS).withResolutionScopePreference(CapabilityScope.NAMESPACE)
                            .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
                    this.capabilityResult = capabilityProvider.provideCapability(forResource, requirement, 10);
                    assertThat(capabilityResult.getProvidedCapability().getMetadata().getUid())
                            .isEqualTo(providedCapability.getMetadata().getUid());
                    attachKubernetesResource("Capability Requirement", requirement);
                });

    }

    @Test
    @Description("Should provide a previously created Capability in the same namespace")
    void shouldResolvePreviouslyCreatedNamespaceScopedCapability() {

        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });
        step("And there is a controller to process requests for the DBMS capability requested",
                () -> doAnswer(withADatabaseCapabilityStatus(DbmsVendor.MYSQL, "my_db")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt()));
        step("And I have created a Capability to be usable by others in the same namespace", () -> {
            //with a cluster scoped capability requirement for a MYSQL server
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL).withResolutionScopePreference(CapabilityScope.NAMESPACE)
                    .withProvisioningStrategy(
                            CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", this.theCapabilityRequirement);
            this.capabilityResult = this.capabilityProvider.provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
        });
        step("When I request for my CapabilityRequirement to be fulfilled at either Cluster or Namespace level", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.DBMS)
                    .withImplementation(StandardCapabilityImplementation.MYSQL)
                    .withResolutionScopePreference(CapabilityScope.CLUSTER, CapabilityScope.NAMESPACE)
                    .withProvisioningStrategy(
                            CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", this.theCapabilityRequirement);
            this.capabilityResult = this.capabilityProvider.provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
        });
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        step("Then I receive a capability that meets my requirements", () -> {
            assertThat(providedCapability).isNotNull();
            assertThat(providedCapability.getSpec().getCapability()).isEqualTo(StandardCapability.DBMS);
            assertThat(providedCapability.getSpec().getImplementation()).contains(StandardCapabilityImplementation.MYSQL);
            assertThat(providedCapability.getSpec().getResolutionScopePreference()).contains(CapabilityScope.NAMESPACE);
            assertTrue(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).flatMap(ServerStatus::getIngressName)
                    .isEmpty());
        });
    }

    @Test
    @Description("Should update a previously created Capability in the same namespace with essential changes")
    void shouldUpdatePreviouslyCreatedCapabilityWithEssentialChanges() {

        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });
        step("And there is a controller to process requests for the SSO capability requested",
                () -> {
                    when(getClient().capabilities()
                            .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.SSO)), anyInt()))
                            .thenAnswer(withAnSsoCapabilityStatus("irrelevant.com", "my-realm"));
                    when(getClient().capabilities()
                            .waitForCapabilityCommencement(argThat(matchesCapability(StandardCapability.SSO)), anyInt()))
                            .thenAnswer(withAStartedCapability());

                });
        step("And I have created a Capability to be usable by others in the same namespace", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.SSO)
                    .withImplementation(StandardCapabilityImplementation.REDHAT_SSO)
                    .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                    .withProvisioningStrategy(
                            CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", this.theCapabilityRequirement);
            this.capabilityResult = this.capabilityProvider.provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
        });
        step("When I request for my CapabilityRequirement to be fulfilled at either Cluster or Namespace level, but with a new "
                        + "ingressHostName, tlsSecretName",
                () -> {
                    this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.SSO)
                            .withImplementation(StandardCapabilityImplementation.REDHAT_SSO)
                            .withResolutionScopePreference(CapabilityScope.CLUSTER, CapabilityScope.NAMESPACE)
                            .withProvisioningStrategy(
                                    CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                            .withPreferredTlsSecretName("my-tls-secret")
                            .withPreferredIngressHostName("my.ingress.host.com")
                            .build();
                    attachKubernetesResource("CapabilityRequirement", this.theCapabilityRequirement);
                    this.capabilityResult = this.capabilityProvider
                            .provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
                    attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
                });
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        step("Then I receive a capability that meets my requirements", () -> {
            assertThat(providedCapability).isNotNull();
            assertThat(providedCapability.getSpec().getCapability()).isEqualTo(StandardCapability.SSO);
            assertThat(providedCapability.getSpec().getImplementation()).contains(StandardCapabilityImplementation.REDHAT_SSO);
            assertThat(providedCapability.getSpec().getResolutionScopePreference()).contains(CapabilityScope.NAMESPACE);
        });
        step("And it reflects the new tlsSecretName and ingressHostname", () -> {
            assertThat(providedCapability.getSpec().getPreferredIngressHostName()).contains("my.ingress.host.com");
            assertThat(providedCapability.getSpec().getPreferredTlsSecretName()).contains("my-tls-secret");
        });
    }

    private Answer<Object> withAStartedCapability() {
        return invocationOnMock -> {
            getScheduler().schedule(() -> {
                ProvidedCapability pc = invocationOnMock.getArgument(0);
                pc.getStatus()
                        .updateDeploymentPhase(EntandoDeploymentPhase.REQUESTED, pc.getMetadata().getGeneration());
                getClient().capabilities().createOrPatchCapability(pc);
            }, 300L, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

    @Test
    @Description("Should provide a Capability that is dedicated to the resource on whose behalf it was requested")
    void shouldProvideDedicatedCapability() {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });
        //with a cluster scoped capability requirement for a MYSQL server
        this.theCapabilityRequirement = new CapabilityRequirementBuilder()
                .withCapability(StandardCapability.DBMS)
                .withImplementation(
                        StandardCapabilityImplementation.MYSQL).withResolutionScopePreference(CapabilityScope.DEDICATED)
                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
        step("And there is a controller to process requests for the DBMS capability requested",
                () -> doAnswer(withADatabaseCapabilityStatus(DbmsVendor.MYSQL, "my_db")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.DBMS)), anyInt()));

        step("When I request for my CapabilityRequirement to be fulfilled", () -> {
            this.capabilityResult = this.capabilityProvider.provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
        });
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        step("Then I receive a capability that meets my requirements", () -> {
            assertThat(providedCapability).isNotNull();
            assertThat(providedCapability.getSpec().getCapability()).isEqualTo(StandardCapability.DBMS);
            assertThat(providedCapability.getSpec().getImplementation()).contains(StandardCapabilityImplementation.MYSQL);
            assertThat(providedCapability.getSpec().getResolutionScopePreference()).contains(CapabilityScope.DEDICATED);
            assertTrue(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).flatMap(ServerStatus::getIngressName)
                    .isEmpty());
        });
        step("With the name 'my-app-db' indicating that it is the MySQL DBMS dedicated to the TestResource 'my-app' in the same namespace",
                () ->
                        assertThat(providedCapability.getMetadata().getName()).isEqualTo("my-app-db"));
        step("And subsequent requests for a DBMS Capability dedicated to the TestResource in question without specifying the "
                        + "implementation result in the same"
                        + " ProvidedCapability",
                () -> {
                    final CapabilityRequirement requirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS).withResolutionScopePreference(CapabilityScope.DEDICATED)
                            .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
                    this.capabilityResult = capabilityProvider.provideCapability(forResource, requirement, 10);
                    assertThat(capabilityResult.getProvidedCapability().getMetadata().getUid())
                            .isEqualTo(providedCapability.getMetadata().getUid());
                    attachKubernetesResource("Capability Requirement", requirement);
                });
    }

    @Test
    @Description("Capabilities could also have an Ingress to expose it to consumers outside of the cluster")
    void shouldProvideDedicatedCapabilityWithIngress() {
        step("Given I have an TestResource", () -> {
            this.forResource = clientDouble.entandoResources().createOrPatchEntandoResource(newTestResource());
            attachKubernetesResource("TestResource", this.forResource);
        });
        step("And I have a requirement for the Keycloak SSO capability", () -> {
            this.theCapabilityRequirement = new CapabilityRequirementBuilder().withCapability(StandardCapability.SSO)
                    .withImplementation(
                            StandardCapabilityImplementation.KEYCLOAK).withResolutionScopePreference(CapabilityScope.DEDICATED)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build();
            attachKubernetesResource("CapabilityRequirement", this.theCapabilityRequirement);
        });
        step("And there is a controller to process requests for the SSO capability requested",
                () -> doAnswer(withAnSsoCapabilityStatus("mykeycloak.com", "my-realm")).when(getClient().capabilities())
                        .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.SSO)), anyInt()));

        step("When I request for my CapabilityRequirement to be fulfilled", () -> {
            this.capabilityResult = this.capabilityProvider.provideCapability(forResource, theCapabilityRequirement, TIMEOUT_SECONDS);
            attachSpiResource("CapabilityProvisioningResult", this.capabilityResult);
        });
        final ProvidedCapability providedCapability = capabilityResult.getProvidedCapability();
        step("Then the resulting ProvidedCapability has an Ingress named 'my-app-sso-ingress'", () -> {
            assertThat(providedCapability).isNotNull();
            assertThat(providedCapability.getSpec().getCapability()).isEqualTo(StandardCapability.SSO);
            assertThat(providedCapability.getSpec().getImplementation()).contains(StandardCapabilityImplementation.KEYCLOAK);
            assertThat(providedCapability.getSpec().getResolutionScopePreference()).contains(CapabilityScope.DEDICATED);
            assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).flatMap(ServerStatus::getServiceName).get())
                    .startsWith(forResource.getMetadata().getName() + "-sso-service");
            assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).flatMap(ServerStatus::getIngressName))
                    .contains(forResource.getMetadata().getName() + "-sso-ingress");
        });
    }

}
