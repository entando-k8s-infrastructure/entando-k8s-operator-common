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
import static java.util.Optional.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.Collection;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.common.EntandoDeploymentSpec;

public interface VolumeMatchAssertions {

    default <S extends EntandoDeploymentSpec> void verifyThatAllVolumesAreMapped(
            EntandoBaseCustomResource<S, EntandoCustomResourceStatus> resource,
            SimpleK8SClient client,
            Deployment deployment) {
        PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
        podSpec.getContainers().stream()
                .map(Container::getVolumeMounts).flatMap(Collection::stream)
                .forEach(volumeMount -> assertMatchingVolume(podSpec, volumeMount));
        podSpec.getVolumes().forEach(volume -> assertMatchingClaim(resource, client, volume));
    }

    default <S extends EntandoDeploymentSpec> void assertMatchingClaim(EntandoBaseCustomResource<S, EntandoCustomResourceStatus> resource,
            SimpleK8SClient client,
            Volume volume) {
        if (ofNullable(volume.getPersistentVolumeClaim()).isPresent()) {
            assertThat(
                    format("The volume %s does not have the PVC %s", volume.getName(),
                            volume.getPersistentVolumeClaim().getClaimName()),
                    client.persistentVolumeClaims().loadPersistentVolumeClaim(resource, volume.getPersistentVolumeClaim().getClaimName()),
                    notNullValue());
        } else if (ofNullable(volume.getSecret()).isPresent()) {
            assertThat(
                    format("The volume %s does not have the Secret %s", volume.getName(),
                            volume.getSecret().getSecretName()),
                    client.secrets().loadSecret(resource, volume.getSecret().getSecretName()),
                    notNullValue());
        }
    }

    default void assertMatchingVolume(PodSpec podSpec, VolumeMount volumeMount) {
        assertTrue(podSpec.getVolumes().stream().anyMatch(volume -> volumeMount.getName().equals(volume.getName())),
                format("VolumeMount %s has no Volume", volumeMount.getName()));
    }

}
