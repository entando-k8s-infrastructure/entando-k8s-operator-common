package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.util.Map;
import java.util.Optional;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.entando.kubernetes.controller.k8sclient.IngressClient;
import org.entando.kubernetes.model.EntandoCustomResource;

@K8SLogger
@Dependent
public class DefaultIngressClient implements IngressClient {

    private final DefaultKubernetesClient client;

    @Inject
    public DefaultIngressClient(DefaultKubernetesClient client) {
        this.client = client;
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    //Because it is 127.0.0.1
    public static String resolveMasterHostname(DefaultKubernetesClient client) {
        String host = client.settings().getMasterUrl().getHost();
        if ("127.0.0.1".equals(host)) {
            //This will only happen on single node installations. Generally it will return some ip/domain name that resolves to the master
            // Retrieve IP from node API and discard local IP
            Node masterNode = client.nodes().list().getItems().get(0);
            Optional<NodeAddress> masterNodeAddress = masterNode.getStatus().getAddresses().stream()
                    .filter(na -> na.getType().equalsIgnoreCase("InternalIP")).findFirst();
            host = masterNodeAddress.orElseThrow(() -> new RuntimeException("Impossible to retrieve node internal IP address"))
                    .getAddress();
        }
        return host;
    }

    @Override
    public Ingress addHttpPath(Ingress ingress, HTTPIngressPath httpIngressPath, Map<String, String> annotations) {
        return client.extensions().ingresses().inNamespace(ingress.getMetadata().getNamespace())
                .withName(ingress.getMetadata().getName()).edit()
                .editSpec().editFirstRule().editHttp()
                .addNewPathLike(httpIngressPath)
                .endPath().endHttp().endRule().endSpec()
                .editMetadata().addToAnnotations(annotations).endMetadata()
                .done();
    }

    @Override
    public String getMasterUrlHost() {
        return resolveMasterHostname(this.client);
    }

    @Override
    public Ingress createIngress(EntandoCustomResource peerInNamespace, Ingress ingress) {
        return client.extensions().ingresses().inNamespace(peerInNamespace.getMetadata().getNamespace()).create(ingress);
    }

    @Override
    public Ingress loadIngress(String namespace, String name) {
        return client.extensions().ingresses().inNamespace(namespace).withName(name).get();
    }
}
