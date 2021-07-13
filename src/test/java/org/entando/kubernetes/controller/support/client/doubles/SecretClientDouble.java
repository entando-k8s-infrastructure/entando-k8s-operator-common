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

package org.entando.kubernetes.controller.support.client.doubles;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class SecretClientDouble extends AbstractK8SClientDouble implements SecretClient {

    public SecretClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
    }

    @Override
    public void overwriteControllerSecret(Secret secret) {
        getNamespaces().get(CONTROLLER_NAMESPACE).putSecret(secret);
    }

    @Override
    public void createSecretIfAbsent(EntandoCustomResource peerInNamespace, Secret secret) {
        getNamespace(peerInNamespace).putSecret(secret);
    }

    @Override
    public Secret loadSecret(EntandoCustomResource resource, String secretName) {
        return getNamespace(resource).getSecret(secretName);
    }

    @Override
    public Secret loadControllerSecret(String secretName) {
        return getNamespace(CONTROLLER_NAMESPACE).getSecret(secretName);
    }

    @Override
    public ConfigMap loadControllerConfigMap(String configMapName) {
        return getNamespace(CONTROLLER_NAMESPACE).getConfigMap(configMapName);
    }

    @Override
    public void createConfigMapIfAbsent(EntandoCustomResource peerInNamespace, ConfigMap configMap) {
        getNamespace(peerInNamespace).putConfigMap(configMap);
    }

    @Override
    public ConfigMap loadConfigMap(EntandoCustomResource peerInNamespace, String name) {
        return getNamespace(peerInNamespace).getConfigMap(name);
    }

    @Override
    //Will remove once we have propagate latest version to all downstream projects. Currently only used in tests
    @Deprecated
    @SuppressWarnings("deprecated")
    public void overwriteControllerConfigMap(ConfigMap configMap) {
        getNamespace(CONTROLLER_NAMESPACE).putConfigMap(configMap);
    }
}
