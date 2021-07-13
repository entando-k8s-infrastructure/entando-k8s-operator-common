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

package org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers;

import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.RawCustomResourceOperationsImpl;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CustomResourceDeletionWaiter {

    private final RawCustomResourceOperationsImpl operation;
    private String name;
    private String namespace;

    public CustomResourceDeletionWaiter(KubernetesClient client, String kind) {
        this.operation = client.customResource(new CustomResourceDefinitionContext.Builder()
                .withPlural(kind.toLowerCase() + "s")
                .withVersion(TestFixturePreparation.CURRENT_ENTANDO_RESOURCE_VERSION)
                .withGroup("entando.org")
                .withScope("Namespaced")
                .withName(kind)
                .build());
    }

    public CustomResourceDeletionWaiter named(String name) {
        this.name = name;
        return this;
    }

    public CustomResourceDeletionWaiter fromNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public void waitingAtMost(long duration, TimeUnit timeUnit) {
        if (name == null) {
            if (((List) this.operation.list(namespace).get("items")).size() > 0) {
                this.operation.delete(namespace);
                await().atMost(duration, timeUnit)
                        .ignoreExceptions()
                        .until(() -> ((List) this.operation.list(namespace).get("items")).isEmpty());
            }
        } else {
            if (this.operation.get(namespace, name) != null) {
                try {
                    this.operation.delete(namespace, name);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                await().atMost(duration, timeUnit)
                        .ignoreExceptions()
                        .until(() -> this.operation.get(namespace, name) == null);
            }
        }
    }
}
