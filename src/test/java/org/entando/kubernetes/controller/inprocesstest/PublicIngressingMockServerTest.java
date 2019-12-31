package org.entando.kubernetes.controller.inprocesstest;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.entando.kubernetes.client.DefaultSimpleK8SClient;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.controller.test.PodBehavior;
import org.junit.Rule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

/**
 * This is the new approach for testing this module. It uses the Kubernetes mock server. At this point in time, it seems to be a 50/50
 * decision whether to use the mock server or to mock the SimpleClient interfaces. Pros and cons are: <br/> Pros. <br/> We can test most of
 * the Client classes now. <br/> We can trap issues with invalid or missing fields earlier. <br/> Cons: <br/> The resulting tests are about
 * 10 time slower than Mockito level mocking. <br/> The mock server doesn't support Watches, so it was quite difficult to emulate the
 * Websocket logic. <br/> We still don't cover the Keycloak client. <br/> The mockServer doesn't automatically generate statuses, so we
 * still have to set them somehow to test status updates. <br/> Future possibilities: <br/> We can perhaps implement test cases to run in
 * one of three modes: <br/> 1. Mockito mocked. <br/> 2. Mockserver. <br/> 3. Actual server.
 */
@Tag("in-process")
@EnableRuleMigrationSupport
public class PublicIngressingMockServerTest extends PublicIngressingTestBase implements PodBehavior {

    @Rule
    public KubernetesServer server = new KubernetesServer(false, true);
    private SimpleK8SClient defaultSimpleK8SClient;

    @Override
    public SimpleK8SClient getClient() {
        if (defaultSimpleK8SClient == null) {
            defaultSimpleK8SClient = new DefaultSimpleK8SClient(server.getClient());
        }
        return defaultSimpleK8SClient;
    }

}
