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

package org.entando.kubernetes.test.common;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelector;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.Collection;
import java.util.function.Predicate;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public interface VariableReferenceAssertions {

    default Predicate<EnvVarSource> theSecretKey(String secretName, String key) {
        return envVarSource -> envVarSource.getSecretKeyRef() != null && secretName.equals(envVarSource.getSecretKeyRef().getName()) && key
                .equals(envVarSource.getSecretKeyRef().getKey());
    }

    default Predicate<EnvVarSource> theConfigMapKey(String configMapName, String key) {
        return envVarSource -> envVarSource.getConfigMapKeyRef() != null && configMapName
                .equals(envVarSource.getConfigMapKeyRef().getName())
                && key
                .equals(envVarSource.getConfigMapKeyRef().getKey());
    }

    default void verifyThatAllVariablesAreMapped(EntandoCustomResource resource, SimpleK8SClient client,
            Deployment deployment) {
        deployment.getSpec().getTemplate().getSpec().getContainers().stream().map(Container::getEnv)
                .flatMap(Collection::stream)
                .forEach(envVar -> {
                    if (envVar.getValueFrom() == null) {
                        assertThat(envVar.getName() + " has no value", envVar.getValue(), is(notNullValue()));
                    } else if (envVar.getValueFrom().getSecretKeyRef() != null) {
                        SecretKeySelector secretKeyRef = envVar.getValueFrom().getSecretKeyRef();
                        Secret secret = client.secrets().loadSecret(resource, secretKeyRef.getName());
                        assertThat("The secret " + secretKeyRef.getName() + " does not exist", secret, is(notNullValue()));
                        assertThat("The key " + secretKeyRef.getKey() + " does not exist on " + secretKeyRef.getName(),
                                secret.getStringData().get(secretKeyRef.getKey()), is(notNullValue()));

                    } else if (envVar.getValueFrom().getConfigMapKeyRef() != null) {
                        ConfigMapKeySelector configMapKeyRef = envVar.getValueFrom().getConfigMapKeyRef();
                        ConfigMap configMap = client.secrets().loadConfigMap(resource, configMapKeyRef.getName());
                        assertThat("The ConfigMap " + configMapKeyRef.getName() + " does not exist", configMap, is(notNullValue()));
                        assertThat("The key " + configMapKeyRef.getKey() + " does not exist on " + configMapKeyRef.getName(),
                                configMap.getData().get(configMapKeyRef.getKey()), is(notNullValue()));

                    }

                });
    }

    default Matcher<EnvVar> mapsToSecretKey(String expectedSecretName, String expectedKey) {
        return new BaseMatcher<>() {
            private String actualKey;
            private String actualSecretName;
            private EnvVar envVar;

            @Override
            public boolean matches(Object actual) {
                this.envVar = (EnvVar) actual;
                if (envVar.getValueFrom() != null && envVar.getValueFrom().getSecretKeyRef() != null) {
                    this.actualSecretName = envVar.getValueFrom().getSecretKeyRef().getName();
                    this.actualKey = envVar.getValueFrom().getSecretKeyRef().getKey();
                    return expectedSecretName.equals(actualSecretName) && expectedKey.equals(actualKey);
                }
                return false;

            }

            @Override
            public void describeTo(Description description) {
                description.appendText(
                        format("Expected the secret %s and key %s but found the secret %s with the key %s ", expectedSecretName,
                                expectedKey, actualSecretName, actualKey));
            }
        };
    }
}
