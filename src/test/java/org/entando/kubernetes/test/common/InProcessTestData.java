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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.client.InfrastructureConfig;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.doubles.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.support.command.CreateExternalServiceCommand;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.JeeServer;
import org.entando.kubernetes.model.KeycloakAwareSpec;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseServiceBuilder;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureBuilder;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerBuilder;
import org.entando.kubernetes.model.keycloakserver.StandardKeycloakImage;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.PluginSecurityLevel;

public interface InProcessTestData {

    String DEPLOYMENT_LABEL_NAME = KubeUtils.DEPLOYMENT_LABEL_NAME;
    String ENTANDO_PLUGIN_LABEL_NAME = "EntandoPlugin";
    String ENTANDO_APP_LABEL_NAME = "EntandoApp";
    String ENTANDO_CLUSTER_INFRASTRUCTURE_LABEL_NAME = "EntandoClusterInfrastructure";
    String KEYCLOAK_SERVER_LABEL_NAME = "EntandoKeycloakServer";
    String ENTANDO_APP_PLUGIN_LINK_LABEL_NAME = "EntandoAppPluginLink";
    String KEYCLOAK_SECRET = "ASDFASDFAS";
    String TCP = "TCP";
    String MY_KEYCLOAK = "my-keycloak";
    String MY_KEYCLOAK_ADMIN_USERNAME = "entando_keycloak_admin";
    String MY_KEYCLOAK_ADMIN_PASSWORD = MY_KEYCLOAK_ADMIN_USERNAME + "123";
    String TLS_SECRET = "tls-secret";
    String MY_KEYCLOAK_TLS_SECRET = MY_KEYCLOAK + "-" + TLS_SECRET;
    String NAMESPACE = "namespace";
    String MY_KEYCLOAK_NAMESPACE = MY_KEYCLOAK + "-" + NAMESPACE;
    String MY_CLUSTER_INFRASTRUCTURE = "my-eci";
    String MY_CLUSTER_INFRASTRUCTURE_TLS_SECRET = MY_CLUSTER_INFRASTRUCTURE + "-" + TLS_SECRET;
    String MY_CLUSTER_INFRASTRUCTURE_NAMESPACE = MY_CLUSTER_INFRASTRUCTURE + "-" + NAMESPACE;
    String MY_APP = "my-app";
    String MY_APP_TLS_SECRET = MY_APP + "-" + TLS_SECRET;
    String MY_APP_NAMESPACE = MY_APP + "-" + NAMESPACE;
    String MY_PLUGIN = "my-plugin";
    String MY_PLUGIN_NAMESPACE = MY_PLUGIN + "-" + NAMESPACE;
    String MY_KEYCLOAK_HOSTNAME = "access.192.168.0.100.nip.io";
    String MY_KEYCLOAK_BASE_URL = "http://" + MY_KEYCLOAK_HOSTNAME + "/auth";

