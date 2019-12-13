package org.entando.kubernetes.controller.inprocesstest.legacy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.util.Optional;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.KeycloakConnectionSecret;
import org.entando.kubernetes.controller.common.example.EntandoDbConsumingDeployable;
import org.entando.kubernetes.controller.database.DatabaseDeployable;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.inprocesstest.FluentTraversals;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestUtil;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.LabeledArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.app.EntandoApp;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
//in execute component test
@Tag("in-process")
public class DeployEntandoDbConsumerTest implements InProcessTestUtil, FluentTraversals {

    @Spy
    private final SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble();
    @Mock
    private SimpleKeycloakClient keycloakClient;

    @Test
    public void testit() {
        //Given I have an EntandoApp
        EntandoApp app = newTestEntandoApp();
        //And a database deployment
        DatabaseServiceResult databaseServiceResult = new DeployCommand<>(new DatabaseDeployable(DbmsImageVendor.POSTGRESQL, app, "db"))
                .execute(client, Optional.empty());
        //And a KeycloakConnectionsecret
        KeycloakConnectionSecret keycloakConnectionSecret = new KeycloakConnectionSecret(new SecretBuilder()
                .withNewMetadata().endMetadata()
                .addToStringData(KubeUtils.URL_KEY, "http://test.domain/auth")
                .addToStringData(KubeUtils.USERNAME_KEY, "keycloak-admin")
                .addToStringData(KubeUtils.PASSSWORD_KEY, "P@ssw0rd!")
                .build());
        //When I deploy the entandoApp using the KeycloakConsumingDeployable implementations
        new DeployCommand<>(new EntandoDbConsumingDeployable(keycloakConnectionSecret, app, databaseServiceResult))
                .execute(client, Optional.of(keycloakClient));
        //Then I need to see
        LabeledArgumentCaptor<Pod> dbJobCaptor = forResourceWithLabel(Pod.class, ENTANDO_APP_LABEL_NAME, MY_APP)
                .andWithLabel(KubeUtils.DB_JOB_LABEL_NAME, MY_APP + "-db-preparation-job");
        verify(client.pods()).runToCompletion(eq(app), dbJobCaptor.capture());
        Pod dbJob = dbJobCaptor.getValue();
        //That the portdb schema was initialized
        Container portdbInitializer = theInitContainerNamed(MY_APP + "-portdb-schema-creation-job").on(dbJob);
        verifyStandardSchemaCreationVariables(MY_APP + "-db-admin-secret", MY_APP + "-portdb-secret", portdbInitializer,
                DbmsImageVendor.POSTGRESQL);
        assertThat(theVariableNamed(DATABASE_SERVER_HOST).on(portdbInitializer),
                is(MY_APP + "-db-service." + MY_APP_NAMESPACE + ".svc.cluster.local"));
        assertThat(theVariableNamed(DATABASE_NAME).on(portdbInitializer), is("my_app_db"));

        //That the servdb schema was initialized
        Container servdbInitializer = theInitContainerNamed(MY_APP + "-servdb-schema-creation-job").on(dbJob);

        verifyStandardSchemaCreationVariables(MY_APP + "-db-admin-secret", MY_APP + "-servdb-secret", servdbInitializer,
                DbmsImageVendor.POSTGRESQL);
        assertThat(theVariableNamed(DATABASE_SERVER_HOST).on(servdbInitializer),
                is(MY_APP + "-db-service." + MY_APP_NAMESPACE + ".svc.cluster.local"));
        assertThat(theVariableNamed(DATABASE_NAME).on(servdbInitializer), is("my_app_db"));
        //And the Entando port and serv dbs have been populated
        Container dbPopulationJob = theInitContainerNamed(MY_APP + "-server-db-population-job").on(dbJob);
        assertThat(dbPopulationJob.getCommand().get(2), is("/entando-common/init-db-from-deployment.sh"));
        assertThat(theVariableNamed("PORTDB_URL").on(dbPopulationJob),
                is("jdbc:postgresql://" + MY_APP + "-db-service." + MY_APP_NAMESPACE + ".svc.cluster.local:5432/my_app_db"));
        assertThat(theVariableNamed("SERVDB_URL").on(dbPopulationJob),
                is("jdbc:postgresql://" + MY_APP + "-db-service." + MY_APP_NAMESPACE + ".svc.cluster.local:5432/my_app_db"));
    }
}