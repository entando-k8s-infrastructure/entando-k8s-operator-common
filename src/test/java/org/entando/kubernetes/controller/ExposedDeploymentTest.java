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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.entando.kubernetes.controller.spi.container.ProvidedSsoCapability;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpec;
import org.entando.kubernetes.fluentspi.ExposingControllerFluent;
import org.entando.kubernetes.fluentspi.IngressingContainerFluent;
import org.entando.kubernetes.fluentspi.IngressingDeployableFluent;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.test.common.CertificateSecretHelper;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.common.SourceLink;
import org.entando.kubernetes.test.common.ValueHolder;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("inner-hexagon"), @Tag("in-process"), @Tag("allure"), @Tag("pre-deployment")})
@Feature("As a controller developer, I would like to request the OIDC capability so that I can use it to provide single sign on to my "
        + "users")
@Issue("ENG-2284")
@SourceLink("SsoConsumerTest.java")
class ExposedDeploymentTest extends ControllerTestBase implements VariableReferenceAssertions, CommonLabels {

    public static final String GENERATED_SSO_CLIENT_SECRET = "SOME-ASDF-KEYCLOAK-SECRET";

    /*
              Classes to be implemented by the controller provider
            */
    @CommandLine.Command()
    public static class BasicExposingController extends ExposingControllerFluent<BasicExposingController> {

        public BasicExposingController(KubernetesClientForControllers k8sClient,
                DeploymentProcessor deploymentProcessor,
                CapabilityProvider capabilityProvider) {
            super(k8sClient, deploymentProcessor, capabilityProvider);
        }
    }

    public static class BasicIngressingDeployable extends IngressingDeployableFluent<BasicIngressingDeployable> {

    }

    public static class BasicIngressContainer extends IngressingContainerFluent<BasicIngressContainer> {

    }

    private CapabilityRequirement ssoRequirement;
    private BasicIngressingDeployable deployable;
    private TestResource entandoCustomResource;
    private CapabilityProvisioningResult capabilityProvisioningResult;
    @Mock
    SimpleKeycloakClient keycloakClient;

    @Override
    public Optional<SimpleKeycloakClient> getKeycloakClient() {
        return Optional.ofNullable(keycloakClient);
    }

    @Override
    public Runnable createController(KubernetesClientForControllers entandoResourceClientDouble,
            DeploymentProcessor deploymentProcessor,
            CapabilityProvider capabilityProvider) {
        return new BasicExposingController(entandoResourceClientDouble, deploymentProcessor, capabilityProvider)
                .withDeployable(this.deployable)
                .withSsoRequirement(this.ssoRequirement)
                .withSupportedClass(TestResource.class);
    }

