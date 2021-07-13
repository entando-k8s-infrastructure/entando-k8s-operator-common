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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBackend;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.hamcrest.Matchers;

public interface FluentTraversals {

    String ENTANDO_KEYCLOAK_REALM = KeycloakName.ENTANDO_DEFAULT_KEYCLOAK_REALM;
    String ENTANDO_PUBLIC_CLIENT = KeycloakName.PUBLIC_CLIENT_ID;
    String DATABASE_ADMIN_USER = "DATABASE_ADMIN_USER";
    String DATABASE_ADMIN_PASSWORD = "DATABASE_ADMIN_PASSWORD";
    String DATABASE_USER = "DATABASE_USER";
    String DATABASE_PASSWORD = "DATABASE_PASSWORD";
    String DATABASE_VENDOR = "DATABASE_VENDOR";
    String DATABASE_NAME = "DATABASE_NAME";
    String DATABASE_SERVER_HOST = "DATABASE_SERVER_HOST";
    String DATABASE_SCHEMA_COMMAND = "DATABASE_SCHEMA_COMMAND";
    String DATABASE_SERVER_PORT = "DATABASE_SERVER_PORT";
    String SERVER_PORT = "server-port";
    String DB_PORT = "db-port";
    String KEYCLOAK_USER = "KEYCLOAK_USER";
    String KEYCLOAK_PASSWORD = "KEYCLOAK_PASSWORD";
    String DB_VENDOR = "DB_VENDOR";
    //TODO reconsider these two as they will only work on inprocess test scenarios
    String MY_KEYCLOAK_HOSTNAME = "access.192.168.0.100.nip.io";
    String MY_KEYCLOAK_BASE_URL = "http://" + MY_KEYCLOAK_HOSTNAME + "/auth";

    default ContainerFinder theContainerNamed(String name) {
        return new ContainerFinder(name);
    }

    default InitContainerFinder theInitContainerNamed(String name) {
        return new InitContainerFinder(name);
    }

    default PortFinder thePortNamed(String name) {
        return new PortFinder(name);
    }

    default VariableFinder theVariableNamed(String name) {
        return new VariableFinder(name);
    }

    default VariableReferenceFinder theVariableReferenceNamed(String name) {
        return new VariableReferenceFinder(name);
    }

    default VolumeFinder theVolumeNamed(String name) {
        return new VolumeFinder(name);
    }

    default Container thePrimaryContainerOn(Deployment deployment) {
        return deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
    }

    default Container thePrimaryContainerOn(Pod pod) {
        return pod.getSpec().getContainers().get(0);
    }

    default VolumeMountFinder theVolumeMountNamed(String name) {
        return new VolumeMountFinder(name);
    }

    default IngressPathFinder theHttpPath(String path) {
        return new IngressPathFinder(path);
    }

    default BackendFinder theBackendFor(String path) {
        return new BackendFinder(path);
    }

    default SecretKeyFinder theKey(String key) {
        return new SecretKeyFinder(key);
    }

    default LabelFinder theLabel(String label) {
        return new LabelFinder(label);
    }

    default String theHostOn(Ingress theIngress) {
        return theIngress.getSpec().getRules().get(0).getHost();
    }

    default void verifyKeycloakSettings(Container container, String keycloakClientSecret) {
        assertThat(theVariableNamed("KEYCLOAK_AUTH_URL").on(container),
                is(MY_KEYCLOAK_BASE_URL));
        assertThat(theVariableNamed("KEYCLOAK_REALM").on(container), is(ENTANDO_KEYCLOAK_REALM));
        assertThat(theVariableReferenceNamed("KEYCLOAK_CLIENT_ID").on(container).getSecretKeyRef().getName(),
                is(keycloakClientSecret));
        assertThat(theVariableReferenceNamed("KEYCLOAK_CLIENT_ID").on(container).getSecretKeyRef().getKey(),
                is(KeycloakName.CLIENT_ID_KEY));
        assertThat(theVariableReferenceNamed("KEYCLOAK_CLIENT_SECRET").on(container).getSecretKeyRef()
                .getName(), is(keycloakClientSecret));
        assertThat(
                theVariableReferenceNamed("KEYCLOAK_CLIENT_SECRET").on(container).getSecretKeyRef().getKey(),
                is(KeycloakName.CLIENT_SECRET_KEY));
    }

