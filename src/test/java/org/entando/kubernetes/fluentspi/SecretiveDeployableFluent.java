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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.entando.kubernetes.controller.spi.deployable.Secretive;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class SecretiveDeployableFluent<N extends SecretiveDeployableFluent<N>>
        extends DeployableFluent<N>
        implements Secretive {

    private final List<Secret> secrets = new ArrayList<>();

    public N withSecret(String name, Map<String, String> data) {
        this.secrets.add(new SecretBuilder().withNewMetadata().withName(name).endMetadata().withStringData(data).build());
        return thisAsN();
    }

    @Override
    public List<Secret> getSecrets() {
        return this.secrets;
    }

    public N withCustomResource(EntandoCustomResource customResource) {
        this.secrets.forEach(secret -> secret.getMetadata().setNamespace(customResource.getMetadata().getNamespace()));
        return super.withCustomResource(customResource);
    }
}
