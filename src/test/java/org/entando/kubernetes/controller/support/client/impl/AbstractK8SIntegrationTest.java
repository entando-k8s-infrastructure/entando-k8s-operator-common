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

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.qameta.allure.Allure;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.TestFixturePreparation;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpecBuilder;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractK8SIntegrationTest implements FluentTraversals {

    public static final String MY_APP_NAMESPACE_1 = EntandoOperatorTestConfig.calculateNameSpace("my-app-namespace") + "-test1";
    public static final String MY_APP_NAMESPACE_2 = MY_APP_NAMESPACE_1 + "2";
    public static final String TEST_CONTROLLER_POD = "test-controller-pod";
    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    protected KubernetesClient fabric8Client;

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    protected void awaitDefaultToken(String namespace) {
        await().atMost(30, TimeUnit.SECONDS).ignoreExceptions()
                .until(() -> getFabric8Client().secrets().inNamespace(namespace).list()
                        .getItems().stream().anyMatch(secret -> TestFixturePreparation.isValidTokenSecret(secret, "default")));
    }

    protected TestResource newTestResource() {
        return new TestResource()
                .withNames(MY_APP_NAMESPACE_1, "my-app")
                .withSpec(new BasicDeploymentSpecBuilder()
                        .withReplicas(1)
                        .build());
    }

    @AfterEach
    void teardown() {
        scheduler.shutdownNow();
        System.clearProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY);
        System.clearProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME.getJvmSystemProperty());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_OF_INTEREST.getJvmSystemProperty());
    }

    protected final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    protected KubernetesClient getFabric8Client() {
        return this.fabric8Client;
    }

    protected void attachResource(String name, HasMetadata resource) throws JsonProcessingException {
        Allure.attachment(name, objectMapper.writeValueAsString(resource));
    }

    protected void attachResources(String name, Collection<? extends HasMetadata> resource) throws JsonProcessingException {
        Allure.attachment(name, objectMapper.writeValueAsString(resource));
    }

    @BeforeEach
    public void setup() {
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME.getJvmSystemProperty(), TEST_CONTROLLER_POD);
        fabric8Client = new SupportProducer().getKubernetesClient();
        for (String s : getNamespacesToUse()) {
            await().atMost(240, TimeUnit.SECONDS).ignoreExceptions()
                    .until(() -> {
                        if (fabric8Client.namespaces().withName(s).get() == null) {
                            return true;
                        } else {
                            fabric8Client.namespaces().withName(s).delete();
                            return false;
                        }
                    });
        }
        for (String s : getNamespacesToUse()) {
            TestFixturePreparation.createNamespace(fabric8Client, s);
        }
    }

    protected abstract String[] getNamespacesToUse();
}
