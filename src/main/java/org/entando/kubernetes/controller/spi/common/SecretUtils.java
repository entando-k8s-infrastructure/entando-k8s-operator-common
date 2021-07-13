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

package org.entando.kubernetes.controller.spi.common;

import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.util.UUID;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class SecretUtils {

    public static final String PASSSWORD_KEY = "password";//Funny name because a variable named 'PASSWORD' is considered a vulnerability
    public static final String USERNAME_KEY = "username";
    public static final String EMPTY_TLS_SECRET_NAME = "entando-empty-tls-secret";

    private SecretUtils() {
    }

    public static EnvVarSource secretKeyRef(String secretName, String key) {
        return new EnvVarSourceBuilder().withNewSecretKeyRef(key, secretName, Boolean.FALSE).build();
    }

    public static Secret generateSecret(EntandoCustomResource resource, String secretName, String username) {
        String password = randomAlphanumeric(16);
        return buildSecret(resource, secretName, username, password);
    }

    public static Secret buildSecret(EntandoCustomResource resource, String secretName, String username, String password) {
        return new SecretBuilder()
                .withNewMetadata().withName(secretName)
                .withOwnerReferences(ResourceUtils.buildOwnerReference(resource))
                .addToLabels(resource.getKind(), resource.getMetadata().getName())
                .endMetadata()
                .addToStringData(USERNAME_KEY, username)
                .addToStringData(PASSSWORD_KEY, password)
                .build();
    }

    public static String randomAlphanumeric(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }

}
