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

package org.entando.kubernetes.controller.spi.container;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface DeployableContainer {

    String ENTANDO_SECRET_MOUNTS_ROOT = "/etc/entando/secrets";

    String getNameQualifier();

    int getPrimaryPort();

    List<EnvVar> getEnvironmentVariables();

    default DockerImageInfo getDockerImageInfo() {
        return new DockerImageInfo(determineImageToUse());
    }

    default String determineImageToUse() {
        return "busybox";
    }

    default Optional<Integer> getMaximumStartupTimeSeconds() {
        return Optional.empty();
    }

    default List<PortSpec> getAdditionalPorts() {
        return Collections.emptyList();
    }

    default int getMemoryLimitMebibytes() {
        return 256;
    }

    default int getCpuLimitMillicores() {
        return 500;
    }

    default List<String> getNamesOfSecretsToMount() {
        return Collections.emptyList();
    }

    default List<SecretToMount> getSecretsToMount() {
        return getNamesOfSecretsToMount().stream().map(s -> new SecretToMount(s, ENTANDO_SECRET_MOUNTS_ROOT + "/" + s))
                .collect(Collectors.toList());
    }

    default List<KubernetesPermission> getKubernetesPermissions() {
        return Collections.emptyList();
    }

}
