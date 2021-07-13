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

import io.fabric8.kubernetes.client.VersionInfo;
import java.text.ParseException;
import org.entando.kubernetes.controller.spi.capability.doubles.CapabilityClientDouble;
import org.entando.kubernetes.controller.support.client.CapabilityClient;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.controller.support.client.IngressClient;
import org.entando.kubernetes.controller.support.client.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.support.client.PodClient;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.client.ServiceAccountClient;
import org.entando.kubernetes.controller.support.client.ServiceClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.mockito.Mockito;

public class SimpleK8SClientDouble extends AbstractK8SClientDouble implements SimpleK8SClient<EntandoResourceClientDouble> {

    private final ServiceClient serviceClient = Mockito.spy(new ServiceClientDouble(getNamespaces(), getCluster()));
    private final PersistentVolumeClaimClient persistentVolumeClaimClient = Mockito
            .spy(new PersistentVolumentClaimClientDouble(
                    getNamespaces(), getCluster()));
    private final IngressClient ingressClient = Mockito.spy(new IngressClientDouble(getNamespaces(), getCluster()));
    private final DeploymentClient deploymentClient;
    private final SecretClient secretClient = Mockito.spy(new SecretClientDouble(getNamespaces(), getCluster()));
    private final EntandoResourceClientDouble entandoResourceClient = Mockito
            .spy(new EntandoResourceClientDouble(getNamespaces(), getCluster()));
    private final PodClient podClient = Mockito.spy(new PodClientDouble(getNamespaces(), getCluster()));
    private final ServiceAccountClientDouble serviceAccountClient = Mockito
            .spy(new ServiceAccountClientDouble(getNamespaces(), getCluster()));
    private final CapabilityClientDouble capabilityClient = Mockito.spy(new CapabilityClientDouble(getNamespaces(), getCluster()));
    private final VersionInfo version;

    public SimpleK8SClientDouble() {
        this(getVersionInfo(20));
    }

    public SimpleK8SClientDouble(int minorVersion) {
        this(getVersionInfo(minorVersion));
    }

    public SimpleK8SClientDouble(VersionInfo version) {
        this.version = version;
        this.deploymentClient = Mockito.spy(new DeploymentClientDouble(getNamespaces(), getCluster(), version));
    }

    private static VersionInfo getVersionInfo(int minorVersion) {
        try {
            return new VersionInfo.Builder().withMinor(String.valueOf(minorVersion))
                    .withMajor("1")
                    .withGitCommit("123")
                    .withGitVersion("1")
                    .withBuildDate("2021-01-31T14:00:12Z").build();
        } catch (ParseException e) {
            //no need to do anything here
            return null;
        }
    }

    public VersionInfo getVersion() {
        return version;
    }

    @Override
    public ServiceClient services() {
        return this.serviceClient;
    }

    @Override
    public CapabilityClient capabilities() {
        return this.capabilityClient;
    }

    @Override
    public PodClient pods() {
        return this.podClient;
    }

    @Override
    public SecretClient secrets() {
        return this.secretClient;
    }

    @Override
    public EntandoResourceClientDouble entandoResources() {
        return this.entandoResourceClient;
    }

    @Override
    public ServiceAccountClient serviceAccounts() {
        return this.serviceAccountClient;
    }

    @Override
    public DeploymentClient deployments() {
        return this.deploymentClient;
    }

    @Override
    public IngressClient ingresses() {
        return this.ingressClient;
    }

    @Override
    public PersistentVolumeClaimClient persistentVolumeClaims() {
        return this.persistentVolumeClaimClient;
    }

}
