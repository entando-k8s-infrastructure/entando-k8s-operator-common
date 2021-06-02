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

package org.entando.kubernetes.test.e2etest.helpers;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.time.Duration;
import org.entando.kubernetes.controller.support.client.impl.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.FluentIntegrationTesting;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.HttpTestHelper;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.test.e2etest.podwaiters.JobPodWaiter;
import org.entando.kubernetes.test.e2etest.podwaiters.ServicePodWaiter;

public class EntandoAppE2ETestHelper extends E2ETestHelperBase<EntandoApp> {

    public static final String TEST_NAMESPACE = EntandoOperatorTestConfig.calculateNameSpace("test-namespace");
    public static final String TEST_APP_NAME = EntandoOperatorTestConfig.calculateName("test-entando");
    public static final String CLUSTER_INFRASTRUCTURE_NAMESPACE = EntandoOperatorTestConfig
            .calculateNameSpace("entando-infra-namespace");
    public static final String CLUSTER_INFRASTRUCTURE_NAME = EntandoOperatorTestConfig.calculateName("eti");
    public static final String K8S_SVC_CLIENT_ID = CLUSTER_INFRASTRUCTURE_NAME + "-k8s-svc";

    public EntandoAppE2ETestHelper(DefaultKubernetesClient client) {
        super(client, EntandoApp.class);
    }

    public void createAndWaitForApp(EntandoApp entandoApp, int waitOffset, boolean hasContainerizedDatabase) {
        getOperations().inNamespace(entandoApp.getMetadata().getNamespace()).create(entandoApp);
        if (hasContainerizedDatabase) {
            waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                    entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName() + "-db");

        }
        if (requiresDatabaseJob(entandoApp)) {
            this.waitForDbJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(150 + waitOffset)), entandoApp, "server");
        }
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(180 + waitOffset)),
                entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName() + "-server");
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(180 + waitOffset)),
                entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName() + "-ab");
        if (requiresDatabaseJob(entandoApp)) {
            this.waitForDbJobPod(new JobPodWaiter().limitCompletionTo(Duration.ofSeconds(40 + waitOffset)), entandoApp, "cm");
        }
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(180 + waitOffset)),
                entandoApp.getMetadata().getNamespace(), entandoApp.getMetadata().getName() + "-cm");
        await().atMost(60, FluentIntegrationTesting.SECONDS).until(
                () -> {
                    EntandoCustomResourceStatus status = getOperations()
                            .inNamespace(entandoApp.getMetadata().getNamespace())
                            .withName(entandoApp.getMetadata().getName())
                            .fromServer().get().getStatus();
                    return status.getServerStatus("server").isPresent()
                            && status.getPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });

        await().atMost(60, FluentIntegrationTesting.SECONDS).until(() -> HttpTestHelper.read(
                HttpTestHelper.getDefaultProtocol() + "://" + entandoApp.getSpec().getIngressHostName()
                        .orElseThrow(IllegalStateException::new)
                        + "/entando-de-app/index.jsp").contains("Entando - Welcome"));
    }

    public Boolean requiresDatabaseJob(EntandoApp entandoApp) {
        return entandoApp.getSpec().getDbms().map(dbmsVendor -> !(dbmsVendor == DbmsVendor.EMBEDDED || dbmsVendor == DbmsVendor.NONE))
                .orElse(false);
    }
}
