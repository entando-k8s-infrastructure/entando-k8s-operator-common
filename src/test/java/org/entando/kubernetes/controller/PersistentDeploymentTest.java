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

package org.entando.kubernetes.controller;

import static io.qameta.allure.Allure.step;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpec;
import org.entando.kubernetes.fluentspi.ControllerFluent;
import org.entando.kubernetes.fluentspi.DeployableFluent;
import org.entando.kubernetes.fluentspi.PersistentContainerFluent;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.SourceLink;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("inner-hexagon"), @Tag("in-process"), @Tag("allure"), @Tag("pre-deployment")})
@Feature("As a controller developer, I would like to mount persistent storage in the deployed container so that I can store files that "
        + "persist beyond pod restarts")
@Issue("ENG-2284")
@SourceLink("PersistentDeploymentTest.java")
class PersistentDeploymentTest extends ControllerTestBase implements FluentTraversals, VariableReferenceAssertions {

    /*
    Classes to be implemented by the controller provider
     */
    @CommandLine.Command()
    public static class BasicController extends ControllerFluent<BasicController> {

        public BasicController(KubernetesClientForControllers k8sClient,
                DeploymentProcessor deploymentProcessor) {
            super(k8sClient, deploymentProcessor);
        }
    }

    public static class BasicDeployable extends DeployableFluent<BasicDeployable> {

    }

    public static class PersistentContainer extends PersistentContainerFluent<PersistentContainer> {

    }

    private BasicDeployable deployable;

    @BeforeEach
    @AfterEach
    void resetSystemPropertiesUsed() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_REQUIRES_FILESYSTEM_GROUP_OVERRIDE.getJvmSystemProperty());
    }

    @Override
    public Runnable createController(
            KubernetesClientForControllers kubernetesClientForControllers,
            DeploymentProcessor deploymentProcessor, CapabilityProvider capabilityProvider) {
        return new BasicController(kubernetesClientForControllers, deploymentProcessor)
                .withDeployable(deployable)
                .withSupportedClass(TestResource.class);
    }

    @Test
    @Description("Should create and mount PersistentVolumeClaims using the correct accessMode, storageClass and filesystem user/group "
            + "overrides")
    void createPersistentVolumeClaimWithGivenConfigs() {
        step("Given I have a basic Deployable ", () -> this.deployable = new BasicDeployable());
        step("And I have configured the Entando Operator to enforce the filesystem user/group override for all persistent volumes mounted "
                        + "from the pod as required by several providers",
                () -> System.setProperty(EntandoOperatorConfigProperty.ENTANDO_REQUIRES_FILESYSTEM_GROUP_OVERRIDE
                        .getJvmSystemProperty(), "true"));
        step("And the Deployable specifies its filesystem user/group override as the user/group id 1001",
                () -> this.deployable.withFileSystemUserAndGroupId(1001L));
        step("And a PersistentVolumeAware DeployableContainer qualified as 'server' that uses the image test/my-image:6.3.2 and exports "
                        + "port 8081",
                () -> {
                    deployable.withContainer(
                            new PersistentContainer().withDockerImageInfo("test/my-image:6.3.2").withNameQualifier("server")
                                    .withPrimaryPort(8081));
                    attachSpiResource("DeployableContainer", SerializationHelper.serialize(deployable.getContainers().get(0)));
                });
        step("And the DeployableContainer specifies  the following PersistentVolume related configuration:", () -> {
            PersistentContainer container = (PersistentContainer) deployable.getContainers().get(0);
            step("a storage limit of 500Mi", () -> container.withStorageLimitMebibytes(500));
            step("the storage class 'gluster'", () -> container.withStorageClass("gluster"));
            step("the access mode 'ReadWriteMany'", () -> container.withAccessMode("ReadWriteMany"));
            step("and the mount path '/my-data'", () -> container.withVolumeMountPath("/my-data"));
        });

        final EntandoCustomResource entandoCustomResource = new TestResource()
                .withNames(MY_NAMESPACE, MY_APP)
                .withSpec(new BasicDeploymentSpec());
        step("When the controller processes a new TestResource", () -> {
            runControllerAgainstCustomResource(entandoCustomResource);
        });
        step(format("Then a Deployment was created reflecting the name of the TestResource and the suffix '%s'",
                NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                () -> {
                    final Deployment deployment = getClient().deployments()
                            .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
                    attachKubernetesResource("Deployment", deployment);
                    assertThat(deployment).isNotNull();
                    step("and it has a single container with a name reflecting the qualifier 'server'", () -> {
                        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().size()).isEqualTo(1);
                        assertThat(thePrimaryContainerOn(deployment).getName()).isEqualTo("server-container");
                    });
                    step("and it specifies the volume 'my-app-server-volume', reflecting the qualifier 'server' mapping to the "
                                    + "PersistentVolumeClaim",
                            () -> {
                                assertThat(theVolumeNamed("my-app-server-volume").on(deployment).getPersistentVolumeClaim().getClaimName())
                                        .isEqualTo("my-app-server-pvc");
                            });
                    step("and it enforces the filesystem user/group override of 1001", () -> {
                        assertThat(deployment.getSpec().getTemplate().getSpec().getSecurityContext().getFsGroup()).isEqualTo(1001L);
                    });
                    step("and the volume is mounted at /my-data in the files system  ", () -> {
                        assertThat(theVolumeMountNamed("my-app-server-volume").on(thePrimaryContainerOn(deployment)).getMountPath())
                                .isEqualTo("/my-data");
                    });
                });
        step(format(
                "And a PersistentVolumeClaim was created reflecting the name of the TestResource, the container qualifier 'server' and "
                        + "the suffix '%s'", NameUtils.DEFAULT_PVC_SUFFIX),
                () -> {
                    final PersistentVolumeClaim persistentVolumeClaim = getClient().persistentVolumeClaims()
                            .loadPersistentVolumeClaim(entandoCustomResource,
                                    NameUtils.standardPersistentVolumeClaim(entandoCustomResource, "server"));
                    attachKubernetesResource("PersistentVolumeClaim", persistentVolumeClaim);
                    assertThat(persistentVolumeClaim).isNotNull();
                    step("and it specifies the accessMode 'ReadWriteMany'", () ->
                            assertThat(persistentVolumeClaim.getSpec().getAccessModes()).contains("ReadWriteMany"));
                    step("and the storageClass 'gluster'", () ->
                            assertThat(persistentVolumeClaim.getSpec().getStorageClassName()).contains("gluster"));
                });

        attachKubernetesState();
    }

    @Test
    @Disabled("TODO")
    @Description("Should create and mount PersistentVolumeClaims using defaults derived from the Entando Operator config properties")
    void createPersistentVolumeClaimUsingDefaults() {
        fail("Not implemented");
    }
}
