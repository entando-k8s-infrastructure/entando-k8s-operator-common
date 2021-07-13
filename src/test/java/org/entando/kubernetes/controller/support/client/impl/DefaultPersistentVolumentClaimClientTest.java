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

package org.entando.kubernetes.controller.support.client.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import org.entando.kubernetes.controller.spi.client.AbstractSupportK8SIntegrationTest;
import org.entando.kubernetes.fluentspi.TestResource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultPersistentVolumentClaimClientTest extends AbstractSupportK8SIntegrationTest {

    private final TestResource testResource = newTestResource();

    @Test
    void shouldCreatePersistentVolumeClaimIfAbsent() {
        //Given I have an existing PersistentVolumeClaim  with the annotation "test: 123"
        final PersistentVolumeClaim firstPvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withNamespace(testResource.getMetadata().getNamespace())
                .withName("my-pvc")
                .addToAnnotations("test", "123")
                .endMetadata()
                .withNewSpec()
                .withNewResources()
                .addToLimits("storage", new Quantity("1", "Gi"))
                .addToRequests("storage", new Quantity("1", "Gi"))
                .endResources()
                .withAccessModes("ReadWriteOnce")
                .endSpec()
                .build();
        getSimpleK8SClient().persistentVolumeClaims().createPersistentVolumeClaimIfAbsent(testResource, firstPvc);
        //When I attempt to findOrCreate a PersistentVolumeClaim  with the same name but with the annotation "test: 234"
        firstPvc.getMetadata().getAnnotations().put("test", "234");
        getSimpleK8SClient().persistentVolumeClaims().createPersistentVolumeClaimIfAbsent(testResource, firstPvc);
        //Then it has the original PersistentVolumeClaim remains in tact
        final PersistentVolumeClaim secondPvc = getSimpleK8SClient().persistentVolumeClaims()
                .loadPersistentVolumeClaim(testResource, "my-pvc");
        assertThat(secondPvc.getMetadata().getAnnotations().get("test"), is("123"));
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE_1};
    }

}
