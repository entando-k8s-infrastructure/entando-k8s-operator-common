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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.spi.examples.SamplePublicIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.support.client.doubles.ClusterDouble;
import org.entando.kubernetes.controller.support.client.doubles.PersistentVolumentClaimClientDouble;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class PersistentVolumeClaimCreatorTest implements InProcessTestData {

    private EntandoApp entandoApp = newTestEntandoApp();
    private SamplePublicIngressingDbAwareDeployable<EntandoAppSpec> deployable = new SamplePublicIngressingDbAwareDeployable<>(
            entandoApp, null, null);

    @AfterEach
    @BeforeEach
    void cleanUp() {
        System.getProperties()
                .remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DISABLE_PVC_GARBAGE_COLLECTION.getJvmSystemProperty());
    }

    @Test
    void createPersistentVolumeClaimWithoutGarbageCollection() {

        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DISABLE_PVC_GARBAGE_COLLECTION.getJvmSystemProperty(),
                "true");

        PersistentVolumeClaim persistentVolumeClaim = executeCreateDeploymentTest();

        assertThat(persistentVolumeClaim.getMetadata().getOwnerReferences().size(), is(0));
    }

    @Test
    void createPersistentVolumeClaimWithGarbageCollection() {

        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DISABLE_PVC_GARBAGE_COLLECTION.getJvmSystemProperty(),
                "false");

        PersistentVolumeClaim persistentVolumeClaim = executeCreateDeploymentTest();

        assertThat(persistentVolumeClaim.getMetadata().getOwnerReferences().size(), is(1));
    }

    /**
     * executes tests of types CreateDeploymentTest.
     *
     * @return the ResourceRequirements of the first container of the resulting Deployment
     */
    private PersistentVolumeClaim executeCreateDeploymentTest() {

        PersistentVolumentClaimClientDouble persistentVolumentClaimClientDouble = new PersistentVolumentClaimClientDouble(
                new ConcurrentHashMap<>(), new ClusterDouble());
        PersistentVolumeClaimCreator persistentVolumeClaimCreator = new PersistentVolumeClaimCreator(entandoApp);

        persistentVolumeClaimCreator.createPersistentVolumeClaimsFor(persistentVolumentClaimClientDouble, deployable);

        return persistentVolumentClaimClientDouble.loadPersistentVolumeClaim(entandoApp, "my-app-server-pvc");
    }
}
