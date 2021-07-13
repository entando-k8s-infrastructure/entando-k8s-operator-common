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

import static io.qameta.allure.Allure.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.client.AbstractSupportK8SIntegrationTest;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.HttpTestHelper;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.test.common.ValueHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@Feature("As a support developer, I would like perform common operations on Services and Endpoints resources through a simple "
        + "interface to reduce the learning curve")
class DefaultServiceClientTest extends AbstractSupportK8SIntegrationTest {

    @Test
    @Description("Should create or replace an existing Service with the new value without generating duplicate exceptions")
    void shouldCreateOrReplaceService() {
        TestResource testResource = newTestResource();
        ValueHolder<Service> firstService = new ValueHolder<>();
        step("Given I have an existing service with the annotation 'test: 123'", () -> {
            firstService.set(getSimpleK8SClient().services().createOrReplaceService(testResource,
                    new ServiceBuilder()
                            .withNewMetadata()
                            .withNamespace(testResource.getMetadata().getNamespace())
                            .withName("my-service")
                            .addToAnnotations("test", "123")
                            .endMetadata()
                            .withNewSpec()
                            .withExternalName("google.com")
                            .addNewPort()
                            .withPort(80)
                            .endPort()
                            .endSpec()
                            .build()));
            attachResource("First Service", firstService.get());
        });
        step("When I attempt to createOrReplaceService a service with the same name but with the annotation 'test: 234'", () -> {
            firstService.get().getMetadata().getAnnotations().put("test", "234");
            getSimpleK8SClient().services().createOrReplaceService(testResource, firstService.get());
            attachResource("Second Service", firstService.get());
        });
        step("Then it has reflects the new annotation", () -> {
            final Service second = getSimpleK8SClient().services().loadService(testResource, "my-service");
            assertThat(second.getMetadata().getAnnotations()).containsEntry("test", "234");
        });
    }

    @Test
    @Description("Should create or replace an existing Endpoints with the new value without generating duplicate exceptions")
    void shouldCreateOrReplaceEndpoints() {
        TestResource testResource = newTestResource();
        final ValueHolder<Endpoints> firstEndpoints = new ValueHolder<>();
        step("Given I have an EndPoints with that is exposed on port 80", () -> {
            firstEndpoints.set(getSimpleK8SClient().services().createOrReplaceEndpoints(testResource,
                    new EndpointsBuilder()
                            .withNewMetadata()
                            .withNamespace(testResource.getMetadata().getNamespace())
                            .withName("my-service")
                            .addToAnnotations("test", "123")
                            .endMetadata()
                            .addNewSubset()
                            .addNewAddress().withIp("172.17.0.123").endAddress()
                            .addNewPort(null, "my-port", 80, null)
                            .endSubset()
                            .build()));
            attachResource("First Endpoints", firstEndpoints.get());
        });
        step("When I attempt to createOrReplaceEndpoints an endpoints with the same name but with the port 81", () -> {
            firstEndpoints.get().getSubsets().get(0).getPorts().get(0).setPort(81);
            getSimpleK8SClient().services().createOrReplaceEndpoints(testResource, firstEndpoints.get());
        });
        step("Then the new Endpoints  the new port", () -> {
            Endpoints secondEndpoints = getSimpleK8SClient().services().loadEndpoints(testResource, "my-service");
            assertThat(secondEndpoints.getSubsets().get(0).getPorts().get(0).getPort()).isEqualTo(81);
            attachResource("Second Endpoints", secondEndpoints);
        });
    }

