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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;

public class TestFixtureRequest {

    private final Map<String, List<Class<? extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>>>> requiredDeletions =
            new ConcurrentHashMap<>();

    private final Map<String, List<EntandoBaseCustomResource<?, EntandoCustomResourceStatus>>> requiredAdditions =
            new ConcurrentHashMap<>();

    public DeletionRequestBuilder deleteAll(Class<? extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>> type) {
        return new DeletionRequestBuilder(type, this);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public final AdditionRequestBuilder addAll(EntandoBaseCustomResource... objects) {
        return new AdditionRequestBuilder(Arrays.asList(objects), this);
    }

    public Map<String, List<Class<? extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>>>> getRequiredDeletions() {
        return requiredDeletions;
    }

    public Map<String, List<EntandoBaseCustomResource<?, EntandoCustomResourceStatus>>> getRequiredAdditions() {
        return requiredAdditions;
    }

    public static class DeletionRequestBuilder {

        private final Class<? extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>> typesToDelete;
        private final TestFixtureRequest request;

        private DeletionRequestBuilder(Class<? extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>> typesToDelete,
                TestFixtureRequest request) {
            this.typesToDelete = typesToDelete;
            this.request = request;
        }

        public TestFixtureRequest fromNamespace(String namespace) {
            List<Class<? extends EntandoBaseCustomResource<?, EntandoCustomResourceStatus>>> types = request.requiredDeletions
                    .computeIfAbsent(namespace, k -> new ArrayList<>());
            types.add(typesToDelete);
            return request;
        }
    }

    public static class AdditionRequestBuilder {

        private final List<EntandoBaseCustomResource<?, EntandoCustomResourceStatus>> objectsToAdd;
        private final TestFixtureRequest request;

        private AdditionRequestBuilder(List<EntandoBaseCustomResource<?, EntandoCustomResourceStatus>> objectsToAdd,
                TestFixtureRequest request) {
            this.objectsToAdd = new ArrayList<>(objectsToAdd);
            this.request = request;
        }

        public TestFixtureRequest toNamespace(String namespace) {
            if (request.requiredAdditions.containsKey(namespace)) {
                request.requiredAdditions.get(namespace).addAll(objectsToAdd);
            } else {
                request.requiredAdditions.put(namespace, objectsToAdd);
            }
            return request;
        }
    }
}