    @AfterEach
    @BeforeEach
    void resetSystemPropertiesUsed() {
        System.clearProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CA_SECRET_NAME.getJvmSystemProperty());
    }

    @Test
    @Description("Should expose a service over HTTPS using the host name specified ")
    void exposeHttpsServiceOverSpecifiedHostName() {
        step("Given I have a custom resource of kind TestResource with name 'my-app'", () -> {
            this.entandoCustomResource = new TestResource()
                    .withNames(MY_NAMESPACE, MY_APP)
                    .withSpec(new BasicDeploymentSpec());
            attachKubernetesResource("TestResource", entandoCustomResource);
        });
        step("And I have three Kubernetes Secrets",
                () -> {
                    CertificateSecretHelper.buildCertificateSecretsFromDirectory(
                            entandoCustomResource.getMetadata().getNamespace(),
                            Paths.get("src", "test", "resources", "tls", "ampie.dynu.net"))
                            .forEach(secret -> getClient().secrets().overwriteControllerSecret(secret));
                    step(format("a standard TLS Secret named '%s'", CertificateSecretHelper.TEST_TLS_SECRET), () ->
                            attachKubernetesResource("TlsSecret",
                                    getClient().secrets().loadSecret(entandoCustomResource,
                                            CertificateSecretHelper.TEST_TLS_SECRET)));
                    step(format("an Opaque Secret containing trusted certificates '%s' that is also configured as the default CA Secret",
                            CertificateSecretHelper.TEST_CA_SECRET),
                            () -> {
                                attachKubernetesResource("CASecret",
                                        getClient().secrets()
                                                .loadSecret(entandoCustomResource, CertificateSecretHelper.TEST_CA_SECRET));
                                System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CA_SECRET_NAME.getJvmSystemProperty(),
                                        CertificateSecretHelper.TEST_CA_SECRET);
                            });
                    step(format(
                            "another Opaque Secret containing the equivalent Java trust store with the default truststore secret name '%s'",
                            TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET),
                            () -> attachKubernetesResource("TrustStoreSecret",
                                    getClient().secrets()
                                            .loadSecret(entandoCustomResource, TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET)));
                });
        step("And there is a controller to process requests for the SSO capability requested",
                () -> {
                    doAnswer(withAnSsoCapabilityStatus("mykeycloak.com", "my-realm"))
                            .when(getClient().capabilities())
                            .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.SSO)), anyInt());
                    when(keycloakClient.prepareClientAndReturnSecret(any())).thenReturn(GENERATED_SSO_CLIENT_SECRET);
                });
        step("And I have requested a requirement for the SSO capability",
                () -> {
                    this.ssoRequirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.SSO)
                            .withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                            .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                            .addAllToCapabilityParameters(Map.of(ProvidedSsoCapability.DEFAULT_REALM_PARAMETER, "my-realm"))
                            .build();
                });

        step(format(
                "And I have an IngressingDeployable that specifies the TLS Secret %s and the hostname 'myhost.com', targeting an Ingress "
                        + "in the same namespace as the custom resource ",
                CertificateSecretHelper.TEST_TLS_SECRET),
                () -> {
                    this.deployable = new BasicIngressingDeployable()
                            .withIngressHostName("myhost.com")
                            .withTlsSecretName(CertificateSecretHelper.TEST_TLS_SECRET)
                            .withIngressRequired(true)
                            .withIngressNamespace(this.entandoCustomResource.getMetadata().getNamespace())
                            .withIngressName(NameUtils.standardIngressName(entandoCustomResource))
                            .withSsoClientConfig(new SsoClientConfig("my-realm", "my-client", "my-client"));
                    attachSpiResource("Deployable", deployable);
                });
        final BasicIngressContainer container = deployable
                .withContainer(new BasicIngressContainer().withDockerImageInfo("test/my-image:6.3.2")
                        .withPrimaryPort(8081)
                        .withNameQualifier("server"));
        step("and a TrustStoreAware, IngressingContainer with the context path '/my-app' and the health check path '/my-app/health'",
                () -> {
                    container.withWebContextPath("/my-app").withHealthCheckPath("/my-app/health");
                    attachSpiResource("Container", container);
                });
        step("When the controller processes a new TestResource", () -> {
            attachKubernetesResource("TestResource", entandoCustomResource);
            runControllerAgainstCustomResource(entandoCustomResource);
            this.entandoCustomResource = getClient().entandoResources().reload(entandoCustomResource);
        });

        step("Then a Deployment was created with a single Container", () -> {
            final Deployment deployment = getClient().deployments()
                    .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
            assertThat(deployment).isNotNull();
            attachKubernetesResource("Deployment", deployment);
            step(format("that has the standard Java truststore variable %s set to point to the "
                            + "Secret key %s.%s", TrustStoreHelper.JAVA_TOOL_OPTIONS, TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET,
                    TrustStoreHelper.TRUSTSTORE_SETTINGS_KEY),
                    () -> assertThat(theVariableReferenceNamed(TrustStoreHelper.JAVA_TOOL_OPTIONS)
                            .on(thePrimaryContainerOn(deployment)))
                            .matches(theSecretKey(TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET,
                                    TrustStoreHelper.TRUSTSTORE_SETTINGS_KEY)));
            step("and its startupProbe, readinessProbe and livenessProbe all point to the path /my-app/health", () -> {
                assertThat(thePrimaryContainerOn(deployment).getStartupProbe().getHttpGet().getPath()).isEqualTo("/my-app/health");
                assertThat(thePrimaryContainerOn(deployment).getReadinessProbe().getHttpGet().getPath()).isEqualTo("/my-app/health");
                assertThat(thePrimaryContainerOn(deployment).getLivenessProbe().getHttpGet().getPath()).isEqualTo("/my-app/health");
            });
        });
        step("And a Service was created that points to port 8081 on the target Container", () -> {
            final Service service = getClient().services()
                    .loadService(entandoCustomResource, NameUtils.standardServiceName(entandoCustomResource));
            attachKubernetesResource("Service", service);
            assertThat(service).isNotNull();
            assertThat(thePortNamed("server-port").on(service).getPort()).isEqualTo(8081);
            assertThat(thePortNamed("server-port").on(service).getTargetPort().getIntVal()).isEqualTo(8081);
            attachKubernetesResource("Service", service);
        });
        step("And an Ingress for was created", () -> {
            final Ingress ingress = getClient().ingresses()
                    .loadIngress(entandoCustomResource.getMetadata().getNamespace(),
                            NameUtils.standardIngressName(entandoCustomResource));
            attachKubernetesResource("Ingress", ingress);
            assertThat(ingress).isNotNull();
            step(" that points to port 8081 on the target Service", () -> {
                assertThat(theHttpPath("/my-app").on(ingress).getBackend().getServicePort().getIntVal()).isEqualTo(8081);
                assertThat(theHttpPath("/my-app").on(ingress).getBackend().getServiceName())
                        .isEqualTo(NameUtils.standardServiceName(entandoCustomResource));
            });
            step("and exposes the service on the host 'myhost.com'",
                    () -> assertThat(ingress.getSpec().getRules().get(0).getHost()).isEqualTo("myhost.com"));
            step(format("and uses the TLS Secret '%s' for this host", CertificateSecretHelper.TEST_TLS_SECRET),
                    () -> {
                        assertThat(ingress.getSpec().getTls().get(0).getSecretName()).isEqualTo(CertificateSecretHelper.TEST_TLS_SECRET);
                        assertThat(ingress.getSpec().getTls().get(0).getHosts()).contains("myhost.com");
                    });
        });
        step("And all the environment variables referring to Secrets are resolved",
                () -> verifyThatAllVariablesAreMapped(entandoCustomResource, getClient(), getClient().deployments()
                        .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource))));

        step("And the path is carried on the status of the CustomResource against the correct qualifier", () -> {
            assertThat(entandoCustomResource.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getWebContexts())
                    .containsEntry(NameUtils.DEFAULT_SERVER_QUALIFIER, "/my-app");
        });
    }

    @Test
    @Description("Should expose the path to a second service over HTTPS using the host name specified ")
    void exposeSecondPathOnHttpsServiceOverSpecifiedHostName() {
        ValueHolder<TestResource> firstResource = new ValueHolder<>();
        ValueHolder<TestResource> secondResource = new ValueHolder<>();
        step("Given I have two custom resource of kind TestResource", () -> {
            step(format("the first in the namespace '%s' with name '%s'", MY_NAMESPACE, MY_APP), () -> {
                firstResource.set(new TestResource()
                        .withNames(MY_NAMESPACE, MY_APP)
                        .withSpec(new BasicDeploymentSpec()));
                attachKubernetesResource("TestResource 1", entandoCustomResource);
            });
            step(format("the second in the namespace '%s' with name '%s'", MY_NAMESPACE + "2", MY_APP + "2"), () -> {
                secondResource.set(new TestResource()
                        .withNames(MY_NAMESPACE + "2", MY_APP + "2")
                        .withSpec(new BasicDeploymentSpec()));
                attachKubernetesResource("TestResource 2", entandoCustomResource);
            });
        });
        step("And I have created the necessary Kubernetes Secrets to support TLS", () -> {
            CertificateSecretHelper.buildCertificateSecretsFromDirectory(
                    firstResource.get().getMetadata().getNamespace(),
                    Paths.get("src", "test", "resources", "tls", "ampie.dynu.net"))
                    .forEach(secret -> getClient().secrets().overwriteControllerSecret(secret));
        });
        step("And there is a controller to process requests for the SSO capability requested",
                () -> {
                    doAnswer(withAnSsoCapabilityStatus("mykeycloak.com", "my-realm"))
                            .when(getClient().capabilities())
                            .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.SSO)), anyInt());
                    when(keycloakClient.prepareClientAndReturnSecret(any())).thenReturn(GENERATED_SSO_CLIENT_SECRET);
                });
        step("And I have requested a requirement for the SSO capability",
                () -> {
                    this.ssoRequirement = new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.SSO)
                            .withImplementation(StandardCapabilityImplementation.KEYCLOAK)
                            .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                            .addAllToCapabilityParameters(Map.of(ProvidedSsoCapability.DEFAULT_REALM_PARAMETER, "my-realm"))
                            .build();
                });

        step(format(
                "And I have an IngressingDeployable that specifies the TLS Secret %s and the hostname 'myhost.com', targeting an Ingress "
                        + "in the same namespace as the custom resource ",
                CertificateSecretHelper.TEST_TLS_SECRET),
                () -> {
                    this.deployable = new BasicIngressingDeployable()
                            .withIngressHostName("myhost.com")
                            .withTlsSecretName(CertificateSecretHelper.TEST_TLS_SECRET)
                            .withIngressRequired(true)
                            .withIngressNamespace(firstResource.get().getMetadata().getNamespace())
                            .withIngressName(NameUtils.standardIngressName(firstResource.get()))
                            .withSsoClientConfig(new SsoClientConfig("my-realm", "my-client", "my-client"));
                    attachSpiResource("Deployable", deployable);
                });
        final BasicIngressContainer container = deployable
                .withContainer(new BasicIngressContainer().withDockerImageInfo("test/my-image:6.3.2")
                        .withPrimaryPort(8081)
                        .withNameQualifier("server")
                        .withSsoClientConfig(new SsoClientConfig("my-realm", "my-client", "my-client")));
        step(format(
                "and I have deployed the first TestResource '%s' in namespace '%s' with the context path '/my-app' and the health check "
                        + "path "
                        + "'/my-app/health'",
                MY_APP, MY_NAMESPACE),
                () -> {
                    container.withWebContextPath("/my-app").withHealthCheckPath("/my-app/health");
                    attachSpiResource("Container", container);
                    runControllerAgainstCustomResource(firstResource.get());
                    firstResource.set(getClient().entandoResources().reload(firstResource.get()));

                });
        step(format(
                "When I deploy the second TestResource '%s' in namespace '%s' with the context path '/my-app2' and the health check path "
                        + "'/my-app2/health'",
                MY_APP + "2", MY_NAMESPACE + "2"),
                () -> {
                    container.withWebContextPath("/my-app2").withHealthCheckPath("/my-app2/health");
                    attachSpiResource("Container", container);
                    attachKubernetesResource("TestResource", secondResource.get());
                    runControllerAgainstCustomResource(secondResource.get());
                    secondResource.set(getClient().entandoResources().reload(secondResource.get()));
                });

        step("Then a second Deployment was created with a single Container", () -> {
            final Deployment deployment = getClient().deployments()
                    .loadDeployment(secondResource.get(), NameUtils.standardDeployment(secondResource.get()));
            assertThat(deployment).isNotNull();
            attachKubernetesResource("Deployment", deployment);
            step(format("that has the standard Java truststore variable %s set to point to the "
                            + "Secret key %s.%s", TrustStoreHelper.JAVA_TOOL_OPTIONS, TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET,
                    TrustStoreHelper.TRUSTSTORE_SETTINGS_KEY),
                    () -> assertThat(theVariableReferenceNamed(TrustStoreHelper.JAVA_TOOL_OPTIONS)
                            .on(thePrimaryContainerOn(deployment)))
                            .matches(theSecretKey(TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET,
                                    TrustStoreHelper.TRUSTSTORE_SETTINGS_KEY)));
            step("and its startupProbe, readinessProbe and livenessProbe all point to the path /my-app/health", () -> {
                assertThat(thePrimaryContainerOn(deployment).getStartupProbe().getHttpGet().getPath()).isEqualTo("/my-app2/health");
                assertThat(thePrimaryContainerOn(deployment).getReadinessProbe().getHttpGet().getPath()).isEqualTo("/my-app2/health");
                assertThat(thePrimaryContainerOn(deployment).getLivenessProbe().getHttpGet().getPath()).isEqualTo("/my-app2/health");
            });
        });

        final Service secondService = getClient().services()
                .loadService(secondResource.get(), NameUtils.standardServiceName(secondResource.get()));
        step("And a Service was created that points to port 8081 on the target Container", () -> {
            attachKubernetesResource("Service", secondService);
            assertThat(secondService).isNotNull();
            assertThat(thePortNamed("server-port").on(secondService).getPort()).isEqualTo(8081);
            assertThat(thePortNamed("server-port").on(secondService).getTargetPort().getIntVal()).isEqualTo(8081);
            attachKubernetesResource("Service", secondService);
        });
        final String delegateName = NameUtils.standardIngressName(firstResource.get()) + "-to-" + NameUtils
                .standardServiceName(secondResource.get());
        step(format("And a delegate Service '%s' was created in namespace '%s' that points to the service in namespace '%s' ",
                delegateName,
                MY_NAMESPACE,
                MY_NAMESPACE + "2"),
                () -> {
                    final Service delegateService = getClient().services().loadService(firstResource.get(), delegateName);
                    attachKubernetesResource("Service", delegateService);
                    assertThat(delegateService).isNotNull();
                    assertThat(thePortNamed("server-port").on(delegateService).getPort()).isEqualTo(8081);
                    assertThat(thePortNamed("server-port").on(delegateService).getTargetPort().getIntVal()).isEqualTo(8081);
                    attachKubernetesResource("Service", delegateService);
                });
        step(format("And the delegate Service is backed by a Endpoints resource that points to the second service in namespace '%s'",
                MY_NAMESPACE + "2"),
                () -> {
                    final Endpoints delegateService = getClient().services().loadEndpoints(firstResource.get(), delegateName);
                    attachKubernetesResource("Service", delegateService);
                    assertThat(delegateService).isNotNull();
                    assertThat(delegateService.getSubsets().get(0).getAddresses().get(0).getIp())
                            .isEqualTo(secondService.getSpec().getClusterIP());
                    assertThat(thePortNamed("server-port").on(delegateService).getPort()).isEqualTo(8081);
                    attachKubernetesResource("Service", delegateService);
                });

        step("And a new path was created on the original Ingress for '/my-app2'", () -> {
            final Ingress ingress = getClient().ingresses()
                    .loadIngress(firstResource.get().getMetadata().getNamespace(),
                            NameUtils.standardIngressName(firstResource.get()));
            attachKubernetesResource("Ingress", ingress);
            assertThat(ingress).isNotNull();
            step(" that points to port 8081 on the target Service", () -> {
                assertThat(theHttpPath("/my-app2").on(ingress).getBackend().getServicePort().getIntVal()).isEqualTo(8081);
                assertThat(theHttpPath("/my-app2").on(ingress).getBackend().getServiceName())
                        .isEqualTo(delegateName);
            });
        });

        step("And both paths are represented on the status of the CustomResource", () -> {
            assertThat(firstResource.get().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getWebContexts())
                    .containsEntry(NameUtils.DEFAULT_SERVER_QUALIFIER, "/my-app");
            assertThat(secondResource.get().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getWebContexts())
                    .containsEntry(NameUtils.DEFAULT_SERVER_QUALIFIER, "/my-app2");
        });

    }

}
