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

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.entando.kubernetes.controller.spi.container.DatabasePopulator;
import org.entando.kubernetes.controller.spi.container.DockerImageInfo;

public class BasicDatabasePopulator implements DatabasePopulator {

    private final List<EnvVar> environmentVariables = new ArrayList<>();
    private final List<String> command = new ArrayList<>();
    private DockerImageInfo dockerImageInfo;

    @Override
    public DockerImageInfo getDockerImageInfo() {
        return this.dockerImageInfo;
    }

    public BasicDatabasePopulator withDockerImageInfo(DockerImageInfo dockerImageInfo) {
        this.dockerImageInfo = dockerImageInfo;
        return this;
    }

    @Override
    public List<String> getCommand() {
        return this.command;
    }

    public BasicDatabasePopulator withCommand(String... command) {
        this.command.addAll(Arrays.asList(command));
        return this;
    }

    public BasicDatabasePopulator withEnvironmentVariables(Collection<EnvVar> envVars) {
        this.environmentVariables.clear();
        this.environmentVariables.addAll(envVars);
        return this;
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        return this.environmentVariables;
    }

}
