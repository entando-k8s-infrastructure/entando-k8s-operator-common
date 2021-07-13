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

import static io.qameta.allure.Allure.step;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.ThrowableAssert;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers.TestFixturePreparation;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.creators.EntandoRbacRole;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.ProvidedCapabilityBuilder;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;
import org.entando.kubernetes.test.common.CustomResourceStatusEmulator;
import org.entando.kubernetes.test.common.ValueHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@Feature("As a support developer, I would like perform common operations on the ProvidedCapability resources through a simple "
        + "interface to reduce the learning curve")
@EnableRuleMigrationSupport
class DefaultCapabilityClientTest extends AbstractK8SIntegrationTest implements CustomResourceStatusEmulator<DefaultSimpleK8SClient> {

    private DefaultSimpleK8SClient client;

    public DefaultSimpleK8SClient getClient() {
        this.client = Objects.requireNonNullElseGet(this.client, () -> new DefaultSimpleK8SClient(getFabric8Client()));
        return this.client;
    }

    @Test
    @Description("Should resolve a ProvidedCapability at  Cluster scope by labels")
    void shouldResolveFromClusterByLabels() {
        step(format("Given I have deployed the Operator with cluster scope access"),
                () -> attachEnvironmentVariable(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE, "*"));

        step(format("Given I have created a ProvidedCapability 'my-capability1' with the label 'my-label=value1' in the Namespace '%s'",
                MY_APP_NAMESPACE_1), () -> attachResource("ProvidedCapability 1",
                createCapability(MY_APP_NAMESPACE_1, "my-capability1", "value1")));
        step(format("And I have created a ProvidedCapability 'my-capability2' with the label 'my-label=value2' in the Namespace '%s'",
                MY_APP_NAMESPACE_2), () -> attachResource("ProvidedCapability 1",
                createCapability(MY_APP_NAMESPACE_2, "my-capability2", "value2")));
        step("Expect ProvidedCapability 'my-capability1' to be resolved using the label 'my-label=value1'", () -> {
            final ProvidedCapability capability = getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value1")).get();
            attachResource("Resolve ProvidedCapability 'my-capability1'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability1");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE_1);
        });
        step("Expect ProvidedCapability 'my-capability2' to be resolved using the label 'my-label=value2'", () -> {
            final ProvidedCapability capability = getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value2")).get();
            attachResource("Resolve ProvidedCapability 'my-capability2'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability2");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE_2);
        });

    }

    private void createRoleBindingForClusterRole(EntandoCustomResource entandoCustomResource, ServiceAccount serviceAccount) {
        getClient().serviceAccounts().createRoleBindingIfAbsent(entandoCustomResource, new RoleBindingBuilder()
                .withNewMetadata()
                .withName(serviceAccount.getMetadata().getName() + "-" + EntandoRbacRole.ENTANDO_EDITOR.getK8sName())
                .endMetadata()
                .withNewRoleRef()
                .withName(EntandoRbacRole.ENTANDO_EDITOR.getK8sName())
                .withKind("ClusterRole")
                .endRoleRef()
                .addNewSubject()
                .withKind("ServiceAccount")
                .withName(serviceAccount.getMetadata().getName())
                .withNamespace(serviceAccount.getMetadata().getNamespace())
                .endSubject()
                .build());
    }

    @Test
    @Description("Should not resolve a ProvidedCapability at  Cluster scope by labels from namespaces I don't have access to")
    void shouldNotResolveProvidedCapabilityFromInaccessibleNamespaces() {
        final ValueHolder<ProvidedCapability> capability1 = new ValueHolder<>();
        final ValueHolder<ProvidedCapability> capability2 = new ValueHolder<>();
        step(format("Given I have configured to operator to observe the Namespace '%s'", MY_APP_NAMESPACE_1), () -> {
            attachEnvironmentVariable(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE, MY_APP_NAMESPACE_1);
        });
        step(format("And I have configured to operator to have interest in the Namespace '%s'", MY_APP_NAMESPACE_2), () -> {
            attachEnvironmentVariable(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_OF_INTEREST, MY_APP_NAMESPACE_2);
        });
        step(format("And I have created a ProvidedCapability 'my-capability1' with the label 'my-label=value1' in the Namespace '%s'",
                MY_APP_NAMESPACE_1),
                () -> {
                    capability1.set(createCapability(MY_APP_NAMESPACE_1, "my-capability1", "value1"));
                    attachResource("ProvidedCapability 1", capability1.get());
                });
        step(format("And I have created a ProvidedCapability 'my-capability2' with the label 'my-label=value2' in the Namespace '%s'",
                MY_APP_NAMESPACE_2),
                () -> {
                    capability2.set(createCapability(MY_APP_NAMESPACE_2, "my-capability2", "value2"));
                    attachResource("ProvidedCapability 2", capability2.get());
                });
        step(format("And I have logged in with an account that does not have access to the namespaces %s", MY_APP_NAMESPACE_2),
                () -> loginWithAccessToTheNamespacesOf(capability1.get()));
        step("Expect ProvidedCapability 'my-capability1' to be resolved using the label 'my-label=value1'", () -> {
            final ProvidedCapability capability = getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value1")).get();
            attachResource("Resolve ProvidedCapability 'my-capability1'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability1");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE_1);
        });
        step("But expect no ProvidedCapability to be resolved using the label 'my-label=value2'", () -> {
            assertThat(getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value2"))).isEmpty();
        });
    }

    @Test
    @Description("Should not resolve a ProvidedCapability at  Cluster scope by labels from namespaces I haven't registered interest in")
    void shouldNotResolveProvidedCapabilityFromUninterestingNamespaces() {
        final ValueHolder<ProvidedCapability> capability1 = new ValueHolder<>();
        final ValueHolder<ProvidedCapability> capability2 = new ValueHolder<>();
        step(format("Given I have configured to operator to observe the Namespace '%s'", MY_APP_NAMESPACE_1),
                () -> attachEnvironmentVariable(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE, MY_APP_NAMESPACE_1));
        step(format("And I have created a ProvidedCapability 'my-capability1' with the label 'my-label=value1' in the Namespace '%s'",
                MY_APP_NAMESPACE_1),
                () -> {
                    capability1.set(createCapability(MY_APP_NAMESPACE_1, "my-capability1", "value1"));
                    attachResource("ProvidedCapability 1", capability1.get());
                });
        step(format("And I have created a ProvidedCapability 'my-capability2' with the label 'my-label=value2' in the Namespace '%s'",
                MY_APP_NAMESPACE_2),
                () -> {
                    capability2.set(createCapability(MY_APP_NAMESPACE_2, "my-capability2", "value2"));
                    attachResource("ProvidedCapability 2", capability2.get());
                });
        step(format("And I have logged in with an account that has access to the namespaces %s and %s", MY_APP_NAMESPACE_1,
                MY_APP_NAMESPACE_2),
                () -> loginWithAccessToTheNamespacesOf(capability1.get(), capability2.get()));
        step("Expect ProvidedCapability 'my-capability1' to be resolved using the label 'my-label=value1'", () -> {
            final ProvidedCapability capability = getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value1")).get();
            attachResource("Resolve ProvidedCapability 'my-capability1'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability1");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE_1);
        });
        step("But expect no ProvidedCapability to be resolved using the label 'my-label=value2'", () ->
                assertThat(getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value2"))).isEmpty());
    }

    @Test
    @Description("Should resolve a ProvidedCapability at Cluster scope by labels from namespaces I have access to")
    void shouldResolveProvidedCapabilityFromAccessibleNamespaces() {
        final ValueHolder<ProvidedCapability> capability1 = new ValueHolder<>();
        final ValueHolder<ProvidedCapability> capability2 = new ValueHolder<>();
        step(format("Given I have configured to operator to observe the Namespace '%s'", MY_APP_NAMESPACE_1), () -> {
            attachEnvironmentVariable(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE, MY_APP_NAMESPACE_1);
        });
        step(format("And I have configured to operator to have interest in the Namespace '%s'", MY_APP_NAMESPACE_2), () -> {
            attachEnvironmentVariable(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_OF_INTEREST, MY_APP_NAMESPACE_2);
        });
        step(format("And I have created a ProvidedCapability 'my-capability1' with the label 'my-label=value1' in the Namespace '%s'",
                MY_APP_NAMESPACE_1),
                () -> {
                    capability1.set(createCapability(MY_APP_NAMESPACE_1, "my-capability1", "value1"));
                    attachResource("ProvidedCapability 1", capability1.get());
                });
        step(format("And I have created a ProvidedCapability 'my-capability2' with the label 'my-label=value2' in the Namespace '%s'",
                MY_APP_NAMESPACE_2),
                () -> {
                    capability2.set(createCapability(MY_APP_NAMESPACE_2, "my-capability2", "value2"));
                    attachResource("ProvidedCapability 2", capability2.get());
                });
        step(format("And I have logged in with an account that has access to the namespaces %s and ", MY_APP_NAMESPACE_1,
                MY_APP_NAMESPACE_2),
                () -> loginWithAccessToTheNamespacesOf(capability1.get(), capability2.get()));
        step("Expect ProvidedCapability 'my-capability1' to be resolved using the label 'my-label=value1'", () -> {
            final ProvidedCapability capability = getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value1")).get();
            attachResource("Resolve ProvidedCapability 'my-capability1'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability1");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE_1);
        });
        step("And expect  ProvidedCapability 'my-capability2' to be resolved using the label 'my-label=value2'", () -> {
            final ProvidedCapability capability = getClient().capabilities().providedCapabilityByLabels(Map.of("my-label", "value2")).get();
            attachResource("Resolve ProvidedCapability 'my-capability2'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability2");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE_2);
        });
    }

    private void loginWithAccessToTheNamespacesOf(ProvidedCapability... entandoCustomResource) {
        final ServiceAccount serviceAccount = getClient().serviceAccounts()
                .findOrCreateServiceAccount(entandoCustomResource[0], "test-account").done();
        for (ProvidedCapability providedCapability : entandoCustomResource) {
            createRoleBindingForClusterRole(providedCapability, serviceAccount);
        }
        await().atMost(30, TimeUnit.SECONDS).ignoreExceptions()
                .until(() -> getFabric8Client().secrets().inNamespace(entandoCustomResource[0].getMetadata().getNamespace()).list()
                        .getItems().stream().anyMatch(secret -> TestFixturePreparation.isValidTokenSecret(secret, "test-account")));
        final List<Secret> items = getFabric8Client().secrets().inNamespace(entandoCustomResource[0].getMetadata().getNamespace()).list()
                .getItems();
        final Secret tokenSecret = items.stream()
                .filter(s -> TestFixturePreparation.isValidTokenSecret(s, "test-account")).findFirst().get();
        String token = new String(Base64.getDecoder().decode(tokenSecret.getData().get("token")), StandardCharsets.UTF_8);
        Config config = new ConfigBuilder().build();
        System.setProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY, "true");
        config = new ConfigBuilder()
                .withMasterUrl(config.getMasterUrl())
                .withTrustCerts(true)
                .withOauthToken(token).build();
        this.client = new DefaultSimpleK8SClient(new DefaultKubernetesClient(config));
    }

    private ProvidedCapability createCapability(String namespace, String name, String value1) {
        return getClient().entandoResources()
                .createOrPatchEntandoResource(new ProvidedCapabilityBuilder().withNewMetadata().withNamespace(namespace)
                        .withName(name)
                        .addToLabels("my-label", value1)
                        .endMetadata()
                        .withNewSpec()
                        .withCapability(StandardCapability.SSO).withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                        .endSpec().build());
    }

    @Test
    @Description("Should resolve a ProvidedCapability at  Namespace scope by labels")
    void shouldResolveFromNamespaceByLabels() {
        step(format("Given I have created a ProvidedCapability 'my-capability1' with the label 'my-label=value1' in the Namespace '%s'",
                MY_APP_NAMESPACE_1), () -> attachResource("ProvidedCapability 1",
                createCapability(MY_APP_NAMESPACE_1, "my-capability1", "value1")));
        step(format("And I have created a ProvidedCapability 'my-capability2' with the label 'my-label=value2' in the Namespace '%s'",
                MY_APP_NAMESPACE_2), () -> attachResource("ProvidedCapability 1",
                createCapability(MY_APP_NAMESPACE_2, "my-capability2", "value2")));
        step("Expect ProvidedCapability 'my-capability1' to be resolved using the label 'my-label=value1'", () -> {
            final ProvidedCapability capability = getClient().capabilities()
                    .providedCapabilityByLabels(MY_APP_NAMESPACE_1, Map.of("my-label", "value1")).get();
            attachResource("Resolve ProvidedCapability 'my-capability1'", capability);
            assertThat(capability.getMetadata().getName()).isEqualTo("my-capability1");
            assertThat(capability.getMetadata().getNamespace()).isEqualTo(MY_APP_NAMESPACE_1);
        });
        step("Expect ProvidedCapability 'my-capability2' to be resolved using the label 'my-label=value2'", () -> {
            assertThat(getClient().capabilities().providedCapabilityByLabels(MY_APP_NAMESPACE_1, Map.of("my-label", "value2"))).isEmpty();
        });
    }

    @Test
    @Description("Should resolve a providedCapability by name and namespace")
    void shouldResolveByNameAndNamespace() {
        step("Given I have created a ProvidedCapability", () -> {
            attachResource("ProvidedCapability", getClient().entandoResources()
                    .createOrPatchEntandoResource(newCapability("my-capability")));
        });
        step("Expect it to be resolved by name and namespace", () -> {
            final Optional<ProvidedCapability> actualCapability = getClient().capabilities()
                    .providedCapabilityByName(MY_APP_NAMESPACE_1, "my-capability");
            assertThat(actualCapability).isPresent();
            attachResource("Resolved ProvidedCapability", actualCapability.get());
        });
    }

    private ProvidedCapability newCapability(String name) {
        return new ProvidedCapabilityBuilder().withNewMetadata().withNamespace(MY_APP_NAMESPACE_1)
                .withName(name).endMetadata()
                .withNewSpec()
                .withCapability(StandardCapability.SSO).withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                .endSpec().build();
    }

    @Test
    @Description("Should resolve a providedCapability by name and namespace")
    void shouldBuildACapabilityResult() {
        ValueHolder<ProvidedCapability> capability = new ValueHolder<>();
        step("Given I have created a ProvidedCapability", () -> {
            final ProvidedCapability providedCapability = getClient().entandoResources()
                    .createOrPatchEntandoResource(newCapability("my-capability"));
            capability.set(providedCapability);
            attachResource("ProvidedCapability", providedCapability);
        });
        step("And I have updated its status with an ExternalServerStatus", () -> {
            putExternalServerStatus(capability.get(), "myhost.com", 8081, "/my-context", Collections.emptyMap());
        });
        ValueHolder<CapabilityProvisioningResult> result = new ValueHolder<>();
        step("When I build its CapabilityProvisioningResult", () -> {
            result.set(getClient().capabilities().buildCapabilityProvisioningResult(capability.get()));
        });
        step("Then its Service resolved successfully", () -> {
            assertThat(result.get().getService()).isNotNull();
            attachResource("Service", result.get().getService());
        });
        step("And its admin Secret resolved successfully", () -> {
            assertThat(result.get().getAdminSecret()).isNotNull();
            attachResource("Admin Secret", result.get().getAdminSecret().get());
        });
        step("Then its Ingress resolved successfully", () -> {
            assertThat(result.get().getIngress()).isNotNull();
            attachResource("Ingress", result.get().getIngress().get());
        });
    }

    @Test
    @Description("Should create a providedCapability and wait for its status to enter a complectionPhase")
    void shouldCreateAProvidedCapabilityAndWaitForItsStatusToEnterACompletionPhase() {
        ValueHolder<ProvidedCapability> capability = new ValueHolder<>();
        step("Given I have created a ProvidedCapability", () -> {
            capability.set(getClient().capabilities().createOrPatchCapability(newCapability("my-capability")));
            attachResource("ProvidedCapability", capability.get());
        });
        step("And there is a background process such as a controller that updates the Phase on its Status to 'SUCCESSFUL'", () -> {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.schedule(() -> {
                try {
                    KubernetesClientForControllers clientForControllers = getClient().entandoResources();
                    await().atMost(10, TimeUnit.SECONDS)
                            .until(() -> clientForControllers.load(ProvidedCapability.class, MY_APP_NAMESPACE_1, "my-capability") != null);
                    capability.set(clientForControllers.load(ProvidedCapability.class, MY_APP_NAMESPACE_1, "my-capability"));
                    capability.set(clientForControllers.updateStatus(capability.get(),
                            new ServerStatus("server").withOriginatingControllerPod(
                                    client.entandoResources()
                                            .getNamespace(), EntandoOperatorSpiConfig.getControllerPodName()))
                    );
                    capability.set(clientForControllers.updatePhase(capability.get(), EntandoDeploymentPhase.SUCCESSFUL));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 2, TimeUnit.SECONDS);
        });
        step("When I wait for the ProvidedCapability to enter a completion phase", () -> {
            capability.set(getClient().capabilities().waitForCapabilityCompletion(
                    capability.get(), 15));
            attachResource("ProvidedCapability", capability.get());
        });
        step("Then it reflects the 'SUCCESSFUL' Phase and the correct state", () -> {
            assertThat(capability.get().getMetadata().getName()).isEqualTo("my-capability");
            assertThat(capability.get().getSpec().getCapability()).isEqualTo(StandardCapability.SSO);
            assertThat(capability.get().getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.SUCCESSFUL);

            attachResource("ProvidedCapability", capability.get());
        });
    }

    @Test
    @Description("Should throw a TimeoutException when a providedCapability does not enter a completionPhase within the time specified")
    void shouldThrowTimeoutException() throws TimeoutException {
        ValueHolder<ProvidedCapability> capability = new ValueHolder<>();
        step("Given I have created a ProvidedCapability", () -> {
            capability.set(getClient().capabilities().createOrPatchCapability(newCapability("my-capability")));
            attachResource("ProvidedCapability", capability.get());
        });
        step("And there is no background process that updates the Phase on its Status");
        ValueHolder<ThrowableAssert> exceptionAssert = new ValueHolder<>();
        step("When I create the capability and wait for its completion phase with a timeout of one second", () -> {
            exceptionAssert.set((ThrowableAssert) assertThatThrownBy(() ->
                    getClient().capabilities().waitForCapabilityCompletion(
                            capability.get(), 1)));
        });
        step("Then a TimeoutException is through", () -> {
            exceptionAssert.get().isInstanceOf(TimeoutException.class);
        });
    }

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE_1, MY_APP_NAMESPACE_2};
    }
}
