///*
// *
// * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
// *
// * This library is free software; you can redistribute it and/or modify it under
// * the terms of the GNU Lesser General Public License as published by the Free
// * Software Foundation; either version 2.1 of the License, or (at your option)
// * any later version.
// *
// *  This library is distributed in the hope that it will be useful, but WITHOUT
// * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
// * details.
// *
// */
//
//package org.entando.kubernetes.controller.spi.capability.impl;
//
//import static org.awaitility.Awaitility.await;
//import static org.hamcrest.MatcherAssert.assertThat;
//import static org.hamcrest.Matchers.is;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import io.fabric8.kubernetes.api.model.IntOrString;
//import io.fabric8.kubernetes.api.model.Secret;
//import io.fabric8.kubernetes.api.model.SecretBuilder;
//import io.fabric8.kubernetes.api.model.Service;
//import io.fabric8.kubernetes.api.model.ServiceBuilder;
//import io.fabric8.kubernetes.api.model.extensions.Ingress;
//import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
//import java.util.Collections;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.TimeUnit;
//import org.entando.kubernetes.controller.spi.capability.CapabilityClient;
//import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
//import org.entando.kubernetes.controller.support.client.impl.AbstractK8SIntegrationTest;
//import org.entando.kubernetes.controller.support.client.impl.DefaultSimpleK8SClient;
//import org.entando.kubernetes.model.capability.ProvidedCapability;
//import org.entando.kubernetes.model.capability.ProvidedCapabilityBuilder;
//import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
//import org.entando.kubernetes.model.common.ServerStatus;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Tag;
//import org.junit.jupiter.api.Tags;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
//
//@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("integration")})
//@EnableRuleMigrationSupport
//class DefaultCapabilityClientTest extends AbstractK8SIntegrationTest {
//
//    public static final String MY_OTHER_NAMESPACE = MY_APP_NAMESPACE + "1";
//    private DefaultSimpleK8SClient simpleK8SClient;
//
//    @Override
//    protected String[] getNamespacesToUse() {
//        return new String[]{MY_APP_NAMESPACE, MY_OTHER_NAMESPACE};
//    }
//
//    @BeforeEach
//    void deleteCapabilities() {
//        deleteAll(getFabric8Client().customResources(ProvidedCapability.class));
//        deleteAll(getFabric8Client().services());
//        deleteAll(getFabric8Client().extensions().ingresses());
//    }
//
//    @Test
//    void shouldFindCapabilityInClusterByLabels() {
//        crateCapabilityWithLabels(MY_APP_NAMESPACE, "my-capability", Collections.singletonMap("label1", "value1"));
//        crateCapabilityWithLabels(MY_APP_NAMESPACE, "my-other-capability", Collections.singletonMap("label1", "value2"));
//        crateCapabilityWithLabels(MY_OTHER_NAMESPACE, "my-capability-in-other-namespace", Collections.singletonMap("label1", "value3"));
//
//        assertThat(getDefaultSimpleK8SClient().capabilities()
//                        .providedCapabilityByLabels(Collections.singletonMap("label1", "value1")).get().getMetadata().getName(),
//                is("my-capability"));
//        assertThat(getDefaultSimpleK8SClient().capabilities()
//                        .providedCapabilityByLabels(Collections.singletonMap("label1", "value2")).get().getMetadata().getName(),
//                is("my-other-capability"));
//        assertThat(getDefaultSimpleK8SClient().capabilities()
//                        .providedCapabilityByLabels(Collections.singletonMap("label1", "value3")).get().getMetadata().getName(),
//                is("my-capability-in-other-namespace"));
//    }
//
//    @Test
//    void shouldFindCapabilityInNamespaceByLabels() {
//        crateCapabilityWithLabels(MY_APP_NAMESPACE, "my-capability", Collections.singletonMap("label1", "value1"));
//        crateCapabilityWithLabels(MY_APP_NAMESPACE, "my-other-capability", Collections.singletonMap("label1", "value2"));
//        crateCapabilityWithLabels(MY_OTHER_NAMESPACE, "my-capability-in-other-namespace", Collections.singletonMap("label1", "value3"));
//
//        assertThat(getDefaultSimpleK8SClient().capabilities()
//                        .providedCapabilityByLabels(MY_APP_NAMESPACE, Collections.singletonMap("label1", "value1"))
//                        .get().getMetadata().getName(),
//                is("my-capability"));
//        assertThat(getDefaultSimpleK8SClient().capabilities()
//                        .providedCapabilityByLabels(MY_APP_NAMESPACE, Collections.singletonMap("label1", "value2"))
//                        .get().getMetadata().getName(),
//                is("my-other-capability"));
//        assertFalse(getDefaultSimpleK8SClient().capabilities()
//                .providedCapabilityByLabels(MY_APP_NAMESPACE, Collections.singletonMap("label1", "value3")).isPresent());
//    }
//
//    @Test
//    void shouldFindCapabilityByNameAndNamespace() {
//        crateCapabilityWithLabels(MY_APP_NAMESPACE, "my-capability", Collections.singletonMap("label1", "value1"));
//        assertTrue(getDefaultSimpleK8SClient().capabilities()
//                .providedCapabilityByName(MY_APP_NAMESPACE, "my-capability").isPresent());
//        assertFalse(getDefaultSimpleK8SClient().capabilities()
//                .providedCapabilityByName(MY_APP_NAMESPACE, "my-capabilityyyy").isPresent());
//    }
//
//    private void crateCapabilityWithLabels(String namespace, String name, Map<String, String> labels) {
//        final ProvidedCapability providedCapability = new ProvidedCapabilityBuilder()
//                .withNewMetadata()
//                .withNamespace(namespace)
//                .withName(name)
//                .withLabels(labels)
//                .endMetadata()
//                .build();
//        final CapabilityRequirementWatcher watcher = new CapabilityRequirementWatcher(new CompletableFuture<>());
//        getDefaultSimpleK8SClient().capabilities().createAndWatchResource(providedCapability, watcher);
//    }
//
//    @Test
//    void shouldCreateCapability() {
//        final CapabilityClient defaultCapabilityClient = getDefaultSimpleK8SClient().capabilities();
//        final ProvidedCapability providedCapability = new ProvidedCapabilityBuilder()
//                .withNewMetadata()
//                .withNamespace(MY_APP_NAMESPACE)
//                .withName("my-capability")
//                .endMetadata()
//                .build();
//        final CapabilityRequirementWatcher watcher = new CapabilityRequirementWatcher(new CompletableFuture<>());
//        defaultCapabilityClient.createAndWatchResource(providedCapability, watcher);
//        ProvidedCapability actualCapability = getDefaultSimpleK8SClient().entandoResources().reload(providedCapability);
//        getDefaultSimpleK8SClient().entandoResources().updatePhase(actualCapability, EntandoDeploymentPhase.SUCCESSFUL);
//        await().atMost(20, TimeUnit.SECONDS).ignoreExceptions().until(() -> !watcher.hasFailed());
//    }
//
//    @Test
//    void shouldBuildCapabilityResult() {
//        //Given I have created a ProvidedCapability
//        final CapabilityClient defaultCapabilityClient = getDefaultSimpleK8SClient().capabilities();
//        final ProvidedCapability providedCapability = new ProvidedCapabilityBuilder()
//                .withNewMetadata()
//                .withNamespace(MY_APP_NAMESPACE)
//                .withName("my-capability")
//                .endMetadata()
//                .build();
//        defaultCapabilityClient.createAndWatchResource(providedCapability, new CapabilityRequirementWatcher(new CompletableFuture<>()));
//        final ProvidedCapability createdCapability = getDefaultSimpleK8SClient().entandoResources().reload(providedCapability);
//        //And I have updated its status
//        final ServerStatus status = new ServerStatus("main");
//        status.finish();
//        //With an adminSecret
//        final Secret adminSecret = getFabric8Client().secrets().inNamespace(MY_APP_NAMESPACE).createOrReplace(new SecretBuilder()
//                .withNewMetadata()
//                .withNamespace(MY_APP_NAMESPACE)
//                .withName("admin-secret")
//                .endMetadata()
//                .addToData("username", "john")
//                .build());
//        //And with service
//        final Service service = getFabric8Client().services().inNamespace(MY_APP_NAMESPACE).createOrReplace(new ServiceBuilder()
//                .withNewMetadata()
//                .withName("my-service")
//                .withNamespace(MY_APP_NAMESPACE)
//                .endMetadata()
//                .withNewSpec()
//                .withType("ExternalName")
//                .withExternalName("google.com")
//                .addNewPort()
//                .withPort(8080)
//                .endPort()
//                .endSpec()
//                .build());
//        //And with an Ingres
//        final Ingress ingress = getFabric8Client().extensions().ingresses().inNamespace(MY_APP_NAMESPACE)
//                .createOrReplace(new IngressBuilder()
//                        .withNewMetadata()
//                        .withName("my-ingress")
//                        .withNamespace(MY_APP_NAMESPACE)
//                        .endMetadata()
//                        .withNewSpec()
//                        .addNewRule()
//                        .withNewHttp()
//                        .addNewPath()
//                        .withNewBackend()
//                        .withServiceName(service.getMetadata().getName())
//                        .withServicePort(new IntOrString(8080))
//                        .endBackend()
//                        .withPath("/non-existing")
//                        .endPath()
//                        .endHttp()
//                        .endRule()
//                        .endSpec()
//                        .build());
//        status.setAdminSecretName(adminSecret.getMetadata().getName());
//        status.setIngressName(ingress.getMetadata().getName());
//        status.setServiceName(service.getMetadata().getName());
//        getDefaultSimpleK8SClient().entandoResources().updateStatus(createdCapability, status);
//        ProvidedCapability capabilityAfterStatusUpdate = getDefaultSimpleK8SClient().entandoResources().reload(createdCapability);
//        //When I build the CapabilityProvisioningResult
//        final CapabilityProvisioningResult result = getDefaultSimpleK8SClient().capabilities()
//                .buildCapabilityProvisioningResult(capabilityAfterStatusUpdate);
//        //The previously specified connection related objects have been set
//        assertThat(result.getIngress().get().getMetadata().getName(), is(ingress.getMetadata().getName()));
//        assertThat(result.getService().getMetadata().getName(), is(service.getMetadata().getName()));
//        assertThat(result.getAdminSecret().get().getMetadata().getName(), is(adminSecret.getMetadata().getName()));
//    }
//
//    private DefaultSimpleK8SClient getDefaultSimpleK8SClient() {
//        if (this.simpleK8SClient == null) {
//            this.simpleK8SClient = new DefaultSimpleK8SClient(getFabric8Client());
//        }
//        return simpleK8SClient;
//    }
//}