    default EntandoKeycloakServer newEntandoKeycloakServer() {
        return new EntandoKeycloakServerBuilder()
                .withNewMetadata()
                .withName(MY_KEYCLOAK)
                .withNamespace(MY_KEYCLOAK_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withDefault(true)
                .withReplicas(2)
                .withStandardImage(StandardKeycloakImage.KEYCLOAK)
                .withIngressHostName(MY_KEYCLOAK_HOSTNAME)
                .withDbms(DbmsVendor.MYSQL)
                //                .withTlsSecretName(MY_KEYCLOAK_TLS_SECRET)
                .endSpec()
                .build();
    }

    default EntandoClusterInfrastructure newEntandoClusterInfrastructure() {
        return new EntandoClusterInfrastructureBuilder()
                .withNewMetadata()
                .withName(MY_CLUSTER_INFRASTRUCTURE)
                .withNamespace(MY_CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsVendor.MYSQL)
                .withIngressHostName("entando-infra.192.168.0.100.nip.io")
                .withReplicas(3)
                .withDefault(true)
                .withTlsSecretName(MY_CLUSTER_INFRASTRUCTURE_TLS_SECRET)
                .endSpec()
                .build();
    }

    default EntandoApp newTestEntandoApp() {
        return new EntandoAppBuilder()
                .withNewMetadata()
                .withName(MY_APP)
                .withNamespace(MY_APP_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withStandardServerImage(JeeServer.WILDFLY)
                .withDbms(DbmsVendor.MYSQL)
                .withIngressHostName("myapp.192.168.0.100.nip.io")
                .withReplicas(1)
                .withTlsSecretName(MY_APP_TLS_SECRET)
                .endSpec()
                .build();
    }

    default EntandoPlugin newTestEntandoPlugin() {
        return new EntandoPluginBuilder()
                .withNewMetadata()
                .withName(MY_PLUGIN)
                .withNamespace(MY_PLUGIN_NAMESPACE)
                .endMetadata()
                .withNewSpec()
                .withImage("entando/myplugin")
                .withDbms(DbmsVendor.MYSQL)
                .withReplicas(2)
                .withIngressPath("/myplugin")
                .withHealthCheckPath("/actuator/health")
                .withSecurityLevel(PluginSecurityLevel.STRICT)
                .addNewRole("some-role", "role-name")
                .addNewPermission("myplugin", "plugin-admin")
                .addNewConnectionConfigName("pam-connection")
                .endSpec()
                .build();
    }

    default <T extends KeycloakAwareSpec> KeycloakConnectionConfig emulateKeycloakDeployment(SimpleK8SClient<?> client) {
        Secret secret = new SecretBuilder().withNewMetadata().withName(KeycloakName.DEFAULT_KEYCLOAK_ADMIN_SECRET)
                .endMetadata()
                .addToStringData(SecretUtils.USERNAME_KEY, MY_KEYCLOAK_ADMIN_USERNAME)
                .addToStringData(SecretUtils.PASSSWORD_KEY, MY_KEYCLOAK_ADMIN_PASSWORD)
                .build();
        client.secrets().overwriteControllerSecret(secret);
        ConfigMap configMap = new ConfigMapBuilder().withNewMetadata().withName(KeycloakName.DEFAULT_KEYCLOAK_CONNECTION_CONFIG)
                .endMetadata()
                .addToData(NameUtils.URL_KEY, MY_KEYCLOAK_BASE_URL)
                .addToData(NameUtils.INTERNAL_URL_KEY, MY_KEYCLOAK_BASE_URL)
                .build();
        client.secrets().overwriteControllerConfigMap(configMap);
        return new KeycloakConnectionConfig(secret, configMap);
    }

    default <T extends KeycloakAwareSpec> void emulateClusterInfrastuctureDeployment(SimpleK8SClient<?> client) {
        EntandoClusterInfrastructure dummyClusterInfrastructure = newEntandoClusterInfrastructure();
        ConfigMap configMap = new ConfigMapBuilder().withNewMetadata()
                .withName(InfrastructureConfig.connectionConfigMapNameFor(dummyClusterInfrastructure))
                .endMetadata()
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_CLIENT_ID_KEY, "asdf")
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY, "http://som.com/asdf")
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY, "http://som.com/asdf")
                .build();
        client.secrets().createConfigMapIfAbsent(dummyClusterInfrastructure, configMap);
    }

    default DatabaseServiceResult emulateDatabasDeployment(SimpleK8SClient<EntandoResourceClientDouble> client) {
        client.entandoResources().createOrPatchEntandoResource(newTestEntandoApp());
        final EntandoDatabaseService entandoDatabaseService = newEntandoDatabaseService();
        return new CreateExternalServiceCommand(entandoDatabaseService).execute(client);
    }

    default EntandoDatabaseService newEntandoDatabaseService() {
        return new EntandoDatabaseServiceBuilder()
                .withNewMetadata()
                .withNamespace(MY_APP_NAMESPACE)
                .withName("my-ds")
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsVendor.MYSQL)
                .withHost("localhost")
                .withPort(3306)
                .withDatabaseName("my_db")
                .endSpec()
                .build();
    }
}
