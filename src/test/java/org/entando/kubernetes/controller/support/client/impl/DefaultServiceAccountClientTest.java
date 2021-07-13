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

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import org.entando.kubernetes.controller.spi.client.AbstractSupportK8SIntegrationTest;
import org.entando.kubernetes.fluentspi.TestResource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultServiceAccountClientTest extends AbstractSupportK8SIntegrationTest {

    private final TestResource testResource = newTestResource();

    @Test
    void shouldFindPreviouslyCreatedServiceAccount() {
        //Given I have an existing serviceAccount with the annotation "test: 123"
        getSimpleK8SClient().serviceAccounts().findOrCreateServiceAccount(testResource, "my-serviceaccount")
                .editMetadata()
                .addToAnnotations("test", "123")
                .endMetadata()
                .done();
        //When I attempt to findOrCreate a service account with the same name
        final ServiceAccount done = getSimpleK8SClient().serviceAccounts().findOrCreateServiceAccount(testResource, "my-serviceaccount")
                .done();
        //Then it has the previously created annotation
        assertThat(done.getMetadata().getAnnotations().get("test"), is("123"));
    }

    @Test
    void shouldNotReplacePreviouslyCreatedRole() {
        //Given I have an existing Role with the annotation "created: first"
        getSimpleK8SClient().serviceAccounts().createRoleIfAbsent(testResource, new RoleBuilder()
                .editOrNewMetadata()
                .withNamespace(testResource.getMetadata().getNamespace())
                .withName("my-role")
                .addToAnnotations("created", "first")
                .endMetadata().build());
        //When I attempt to create a new role with the same name, but with the annotation "created: second"
        getSimpleK8SClient().serviceAccounts().createRoleIfAbsent(testResource, new RoleBuilder()
                .editOrNewMetadata()
                .withName("my-role")
                .withNamespace(testResource.getMetadata().getNamespace())
                .addToAnnotations("created", "second")
                .endMetadata().build());
        //Then it has left the previous role unchanged
        assertThat(getSimpleK8SClient().serviceAccounts().loadRole(testResource, "my-role").getMetadata().getAnnotations().get("created"),
                is("first"));
    }

    @Test
    void shouldNotReplacePreviouslyCreatedRoleBinding() {
        //Given I have a service account and a role
        getSimpleK8SClient().serviceAccounts().findOrCreateServiceAccount(testResource, "my-serviceaccount")
                .done();
        getSimpleK8SClient().serviceAccounts().createRoleIfAbsent(testResource, new RoleBuilder()
                .editOrNewMetadata()
                .withName("my-role")
                .withNamespace(testResource.getMetadata().getNamespace())
                .endMetadata().build());

        //And I have an existing Rolebinding with the annotation "created: first"
        getSimpleK8SClient().serviceAccounts().createRoleBindingIfAbsent(testResource, new RoleBindingBuilder()
                .editOrNewMetadata()
                .withNamespace(testResource.getMetadata().getNamespace())
                .withName("my-role-binding")
                .addToAnnotations("created", "first")
                .endMetadata()
                .withNewRoleRef()
                .withKind("Role")
                .withName("my-role")
                .endRoleRef()
                .addNewSubject()
                .withName("my-serviceaccount")
                .withKind("ServiceAccount")
                .withNamespace(testResource.getMetadata().getNamespace())
                .endSubject()
                .build());
        //When I attempt to create a new rolebinding with the same name, but with the annotation "created: second"
        getSimpleK8SClient().serviceAccounts().createRoleBindingIfAbsent(testResource, new RoleBindingBuilder()
                .editOrNewMetadata()
                .withName("my-role-binding")
                .addToAnnotations("created", "second")
                .endMetadata()
                .withNewRoleRef()
                .withKind("Role")
                .withName("my-role")
                .endRoleRef()
                .addNewSubject()
                .withName("my-serviceaccount")
                .withKind("ServiceAccount")
                .withNamespace(testResource.getMetadata().getNamespace())
                .endSubject()
                .build());
        //Then it has left the previous role unchanged
        assertThat(getSimpleK8SClient().serviceAccounts().loadRoleBinding(testResource, "my-role-binding").getMetadata().getAnnotations()
                        .get("created"),
                is("first"));
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE_1};
    }
}