    @Test
    @Description("Should create a delegate Service/Endpoints pair that delegate to a service in another namespace to be exposed on an "
            + "Ingress in this namespace. (Required for Openshift)")
    void shouldCreateADelegateServiceAndEndpoints() {
        TestResource testResource1 = newTestResource();
        TestResource testResource2 = newTestResource()
                .withNames(testResource1.getMetadata().getNamespace() + "2", testResource1.getMetadata().getName() + "2");
        ValueHolder<Service> firstService = new ValueHolder<>();
        step("Given I have started a new NGINX Pod with the label 'pod-label: 123'", () -> {
            final Pod startedPod = getSimpleK8SClient().pods().start(new PodBuilder()
                    .withNewMetadata()
                    .withName("my-pod")
                    .withNamespace(testResource1.getMetadata().getNamespace())
                    .addToLabels("pod-label", "123")
                    .endMetadata()
                    .withNewSpec()
                    .addNewContainer()
                    .addNewPort()
                    .withContainerPort(8080)
                    .endPort()
                    .withImage("entando/test-nginx-container:latest")
                    .withName("nginx")
                    .withCommand("/usr/libexec/s2i/run")
                    .endContainer()
                    .endSpec()
                    .build());
            final Pod pod = getSimpleK8SClient().pods().waitForPod(testResource1.getMetadata().getNamespace(), "pod-label", "123",
                    EntandoOperatorSpiConfig.getPodReadinessTimeoutSeconds());
            attachResource("Pod", pod);
        });
        step("And I have created a service to expose it internally", () -> {
            firstService.set(getSimpleK8SClient().services().createOrReplaceService(testResource1,
                    new ServiceBuilder()
                            .withNewMetadata()
                            .withName("my-service")
                            .addToAnnotations("test", "123")
                            .endMetadata()
                            .withNewSpec()
                            .withSelector(Map.of("pod-label", "123"))
                            .addNewPort()
                            .withPort(8080)
                            .endPort()
                            .endSpec()
                            .build()));
            attachResource("First Service", firstService.get());
        });
        step("When I create a second Service/Endpoints pair in another namespace pointing to the IP address of the first service", () -> {
            attachResource("Second Service", getSimpleK8SClient().services().createOrReplaceDelegateService(
                    new ServiceBuilder()
                            .withNewMetadata()
                            .withNamespace(testResource2.getMetadata().getNamespace())
                            .withName("my-service2")
                            .endMetadata()
                            .withNewSpec()
                            .addNewPort()
                            .withPort(8080)
                            .endPort()
                            .endSpec()
                            .build()));
            attachResource("Second Endpoints", getSimpleK8SClient().services().createOrReplaceDelegateEndpoints(
                    new EndpointsBuilder()
                            .withNewMetadata()
                            .withNamespace(testResource2.getMetadata().getNamespace())
                            .withName("my-service2")
                            .endMetadata()
                            .addNewSubset()
                            .addNewAddress().withIp(firstService.get().getSpec().getClusterIP()).endAddress()
                            .addNewPort(null, "my-port", 8080, null)
                            .endSubset()
                            .build()));
        });
        final String hostname = testResource2.getMetadata().getName() + "." + EntandoOperatorConfig.getDefaultRoutingSuffix()
                .orElse("apps.serv.run");
        step("Then I can successfully expose the delegate service on an ingress in the second namespace", () -> {
            getSimpleK8SClient().ingresses().createIngress(testResource2, new IngressBuilder()
                    .withNewMetadata()
                    .withName("my-ingress2")
                    .endMetadata()
                    .withNewSpec()
                    .addNewRule()
                    .withHost(hostname)
                    .withNewHttp()
                    .addNewPath()
                    .withNewBackend()
                    .withServiceName("my-service2")
                    .withServicePort(new IntOrString(8080))
                    .endBackend()
                    .withNewPath("/")
                    .endPath()
                    .endHttp()
                    .endRule()
                    .endSpec()
                    .build());
            await().atMost(40, TimeUnit.SECONDS).ignoreExceptions()
                    .until(() -> HttpTestHelper.getStatus("http://" + hostname + "/index2.html") == 200);
            assertThat(HttpTestHelper.getStatus("http://" + hostname + "/index2.html")).isEqualTo(200);
        });
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE_1, MY_APP_NAMESPACE_2};
    }
}
