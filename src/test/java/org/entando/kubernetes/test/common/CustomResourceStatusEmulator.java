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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressBuilder;
import io.qameta.allure.Allure;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.common.ConfigProperty;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.ExceptionUtils;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.container.ProvidedSsoCapability;
import org.entando.kubernetes.controller.support.client.EntandoResourceClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;
import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;

public interface CustomResourceStatusEmulator<T extends SimpleK8SClient<? extends EntandoResourceClient>> {

    T getClient();

    ScheduledExecutorService getScheduler();

    default void attachKubernetesResource(String name, Object resource) {
        try {
            Allure.attachment(name, new ObjectMapper(new YAMLFactory()).writeValueAsString(resource));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    default ArgumentMatcher<ProvidedCapability> matchesCapability(StandardCapability capability) {
        return t -> t != null && t.getSpec().getCapability() == capability;
    }

    default <T extends HasMetadata> ArgumentMatcher<T> matchesResource(String namespace, String name) {
        return t -> t != null && namespace.equals(t.getMetadata().getNamespace()) && name.equals(t.getMetadata().getName());
    }

    default <T extends EntandoCustomResource> T putServerStatus(T customResource, int port,
            Map<String, String> derivedDeploymentParameters) {
        return putStatus(customResource, port, derivedDeploymentParameters, new ServerStatus(NameUtils.MAIN_QUALIFIER));
    }

    default <T extends EntandoCustomResource> T putInternalServerStatus(T customResource, int port, ServerStatus status) {
        return putStatus(customResource, port, Collections.emptyMap(), status);
    }

    default <T extends EntandoCustomResource> T putExposedServerStatus(T customResource, String host, int port, ServerStatus status) {
        //TODO this may need to change if we expose two paths on on Deployment
        return putExternalServerStatus(customResource, host, port, status.getWebContexts().values().iterator().next(),
                Collections.emptyMap(), status);
    }

    default <T extends EntandoCustomResource> T putExternalServerStatus(T customResource, String host, int port, String path,
            Map<String, String> derivedParameters) {
        final ServerStatus status = new ServerStatus(NameUtils.MAIN_QUALIFIER);
        return putExternalServerStatus(customResource, host, port, path, derivedParameters, status);
    }

    private <T extends EntandoCustomResource> T putExternalServerStatus(T customResource, String host, int port, String path,
            Map<String, String> derivedParameters, ServerStatus status) {
        status.setExternalBaseUrl(format("https://%s%s", host, path));
        Ingress ingress = getClient().ingresses()
                .loadIngress(customResource.getMetadata().getNamespace(), NameUtils.standardIngressName(customResource));
        if (ingress == null) {
            ingress = getClient().ingresses().createIngress(customResource, new IngressBuilder()
                    .withNewMetadata()
                    .withNamespace(customResource.getMetadata().getNamespace())
                    .withName(NameUtils.standardIngressName(customResource))
                    .endMetadata()
                    .withNewSpec()
                    .addNewRule()
                    .withHost(host)
                    .withNewHttp()
                    .addNewPath()
                    .withNewBackend()
                    .withServiceName(NameUtils.standardServiceName(customResource, status.getQualifier()))
                    .withServicePort(new IntOrString(port))
                    .endBackend()
                    .withPath(path)
                    .endPath()
                    .endHttp()
                    .endRule()
                    .addNewTl()
                    .addNewHost(host)
                    .endTl()
                    .endSpec()
                    .build());
        } else {
            ingress = getClient().ingresses().editIngress(customResource, NameUtils.standardIngressName(customResource))
                    .editSpec()
                    .editFirstRule()
                    .editHttp()
                    .addNewPath()
                    .withNewBackend()
                    .withServiceName(NameUtils.standardServiceName(customResource, status.getQualifier()))
                    .withServicePort(new IntOrString(port))
                    .endBackend()
                    .withPath(path)
                    .endPath()
                    .endHttp()
                    .endRule()
                    .endSpec()
                    .done();
        }
        status.setIngressName(ingress.getMetadata().getName());
        final T updatedCapability = putStatus(customResource, port, derivedParameters, status);
        Ingress finalIngress = ingress;
        step(format("and the Ingress '%s'", ingress.getMetadata().getName()), () ->
                attachKubernetesResource(resolveServiceType(customResource) + " Ingress", finalIngress));
        return updatedCapability;
    }

    private String resolveServiceType(EntandoCustomResource customResource) {
        if (customResource instanceof ProvidedCapability) {
            return ((ProvidedCapability) customResource).getSpec().getCapability().name();
        } else {
            return customResource.getKind();
        }
    }

    private <T extends EntandoCustomResource> T putStatus(T customResource, int port,
            Map<String, String> derivedDeploymentParameters, ServerStatus status) {
        try {
            customResource.getStatus().putServerStatus(status);
            status.withOriginatingCustomResource(customResource);
            final Service service = getClient().services().createOrReplaceService(customResource, new ServiceBuilder()
                    .withNewMetadata()
                    .withNamespace(customResource.getMetadata().getNamespace())
                    .withName(NameUtils.standardServiceName(customResource, status.getQualifier()))
                    .endMetadata()
                    .withNewSpec()
                    .addNewPort()
                    .withPort(port)
                    .endPort()
                    .endSpec()
                    .build());
            status.setServiceName(service.getMetadata().getName());
            step(format("with the %s service '%s'", resolveServiceType(customResource), service.getMetadata().getName()),
                    () ->
                            attachKubernetesResource(resolveServiceType(customResource) + " Service", service));
            final Secret secret = new SecretBuilder()
                    .withNewMetadata()
                    .withNamespace(customResource.getMetadata().getNamespace())
                    .withName(NameUtils.standardAdminSecretName(customResource))
                    .endMetadata()
                    .addToStringData("username", "jon")
                    .addToStringData("password", "password123")
                    .build();
            getClient().secrets().createSecretIfAbsent(customResource, secret);
            step(format("and the admin secret '%s'", secret.getMetadata().getName()), () ->
                    attachKubernetesResource("Admin Secret",
                            getClient().secrets().loadSecret(customResource, secret.getMetadata().getName())));

            status.setAdminSecretName(secret.getMetadata().getName());
            status.withOriginatingControllerPod(getClient().entandoResources().getNamespace(), "my-test-pod");
            derivedDeploymentParameters.forEach(status::putDerivedDeploymentParameter);
            getClient().entandoResources().updateStatus(customResource, status);
            getClient().entandoResources().updatePhase(customResource, EntandoDeploymentPhase.SUCCESSFUL);
            return getClient().entandoResources().reload(customResource);
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

    default void attachSpiResource(String name, Object resource) {
        Allure.attachment(name, SerializationHelper.serialize(resource));
    }

    default void attachEnvironmentVariable(ConfigProperty prop, String value) {
        System.setProperty(prop.getJvmSystemProperty(), value);
        Allure.attachment("Environment Variable", prop.name() + "=" + value);
    }

    default Answer<Object> withADatabaseCapabilityStatus(DbmsVendor vendor, String databaseName) {
        return invocationOnMock -> {
            getScheduler().schedule(() -> {
                Map<String, String> derivedParameters = new HashMap<>();
                derivedParameters.put(ProvidedDatabaseCapability.DATABASE_NAME_PARAMETER, databaseName);
                derivedParameters.put(ProvidedDatabaseCapability.DBMS_VENDOR_PARAMETER, vendor.name().toLowerCase(Locale.ROOT));
                DbmsVendorConfig dbmsVendorConfig = DbmsVendorConfig.valueOf(vendor.name());
                return putServerStatus(invocationOnMock.getArgument(0), dbmsVendorConfig.getDefaultPort(), derivedParameters);
            }, 200L, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

    default Answer<Object> withFailedServerStatus(String qualifier, Exception exception) {
        ServerStatus exposedServerStatus = new ServerStatus(qualifier)
                .withOriginatingControllerPod(getClient().entandoResources().getNamespace(), "test-pod");
        return invocationOnMock -> {
            getScheduler().schedule(() -> {
                try {
                    EntandoCustomResource cr = invocationOnMock.getArgument(0);
                    exposedServerStatus.finishWith(ExceptionUtils.failureOf(cr, exception));
                    cr = getClient().entandoResources().updateStatus(cr, exposedServerStatus);
                    return getClient().entandoResources().updatePhase(cr, EntandoDeploymentPhase.FAILED);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }, 200L, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

    default Answer<Object> withAnSsoCapabilityStatus(String host, String realmName) {
        return invocationOnMock -> {
            getScheduler().schedule(() -> {
                Map<String, String> derivedParameters = new HashMap<>();
                derivedParameters.put(ProvidedSsoCapability.DEFAULT_REALM_PARAMETER, realmName);
                return putExternalServerStatus(invocationOnMock.getArgument(0), host, 8080, "/auth", derivedParameters);

            }, 200L, TimeUnit.MILLISECONDS);
            return invocationOnMock.callRealMethod();
        };
    }

}
