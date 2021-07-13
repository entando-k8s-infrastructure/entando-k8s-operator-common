package org.entando.kubernetes.controller;

import static io.qameta.allure.Allure.step;
import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import java.util.Map;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpec;
import org.entando.kubernetes.fluentspi.BasicDeploymentSpecBuilder;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.fluentspi.TestResourceController;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.test.common.CommonLabels;
import org.entando.kubernetes.test.common.SourceLink;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("inner-hexagon"), @Tag("in-process"), @Tag("allure"), @Tag("pre-deployment")})
@Feature("As a controller developer, I would like to implement a controller that responds to CapabilityRequirements so that I can extend"
        + "Kubernetes with my own controllers")
@Issue("ENG-2284")
@SourceLink("ExampleCapabilityTest.java")
class ExampleCapabilityTest extends ControllerTestBase {

    static final String DEFAULT_DBMS_IN_NAMESPACE = "default-postgresql-dbms-in-namespace";

    @Override
    public Runnable createController(KubernetesClientForControllers kubernetesClientForControllers, DeploymentProcessor deploymentProcessor,
            CapabilityProvider capabilityProvider) {
        return new TestResourceController(kubernetesClientForControllers, deploymentProcessor);
    }

    @Test
    @Description("Should translate the ProvidedCapability received to a TestResource and deploy it")
    void shouldTranslateProvidedCapabilityToTestResource() {
        step("Given I have an example Controller that responds to DBMS CapabilityRequirements");
        step("And I have created the ProvidedCapability CRD", () -> {
            getClient().entandoResources().registerCustomResourceDefinition("crd/providedcapabilities.entando.org.crd.yaml");
        });

        step("When I request a namespace scoped, PostgreSQL DBMS Capability for direct deployment",
                () -> runControllerAgainstCapabilityRequirement(newResourceRequiringCapability(), new CapabilityRequirementBuilder()
                        .withCapability(StandardCapability.DBMS)
                        .withImplementation(StandardCapabilityImplementation.POSTGRESQL)
                        .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY)
                        .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                        .build()));
        ProvidedCapability providedCapability = getClient().entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, DEFAULT_DBMS_IN_NAMESPACE);
        TestResource testResource = getClient().entandoResources().load(TestResource.class, MY_NAMESPACE, DEFAULT_DBMS_IN_NAMESPACE);
        step("Then an TestResource was provisioned:", () -> {
            step("using the DeployDirectly provisioningStrategy",
                    () -> assertThat(testResource.getSpec().getProvisioningStrategy())
                            .isEqualTo(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY));
            step("a PostgreSQL database",
                    () -> assertThat(testResource.getSpec().getDbms()).isEqualTo(DbmsVendor.POSTGRESQL));
            step("and it is owned by the ProvidedCapability to ensure only changes from the ProvidedCapability will change the "
                            + "implementing Kubernetes resources",
                    () -> assertThat(ResourceUtils.customResourceOwns(providedCapability, testResource)));
            attachKubernetesResource("TestResource", testResource);
        });
        step("And a Kubernetes Deployment was created reflecting the requirements of the PostgreSQL image:"
                        + DbmsDockerVendorStrategy.CENTOS_POSTGRESQL
                        .getOrganization() + "/" + DbmsDockerVendorStrategy.CENTOS_POSTGRESQL.getImageRepository(),
                () -> {
                    final Deployment deployment = getClient().deployments()
                            .loadDeployment(testResource, NameUtils.standardDeployment(testResource));
                    attachKubernetesResource("Deployment", deployment);
                    step("using the PostgreSQL Image " + DbmsDockerVendorStrategy.CENTOS_POSTGRESQL.getOrganization() + "/"
                                    + DbmsDockerVendorStrategy.CENTOS_POSTGRESQL
                                    .getImageRepository(),
                            () -> assertThat(thePrimaryContainerOn(deployment).getImage())
                                    .contains(DbmsDockerVendorStrategy.CENTOS_POSTGRESQL.getOrganization() + "/"
                                            + DbmsDockerVendorStrategy.CENTOS_POSTGRESQL
                                            .getImageRepository()));
                    step("With a volume mounted to the standard data directory /var/lib/pgsql/data",
                            () -> assertThat(
                                    theVolumeMountNamed("default-postgresql-dbms-in-namespace-db-volume")
                                            .on(thePrimaryContainerOn(deployment))
                                            .getMountPath()).isEqualTo("/var/lib/pgsql/data"));
                    step("Which is bound to a PersistentVolumeClain", () -> {
                        final PersistentVolumeClaim pvc = getClient().persistentVolumeClaims()
                                .loadPersistentVolumeClaim(testResource, "default-postgresql-dbms-in-namespace-db-pvc");
                        attachKubernetesResource("PersistentVolumeClaim", pvc);
                        assertThat(
                                theVolumeNamed("default-postgresql-dbms-in-namespace-db-volume").on(deployment).getPersistentVolumeClaim()
                                        .getClaimName()).isEqualTo(
                                "default-postgresql-dbms-in-namespace-db-pvc");
                    });
                    step("And livenessProbe, startupProbe and readinessProbe all use the standard PostgreSQL command "
                                    + "/usr/libexec/check-container",
                            () -> {
                                assertThat(thePrimaryContainerOn(deployment).getLivenessProbe().getExec().getCommand())
                                        .contains("/usr/libexec/check-container");
                                assertThat(thePrimaryContainerOn(deployment).getReadinessProbe().getExec().getCommand())
                                        .contains("/usr/libexec/check-container");
                                assertThat(thePrimaryContainerOn(deployment).getStartupProbe().getExec().getCommand())
                                        .contains("/usr/libexec/check-container");
                            });

                });
        step("And the admin secret specifies the standard super user 'postgres' as user and has a dynamically generated password", () -> {
            final Secret secret = getClient().secrets()
                    .loadSecret(testResource, NameUtils.standardAdminSecretName(testResource));
            attachKubernetesResource("Admin Secret", secret);
            assertThat(theKey("username").on(secret)).isEqualTo("postgres");
            assertThat(theKey("password").on(secret)).isNotBlank();
        });
        step("And a Kubernetes Service was created:", () -> {
            final Service service = getClient().services()
                    .loadService(testResource, NameUtils.standardServiceName(testResource));
            attachKubernetesResource("Service", service);
            step("Exposing the port 5432 ",
                    () -> assertThat(service.getSpec().getPorts().get(0).getPort()).isEqualTo(5432));
            step("Targeting port 5432 in the Deployment",
                    () -> assertThat(service.getSpec().getPorts().get(0).getTargetPort().getIntVal()).isEqualTo(5432));
            step("And with a label selector matching the labels of the Pod Template on the  Deployment",
                    () -> assertThat(service.getSpec().getSelector()).containsAllEntriesOf(
                            Map.of(LabelNames.RESOURCE_KIND.getName(), "TestResource", "TestResource",
                                    testResource.getMetadata().getName(),
                                    LabelNames.DEPLOYMENT.getName(), testResource.getMetadata().getName())
                    ));
        });

        step("And the resulting DatabaseServiceResult reflects the correct information to connect to the deployed DBMS service", () -> {
            DatabaseConnectionInfo connectionInfo = new ProvidedDatabaseCapability(
                    getClient().entandoResources()
                            .loadCapabilityProvisioningResult(
                                    providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get()));
            Allure.attachment("DatabaseServiceResult", SerializationHelper.serialize(connectionInfo));
            assertThat(connectionInfo.getDatabaseName()).isEqualTo("default_postgresql_dbms_in_namespace");
            assertThat(connectionInfo.getPort()).isEqualTo("5432");
            assertThat(connectionInfo.getInternalServiceHostname())
                    .isEqualTo("default-postgresql-dbms-in-namespace-service.my-namespace.svc.cluster.local");
            assertThat(connectionInfo.getVendor()).isEqualTo(DbmsVendorConfig.POSTGRESQL);
        });
        attachKubernetesState();
    }

    @Test
    @Description("Should translate the TestResource to a  ProvidedCapability and deploy it")
    void shouldTranslateTestResourceToProvidedCapability() {
        step("Given I have an example Controller that responds to DBMS CapabilityRequirements");
        step("And I have created the ProvidedCapability CRD", () -> {
            getClient().entandoResources().registerCustomResourceDefinition("crd/providedcapabilities.entando.org.crd.yaml");
        });

        step("When I request a namespace scoped, PostgreSQL DBMS Capability for direct deployment",
                () -> runControllerAgainstCustomResource(new TestResource().withNames(MY_NAMESPACE, DEFAULT_DBMS_IN_NAMESPACE).withSpec(
                        new BasicDeploymentSpecBuilder().withDbms(DbmsVendor.POSTGRESQL)
                                .withProvisioningStrategy(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY).build())));
        ProvidedCapability providedCapability = getClient().entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, DEFAULT_DBMS_IN_NAMESPACE);
        TestResource testResource = getClient().entandoResources().load(TestResource.class, MY_NAMESPACE, DEFAULT_DBMS_IN_NAMESPACE);
        step("Then an ProvidedCapability was created:", () -> {
            step("using the DeployDirectly provisioningStrategy",
                    () -> assertThat(providedCapability.getSpec().getProvisioningStrategy())
                            .contains(CapabilityProvisioningStrategy.DEPLOY_DIRECTLY));
            step("a PostgreSQL database",
                    () -> assertThat(providedCapability.getSpec().getImplementation())
                            .contains(StandardCapabilityImplementation.POSTGRESQL));
            step("and it is owned by the TestResource to ensure only changes from the TestResource will change the "
                            + "implementing Kubernetes resources",
                    () -> assertThat(ResourceUtils.customResourceOwns(testResource, providedCapability)));
            step("and it has the correct labels",
                    () -> {
                        assertThat(providedCapability.getMetadata().getLabels())
                                .containsEntry(LabelNames.CAPABILITY.getName(), StandardCapability.DBMS.getCamelCaseName());
                        assertThat(providedCapability.getMetadata().getLabels())
                                .containsEntry(LabelNames.CAPABILITY_IMPLEMENTATION.getName(),
                                        StandardCapabilityImplementation.POSTGRESQL.getCamelCaseName());
                        assertThat(providedCapability.getMetadata().getLabels())
                                .containsEntry(LabelNames.CAPABILITY_PROVISION_SCOPE.getName(),
                                        CapabilityScope.NAMESPACE.getCamelCaseName());
                    });
            attachKubernetesResource("TestResource", providedCapability);
        });
        step("And the resulting DatabaseServiceResult reflects the correct information to connect to the deployed DBMS service", () -> {
            DatabaseConnectionInfo connectionInfo = new ProvidedDatabaseCapability(
                    getClient().entandoResources()
                            .loadCapabilityProvisioningResult(
                                    providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get()));
            Allure.attachment("DatabaseServiceResult", SerializationHelper.serialize(connectionInfo));
            assertThat(connectionInfo.getDatabaseName()).isEqualTo("default_postgresql_dbms_in_namespace");
            assertThat(connectionInfo.getPort()).isEqualTo("5432");
            assertThat(connectionInfo.getInternalServiceHostname())
                    .isEqualTo("default-postgresql-dbms-in-namespace-service.my-namespace.svc.cluster.local");
            assertThat(connectionInfo.getVendor()).isEqualTo(DbmsVendorConfig.POSTGRESQL);
        });
        attachKubernetesState();
    }
}
