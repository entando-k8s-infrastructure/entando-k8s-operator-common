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

package org.entando.kubernetes.fluentspi;

import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.DockerImageInfo;
import org.entando.kubernetes.controller.spi.container.KubernetesPermission;
import org.entando.kubernetes.controller.spi.container.PortSpec;
import org.entando.kubernetes.controller.spi.container.SecretToMount;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DeployableContainerFluent<N extends DeployableContainerFluent<N>> implements DeployableContainer {

    private String nameQualifier;
    private int primaryPort = 8080;
    private final List<EnvVar> environmentVariables = new ArrayList<>();
    private DockerImageInfo dockerImageInfo;
    private Integer maximumStartupSeconds;
    private final List<PortSpec> additionalPorts = new ArrayList<>();
    private int cpuLimitMillicores = DeployableContainer.super.getCpuLimitMillicores();
    private final List<SecretToMount> secretsToMount = new ArrayList<>();
    private final List<KubernetesPermission> kubernetesPermissions = new ArrayList<>();
    private int memoryLimitMebibytes = DeployableContainer.super.getMemoryLimitMebibytes();
    protected EntandoCustomResource customResource;

    @Override
    public String getNameQualifier() {
        return this.nameQualifier;
    }

    public N withNameQualifier(String nameQualifier) {
        this.nameQualifier = nameQualifier;
        return thisAsN();
    }

    @SuppressWarnings("unchecked")
    protected N thisAsN() {
        return (N) this;
    }

    @Override
    public int getPrimaryPort() {
        return this.primaryPort;
    }

    public N withPrimaryPort(int primaryPort) {
        this.primaryPort = primaryPort;
        return thisAsN();
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        return this.environmentVariables;
    }

    public N withEnvVar(String name, String value) {
        environmentVariables.add(new EnvVar(name, value, null));
        return thisAsN();
    }

    public N withEnvVarFromSecret(String varName, String secretName, String key) {
        environmentVariables.add(new EnvVar(varName, null,
                new EnvVarSourceBuilder().withSecretKeyRef(new SecretKeySelector(key, secretName, Boolean.FALSE)).build()));
        return thisAsN();
    }

    public N withEnvVarFromConfigMap(String varName, String configMapName, String key) {
        environmentVariables.add(new EnvVar(varName, null,
                new EnvVarSourceBuilder().withConfigMapKeyRef(new ConfigMapKeySelector(key, configMapName, Boolean.FALSE)).build()));
        return thisAsN();
    }

    @Override
    public DockerImageInfo getDockerImageInfo() {
        return dockerImageInfo;
    }

    public N withDockerImageInfo(String imageUri) {
        this.dockerImageInfo = new DockerImageInfo(imageUri);
        return thisAsN();
    }

    @Override
    public Optional<Integer> getMaximumStartupTimeSeconds() {
        return Optional.ofNullable(maximumStartupSeconds);
    }

    public N withMaximumStartTimeSeconds(int maximumStartupSeconds) {
        this.maximumStartupSeconds = maximumStartupSeconds;
        return thisAsN();
    }

    @Override
    public List<PortSpec> getAdditionalPorts() {
        return additionalPorts;
    }

    public N withAdditionalPort(String name, int port) {
        this.additionalPorts.add(new PortSpec(name, port));
        return thisAsN();
    }

    public int getMemoryLimitMebibytes() {
        return this.memoryLimitMebibytes;
    }

    public N withMemoryLimitMebibytes(int memoryLimitMebibytes) {
        this.memoryLimitMebibytes = memoryLimitMebibytes;
        return thisAsN();
    }

    public int getCpuLimitMillicores() {
        return cpuLimitMillicores;
    }

    public N withCpuLimitMillicores(int cpuLimitMillicores) {
        this.cpuLimitMillicores = cpuLimitMillicores;
        return thisAsN();
    }

    public List<SecretToMount> getSecretsToMount() {
        return this.secretsToMount;
    }

    public N withSecretToMount(String secretName, String mountPath) {
        this.secretsToMount.add(new SecretToMount(secretName, mountPath));
        return thisAsN();
    }

    public List<KubernetesPermission> getKubernetesPermissions() {
        return this.kubernetesPermissions;
    }

    public N withKubernetesPermission(String apiGroup, String resource, String... verbs) {
        this.kubernetesPermissions.add(new KubernetesPermission(apiGroup, resource, verbs));
        return thisAsN();
    }

    public N withCustomResource(EntandoCustomResource entandoCustomResource) {
        this.customResource = entandoCustomResource;
        return thisAsN();
    }
}
