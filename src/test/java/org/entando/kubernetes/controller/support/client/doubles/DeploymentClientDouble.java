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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.VersionInfo;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DeploymentClientDouble extends AbstractK8SClientDouble implements DeploymentClient {

    private final VersionInfo versionInfo;

    public DeploymentClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster, VersionInfo versionInfo) {
        super(namespaces, cluster);
        this.versionInfo = versionInfo;
    }

    @Override
    public boolean supportsStartupProbes() {
        return Integer.parseInt(versionInfo.getMinor()) >= 16;
    }

    @Override
    public Deployment createOrPatchDeployment(EntandoCustomResource peerInNamespace, Deployment deployment, int timeoutSeconds) {
        if (peerInNamespace == null) {
            return null;
        }
        deployment.getMetadata().getName();
        getNamespace(peerInNamespace).putDeployment(deployment);
        Pod pod = createPodFrom(deployment);
        getNamespace(peerInNamespace).putPod(pod);
        return deployment;
    }

    private Pod createPodFrom(Deployment deployment) {
        return new PodBuilder().withNewMetadataLike(deployment.getMetadata()).endMetadata().build();
    }

    @Override
    public Deployment loadDeployment(EntandoCustomResource peerInNamespace, String name) {
        if (peerInNamespace == null) {
            return null;
        }
        return getNamespace(peerInNamespace).getDeployment(name);
    }

}
