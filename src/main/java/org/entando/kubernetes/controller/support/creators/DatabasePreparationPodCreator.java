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

package org.entando.kubernetes.controller.support.creators;

import static org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase.lookupProperty;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.DatabasePopulator;
import org.entando.kubernetes.controller.spi.container.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.spi.container.DbAware;
import org.entando.kubernetes.controller.spi.deployable.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.result.DatabaseServiceResult;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.common.EntandoImageResolver;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.EntandoCustomResource;

public class DatabasePreparationPodCreator extends AbstractK8SResourceCreator {

    public DatabasePreparationPodCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public Pod runToCompletion(
            SimpleK8SClient<?> client,
            DbAwareDeployable<?> dbAwareDeployable,
            EntandoImageResolver entandoImageResolver) {
        client.pods().removeAndWait(
                entandoCustomResource.getMetadata().getNamespace(), buildUniqueLabels(dbAwareDeployable.getNameQualifier()));
        return client.pods().runToCompletion(
                buildJobPod(client.secrets(), entandoImageResolver, dbAwareDeployable, dbAwareDeployable.getNameQualifier()));
    }

    private Pod buildJobPod(SecretClient secretClient, EntandoImageResolver entandoImageResolver, DbAwareDeployable<?> dbAwareDeployable,
            String qualifier) {
        return new PodBuilder().withNewMetadata()
                .withNamespace(entandoCustomResource.getMetadata().getNamespace())
                .withOwnerReferences(ResourceUtils.buildOwnerReference(entandoCustomResource))
                .withLabels(buildUniqueLabels(qualifier))
                .withName(entandoCustomResource.getMetadata().getName() + "-dbprep-" + SecretUtils.randomAlphanumeric(4))
                .endMetadata()
                .withNewSpec()
                .withInitContainers(buildContainers(entandoImageResolver, secretClient, dbAwareDeployable))
                .addNewContainer()
                .withName("dummy")
                .withImage(entandoImageResolver.determineImageUri("entando/busybox"))
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

    private Map<String, String> buildUniqueLabels(String qualifier) {
        Map<String, String> labelsFromResource = labelsFromResource();
        labelsFromResource.put(KubeUtils.JOB_KIND_LABEL_NAME, KubeUtils.JOB_KIND_DB_PREPARATION);
        labelsFromResource.put(KubeUtils.DEPLOYMENT_QUALIFIER_LABEL_NAME, qualifier);
        return labelsFromResource;
    }

    private List<Container> buildContainers(EntandoImageResolver entandoImageResolver, SecretClient secretClient,
            DbAwareDeployable<?> deployable) {
        List<Container> result = new ArrayList<>();
        for (DbAware dbAware : deployable.getDbAwareContainers()) {
            Optional<DatabasePopulator> databasePopulator = dbAware.getDatabasePopulator();
            prepareContainersToCreateSchemas(secretClient, entandoImageResolver, dbAware, result);
            databasePopulator
                    .ifPresent(dbp -> result.add(prepareContainerToPopulateSchemas(entandoImageResolver, dbp, dbAware.getNameQualifier())));
        }
        return result;
    }

    private void prepareContainersToCreateSchemas(SecretClient secretClient,
            EntandoImageResolver entandoImageResolver,
            DbAware dbAware, List<Container> containerList) {
        for (DatabaseSchemaConnectionInfo dbSchemaInfo : dbAware.getSchemaConnectionInfo()) {
            containerList.add(buildContainerToCreateSchema(entandoImageResolver, dbSchemaInfo));
            createSchemaSecret(secretClient, dbSchemaInfo.getSchemaSecret());
        }
    }

    private Container prepareContainerToPopulateSchemas(EntandoImageResolver entandoImageResolver, DatabasePopulator databasePopulator,
            String nameQualifier) {
        String dbJobName = NameUtils
                .shortenTo63Chars(entandoCustomResource.getMetadata().getName() + "-" + nameQualifier + "-db-population-job");
        return new ContainerBuilder()
                .withImage(entandoImageResolver.determineImageUri(databasePopulator.getDockerImageInfo()))
                .withImagePullPolicy(EntandoOperatorConfig.getPullPolicyOverride().orElse("IfNotPresent"))
                .withName(dbJobName)
                .withCommand(databasePopulator.getCommand())
                .withEnv(databasePopulator.getEnvironmentVariables()).build();
    }

    private void createSchemaSecret(SecretClient secretClient, Secret secret) {
        secretClient.createSecretIfAbsent(entandoCustomResource, secret);
    }

    private Container buildContainerToCreateSchema(EntandoImageResolver entandoImageResolver,
            DatabaseSchemaConnectionInfo schemaConnectionInfo) {
        String dbJobName = NameUtils
                .shortenTo63Chars(schemaConnectionInfo.getSchemaName().replace("_", "-") + "-schema-creation-job");
        return new ContainerBuilder()
                .withImage(entandoImageResolver
                        .determineImageUri("entando/entando-k8s-dbjob"))
                .withImagePullPolicy("IfNotPresent")
                .withName(dbJobName)
                .withEnv(buildEnvironment(schemaConnectionInfo)).build();
    }

    private List<EnvVar> buildEnvironment(DatabaseSchemaConnectionInfo schemaConnectionInfo) {
        DatabaseServiceResult databaseDeployment = schemaConnectionInfo.getDatabaseServiceResult();
        List<EnvVar> result = new ArrayList<>();
        result.add(new EnvVar("DATABASE_SERVER_HOST", databaseDeployment.getInternalServiceHostname(), null));
        result.add(new EnvVar("DATABASE_SERVER_PORT", databaseDeployment.getPort(), null));
        result.add(new EnvVar("DATABASE_ADMIN_USER", null, buildSecretKeyRef(databaseDeployment, SecretUtils.USERNAME_KEY)));
        result.add(new EnvVar("DATABASE_ADMIN_PASSWORD", null, buildSecretKeyRef(databaseDeployment, SecretUtils.PASSSWORD_KEY)));
        result.add(new EnvVar("DATABASE_NAME", databaseDeployment.getDatabaseName(), null));
        lookupProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_FORCE_DB_PASSWORD_RESET).ifPresent(s ->
                result.add(new EnvVar("FORCE_PASSWORD_RESET", s, null)));
        result.add(new EnvVar("DATABASE_VENDOR", databaseDeployment.getVendor().getVendorConfig().getName(), null));
        result.add(new EnvVar("DATABASE_SCHEMA_COMMAND", "CREATE_SCHEMA", null));
        result.add(new EnvVar("DATABASE_USER", null,
                SecretUtils.secretKeyRef(schemaConnectionInfo.getSchemaSecretName(), SecretUtils.USERNAME_KEY)));
        result.add(new EnvVar("DATABASE_PASSWORD", null,
                SecretUtils.secretKeyRef(schemaConnectionInfo.getSchemaSecretName(), SecretUtils.PASSSWORD_KEY)));
        databaseDeployment.getTablespace().ifPresent(s -> result.add(new EnvVar("TABLESPACE", s, null)));
        if (!databaseDeployment.getJdbcParameters().isEmpty()) {
            result.add(new EnvVar("JDBC_PARAMETERS", databaseDeployment.getJdbcParameters().entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(",")), null));
        }
        return result;

    }

    private EnvVarSource buildSecretKeyRef(DatabaseServiceResult databaseDeployment, String configKey) {
        return SecretUtils.secretKeyRef(databaseDeployment.getDatabaseSecretName(), configKey);
    }

}
