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

package org.entando.kubernetes.test.componenttest;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.client.PodWatcher;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer;
import org.entando.kubernetes.controller.spi.container.TrustStoreAware;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.spi.examples.SampleController;
import org.entando.kubernetes.controller.spi.examples.SampleExposedDeploymentResult;
import org.entando.kubernetes.controller.spi.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.controller.spi.examples.springboot.SpringBootDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.client.PodWaitingClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.controller.support.creators.DeploymentCreator;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.EntandoBaseCustomResource;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.EntandoPluginSpec;
import org.entando.kubernetes.test.common.CertificateSecretHelper;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.PodBehavior;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("java:S5786")
public abstract class SpringBootContainerTestBase implements InProcessTestUtil, FluentTraversals, PodBehavior, VariableReferenceAssertions,
        CommonLabels {

    public static final String SAMPLE_NAMESPACE = "sample-namespace";
    public static final String SAMPLE_NAME = "sample-name";
    public static final String SAMPLE_NAME_DB = NameUtils.snakeCaseOf(SAMPLE_NAME + "_db");
    EntandoPlugin plugin1 = buildPlugin(SAMPLE_NAMESPACE, SAMPLE_NAME);
    private SampleController<EntandoPluginSpec, EntandoPlugin, SampleExposedDeploymentResult> controller;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    @BeforeEach
    public void enableQueueing() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(true);
    }

    @AfterEach
    public void shutDown() {
        PodWaitingClient.ENQUEUE_POD_WATCH_HOLDERS.set(false);
        scheduler.shutdownNow();
        getClient().pods().getPodWatcherQueue().clear();
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_CA_SECRET_NAME.getJvmSystemProperty());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_TLS_SECRET_NAME.getJvmSystemProperty());
    }

    @Test
    void testBasicDeployment() {
        //Given I have a controller that processes EntandoPlugins
        controller = new SampleController<>(getClient(), getKeycloakClient()) {
            @Override
            protected Deployable<SampleExposedDeploymentResult> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SpringBootDeployable<>(newEntandoPlugin, keycloakConnectionConfig, databaseServiceResult);
            }
        };
        //And I have prepared the Standard KeycloakAdminSecert
        emulateKeycloakDeployment(getClient());
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin1, plugin1.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin1);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                getClient().entandoResources()
                        .load(plugin1.getClass(), plugin1.getMetadata().getNamespace(), plugin1.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect a server deployment
        Deployment serverDeployment = getClient().deployments()
                .loadDeployment(plugin1, SAMPLE_NAME + "-" + NameUtils.DEFAULT_SERVER_QUALIFIER + "-deployment");
        assertThat(serverDeployment.getSpec().getTemplate().getSpec().getContainers().size(), Matchers.is(1));
        verifyThatAllVariablesAreMapped(plugin1, getClient(), serverDeployment);
        verifyThatAllVolumesAreMapped(plugin1, getClient(), serverDeployment);
        verifySpringDatasource(serverDeployment);
        verifySpringSecuritySettings(thePrimaryContainerOn(serverDeployment), SAMPLE_NAME + "-server-secret");
        //Then  a db deployment
        Deployment dbDeployment = getClient().deployments().loadDeployment(plugin1, SAMPLE_NAME + "-db-deployment");
        assertThat(dbDeployment.getSpec().getTemplate().getSpec().getContainers().size(), is(1));

        //And I an ingress paths
        Ingress ingress = getClient().ingresses().loadIngress(plugin1.getMetadata().getNamespace(),
                ((EntandoCustomResource) plugin1).getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX);
        assertThat(theHttpPath(SampleSpringBootDeployableContainer.MY_WEB_CONTEXT).on(ingress).getBackend().getServicePort().getIntVal(),
                Matchers.is(8084));
    }

    @Test
    void testDeploymentWithCertificates() {
        //Given I have a controller that processes EntandoPlugins
        controller = new SampleController<>(getClient(), getKeycloakClient()) {
            @Override
            protected Deployable<SampleExposedDeploymentResult> createDeployable(EntandoPlugin newEntandoPlugin,
                    DatabaseServiceResult databaseServiceResult,
                    KeycloakConnectionConfig keycloakConnectionConfig) {
                return new SpringBootDeployable<>(newEntandoPlugin, keycloakConnectionConfig, databaseServiceResult);
            }
        };
        //And I have configured an additional trusted CA Certificate and a TLS certificate/key pair
        final Path tlsPath = Paths.get("src", "test", "resources", "tls", "ampie.dynu.net");
        CertificateSecretHelper.buildCertificateSecretsFromDirectory(getClient().entandoResources().getNamespace(), tlsPath)
                .forEach(getClient().secrets()::overwriteControllerSecret);
        //And I have prepared the Standard KeycloakAdminSecert
        emulateKeycloakDeployment(getClient());
        //And we can observe the pod lifecycle
        emulatePodWaitingBehaviour(plugin1, plugin1.getMetadata().getName());
        //When I create a new EntandoPlugin
        onAdd(plugin1);

        await().ignoreExceptions().atMost(2, TimeUnit.MINUTES).until(() ->
                getClient().entandoResources()
                        .load(plugin1.getClass(), plugin1.getMetadata().getNamespace(), plugin1.getMetadata().getName())
                        .getStatus()
                        .getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL);
        //Then I expect a server deployment
        Deployment serverDeployment = getClient().deployments()
                .loadDeployment(plugin1, SAMPLE_NAME + "-" + NameUtils.DEFAULT_SERVER_QUALIFIER + "-deployment");
        assertThat(serverDeployment.getSpec().getTemplate().getSpec().getContainers().size(), Matchers.is(1));
        verifyThatAllVariablesAreMapped(plugin1, getClient(), serverDeployment);
        verifyThatAllVolumesAreMapped(plugin1, getClient(), serverDeployment);
        //With a secret mount to the default truststore secret
        assertThat(
                theVolumeNamed(TrustStoreAware.DEFAULT_TRUSTSTORE_SECRET + DeploymentCreator.VOLUME_SUFFIX).on(serverDeployment).getSecret()
                        .getSecretName(),
                is(TrustStoreAware.DEFAULT_TRUSTSTORE_SECRET));
        assertThat(theVolumeMountNamed(TrustStoreAware.DEFAULT_TRUSTSTORE_SECRET + DeploymentCreator.VOLUME_SUFFIX)
                        .on(thePrimaryContainerOn(serverDeployment)).getMountPath(),
                is(TrustStoreAware.CERT_SECRET_MOUNT_ROOT + "/" + TrustStoreAware.DEFAULT_TRUSTSTORE_SECRET));
        assertThat(theVariableReferenceNamed(TrustStoreAware.JAVA_TOOL_OPTIONS).on(thePrimaryContainerOn(serverDeployment))
                .getSecretKeyRef().getName(), is(TrustStoreAware.DEFAULT_TRUSTSTORE_SECRET));
        assertThat(theVariableReferenceNamed(TrustStoreAware.JAVA_TOOL_OPTIONS).on(thePrimaryContainerOn(serverDeployment))
                .getSecretKeyRef().getKey(), is(TrustStoreAware.TRUSTSTORE_SETTINGS_KEY));
        //And I an ingress path
        Ingress ingress = getClient().ingresses().loadIngress(plugin1.getMetadata().getNamespace(),
                ((EntandoCustomResource) plugin1).getMetadata().getName() + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX);
        assertThat(theHttpPath(SampleSpringBootDeployableContainer.MY_WEB_CONTEXT).on(ingress).getBackend().getServicePort().getIntVal(),
                Matchers.is(8084));
        //Exposed over TLS using the previously created TLS secret
        assertThat(ingress.getSpec().getTls().get(0).getHosts().get(0), Matchers.is(ingress.getSpec().getRules().get(0).getHost()));
        assertThat(ingress.getSpec().getTls().get(0).getSecretName(), Matchers.is(CertificateSecretHelper.TEST_TLS_SECRET));

    }

    protected abstract SimpleKeycloakClient getKeycloakClient();

    public void verifySpringDatasource(Deployment serverDeployment) {
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_USERNAME.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(SecretUtils.USERNAME_KEY));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_USERNAME.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(), is(SAMPLE_NAME + "-serverdb-secret"));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_PASSWORD.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getKey(), is(SecretUtils.PASSSWORD_KEY));
        assertThat(theVariableReferenceNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_PASSWORD.name())
                .on(thePrimaryContainerOn(serverDeployment)).getSecretKeyRef().getName(), is(SAMPLE_NAME + "-serverdb-secret"));
        assertThat(theVariableNamed(SpringBootDeployableContainer.SpringProperty.SPRING_DATASOURCE_URL.name())
                .on(thePrimaryContainerOn(serverDeployment)), is(
                "jdbc:postgresql://" + SAMPLE_NAME + "-db-service." + SAMPLE_NAMESPACE + ".svc.cluster.local:5432/" + SAMPLE_NAME_DB));
        assertThat(theVariableNamed("MY_VAR").on(thePrimaryContainerOn(serverDeployment)), is("MY_VAL"));
        assertThat(theVariableNamed("MY_VAR").on(thePrimaryContainerOn(serverDeployment)), is("MY_VAL"));
    }

    private EntandoPlugin buildPlugin(String sampleNamespace, String sampleName) {
        return new EntandoPluginBuilder().withNewMetadata()
                .withNamespace(sampleNamespace)
                .withName(sampleName).endMetadata().withNewSpec()
                .withImage("docker.io/entando/entando-avatar-plugin:6.0.0-SNAPSHOT")
                .addToEnvironmentVariables("MY_VAR", "MY_VAL")
                .withDbms(DbmsVendor.POSTGRESQL).withReplicas(2).withIngressHostName("myhost.name.com")
                .endSpec().build();
    }

    protected final <S extends EntandoDeploymentSpec> void emulatePodWaitingBehaviour(EntandoBaseCustomResource<S> resource,
            String deploymentName) {
        scheduler.schedule(() -> {
            try {
                PodWatcher dbPodWatcher = getClient().pods().getPodWatcherQueue().take();
                Deployment dbDeployment = getClient().deployments().loadDeployment(resource, deploymentName + "-db-deployment");
                dbPodWatcher.eventReceived(Action.MODIFIED, podWithReadyStatus(dbDeployment));
                //Deleting possible existing dbPreprationPods won't require events to be triggered
                getClient().pods().getPodWatcherQueue().take();
                PodWatcher dbPreprationPodWatcher = getClient().pods().getPodWatcherQueue().take();
                Pod dbPreparationPod = getClient().pods()
                        .loadPod(resource.getMetadata().getNamespace(), dbPreparationJobLabels(resource, "server"));
                dbPreprationPodWatcher.eventReceived(Action.MODIFIED, podWithSucceededStatus(dbPreparationPod));
                PodWatcher serverPodWatcher = getClient().pods().getPodWatcherQueue().take();
                Deployment serverDeployment = getClient().deployments().loadDeployment(resource, deploymentName + "-server-deployment");
                serverPodWatcher.eventReceived(Action.MODIFIED, podWithReadyStatus(serverDeployment));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    public <S extends Serializable, T extends EntandoBaseCustomResource<S>> void onAdd(T resource) {
        new Thread(() -> {
            T createResource = getClient().entandoResources().createOrPatchEntandoResource(resource);
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, createResource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, createResource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }).start();
    }

    public <S extends Serializable, T extends EntandoBaseCustomResource<S>> void onDelete(T resource) {
        new Thread(() -> {
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.DELETED.name());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, resource.getMetadata().getNamespace());
            System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, resource.getMetadata().getName());
            controller.onStartup(new StartupEvent());
        }).start();
    }
}
