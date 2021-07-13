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

import static io.qameta.allure.Allure.step;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.qameta.allure.Allure;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.capability.SerializingCapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializingDeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SupportedCommand;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer.SpringProperty;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.client.doubles.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.command.InProcessCommandStream;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public interface ControllerTestHelper extends FluentTraversals, CustomResourceStatusEmulator<SimpleK8SClientDouble>,
        VariableReferenceAssertions {

    String DEFAULT_TLS_SECRET = "default-tls-secret";
    String MY_APP = "my-app";
    String MY_NAMESPACE = "my-namespace";
    String ENTANDO_RESOURCE_ACTION = "entando.resource.action";
    String TEST_CONTROLLER_POD = "test-controller-pod";

    default Optional<SimpleKeycloakClient> getKeycloakClient() {
        return java.util.Optional.empty();
    }

    default void runControllerAgainstCapabilityRequirement(EntandoCustomResource forResource, CapabilityRequirement capabilityRequirement)
            throws TimeoutException {
        try {
            attachKubernetesResource("Resource Requesting Capability", forResource);
            getClient().entandoResources().createOrPatchEntandoResource(forResource);
            attachKubernetesResource("Capability Requirement", capabilityRequirement);
            final StandardCapability capability = capabilityRequirement.getCapability();
            doAnswer(invocationOnMock -> {
                getScheduler().schedule(() -> runControllerAgainstCapabilityAndUpdateStatus(invocationOnMock.getArgument(0)), 200L,
                        TimeUnit.MILLISECONDS);
                return invocationOnMock.callRealMethod();
            }).when(getClient().capabilities()).createOrPatchCapability(argThat(matchesCapability(capability)));
            getCapabilityProvider().provideCapability(forResource, capabilityRequirement, 60);
        } catch (RuntimeException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, e, e::getMessage);
            throw e;
        }
    }

    default void runControllerAgainstCustomResource(EntandoCustomResource entandoCustomResource) {
        try {
            attachKubernetesResource(entandoCustomResource.getKind(), entandoCustomResource);
            System.setProperty(ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
            System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAME.getJvmSystemProperty(),
                    entandoCustomResource.getMetadata().getName());
            System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAMESPACE.getJvmSystemProperty(),
                    entandoCustomResource.getMetadata().getNamespace());
            System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_KIND.getJvmSystemProperty(),
                    entandoCustomResource.getKind());
            System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME.getJvmSystemProperty(), TEST_CONTROLLER_POD);
            getClient().entandoResources().createOrPatchEntandoResource(entandoCustomResource);
            final SimpleKeycloakClient keycloakClient = this.getKeycloakClient().orElse(null);
            final AllureAttachingCommandStream commandStream = new AllureAttachingCommandStream(getClient(), keycloakClient);
            Runnable controller = createController(getClient().entandoResources(),
                    new SerializingDeploymentProcessor(getClient().entandoResources(), commandStream), getCapabilityProvider());
            controller.run();
        } catch (RuntimeException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, e, e::getMessage);
            throw e;
        }
    }

    default SerializingCapabilityProvider getCapabilityProvider() {
        return new SerializingCapabilityProvider(getClient().entandoResources(), new AllureAttachingCommandStream(getClient(),
                getKeycloakClient().orElse(null)));
    }

    private void runControllerAgainstCapabilityAndUpdateStatus(ProvidedCapability providedCapability) {
        System.setProperty(ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAME.getJvmSystemProperty(),
                providedCapability.getMetadata().getName());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAMESPACE.getJvmSystemProperty(),
                providedCapability.getMetadata().getNamespace());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_KIND.getJvmSystemProperty(), providedCapability.getKind());
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME.getJvmSystemProperty(), TEST_CONTROLLER_POD);
        final SimpleKeycloakClient keycloakClient = this.getKeycloakClient().orElse(null);
        final AllureAttachingCommandStream commandStream = new AllureAttachingCommandStream(getClient(), keycloakClient);
        Runnable controller = createController(getClient().entandoResources(),
                new SerializingDeploymentProcessor(getClient().entandoResources(), commandStream), getCapabilityProvider());
        controller.run();
    }

    Runnable createController(KubernetesClientForControllers kubernetesClientForControllers,
            DeploymentProcessor deploymentProcessor,
            CapabilityProvider capabilityProvider);

    default SerializedEntandoResource newResourceRequiringCapability() {
        final SerializedEntandoResource entandoResource = new SerializedEntandoResource();
        entandoResource.setMetadata(new ObjectMetaBuilder()
                .withName(MY_APP)
                .withNamespace(MY_NAMESPACE)
                .build());
        final CustomResourceDefinitionContext ctx = new CustomResourceDefinitionContext.Builder()
                .withGroup("test.org")
                .withKind("TestResource")
                .withVersion("v1")
                .build();
        entandoResource.setDefinition(ctx);
        return entandoResource;
    }

    default void attachKubernetesState() {
        step("Resulting state changes in the Kubernetes cluster", () -> {
            final Map<String, Map<String, Collection<? extends HasMetadata>>> kubernetesState = getClient().getKubernetesState();
            kubernetesState.forEach((key, value) ->
                    step(key, () -> value.forEach((s, hasMetadata) -> step(s,
                            () -> hasMetadata.forEach(m -> attachKubernetesResource(m.getMetadata().getName(), m))))));
        });
    }

    default void theDefaultTlsSecretWasCreatedAndConfiguredAsDefault() {
        step("And a TLS Secret was created and configured as default", () -> {
            attachEnvironmentVariable(EntandoOperatorConfigProperty.ENTANDO_TLS_SECRET_NAME, DEFAULT_TLS_SECRET);
            getClient().secrets().overwriteControllerSecret(new SecretBuilder()
                    .withNewMetadata()
                    .withName(DEFAULT_TLS_SECRET)
                    .withNamespace(MY_NAMESPACE)
                    .endMetadata()
                    .withType("kubernetes.io/tls")
                    .addToData("tls.crt", "")
                    .addToData("tls.key", "")
                    .build());
        });
    }

    class AllureAttachingCommandStream extends InProcessCommandStream {

        public AllureAttachingCommandStream(SimpleK8SClient<EntandoResourceClientDouble> client, SimpleKeycloakClient keycloakClient) {
            super(client, keycloakClient);
        }

        @Override
        public String process(SupportedCommand supportedCommand, String data, int timeoutSeconds) {
            //a bit of a hack:
            AtomicReference<String> resultHolder = new AtomicReference<>();
            step(format("Executing command %s", supportedCommand.name()), () -> {
                Allure.attachment(supportedCommand.getInputName(), data);
                final String result = super.process(supportedCommand, data, timeoutSeconds);
                Allure.attachment(supportedCommand.getOutputName(), result);
                resultHolder.set(result);
            });
            return resultHolder.get();
        }
    }

    default void verifyDbJobConnectionVariables(Container resultingContainer,
            DbmsVendor vendor, String hostName) {
        final String port = String
                .valueOf(DbmsDockerVendorStrategy.forVendor(vendor, EntandoOperatorSpiConfig.getComplianceMode()).getPort());
        step(format("with the standard %s DB Job environment variables pointing to %s:%s ", vendor.toValue(), hostName, port), () -> {
            assertThat(theVariableNamed(DATABASE_VENDOR).on(resultingContainer)).isEqualTo(vendor.toValue());
            assertThat(theVariableNamed(DATABASE_SCHEMA_COMMAND).on(resultingContainer)).isEqualTo("CREATE_SCHEMA");
            assertThat(theVariableNamed(DATABASE_SERVER_PORT).on(resultingContainer)).isEqualTo(
                    port);
            assertThat(theVariableNamed(DATABASE_SERVER_HOST).on(resultingContainer)).isEqualTo(hostName);
        });
    }

    default void verifyDbJobSchemaCredentials(String schemaSecret, Container resultingContainer) {
        step(format("with the DB Schema credentials from the secret %s", schemaSecret), () -> {
            assertThat(theVariableReferenceNamed(DATABASE_USER).on(resultingContainer).getSecretKeyRef().getName()).isEqualTo(schemaSecret);
            assertThat(theVariableReferenceNamed(DATABASE_PASSWORD).on(resultingContainer).getSecretKeyRef().getName())
                    .isEqualTo(schemaSecret);
            assertThat(theVariableReferenceNamed(DATABASE_USER).on(resultingContainer).getSecretKeyRef().getKey())
                    .isEqualTo(SecretUtils.USERNAME_KEY);
            assertThat(theVariableReferenceNamed(DATABASE_PASSWORD).on(resultingContainer).getSecretKeyRef().getKey())
                    .isEqualTo(SecretUtils.PASSSWORD_KEY);
        });
    }

    default void verifySpringSecurityVariables(Container thePluginContainer, String baseUrl, String ssoSecret) {
        assertThat(theVariableNamed(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name())
                .on(thePluginContainer)).isEqualTo(baseUrl);

        assertThat(theVariableReferenceNamed(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name())
                .on(thePluginContainer))
                .matches(theSecretKey(ssoSecret, KeycloakName.CLIENT_SECRET_KEY));
        assertThat(theVariableReferenceNamed(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name())
                .on(thePluginContainer))
                .matches(theSecretKey(ssoSecret, KeycloakName.CLIENT_ID_KEY));
    }

    default void verifyDbJobAdminCredentials(String adminSecret, Container resultingContainer) {
        step(format("with the DB Admin credentials from the secret %s", adminSecret), () -> {
            assertThat(theVariableReferenceNamed(DATABASE_ADMIN_USER).on(resultingContainer).getSecretKeyRef().getKey())
                    .isEqualTo(SecretUtils.USERNAME_KEY);
            assertThat(theVariableReferenceNamed(DATABASE_ADMIN_PASSWORD).on(resultingContainer).getSecretKeyRef().getKey())
                    .isEqualTo(SecretUtils.PASSSWORD_KEY);
            assertThat(theVariableReferenceNamed(DATABASE_ADMIN_USER).on(resultingContainer).getSecretKeyRef().getName())
                    .isEqualTo(adminSecret);
            assertThat(
                    theVariableReferenceNamed(DATABASE_ADMIN_PASSWORD).on(resultingContainer).getSecretKeyRef().getName()).isEqualTo(
                    adminSecret);
        });
    }

    default void registerCrd(String s) throws IOException {
        final URL resource = Thread.currentThread().getContextClassLoader().getResource(s);
        final CustomResourceDefinition crd = getClient().getCluster().putCustomResourceDefinition(new ObjectMapper(new YAMLFactory())
                .readValue(resource,
                        CustomResourceDefinition.class));
        final ConfigMap crdNameMap = getClient().secrets()
                .loadControllerConfigMap(KubernetesClientForControllers.ENTANDO_CRD_NAMES_CONFIG_MAP);
        crdNameMap.setData(Objects.requireNonNullElseGet(crdNameMap.getData(), ConcurrentHashMap::new));
        crdNameMap.getData().put(crd.getSpec().getNames().getKind() + "." + crd.getSpec().getGroup(), crd.getMetadata().getName());
    }

}
