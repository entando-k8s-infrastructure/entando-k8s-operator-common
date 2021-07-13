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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.util.Map;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.fluentspi.TestResourceController;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.ResourceReference;
import org.entando.kubernetes.test.common.SourceLink;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("inner-hexagon"), @Tag("in-process"), @Tag("allure"), @Tag("pre-deployment")})
@Feature("As a controller developer, I would like to implement a controller that responds to CapabilityRequirements so that I can extend"
        + "Kubernetes with my own controllers")
@Issue("ENG-2284")
@SourceLink("ExampleExternalCapabilityTest.java")
class ExampleExternalCapabilityTest extends ControllerTestBase {

    public static final String SPECIFIED_DBMS = "specified-dbms";

    @Test
    @Description("Should link to external database service when all required fields are provided")
    void shouldLinkToExternalDatabaseService() {
        step("Given I have configured a secret with admin credentials to a remote Database service", () -> {
            final Secret adminSecret = new SecretBuilder()
                    .withNewMetadata()
                    .withNamespace(MY_NAMESPACE)
                    .withName("my-existing-dbms-admin-secret")
                    .endMetadata()
                    .addToData(SecretUtils.USERNAME_KEY, "someuser")
                    .addToData(SecretUtils.PASSSWORD_KEY, "somepassword")
                    .build();
            getClient().secrets().createSecretIfAbsent(newResourceRequiringCapability(), adminSecret);
            attachKubernetesResource("Existing Admin Secret", adminSecret);
        });
        step("When I request an DBMS Capability  with its name and namespace explicitly specified, provisioned externally",
                () -> runControllerAgainstCapabilityRequirement(newResourceRequiringCapability(), new CapabilityRequirementBuilder()
                        .withCapability(StandardCapability.DBMS)
                        .withImplementation(StandardCapabilityImplementation.MYSQL)
                        .withProvisioningStrategy(CapabilityProvisioningStrategy.USE_EXTERNAL)
                        .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                        .withNewExternallyProvidedService()
                        .withHost("pg.apps.serv.run")
                        .withPort(3307)
                        .withAdminSecretName("my-existing-dbms-admin-secret")
                        .endExternallyProvidedService()
                        .withSpecifiedCapability(new ResourceReference(MY_NAMESPACE, SPECIFIED_DBMS))
                        .addAllToCapabilityParameters(
                                Map.of(ProvidedDatabaseCapability.JDBC_PARAMETER_PREFIX + "disconnectOnExpiredPasswords", "true"))
                        .build()));
        final ProvidedCapability providedCapability = getClient().entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, SPECIFIED_DBMS);
        final TestResource testResource = getClient().entandoResources()
                .load(TestResource.class, MY_NAMESPACE, SPECIFIED_DBMS);
        step("Then an TestResource was provisioned:", () -> {
            attachKubernetesResource("TestResource", testResource);
            step("with the name explicitly specified", () -> {
                assertThat(testResource.getMetadata().getName()).isEqualTo(SPECIFIED_DBMS);
                assertThat(providedCapability.getMetadata().getName()).isEqualTo(SPECIFIED_DBMS);
                assertThat(providedCapability.getSpec().getSpecifiedCapability().get().getName()).isEqualTo(SPECIFIED_DBMS);
            });

            step("using the 'Use External' provisioningStrategy",
                    () -> assertThat(testResource.getSpec().getProvisioningStrategy())
                            .isEqualTo(CapabilityProvisioningStrategy.USE_EXTERNAL));
            step("and it is owned by the ProvidedCapability to ensure only changes from the ProvidedCapability will change the "
                            + "implementing Kubernetes resources",
                    () -> assertThat(ResourceUtils.customResourceOwns(providedCapability, testResource)));
            step("and its host, port and database name reflect the connection info provided in the CapabilityRequirement",
                    () -> {
                        assertThat(testResource.getSpec().getExternalHostName()).contains("pg.apps.serv.run");
                    });
            step("and the ProvidedCapability's status carries the name of the correct admin secret to use",
                    () -> assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getAdminSecretName())
                            .contains("my-existing-dbms-admin-secret"));
        });
        step("And an 'ExternalName' Service  was provisioned:", () -> {
            final Service service = getClient().services()
                    .loadService(testResource, NameUtils.standardServiceName(testResource));
            attachKubernetesResource("Service", service);
            assertThat(service.getSpec().getType()).isEqualTo("ExternalName");
            step("mapped to the port 3307", () -> {
                assertThat(service.getSpec().getPorts().get(0).getPort()).isEqualTo(3307);
            });

            step("and to the previously configured hostname of the database service",
                    () -> assertThat(service.getSpec().getExternalName()).isEqualTo("pg.apps.serv.run"));
        });
        final DatabaseConnectionInfo providedDatabase = new ProvidedDatabaseCapability(
                getClient().capabilities().buildCapabilityProvisioningResult(providedCapability));
        step("And the provided Database connection info reflects the external service", () -> {
            Allure.attachment("DatabaseConnectionInfo", SerializationHelper.serialize(providedDatabase));
            step("with the port and database name previously specified", () -> {
                assertThat(providedDatabase.getDatabaseName()).isEqualTo("specified_dbms");
                assertThat(providedDatabase.getPort()).isEqualTo("3307");
            });
            step("and a hostname that reflects the previously inspected ExternalName service", () -> {
                assertThat(providedDatabase.getInternalServiceHostname())
                        .isEqualTo("specified-dbms-service.my-namespace.svc.cluster.local");
            });
            step("and previously configured JDBC parameters", () -> {
                assertThat(providedDatabase.getJdbcParameters()).containsAllEntriesOf(Map.of("disconnectOnExpiredPasswords", "true"));
            });
            step("and the previously configured DBMS vendor config", () -> {
                assertThat(providedDatabase.getVendor()).isEqualTo(DbmsVendorConfig.MYSQL);
            });
            step("and the name of the admin secret the was created ", () -> {
                assertThat(providedDatabase.getAdminSecretName()).isEqualTo("my-existing-dbms-admin-secret");
            });
        });
    }

    @Test
    @Description("Should link to external database service using its ip address")
    void shouldLinkToExternalDatabaseServiceUsingItsIpAddress() {
        step("Given I have configured a secret with admin credentials to a remote Database service", () -> {
            final Secret adminSecret = new SecretBuilder()
                    .withNewMetadata()
                    .withNamespace(MY_NAMESPACE)
                    .withName("my-existing-dbms-admin-secret")
                    .endMetadata()
                    .addToData(SecretUtils.USERNAME_KEY, "someuser")
                    .addToData(SecretUtils.PASSSWORD_KEY, "somepassword")
                    .build();
            getClient().secrets().createSecretIfAbsent(newResourceRequiringCapability(), adminSecret);
            attachKubernetesResource("Existing Admin Secret", adminSecret);
        });
        step("When I request an DBMS Capability  with its name and namespace explicitly specified, provisioned externally",
                () -> runControllerAgainstCapabilityRequirement(newResourceRequiringCapability(), new CapabilityRequirementBuilder()
                        .withCapability(StandardCapability.DBMS)
                        .withImplementation(StandardCapabilityImplementation.MYSQL)
                        .withProvisioningStrategy(CapabilityProvisioningStrategy.USE_EXTERNAL)
                        .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                        .withNewExternallyProvidedService()
                        .withHost("10.0.0.234")
                        .withPort(3307)
                        .withAdminSecretName("my-existing-dbms-admin-secret")
                        .endExternallyProvidedService()
                        .withSpecifiedCapability(new ResourceReference(MY_NAMESPACE, SPECIFIED_DBMS))
                        .addAllToCapabilityParameters(
                                Map.of(ProvidedDatabaseCapability.JDBC_PARAMETER_PREFIX + "disconnectOnExpiredPasswords", "true"))
                        .build()));
        final ProvidedCapability providedCapability = getClient().entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, SPECIFIED_DBMS);
        final TestResource testResource = getClient().entandoResources()
                .load(TestResource.class, MY_NAMESPACE, SPECIFIED_DBMS);
        step("Then an TestResource was provisioned:", () -> {
            attachKubernetesResource("TestResource", testResource);
            step("with the name explicitly specified", () -> {
                assertThat(testResource.getMetadata().getName()).isEqualTo(SPECIFIED_DBMS);
                assertThat(providedCapability.getMetadata().getName()).isEqualTo(SPECIFIED_DBMS);
                assertThat(providedCapability.getSpec().getSpecifiedCapability().get().getName()).isEqualTo(SPECIFIED_DBMS);
            });

            step("using the 'Use External' provisioningStrategy",
                    () -> assertThat(testResource.getSpec().getProvisioningStrategy())
                            .isEqualTo(CapabilityProvisioningStrategy.USE_EXTERNAL));
            step("and it is owned by the ProvidedCapability to ensure only changes from the ProvidedCapability will change the "
                            + "implementing Kubernetes resources",
                    () -> assertThat(ResourceUtils.customResourceOwns(providedCapability, testResource)));
            step("and its host, port and database name reflect the connection info provided in the CapabilityRequirement",
                    () -> {
                        assertThat(testResource.getSpec().getExternalHostName()).contains("10.0.0.234");
                    });
            step("and the ProvidedCapability's status carries the name of the correct admin secret to use",
                    () -> assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getAdminSecretName())
                            .contains("my-existing-dbms-admin-secret"));
        });
        step("And an 'ClusterIP' Service  was provisioned with an EndPoints pointing to the original IP of the database service:", () -> {
            final Service service = getClient().services()
                    .loadService(testResource, NameUtils.standardServiceName(testResource));
            final Endpoints endpoints = getClient().services()
                    .loadEndpoints(testResource, NameUtils.standardServiceName(testResource));
            attachKubernetesResource("Service", service);
            attachKubernetesResource("EndPoints", endpoints);
            assertThat(service.getSpec().getType()).isEqualTo("ClusterIP");
            step("mapped to the port 3307", () -> {
                assertThat(service.getSpec().getPorts().get(0).getPort()).isEqualTo(3307);
                assertThat(endpoints.getSubsets().get(0).getPorts().get(0).getPort()).isEqualTo(3307);
            });

            step("and an EndPoints pointing to the previously configured IP address of the database service",
                    () -> assertThat(endpoints.getSubsets().get(0).getAddresses().get(0).getIp()).isEqualTo("10.0.0.234"));
        });
        final DatabaseConnectionInfo providedDatabase = new ProvidedDatabaseCapability(
                getClient().capabilities().buildCapabilityProvisioningResult(providedCapability));
        step("And the provided Database connection info reflects the external service", () -> {
            Allure.attachment("DatabaseConnectionInfo", SerializationHelper.serialize(providedDatabase));
            step("with the port and database name previously specified", () -> {
                assertThat(providedDatabase.getDatabaseName()).isEqualTo("specified_dbms");
                assertThat(providedDatabase.getPort()).isEqualTo("3307");
            });
            step("and a hostname that reflects the previously inspected ExternalName service", () -> {
                assertThat(providedDatabase.getInternalServiceHostname())
                        .isEqualTo("specified-dbms-service.my-namespace.svc.cluster.local");
            });
            step("and previously configured JDBC parameters", () -> {
                assertThat(providedDatabase.getJdbcParameters()).containsAllEntriesOf(Map.of("disconnectOnExpiredPasswords", "true"));
            });
            step("and the previously configured DBMS vendor config", () -> {
                assertThat(providedDatabase.getVendor()).isEqualTo(DbmsVendorConfig.MYSQL);
            });
            step("and the name of the admin secret the was created ", () -> {
                assertThat(providedDatabase.getAdminSecretName()).isEqualTo("my-existing-dbms-admin-secret");
            });
        });
    }

    @Override
    public Runnable createController(KubernetesClientForControllers kubernetesClientForControllers, DeploymentProcessor deploymentProcessor,
            CapabilityProvider capabilityProvider) {
        return new TestResourceController(kubernetesClientForControllers, deploymentProcessor);
    }
}
