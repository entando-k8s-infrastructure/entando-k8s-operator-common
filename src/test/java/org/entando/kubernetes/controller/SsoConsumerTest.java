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

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.container.ProvidedSsoCapability;
import org.entando.kubernetes.controller.spi.container.SpringBootDeployableContainer.SpringProperty;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpec;
import org.entando.kubernetes.fluentspi.SsoAwareContainerFluent;
import org.entando.kubernetes.fluentspi.SsoAwareControllerFluent;
import org.entando.kubernetes.fluentspi.SsoAwareDeployableFluent;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.common.SourceLink;
import org.entando.kubernetes.test.common.VariableReferenceAssertions;
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
class SsoConsumerTest extends ControllerTestBase implements VariableReferenceAssertions, CommonLabels {

    public static final String GENERATED_SSO_CLIENT_SECRET = "SOME-ASDF-KEYCLOAK-SECRET";
    public static final String MY_REALM = "my-realm";

    /*
              Classes to be implemented by the controller provider
            */
    @CommandLine.Command()
    public static class BasicSsoAwareController extends SsoAwareControllerFluent<BasicSsoAwareController> {

        public BasicSsoAwareController(KubernetesClientForControllers k8sClient,
                DeploymentProcessor deploymentProcessor,
                CapabilityProvider capabilityProvider) {
            super(k8sClient, deploymentProcessor, capabilityProvider);
        }
    }

    public static class BasicSsoAwareDeployable extends SsoAwareDeployableFluent<BasicSsoAwareDeployable> {

    }

    public static class BasicSsoAwareContainer extends SsoAwareContainerFluent<BasicSsoAwareContainer> {

    }

    private CapabilityRequirement ssoRequirement;
    private BasicSsoAwareDeployable deployable;
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
        return new BasicSsoAwareController(entandoResourceClientDouble, deploymentProcessor, capabilityProvider)
                .withDeployable(this.deployable)
                .withSsoRequirement(this.ssoRequirement)
                .withSupportedClass(TestResource.class);
    }

    @Test
    @Description("Should request a required SSO capability on-demand and connect to the service using the resulting SsoConnectionInfo ")
    void requestSsoCapabilityOnDemandAndConnectToIt() {
        step(format("Given I have a custom resource of kind TestResource with name '%s'", MY_APP), () -> {
            this.entandoCustomResource = new TestResource()
                    .withNames(MY_NAMESPACE, MY_APP)
                    .withSpec(new BasicDeploymentSpec());
            attachKubernetesResource("TestResource", entandoCustomResource);
        });
        step("And I have a basic BasicSsoAwareDeployable that is not exposed externally", () -> {
            this.deployable = new BasicSsoAwareDeployable().withCustomResource(this.entandoCustomResource);
            step("with the SSO client configured for the realm 'my-realm' and the client 'my-client'",
                    () -> deployable.withSsoClientConfig(new SsoClientConfig("my-realm", "my-client", "my-client")));
            attachSpiResource("Deployable", deployable);
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
        step("And a basic SsoAwareContainer using the image 'test/my-spring-boot-image:6.3.2'", () -> {
            final BasicSsoAwareContainer container = deployable
                    .withContainer(new BasicSsoAwareContainer().withDockerImageInfo("test/my-spring-boot-image:6.3.2")
                            .withPrimaryPort(8081)
                            .withNameQualifier("server"));
            attachSpiResource("Container", container);
        });
        step("And there is a controller to process requests for the SSO capability requested",
                () -> {
                    doAnswer(withAnSsoCapabilityStatus("mykeycloak.com", "my-realm"))
                            .when(getClient().capabilities())
                            .waitForCapabilityCompletion(argThat(matchesCapability(StandardCapability.SSO)), anyInt());
                    when(keycloakClient.prepareClientAndReturnSecret(any())).thenReturn(GENERATED_SSO_CLIENT_SECRET);
                });

        step("When the controller processes a new TestResource", () -> {
            runControllerAgainstCustomResource(entandoCustomResource);
        });
        step("The deployment process completed successfully", () -> {
            this.entandoCustomResource = getClient().entandoResources().reload(entandoCustomResource);
            assertThat(this.entandoCustomResource.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.SUCCESSFUL);
        });
        step("Then an SSO capability was provided for the SsoAwareDeployable with a name reflecting that it is the default            "
                        + "    Keycloak SSO service in the namespace",
                () -> {
                    this.capabilityProvisioningResult = getClient().entandoResources().loadCapabilityProvisioningResult(
                            getClient().capabilities().providedCapabilityByName(MY_NAMESPACE,
                                    "default-keycloak-sso-in-namespace")
                                    .get().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get());
                    assertThat(this.capabilityProvisioningResult).isNotNull();
                });
        step("And a Secret was created carrying the SSO CLientID and ClienSecret", () -> {
            final Secret secret = getClient().secrets().loadSecret(entandoCustomResource, "my-client-secret");
        });
        step("And a Deployment was created with a Container that reflects all the environment variables required to connect to the SSO "
                        + "service",
                () -> {
                    final Deployment deployment = getClient().deployments()
                            .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource));
                    attachKubernetesResource("Deployment", deployment);
                    final Container thePrimaryContainer = thePrimaryContainerOn(deployment);
                    final String secretName = "my-client-secret";
                    assertThat(theVariableReferenceNamed(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID.name())
                            .on(thePrimaryContainer)).matches(theSecretKey(secretName, KeycloakName.CLIENT_ID_KEY));
                    assertThat(
                            theVariableReferenceNamed(SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET.name())
                                    .on(thePrimaryContainer)).matches(theSecretKey(secretName, KeycloakName.CLIENT_SECRET_KEY));
                    assertThat(theVariableNamed(
                            SpringProperty.SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI.name())
                            .on(thePrimaryContainer)).isEqualTo("https://mykeycloak.com/auth/realms/my-realm");
                });
        step("And all the environment variables referring to Secrets are resolved",
                () -> verifyThatAllVariablesAreMapped(entandoCustomResource, getClient(), getClient().deployments()
                        .loadDeployment(entandoCustomResource, NameUtils.standardDeployment(entandoCustomResource))));
        step("And all SSO client information that has been registered is on the status of the custom resource",
                () -> {
                    final TestResource reloaded = getClient().entandoResources()
                            .reload(entandoCustomResource);
                    assertThat(reloaded.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getSsoClientId()).contains("my-client");
                    assertThat(reloaded.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getSsoRealm()).contains(MY_REALM);
                });

        attachKubernetesState();

    }

}
