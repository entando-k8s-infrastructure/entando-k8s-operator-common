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

import io.fabric8.kubernetes.client.KubernetesClient;
import java.lang.reflect.Proxy;
import org.entando.kubernetes.controller.support.client.CapabilityClient;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.controller.support.client.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.support.client.PodClient;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.client.ServiceAccountClient;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;

public class DefaultSimpleK8SClient implements SimpleK8SClient<EntandoResourceClient> {

    private final KubernetesClient kubernetesClient;
    private ServiceClient serviceClient;
    private PodClient podClient;
    private SecretClient secretClient;
    private EntandoResourceClient entandoResourceClient;
    private DeploymentClient deploymentClient;
    private IngressClient ingressClient;
    private PersistentVolumeClaimClient persistentVolumeClaimClient;
    private ServiceAccountClient serviceAccountClient;
    private CapabilityClient capabilityClient;

    public DefaultSimpleK8SClient(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public CapabilityClient capabilities() {
        if (this.capabilityClient == null) {
            this.capabilityClient = intercepted(new DefaultCapabilityClient(kubernetesClient));
        }
        return this.capabilityClient;
    }

    @Override
    public ServiceClient services() {
        if (this.serviceClient == null) {
            this.serviceClient = intercepted(new DefaultServiceClient(kubernetesClient));

        }
        return this.serviceClient;
    }

    @Override
    public PodClient pods() {
        if (this.podClient == null) {
            this.podClient = intercepted(new DefaultPodClient(kubernetesClient));
        }
        return this.podClient;
    }

    @Override
    public SecretClient secrets() {
        if (this.secretClient == null) {
            this.secretClient = intercepted(new DefaultSecretClient(kubernetesClient));
        }
        return this.secretClient;
    }

    @Override
    public EntandoResourceClient entandoResources() {
        if (this.entandoResourceClient == null) {
            this.entandoResourceClient = intercepted(new DefaultEntandoResourceClient(kubernetesClient));
        }
        return this.entandoResourceClient;
    }

    @Override
    public ServiceAccountClient serviceAccounts() {
        if (this.serviceAccountClient == null) {
            this.serviceAccountClient = intercepted(new DefaultServiceAccountClient(kubernetesClient));
        }
        return this.serviceAccountClient;
    }

    @Override
    public DeploymentClient deployments() {
        if (this.deploymentClient == null) {
            this.deploymentClient = intercepted(new DefaultDeploymentClient(kubernetesClient));
        }
        return this.deploymentClient;
    }

    @Override
    public IngressClient ingresses() {
        if (this.ingressClient == null) {
            this.ingressClient = intercepted(new DefaultIngressClient(kubernetesClient));
        }
        return this.ingressClient;
    }

    @Override
    public PersistentVolumeClaimClient persistentVolumeClaims() {
        if (this.persistentVolumeClaimClient == null) {
            this.persistentVolumeClaimClient = intercepted(new DefaultPersistentVolumeClaimClient(kubernetesClient));
        }
        return this.persistentVolumeClaimClient;
    }

    @SuppressWarnings("unchecked")
    private <T> T intercepted(T delegate) {
        return (T) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                delegate.getClass().getInterfaces(),
                new KubernetesRestInterceptor(delegate));
    }
}
