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

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.KubernetesPermission;
import org.entando.kubernetes.controller.spi.examples.SampleDeployableContainer;
import org.entando.kubernetes.controller.spi.examples.SamplePublicIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.model.common.EntandoBaseCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class ServiceAcountCreatorTest implements InProcessTestData {

    private static final String MY_SERVICE_ACCOUNT = "my-service-account";
    private static final String MY_IMAGE_PULL_SECRET = "my-image-pull-secret";
    SimpleK8SClient<?> client = new SimpleK8SClientDouble();
    EntandoApp entandoApp = new EntandoAppBuilder(newTestEntandoApp())
            .editSpec()
            .withServiceAccountToUse(MY_SERVICE_ACCOUNT)
            .endSpec()
            .build();

    @AfterEach
    @BeforeEach
    void cleanUp() {
        System.getProperties()
                .remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS.getJvmSystemProperty());
        System.getProperties()
                .remove(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty());
    }

    @Test
    void testImagePullSecrets() {
        //Given that the operator was configured with custom ImagePullSecrets
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS.getJvmSystemProperty(),
                MY_IMAGE_PULL_SECRET);
        SamplePublicIngressingDbAwareDeployable<EntandoAppSpec> deployable = new SamplePublicIngressingDbAwareDeployable<>(entandoApp, null,
                emulateKeycloakDeployment(client));
        //When the operator prepares the service account
        new ServiceAccountCreator(entandoApp).prepareServiceAccountAccess(client.serviceAccounts(), deployable);
        //then the custom image pull secret must be propagated to the new serviceAccount
        assertThat(client.serviceAccounts().findOrCreateServiceAccount(entandoApp, MY_SERVICE_ACCOUNT).buildImagePullSecrets().get(0)
                .getName(), is(MY_IMAGE_PULL_SECRET));
    }

    @Test
    void testStandardClusterRoles() {
        System.getProperties()
                .put(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty(), "*");
        //When the operator prepares the service account
        SamplePublicIngressingDbAwareDeployable<EntandoAppSpec> deployable = new SamplePublicIngressingDbAwareDeployable<>(entandoApp, null,
                emulateKeycloakDeployment(client));
        new ServiceAccountCreator(entandoApp).prepareServiceAccountAccess(client.serviceAccounts(), deployable);
        //then RoleBindings must exist for the standard cluster roles
        RoleBinding entandoEditorRoleBinding = client.serviceAccounts().loadRoleBinding(entandoApp, MY_SERVICE_ACCOUNT + "-entando-editor");
        assertThat(entandoEditorRoleBinding.getSubjects().get(0).getName(), is(MY_SERVICE_ACCOUNT));
        assertThat(entandoEditorRoleBinding.getSubjects().get(0).getNamespace(), is(MY_APP_NAMESPACE));
        assertThat(entandoEditorRoleBinding.getRoleRef().getKind(), is("ClusterRole"));
        assertThat(entandoEditorRoleBinding.getRoleRef().getName(), is(EntandoRbacRole.ENTANDO_EDITOR.getK8sName()));
        RoleBinding podViewerRoleBinding = client.serviceAccounts().loadRoleBinding(entandoApp, MY_SERVICE_ACCOUNT + "-pod-viewer");
        assertThat(podViewerRoleBinding.getSubjects().get(0).getName(), is(MY_SERVICE_ACCOUNT));
        assertThat(podViewerRoleBinding.getSubjects().get(0).getNamespace(), is(MY_APP_NAMESPACE));
        assertThat(podViewerRoleBinding.getRoleRef().getKind(), is("ClusterRole"));
        assertThat(podViewerRoleBinding.getRoleRef().getName(), is(EntandoRbacRole.POD_VIEWER.getK8sName()));
    }

    @Test
    void testCustomPermissions() {
        //Given that I have a container that requires delete permissions on Openshift routes
        SamplePublicIngressingDbAwareDeployable<EntandoAppSpec> deployable = new SamplePublicIngressingDbAwareDeployable<EntandoAppSpec>(
                entandoApp, null,
                emulateKeycloakDeployment(client)) {

            @Override
            protected List<DeployableContainer> createContainers(
                    EntandoBaseCustomResource<EntandoAppSpec, EntandoCustomResourceStatus> entandoResource) {
                return Arrays.asList(new SampleDeployableContainer<EntandoAppSpec>(entandoResource, databaseConnectionInfo) {
                    @Override
                    public List<KubernetesPermission> getKubernetesPermissions() {
                        return Arrays.asList(new KubernetesPermission("route.openshift.io", "routes", "delete"));
                    }
                });
            }
        };
        //When the operator prepares the service account
        new ServiceAccountCreator(entandoApp).prepareServiceAccountAccess(client.serviceAccounts(), deployable);
        //The there must be a role with the required permission that has the same name as the ServiceAccount
        Role role = client.serviceAccounts().loadRole(entandoApp, MY_SERVICE_ACCOUNT);
        assertThat(role.getRules().get(0).getApiGroups(), hasItem("route.openshift.io"));
        assertThat(role.getRules().get(0).getVerbs(), hasItem("delete"));
        assertThat(role.getRules().get(0).getResources(), hasItem("routes"));
        //and there must be a RoleBinding for this new role
        RoleBinding roleBinding = client.serviceAccounts().loadRoleBinding(entandoApp, MY_SERVICE_ACCOUNT + "-rolebinding");
        assertThat(roleBinding.getSubjects().get(0).getName(), is(MY_SERVICE_ACCOUNT));
        assertThat(roleBinding.getSubjects().get(0).getNamespace(), is(MY_APP_NAMESPACE));
        assertThat(roleBinding.getRoleRef().getKind(), is("Role"));
        assertThat(roleBinding.getRoleRef().getName(), is(MY_SERVICE_ACCOUNT));
    }
}
