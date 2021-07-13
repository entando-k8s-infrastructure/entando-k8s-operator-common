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

package org.entando.kubernetes.controller.support.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.container.DockerImageInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class EntandoImageResolverTest {

    private ConcurrentHashMap<Object, Object> storedProps = new ConcurrentHashMap<>();

    @BeforeEach
    void backupSystemProperties() {
        storedProps = new ConcurrentHashMap<>(System.getProperties());
    }

    @AfterEach
    void restoreSystemProperties() {
        Stream.of(EntandoOperatorConfigProperty.values()).forEach(p -> {
            System.getProperties().remove(p.getJvmSystemProperty());
        });
        System.getProperties().putAll(storedProps);
    }

    @Test
    void testResolutionFromFallbackProperties() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "6.1.4");
        //And there is no information in the ConfigMap
        EntandoImageResolver entandoImageResolver = new EntandoImageResolver(null);
        //when I resolve an image
        String imageUri = entandoImageResolver.determineImageUri("test-image");
        //then it reflects the default properties
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"related.image.test-image", "RELATED_IMAGE_TEST_IMAGE", "related.image.test-entando.test-image",
            "RELATED_IMAGE_TEST_ENTANDO_TEST_IMAGE"})
    void testResolutionFromRelatedImageProperty(String variableName) {
        try {
            System.clearProperty(variableName);
            //Given I have set overriding properties  for image resolution
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE.getJvmSystemProperty(), "test.io");
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE.getJvmSystemProperty(), "test-entando");
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE.getJvmSystemProperty(), "6.1.4");
            //And I have information in the Configmap
            ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                    .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                    .addToData("test-entando-test-image",
                            "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                    .build();
            //But I have an environment variable or property injected for the image in question
            System.setProperty(variableName,
                    "openshift/wildfly-101-centos7@sha256:7775d40f77e22897dc760b76f1656f67ef6bd5561b4d74fbb030b977f61d48e8");

            //when I resolve the image
            String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("test-entando/test-image");
            //then it reflects the injected value
            assertThat(imageUri,
                    is("openshift/wildfly-101-centos7@sha256:7775d40f77e22897dc760b76f1656f67ef6bd5561b4d74fbb030b977f61d48e8"));
        } finally {
            System.clearProperty(variableName);
        }
    }

    @Test
    void testResolutionFromCofigmapWithRepositoryKey() {
        //Given I have information in the Configmap against a key reflecting the repository only
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();

        //when I resolve the image
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("test-entando/test-image");
        //then it reflects the injected value
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromAnnotations() {
        //Given I have information in the Configmap against a key reflecting the repository and org
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.3\"}")
                .build();
        //But I have an annotation that overrides the image by an org-aware repository identifier
        final SerializedEntandoResource resourceOfInterest = new SerializedEntandoResource();
        resourceOfInterest.setMetadata(new ObjectMetaBuilder()
                .addToAnnotations(EntandoImageResolver.IMAGE_OVERRIDE_ANNOTATION_PREFIX + "test-entando-test-image",
                        "test.io/test-entando/test-image:6.1.0-SNAPSHOT")
                .build());
        //when I resolve the image
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap, resourceOfInterest).determineImageUri("test-entando/test-image");
        //then it reflects the value from teh annotation
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.0-SNAPSHOT"));
    }

    @Test
    void testResolutionFromCofigmapWithOrganizationAwareRepositoryKey() {
        //Given I have information in the Configmap against a key reflecting the repository only
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-entando-test-image",
                        "{\"registry\":\"test.io\",\"organization\":\"not-test-entando\",\"version\":\"6.1.4\"}")
                .build();

        //when I resolve the image
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("test-entando/test-image");
        //then it reflects the injected value with the organization found in the configMap
        assertThat(imageUri,
                is("test.io/not-test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromCofigmapWithOrganizationAwareRepositoryKeyAndPort() {
        //Given I have information in the Configmap against a key reflecting the repository only
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-entando-test-image",
                        "{\"registry\":\"test.io:5000\",\"organization\":\"not-test-entando\",\"version\":\"6.1.4\"}")
                .build();

        //when I resolve the image
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("ampie.io:5001/test-entando/test-image");
        //then it reflects the injected value with the values in the configMap
        assertThat(imageUri,
                is("test.io:5000/not-test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromCofigmapWithMultilevelOrganization() {
        //Given I have information in the Configmap against a key reflecting the repository only
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("staging-test-entando-test-image",
                        "{\"registry\":\"test.io\",\"organization\":\"not-staging-test-entando\",\"version\":\"6.1.4\"}")
                .build();

        //when I resolve the image
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("ampie.io/staging/test-entando/test-image");
        //then it reflects the injected value with the values in the configMap
        assertThat(imageUri,
                is("test.io/not-staging-test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromOverridingPropertiesWhenThereAreFallbackProperties() {
        //Given I have set fallback properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "default");
        //And I override these properties with their "OVERRIDE" equivalents
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE.getJvmSystemProperty(), "6.1.4");
        //when I resolve an image
        String imageUri = new EntandoImageResolver(null).determineImageUri("entando/test-image");
        //then it reflects the overriding property values
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromDefaultPropertiesWhenThereIsAConfigMap() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "default");
        //And I have a configMap
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        //when I resolve an image
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("entando/test-image");
        //then it reflects the property values from the configmap
        assertThat(imageUri, is("test.io/test-entando/test-image:6.1.4"));
    }

    @Test
    void testResolutionFromOverridingPropertiesWhenThereIsAConfigMap() {
        //Given I have set fallback properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "default.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "default-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "default");
        //And I override these properties with their "OVERRIDE" equivalents
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE.getJvmSystemProperty(), "overridden.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE.getJvmSystemProperty(),
                "overridden-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE.getJvmSystemProperty(), "overridden");
        //And I have a configMap
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        //when I resolve an image
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("entando/test-image");
        //then it reflects the overriding property values
        assertThat(imageUri, is("overridden.io/overridden-entando/test-image:overridden"));
    }

    @Test
    void testVersionResolutionWithProvidedValue() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "default");
        //And I have a configMap
        ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                .build();
        //when I resolve an imageURi with the version already provided
        String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri("test.io/org/test-image:6.3.0-SNAPSHOT");
        //then it reflects the configmap values for everything except for the version, which reflects the provided version
        assertThat(imageUri, is("test.io/test-entando/test-image:6.3.0-SNAPSHOT"));
    }

    @Test
    void testResolutionIgnoredForNonEntandoImages() {
        //Given I have set default properties  for image resolution
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_FALLBACK.getJvmSystemProperty(), "test.io");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_FALLBACK.getJvmSystemProperty(), "test-entando");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK.getJvmSystemProperty(), "6.1.4");
        //when I resolve an image
        String imageUri = new EntandoImageResolver(null).determineImageUri("test.io/not-entando/test-image:1");
        //then it reflects the default properties
        assertThat(imageUri, is("test.io/not-entando/test-image:1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"docker.io:50:50/library/mysql", "docker.io:50/library/mysql:5:3", "docker.io:50/lib/ra/ry/mysql"})
    void testInvalidImageUris(String invalidImageUri) {
        EntandoImageResolver imageResolver = new EntandoImageResolver(null);
        assertThrows(IllegalArgumentException.class, () -> imageResolver.determineImageUri(invalidImageUri));
    }

    @Test
    void testBug() {
        try {
            System.clearProperty("RELATED_IMAGE_RHEL8_POSTGRESQL_12");
            //Given I have set overriding properties  for image resolution
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_REGISTRY_OVERRIDE.getJvmSystemProperty(), "test.io");
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE.getJvmSystemProperty(), "test-entando");
            System.setProperty(EntandoOperatorConfigProperty.ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE.getJvmSystemProperty(), "6.1.4");
            //And I have information in the Configmap
            ConfigMap imageVersionsConfigMap = new ConfigMapBuilder()
                    .addToData("test-image", "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                    .addToData("test-entando-test-image",
                            "{\"registry\":\"test.io\",\"organization\":\"test-entando\",\"version\":\"6.1.4\"}")
                    .build();
            //But I have an environment variable or property injected for the image in question
            System.setProperty("RELATED_IMAGE_RHEL8_POSTGRESQL_12",
                    "openshift/wildfly-101-centos7@sha256:7775d40f77e22897dc760b76f1656f67ef6bd5561b4d74fbb030b977f61d48e8");

            //when I resolve the image
            String imageUri = new EntandoImageResolver(imageVersionsConfigMap).determineImageUri(new DockerImageInfo(
                    DbmsDockerVendorStrategy.RHEL_POSTGRESQL));
            //then it reflects the injected value
            assertThat(imageUri,
                    is("openshift/wildfly-101-centos7@sha256:7775d40f77e22897dc760b76f1656f67ef6bd5561b4d74fbb030b977f61d48e8"));
        } finally {
            System.clearProperty("RELATED_IMAGE_RHEL8_POSTGRESQL_12");
        }
    }
}
