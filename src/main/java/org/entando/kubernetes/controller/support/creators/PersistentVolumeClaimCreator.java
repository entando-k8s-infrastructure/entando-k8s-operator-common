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

package org.entando.kubernetes.controller.support.creators;

import static java.util.Collections.singletonMap;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.support.client.PersistentVolumeClaimClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class PersistentVolumeClaimCreator extends AbstractK8SResourceCreator {

    private List<PersistentVolumeClaim> persistentVolumeClaims;

    public PersistentVolumeClaimCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public boolean needsPersistentVolumeClaims(Deployable<?> deployable) {
        return deployable.getContainers().stream()
                .anyMatch(PersistentVolumeAwareContainer.class::isInstance);
    }

    public void createPersistentVolumeClaimsFor(PersistentVolumeClaimClient k8sClient, Deployable<?> deployable) {
        this.persistentVolumeClaims = deployable.getContainers().stream()
                .filter(PersistentVolumeAwareContainer.class::isInstance)
                .map(PersistentVolumeAwareContainer.class::cast)
                .map(deployableContainer -> createPersistentVolumClaimIfAbsent(k8sClient, deployable, deployableContainer))
                .collect(Collectors.toList());

    }

    private PersistentVolumeClaim createPersistentVolumClaimIfAbsent(PersistentVolumeClaimClient k8sClient, Deployable<?> deployable,
            PersistentVolumeAwareContainer deployableContainer) {
        final PersistentVolumeClaim persistentVolumeClaim = newPersistentVolumeClaim(deployable, deployableContainer);
        return withDiagnostics(
                () -> k8sClient.createPersistentVolumeClaimIfAbsent(entandoCustomResource, persistentVolumeClaim),
                () -> persistentVolumeClaim);
    }

    public List<PersistentVolumeClaim> reloadPersistentVolumeClaims(PersistentVolumeClaimClient k8sClient) {
        return Optional.ofNullable(persistentVolumeClaims).orElse(Collections.emptyList()).stream()
                .map(persistentVolumeClaim -> k8sClient.loadPersistentVolumeClaim(entandoCustomResource,
                        persistentVolumeClaim.getMetadata().getName()))
                .collect(Collectors.toList());
    }

    private PersistentVolumeClaim newPersistentVolumeClaim(Deployable<?> deployable, PersistentVolumeAwareContainer container) {
        StorageCalculator resourceCalculator = buildStorageCalculator(container);
        return new PersistentVolumeClaimBuilder()
                .withMetadata(fromCustomResource(!EntandoOperatorConfig.disablePvcGarbageCollection(),
                        NameUtils.standardPersistentVolumeClaim(entandoCustomResource, container.getNameQualifier()),
                        deployable.getQualifier().orElse(null)))
                .withNewSpec().withAccessModes(container.getAccessMode().orElse("ReadWriteOnce"))
                .withStorageClassName(container.getStorageClass().orElse(null))
                .withNewResources()
                .withRequests(singletonMap("storage", new Quantity(resourceCalculator.getStorageRequest())))
                .withLimits(singletonMap("storage", new Quantity(resourceCalculator.getStorageLimit())))
                .endResources().endSpec()
                .build();
    }

    private StorageCalculator buildStorageCalculator(PersistentVolumeAwareContainer deployableContainer) {
        return deployableContainer instanceof ConfigurableResourceContainer
                ? new ConfigurableStorageCalculator((ConfigurableResourceContainer) deployableContainer)
                : new StorageCalculator(deployableContainer);

    }

}