    default void verifySpringSecuritySettings(Container container, String keycloakClientSecret) {
        assertThat(theVariableNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name())
                .on(container), Matchers.is(MY_KEYCLOAK_BASE_URL + "/realms/entando"));
        assertThat(theVariableReferenceNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name())
                        .on(container).getSecretKeyRef().getName(),
                Matchers.is(keycloakClientSecret));
        assertThat(theVariableReferenceNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name())
                .on(container).getSecretKeyRef().getKey(), Matchers.is(KeycloakName.CLIENT_ID_KEY));
        assertThat(theVariableReferenceNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name())
                        .on(container).getSecretKeyRef().getName(),
                Matchers.is(keycloakClientSecret));
        assertThat(theVariableReferenceNamed(
                SpringBootDeployableContainer.SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name())
                        .on(container).getSecretKeyRef().getKey(),
                Matchers.is(KeycloakName.CLIENT_SECRET_KEY));
    }

    default void verifyStandardSchemaCreationVariables(String adminSecret, String secretToMatch, Container resultingContainer,
            DbmsVendor vendor) {
        assertThat(theVariableNamed(DATABASE_VENDOR).on(resultingContainer), is(vendor.toValue()));
        assertThat(theVariableNamed(DATABASE_SCHEMA_COMMAND).on(resultingContainer), is("CREATE_SCHEMA"));
        assertThat(theVariableNamed(DATABASE_SERVER_PORT).on(resultingContainer),
                is(String.valueOf(DbmsDockerVendorStrategy.forVendor(vendor, EntandoOperatorSpiConfig.getComplianceMode()).getPort())));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_USER).on(resultingContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_PASSWORD).on(resultingContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.PASSSWORD_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_USER).on(resultingContainer).getSecretKeyRef().getName(),
                is(secretToMatch));
        assertThat(theVariableReferenceNamed(DATABASE_PASSWORD).on(resultingContainer).getSecretKeyRef().getName(),
                is(secretToMatch));
        assertThat(theVariableReferenceNamed(DATABASE_USER).on(resultingContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_PASSWORD).on(resultingContainer).getSecretKeyRef().getKey(),
                is(SecretUtils.PASSSWORD_KEY));
        assertThat(theVariableReferenceNamed(DATABASE_ADMIN_USER).on(resultingContainer).getSecretKeyRef().getName(),
                is(adminSecret));
        assertThat(
                theVariableReferenceNamed(DATABASE_ADMIN_PASSWORD).on(resultingContainer).getSecretKeyRef().getName(),
                is(adminSecret));

    }

    abstract class AbstractFinder {

        private final String name;

        protected AbstractFinder(String name) {
            this.name = name;
        }

        protected <T> T find(List<T> list) {
            return list.stream().filter(this::match)
                    .findFirst()
                    .orElseThrow(
                            () -> {
                                throw new AssertionError(format("Could not find '%s'. Available items: %s", this.name,
                                        list.stream().map(this::getName).collect(
                                                Collectors.joining(","))));
                            }
                    );
        }

        private boolean match(Object namedObject) {
            return name.equals(getName(namedObject));
        }

        private String getName(Object namedObject) {
            return getName(namedObject.getClass(), namedObject);
        }

        private String getName(Class<?> clazz, Object namedObject) {
            try {
                Field name = clazz.getDeclaredField("name");
                name.setAccessible(true);
                return (String) name.get(namedObject);
            } catch (NoSuchFieldException e) {
                if (clazz.getSuperclass() == Object.class) {
                    throw new IllegalStateException(e);
                } else {
                    return getName(clazz.getSuperclass(), namedObject);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    class PortFinder extends AbstractFinder {

        protected PortFinder(String name) {
            super(name);
        }

        public ContainerPort on(Container container) {
            return find(container.getPorts());
        }

        public ServicePort on(Service service) {
            return find(service.getSpec().getPorts());
        }

        public EndpointPort on(Endpoints endpoints) {
            return find(endpoints.getSubsets().get(0).getPorts());
        }
    }

    class ContainerFinder extends AbstractFinder {

        protected ContainerFinder(String name) {
            super(name);
        }

        public Container on(Deployment deployment) {
            return find(deployment.getSpec().getTemplate().getSpec().getContainers());
        }

        public Container on(Pod pod) {
            return find(pod.getSpec().getContainers());
        }
    }

    class InitContainerFinder extends AbstractFinder {

        protected InitContainerFinder(String name) {
            super(name);
        }

        public Container on(Deployment deployment) {
            return find(deployment.getSpec().getTemplate().getSpec().getInitContainers());
        }

        public Container on(Pod pod) {
            return find(pod.getSpec().getInitContainers());
        }
    }

    class VariableReferenceFinder extends AbstractFinder {

        protected VariableReferenceFinder(String name) {
            super(name);
        }

        public EnvVarSource on(Container container) {
            return find(container.getEnv()).getValueFrom();
        }
    }

    class VariableFinder extends AbstractFinder {

        protected VariableFinder(String name) {
            super(name);
        }

        public String on(Container container) {
            return find(container.getEnv()).getValue();
        }
    }

    class VolumeFinder extends AbstractFinder {

        protected VolumeFinder(String name) {
            super(name);
        }

        public Volume on(Deployment deployment) {
            return find(deployment.getSpec().getTemplate().getSpec().getVolumes());
        }

        public Volume on(Pod pod) {
            return find(pod.getSpec().getVolumes());
        }
    }

    class VolumeMountFinder extends AbstractFinder {

        protected VolumeMountFinder(String name) {
            super(name);
        }

        public VolumeMount on(Container container) {
            return find(container.getVolumeMounts());
        }
    }

    class IngressPathFinder {

        private final String path;

        public IngressPathFinder(String path) {
            this.path = path;
        }

        public HTTPIngressPath on(Ingress ingress) {
            return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream().filter(this::matches).findFirst()
                    .orElseThrow(AssertionError::new);
        }

        private boolean matches(HTTPIngressPath httpIngressPath) {
            return path.equals(httpIngressPath.getPath());
        }
    }

    class BackendFinder {

        private final String path;

        public BackendFinder(String path) {
            this.path = path;
        }

        public IngressBackend on(Ingress ingress) {
            return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream().filter(this::matches).findFirst()
                    .orElseThrow(AssertionError::new).getBackend();
        }

        private boolean matches(HTTPIngressPath httpIngressPath) {
            return path.equals(httpIngressPath.getPath());
        }
    }

    class SecretKeyFinder {

        private final String key;

        public SecretKeyFinder(String key) {
            this.key = key;
        }

        public String on(Secret secret) {
            String stringValue = null;
            if (secret.getStringData() != null && secret.getStringData().containsKey(key)) {
                stringValue = secret.getStringData().get(key);
            } else if (secret.getData() != null) {
                stringValue = secret.getData().get(key);
            }
            return stringValue;
        }

        public String on(ConfigMap secret) {
            String stringValue = null;
            if (secret.getData() != null && secret.getData().containsKey(key)) {
                stringValue = secret.getData().get(key);
            } else if (secret.getData() != null) {
                stringValue = secret.getData().get(key);
            }
            return stringValue;
        }

    }

    class LabelFinder {

        private final String label;

        public LabelFinder(String label) {
            this.label = label;
        }

        public String on(HasMetadata hasMetadata) {
            return hasMetadata.getMetadata().getLabels().get(label);
        }

        public String on(PodTemplateSpec template) {
            return template.getMetadata().getLabels().get(label);
        }
    }
}
