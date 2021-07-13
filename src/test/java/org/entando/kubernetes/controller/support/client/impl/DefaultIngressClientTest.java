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

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.client.AbstractSupportK8SIntegrationTest;
import org.entando.kubernetes.fluentspi.TestResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultIngressClientTest extends AbstractSupportK8SIntegrationTest {

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE_1};
    }

    @Test
    void shouldRemovePathFromIngress() {
        TestResource app = newTestResource();
        Ingress myIngress = getTestIngress();
        myIngress.getMetadata().setNamespace(app.getMetadata().getNamespace());
        Ingress deployedIngress = this.getSimpleK8SClient().ingresses().createIngress(app, myIngress);

        Assertions.assertTrue(() -> deployedIngress.getSpec().getRules().get(0).getHttp().getPaths().size() == 2);

        HTTPIngressPath ingressPath = myIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        Ingress cleanedIngress = this.getSimpleK8SClient().ingresses().removeHttpPath(deployedIngress, ingressPath);

        Assertions.assertFalse(() ->
                cleanedIngress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                        .anyMatch(p -> p.getPath().equals(ingressPath.getPath())
                                && p.getBackend().getServicePort().equals(ingressPath.getBackend().getServicePort())
                                && p.getBackend().getServiceName().equals(ingressPath.getBackend().getServiceName())));

        Assertions.assertTrue(() -> cleanedIngress.getSpec().getRules().get(0).getHttp()
                .getPaths().size() == 1);
    }

    @Test
    void shouldRemainConsistentWithManyThreads() throws JsonProcessingException, InterruptedException {
        TestResource app = newTestResource();
        Ingress myIngress = getTestIngress();
        myIngress.getSpec().getRules().get(0).getHttp().getPaths().clear();
        final int total = 10;
        ExecutorService executor = Executors.newFixedThreadPool(total + 2);
        //When I create multiple ingresses at the same time with different paths
        for (int i = 0; i < total; i++) {
            Ingress tmp = objectMapper.readValue(objectMapper.writeValueAsString(myIngress), Ingress.class);
            tmp.getSpec().getRules().get(0).getHttp().getPaths().add(new HTTPIngressPathBuilder()
                    .withPath("/path/" + i)
                    .withNewBackend()
                    .withServiceName("service-for-path" + i)
                    .withServicePort(new IntOrString(8080))
                    .endBackend()
                    .build());
            executor.submit(() -> getSimpleK8SClient().ingresses().createIngress(app, tmp));
        }
        executor.shutdown();
        await().atMost(10, TimeUnit.MINUTES).ignoreExceptions().until(() -> executor.awaitTermination(60, TimeUnit.SECONDS));
        Ingress actual = getSimpleK8SClient().ingresses().loadIngress(app.getMetadata().getNamespace(), myIngress.getMetadata().getName());
        //Then the paths should be consistent
        assertThat(actual.getSpec().getRules().get(0).getHttp().getPaths().size(), is(total));
        for (int i = 0; i < total; i++) {
            int finalI = i;
            assertTrue(actual.getSpec().getRules().get(0).getHttp().getPaths().stream()
                    .anyMatch(httpIngressPath -> httpIngressPath.getPath().equals("/path/" + finalI)));
        }
    }

    @Test
    void shouldAddHttpPath() {
        //Given I have an Ingress
        Ingress myIngress = getTestIngress();
        final TestResource app = newTestResource();
        myIngress.getMetadata().setNamespace(app.getMetadata().getNamespace());
        Ingress deployedIngress = this.getSimpleK8SClient().ingresses().createIngress(app, myIngress);
        //When I add the path '/new-path' to it
        getSimpleK8SClient().ingresses().addHttpPath(deployedIngress, new HTTPIngressPathBuilder()
                .withPath("/new-path")
                .withNewBackend()
                .withServiceName("some-service")
                .withServicePort(new IntOrString(80))
                .endBackend()
                .build(), Collections.emptyMap());
        final Ingress actual = getSimpleK8SClient().ingresses()
                .loadIngress(app.getMetadata().getNamespace(), myIngress.getMetadata().getName());
        assertThat(actual.getSpec().getRules().get(0).getHttp().getPaths().get(2).getPath(), is("/new-path"));
    }

    private Ingress getTestIngress() {
        return new IngressBuilder()
                .withNewMetadata()
                .withName("my-ingress")
                .endMetadata()
                .withNewSpec()
                .addNewRule()
                .withHost("my-host-local")
                .withNewHttp()
                .addNewPath()
                .withPath("/path1")
                .withNewBackend()
                .withServiceName("path1-plugin")
                .withServicePort(new IntOrString(8081))
                .endBackend()
                .endPath()
                .addNewPath()
                .withPath("/path2")
                .withNewBackend()
                .withServiceName("path2-plugin")
                .withServicePort(new IntOrString(8081))
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build();
    }

}
