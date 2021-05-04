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

package org.entando.kubernetes.controller.spi.capability;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.WatcherException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.database.DatabaseDeploymentResult;
import org.entando.kubernetes.controller.spi.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.result.ExposedService;
import org.entando.kubernetes.controller.spi.result.ServiceResult;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.AbstractServerStatus;
import org.entando.kubernetes.model.common.DbServerStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ResourceReference;
import org.entando.kubernetes.model.common.WebServerStatus;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
class CapabilityProviderTest implements InProcessTestData {

    @Mock
    SimpleCapabilityClient client;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private ProvidedCapability foundCapability;
    private static final String OPERATOR_NAMESPACE = "entando-operator";

    @Test
    void shouldProvideClusterScopeCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.CLUSTER, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY, null, null,
                null, null);
        when(client.getNamespace()).thenReturn(OPERATOR_NAMESPACE);
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theCapabilityRequirement, withServiceResult(), "default-mysql-dbms"));
        when(client.providedCapabilityByLabels(any())).thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource,
                theCapabilityRequirement);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.CLUSTER));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(OPERATOR_NAMESPACE));
        assertThat(providedCapability.getServiceReference().getName(), is("default-mysql-dbms"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(OPERATOR_NAMESPACE));
        assertThat(providedCapability.getAdminSecretReference().getName(), is("default-mysql-dbms"));
    }

    private Function<ProvidedCapability, ServiceResult> withServiceResult() {
        return (capabilityRequirement) -> new DatabaseDeploymentResult(new ServiceBuilder()
                .withNewMetadata()
                .withNamespace(capabilityRequirement.getMetadata().getNamespace())
                .withName(capabilityRequirement.getMetadata().getName())
                .endMetadata()
                .build(), DbmsDockerVendorStrategy.CENTOS_MYSQL, "my_db",
                capabilityRequirement.getMetadata().getName(), null);
    }

    @Test
    void shouldFailWhenTheWatcherFailed() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.CLUSTER, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY, null, null,
                null, null);
        when(client.getNamespace()).thenReturn(OPERATOR_NAMESPACE);
        when(client.createAndWatchResource(any(), any())).thenAnswer(andGenerateFailEvent());
        when(client.providedCapabilityByLabels(any())).thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalStateException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement));
    }

    @Test
    void shouldFailWhenThereIsAScopeMismatch() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.LABELED, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY,
                Collections.singletonMap("Environment", "Stage"), null,
                null, null);
        when(client.providedCapabilityByLabels(eq(Collections.singletonMap("Environment", "Stage"))))
                .thenReturn(Optional.of(new ProvidedCapability(new ObjectMetaBuilder()
                        .addToLabels(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME, CapabilityScope.DEDICATED.getCamelCaseName())
                        .build(), new CapabilityRequirement())));
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement));
    }

    @Test
    void shouldFailWhenThereIsAnImplementationMismatch() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.LABELED, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY,
                Collections.singletonMap("Environment", "Stage"), null,
                null, null);
        when(client.providedCapabilityByLabels(eq(Collections.singletonMap("Environment", "Stage"))))
                .thenReturn(Optional.of(new ProvidedCapability(new ObjectMetaBuilder()
                        .addToLabels(ProvidedCapability.CAPABILITY_PROVISION_SCOPE_LABEL_NAME,
                                CapabilityScope.LABELED.getCamelCaseName())
                        .addToLabels(ProvidedCapability.IMPLEMENTATION_LABEL_NAME,
                                StandardCapabilityImplementation.POSTGRESQL.getCamelCaseName())
                        .build(), new CapabilityRequirement())));
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement));
    }

    @Test
    void shouldProvideLabeledCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.LABELED, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY,
                Collections.singletonMap("Environment", "Stage"), null,
                null, null);
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theCapabilityRequirement, withServiceResult(), "mysql-dbms"));
        when(client.providedCapabilityByLabels(eq(Collections.singletonMap("Environment", "Stage"))))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource,
                theCapabilityRequirement);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.LABELED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith("mysql-dbms"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getAdminSecretReference().getName(), startsWith("mysql-dbms"));
    }

    @Test
    void shouldFailWhenNoLabelsProvidedForLabeledCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.LABELED, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY,
                Collections.emptyMap(), null, null, null);
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement));
    }

    @Test
    void shouldProvideSpecifiedCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.SPECIFIED, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY,
                Collections.emptyMap(), null,
                new ResourceReference("my-db-namespace", "my-db"), null);
        //When I attempt to fulfill the capability
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theCapabilityRequirement, withServiceResult(), "my-db"));
        when(client.providedCapabilityByName("my-db-namespace", "my-db"))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource,
                theCapabilityRequirement);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.SPECIFIED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is("my-db-namespace"));
        assertThat(providedCapability.getServiceReference().getName(), startsWith("my-db"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is("my-db-namespace"));
        assertThat(providedCapability.getAdminSecretReference().getName(), startsWith("my-db"));
    }

    @Test
    void shouldFailWhenNoReferenceSpecifiedForSpecifiedCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.SPECIFIED, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY,
                Collections.emptyMap(), null, null, null);
        //When I attempt to fulfill the capability
        final CapabilityProvider capabilityProvider = new CapabilityProvider(client);
        assertThrows(IllegalArgumentException.class, () -> capabilityProvider.provideCapability(forResource, theCapabilityRequirement));
    }

    @Test
    void shouldProvideNamespaceScopeCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.NAMESPACE, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY, null,
                null, null, null);
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theCapabilityRequirement, withServiceResult(), "default-mysql-dbms"));
        when(client.providedCapabilityByLabels(eq(forResource.getMetadata().getNamespace()), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource,
                theCapabilityRequirement);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.NAMESPACE));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), is("default-mysql-dbms"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getAdminSecretReference().getName(), is("default-mysql-dbms"));

    }

    @Test
    void shouldProvideDedicatedCapability() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a MYSQL server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.DBMS,
                StandardCapabilityImplementation.MYSQL, CapabilityScope.DEDICATED, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY,
                Collections.emptyMap(), null, null, null);
        //When I attempt to fulfill the capability
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theCapabilityRequirement, withServiceResult(),
                        forResource.getMetadata().getName() + "-db-asdf"));
        when(client.providedCapabilityByName(any(), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource,
                theCapabilityRequirement);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.DBMS));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.MYSQL));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.DEDICATED));
        assertTrue(providedCapability.getIngressReference().isEmpty());
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith(forResource.getMetadata().getName() + "-db"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getAdminSecretReference().getName(), startsWith(forResource.getMetadata().getName() + "-db"));
    }

    @Test
    void shouldProvideDedicatedCapabilityWithIngress() {
        //Given I have an EntandoApp
        final EntandoApp forResource = newTestEntandoApp();
        //with a cluster scoped capability requirement for a Keycloak server
        final CapabilityRequirement theCapabilityRequirement = new CapabilityRequirement(StandardCapability.SSO,
                StandardCapabilityImplementation.KEYCLOAK, CapabilityScope.DEDICATED, CapabilityProvisioningStrategy.DEPLOY_DIRECTLY,
                Collections.emptyMap(), null, null, null);
        //When I attempt to fulfill the capability
        when(client.createAndWatchResource(any(), any()))
                .thenAnswer(andGenerateSuccessEventFor(theCapabilityRequirement, withExposedServiceResult(),
                        forResource.getMetadata().getName() + "-sso-asdfasdf"));
        when(client.providedCapabilityByName(any(), any()))
                .thenAnswer(invocationOnMock -> Optional.ofNullable(foundCapability));
        //When I attempt to fulfill the capability
        final ProvidedCapability providedCapability = new CapabilityProvider(client).provideCapability(forResource,
                theCapabilityRequirement);
        //Then I receive one reflecting matching my requirements
        assertThat(providedCapability, is(notNullValue()));
        assertThat(providedCapability.getSpec().getCapability(), is(StandardCapability.SSO));
        assertThat(providedCapability.getSpec().getImplementation().get(), is(StandardCapabilityImplementation.KEYCLOAK));
        assertThat(providedCapability.getSpec().getScope().get(), is(CapabilityScope.DEDICATED));
        assertThat(providedCapability.getServiceReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getServiceReference().getName(), startsWith(forResource.getMetadata().getName() + "-sso"));
        assertThat(providedCapability.getAdminSecretReference().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getAdminSecretReference().getName(), startsWith(forResource.getMetadata().getName() + "-sso"));
        assertThat(providedCapability.getIngressReference().get().getNamespace().get(), is(forResource.getMetadata().getNamespace()));
        assertThat(providedCapability.getIngressReference().get().getName(), startsWith(forResource.getMetadata().getName() + "-sso"));
    }

    private Function<ProvidedCapability, ServiceResult> withExposedServiceResult() {
        return (capabilityRequirement) -> new SampleExposedDeploymentResult(null, new ServiceBuilder()
                .withNewMetadata()
                .withNamespace(capabilityRequirement.getMetadata().getNamespace())
                .withName(capabilityRequirement.getMetadata().getName())
                .endMetadata()
                .build(),
                new IngressBuilder()
                        .withNewMetadata()
                        .withNamespace(capabilityRequirement.getMetadata().getNamespace())
                        .withName(capabilityRequirement.getMetadata().getName())
                        .endMetadata()
                        .build());
    }

    private Answer<?> andGenerateSuccessEventFor(CapabilityRequirement requiredCapability,
            Function<ProvidedCapability, ServiceResult> serviceResultSupplier, String adminSecretName) {
        return invocationOnMock -> {

            scheduler.schedule(() -> {
                try {
                    foundCapability = (ProvidedCapability) invocationOnMock.getArguments()[0];
                    foundCapability.getStatus().updateDeploymentPhase(EntandoDeploymentPhase.SUCCESSFUL, 1L);
                    final ServiceResult serviceResult = serviceResultSupplier.apply(foundCapability);
                    AbstractServerStatus status;
                    if (serviceResult instanceof ExposedService) {
                        status = new WebServerStatus("server");
                        ((WebServerStatus) status).setIngressName(((ExposedService) serviceResult).getIngress().getMetadata().getName());
                    } else {
                        status = new DbServerStatus("server");
                    }
                    status.setServiceName(serviceResult.getService().getMetadata().getName());
                    status.setAdminSecretName(adminSecretName);
                    foundCapability.getStatus().putServerStatus(status);
                    ((CapabilityRequirementWatcher) invocationOnMock.getArguments()[1]).eventReceived(Action.MODIFIED, foundCapability);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }

            }, 300, TimeUnit.MILLISECONDS);
            return (Watch) () -> {

            };
        };
    }

    private Answer<?> andGenerateFailEvent() {
        return invocationOnMock -> {
            scheduler.schedule(() -> {
                ((CapabilityRequirementWatcher) invocationOnMock.getArguments()[1]).onClose(new WatcherException("ASdf"));

            }, 300, TimeUnit.MILLISECONDS);
            return (Watch) () -> {
            };
        };
    }
}
