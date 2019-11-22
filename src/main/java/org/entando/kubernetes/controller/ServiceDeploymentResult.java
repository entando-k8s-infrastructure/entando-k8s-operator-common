package org.entando.kubernetes.controller;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBackend;

public class ServiceDeploymentResult extends AbstractServiceResult {

    protected final Ingress ingress;

    public ServiceDeploymentResult(Service service, Ingress ingress) {
        super(service);
        this.ingress = ingress;
    }

    public String getExternalBaseUrlForPort(String portName) {
        return getExternalHostUrl() + getHttpIngressPathForPort(portName).getPath();
    }

    public String getExternalHostUrl() {
        String protocol = isTlsEnabled() ? "https" : "http";
        return protocol + "://" + ingress.getSpec().getRules().get(0).getHost();
    }

    public String getExternalBaseUrl() {
        return getExternalHostUrl() + getHttpIngressPath().getPath();
    }

    public String getInternalBaseUrl() {
        return "http://" + getInternalServiceHostname() + ":" + getPort() + getHttpIngressPath().getPath();
    }

    protected boolean isTlsEnabled() {
        return ofNullable(ingress.getSpec().getTls())
                .map(list -> !list.isEmpty())
                .orElse(false);
    }

    private HTTPIngressPath getHttpIngressPath() {
        if (hasMultiplePorts()) {
            throw new IllegalStateException("Cannot make assumption to use a port as there are multiple ports");
        }
        return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream().filter(this::matchesService)
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    private boolean hasMultiplePorts() {
        return service.getSpec().getPorts().size() > 1;
    }

    private HTTPIngressPath getHttpIngressPathForPort(String portName) {
        return ingress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                .filter(path -> this.matchesServiceAndPortName(path, portName))
                .findFirst().orElseThrow(IllegalStateException::new);
    }

    private boolean matchesServiceAndPortName(HTTPIngressPath httpIngressPath, String portName) {
        IngressBackend backend = httpIngressPath.getBackend();
        return (matchesThisService(backend) || matchesDelegatingService(backend)) && hasMatchingServicePortNamed(backend, portName);
    }

    private boolean hasMatchingServicePortNamed(IngressBackend backend, String portName) {
        return service.getSpec().getPorts().stream()
                .anyMatch(servicePort -> portName.equals(servicePort.getName()) && backend.getServicePort().getIntVal()
                        .equals(servicePort.getPort()));
    }

    private boolean matchesService(HTTPIngressPath httpIngressPath) {
        IngressBackend backend = httpIngressPath.getBackend();
        return (matchesThisService(backend) || matchesDelegatingService(backend)) && hasMatchingServicePort(backend);
    }

    private boolean hasMatchingServicePort(IngressBackend backend) {
        return service.getSpec().getPorts().stream()
                .anyMatch(servicePort -> backend.getServicePort().getIntVal().equals(servicePort.getPort()));
    }

    private boolean matchesThisService(IngressBackend backend) {
        return backend.getServiceName().equals(service.getMetadata().getName());
    }

    private boolean matchesDelegatingService(IngressBackend backend) {
        return backend.getServiceName().endsWith("-to-" + service.getMetadata().getName());
    }

    public Ingress getIngress() {
        return ingress;
    }
}
