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

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.container.DbAwareContainer;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.HasHealthCommand;
import org.entando.kubernetes.controller.spi.container.HasWebContext;
import org.entando.kubernetes.controller.spi.container.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer;
import org.entando.kubernetes.controller.spi.container.SecretToMount;
import org.entando.kubernetes.controller.spi.container.SsoAwareContainer;
import org.entando.kubernetes.controller.spi.container.TrustStoreAwareContainer;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.support.client.DeploymentClient;
import org.entando.kubernetes.controller.support.common.EntandoImageResolver;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class DeploymentCreator extends AbstractK8SResourceCreator {

    public static final String VOLUME_SUFFIX = "volume";
    public static final String CONTAINER_SUFFIX = "container";
    public static final String PORT_SUFFIX = "port";
    public static final int DEFAULT_STARTUP_TIME = 120;
    private Deployment deployment;

    public DeploymentCreator(EntandoCustomResource entandoCustomResource) {
        super(entandoCustomResource);
    }

    public Deployment createDeployment(EntandoImageResolver imageResolver, DeploymentClient deploymentClient, Deployable<?> deployable) {
        final Deployment builtDeployment = newDeployment(imageResolver, deployable, deploymentClient.supportsStartupProbes());
        this.deployment = withDiagnostics(
                () -> deploymentClient.createOrPatchDeployment(entandoCustomResource, builtDeployment,
                        EntandoOperatorSpiConfig.getPodShutdownTimeoutSeconds()),
                () -> builtDeployment);
        return this.deployment;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    private DeploymentSpec buildDeploymentSpec(EntandoImageResolver imageResolver, Deployable<?> deployable, boolean supportStartupProbe) {
        final String nameQualifier = deployable.getQualifier().orElse(null);
        return new DeploymentBuilder()
                .withNewSpec()
                .withNewSelector()
                .withMatchLabels(labelsFromResource(nameQualifier))
                .endSelector()
                //We don't support 0 because we will be waiting for a pod after this
                .withReplicas(Math.max(1, deployable.getReplicas()))
                .withNewTemplate()
                .withNewMetadata()
                .withName(resolveName(nameQualifier, "pod"))
                .withLabels(labelsFromResource(nameQualifier))
                .endMetadata()
                .withNewSpec()
                .withSecurityContext(buildSecurityContext(deployable))
                .withContainers(buildContainers(imageResolver, deployable, supportStartupProbe))
                .withDnsPolicy("ClusterFirst")
                .withRestartPolicy("Always")
                .withServiceAccountName(deployable.getServiceAccountToUse())
                .withVolumes(buildVolumesForDeployable(deployable)).endSpec()
                .endTemplate()
                .endSpec().buildSpec();
    }

    private PodSecurityContext buildSecurityContext(Deployable<?> deployable) {
        if (EntandoOperatorConfig.requiresFilesystemGroupOverride()) {
            return deployable.getFileSystemUserAndGroupId()
                    .map(useAndGroupId -> new PodSecurityContextBuilder().withFsGroup(useAndGroupId).build()).orElse(null);
        }
        return null;
    }

    private List<Volume> buildVolumesForDeployable(Deployable<?> deployable) {
        List<Volume> volumeList = deployable.getContainers().stream()
                .map(this::buildVolumesForContainer)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        if (deployable.getContainers().stream().anyMatch(TrustStoreAwareContainer.class::isInstance) && EntandoOperatorSpiConfig
                .getCertificateAuthoritySecretName().isPresent()) {
            volumeList.add(newSecretVolume(TrustStoreAwareContainer.DEFAULT_TRUSTSTORE_SECRET_TO_MOUNT));
        }
        return volumeList;
    }

    private List<Volume> buildVolumesForContainer(DeployableContainer container) {
        List<Volume> volumes = new ArrayList<>();
        if (container instanceof PersistentVolumeAwareContainer) {
            volumes.add(new VolumeBuilder()
                    .withName(volumeName(container))
                    .withNewPersistentVolumeClaim(
                            NameUtils.standardPersistentVolumeClaim(entandoCustomResource, container.getNameQualifier()), false)
                    .build());
        }
        volumes.addAll(container.getSecretsToMount().stream()
                .map(this::newSecretVolume)
                .collect(Collectors.toList()));
        return volumes;
    }

    private Volume newSecretVolume(SecretToMount secretToMount) {
        return new VolumeBuilder()
                .withName(secretToMount.getSecretName() + "-" + VOLUME_SUFFIX)
                .withNewSecret()
                .withSecretName(secretToMount.getSecretName())
                .endSecret()
                .build();
    }

    private List<Container> buildContainers(EntandoImageResolver imageResolver, Deployable<?> deployable, boolean supportStartupProbes) {
        return deployable.getContainers().stream()
                .map(deployableContainer -> this.newContainer(imageResolver, deployableContainer, supportStartupProbes))
                .collect(Collectors.toList());
    }

    private Container newContainer(EntandoImageResolver imageResolver, DeployableContainer deployableContainer,
            boolean supportStartupProbes) {
        return new ContainerBuilder().withName(deployableContainer.getNameQualifier() + "-" + CONTAINER_SUFFIX)
                .withImage(imageResolver.determineImageUri(deployableContainer.getDockerImageInfo()))
                .withImagePullPolicy(EntandoOperatorConfig.getPullPolicyOverride().orElse("IfNotPresent"))
                .withPorts(buildPorts(deployableContainer))
                .withReadinessProbe(buildReadinessProbe(deployableContainer, supportStartupProbes))
                .withLivenessProbe(buildLivenessProbe(deployableContainer, supportStartupProbes))
                .withStartupProbe(buildStartupProbe(deployableContainer, supportStartupProbes))
                .withVolumeMounts(buildVolumeMounts(deployableContainer))
                .withEnv(determineEnvironmentVariables(deployableContainer))
                .withNewResources()
                .addToLimits(buildResourceLimits(deployableContainer))
                .addToRequests(buildResourceRequests(deployableContainer))
                .endResources()
                .build();
    }

    private List<ContainerPort> buildPorts(DeployableContainer deployableContainer) {
        List<ContainerPort> result = new ArrayList<>();
        result.add(new ContainerPortBuilder().withName(deployableContainer.getNameQualifier() + "-" + PORT_SUFFIX)
                .withContainerPort(deployableContainer.getPrimaryPort()).withProtocol("TCP").build());
        result.addAll(deployableContainer.getAdditionalPorts().stream()
                .map(portSpec -> new ContainerPortBuilder()
                        .withName(portSpec.getName())
                        .withContainerPort(portSpec.getPort())
                        .withProtocol("TCP")
                        .build())
                .collect(Collectors.toList()));
        return result;
    }

    private Map<String, Quantity> buildResourceRequests(DeployableContainer deployableContainer) {
        Map<String, Quantity> result = new ConcurrentHashMap<>();
        if (EntandoOperatorConfig.imposeResourceLimits()) {
            ResourceCalculator resourceCalculator = buildResourceCalculator(deployableContainer);
            result.put("memory", new Quantity(resourceCalculator.getMemoryRequest()));
            result.put("cpu", new Quantity(resourceCalculator.getCpuRequest()));
        }
        return result;
    }

    private ResourceCalculator buildResourceCalculator(DeployableContainer deployableContainer) {
        return deployableContainer instanceof ConfigurableResourceContainer
                ? new ConfigurableResourceCalculator((ConfigurableResourceContainer) deployableContainer)
                : new ResourceCalculator(deployableContainer);

    }

    private Map<String, Quantity> buildResourceLimits(DeployableContainer deployableContainer) {
        Map<String, Quantity> result = new ConcurrentHashMap<>();
        if (EntandoOperatorConfig.imposeResourceLimits()) {
            ResourceCalculator resourceCalculator = buildResourceCalculator(deployableContainer);
            result.put("memory", new Quantity(resourceCalculator.getMemoryLimit()));
            result.put("cpu", new Quantity(resourceCalculator.getCpuLimit()));
        }
        return result;
    }

    private List<VolumeMount> buildVolumeMounts(DeployableContainer deployableContainer) {
        List<VolumeMount> volumeMounts = new ArrayList<>(
                deployableContainer.getSecretsToMount().stream()
                        .map(this::newSecretVolumeMount)
                        .collect(Collectors.toList()));
        if (deployableContainer instanceof TrustStoreAwareContainer && EntandoOperatorSpiConfig.getCertificateAuthoritySecretName()
                .isPresent()) {

            volumeMounts.add(newSecretVolumeMount(TrustStoreAwareContainer.DEFAULT_TRUSTSTORE_SECRET_TO_MOUNT));
        }
        if (deployableContainer instanceof PersistentVolumeAwareContainer) {
            String volumeMountPath = ((PersistentVolumeAwareContainer) deployableContainer).getVolumeMountPath();
            volumeMounts.add(
                    new VolumeMountBuilder()
                            .withMountPath(volumeMountPath)
                            .withName(volumeName(deployableContainer))
                            .withReadOnly(false).build());
        }
        return volumeMounts;

    }

    private VolumeMount newSecretVolumeMount(SecretToMount s) {
        return new VolumeMountBuilder()
                .withName(s.getSecretName() + "-" + VOLUME_SUFFIX)
                .withMountPath(s.getMountPath()).withReadOnly(true).build();
    }

    private Probe buildReadinessProbe(DeployableContainer deployableContainer, boolean assumeStartupProbe) {
        int maximumStartupTimeSeconds = calculateMaximumStartupTimeSeconds(deployableContainer);
        ProbeBuilder builder = buildHealthProbe(deployableContainer);
        if (assumeStartupProbe) {
            //No delay, only allow one failure for accuracy, check every 10 seconds
            builder = builder.withPeriodSeconds(10).withFailureThreshold(1);
        } else {
            //Delay for a 6th of the maximum allowed startup time
            //allow for 5 failures that are spaced out enough time for the
            //container only to fail after the maximum startup time
            final Integer numberOfFailures = EntandoOperatorConfig.getNumberOfReadinessFailures().orElse(6);
            builder = builder.withInitialDelaySeconds(maximumStartupTimeSeconds / numberOfFailures)
                    .withPeriodSeconds(maximumStartupTimeSeconds / numberOfFailures)
                    .withFailureThreshold(numberOfFailures - 1);
        }
        //Liveness checks should be fast but we can be a bit forgiving for readiness probes
        return builder.withTimeoutSeconds(5).build();
    }

    private Integer calculateMaximumStartupTimeSeconds(DeployableContainer deployableContainer) {
        return Math.round(deployableContainer.getMaximumStartupTimeSeconds().orElse(DEFAULT_STARTUP_TIME)
                * EntandoOperatorSpiConfig.getTimeoutAdjustmentRatio());
    }

    private Probe buildLivenessProbe(DeployableContainer deployableContainer, boolean assumeStartupProbe) {
        int maximumStartupTimeSeconds = calculateMaximumStartupTimeSeconds(deployableContainer);
        ProbeBuilder builder = buildHealthProbe(deployableContainer).withPeriodSeconds(10).withFailureThreshold(1).withTimeoutSeconds(3);
        if (!assumeStartupProbe) {
            //Delay the entire maximum allowed startup time and a bit. We don't want the container to get caught in a crash loop
            builder = builder.withInitialDelaySeconds(Math.round(maximumStartupTimeSeconds * 1.2F));
        }
        return builder.build();
    }

    private Probe buildStartupProbe(DeployableContainer deployableContainer, boolean assumeStartupProbe) {
        if (assumeStartupProbe) {
            int maximumStartupTimeSeconds = calculateMaximumStartupTimeSeconds(deployableContainer);
            ProbeBuilder builder = buildHealthProbe(deployableContainer);
            //Stretch out the periodSeconds to allow for 10 attempts during startup
            builder = builder.withPeriodSeconds(maximumStartupTimeSeconds / 10)
                    //Allow for one extra failure after the maximumStartupTime
                    .withFailureThreshold(11);
            return builder.withTimeoutSeconds(5).build();
        } else {
            return null;
        }
    }

    private ProbeBuilder buildHealthProbe(DeployableContainer deployableContainer) {
        ProbeBuilder builder = null;
        if (deployableContainer instanceof HasHealthCommand) {
            builder = new ProbeBuilder().withNewExec().addToCommand("/bin/sh", "-i", "-c",
                    ((HasHealthCommand) deployableContainer).getHealthCheckCommand()).endExec();
        } else if (deployableContainer instanceof HasWebContext) {
            Optional<String> healthCheckPath = ((HasWebContext) deployableContainer).getHealthCheckPath();
            if (healthCheckPath.isPresent()) {
                builder = new ProbeBuilder().withNewHttpGet().withNewPort(deployableContainer.getPrimaryPort())
                        .withPath(healthCheckPath.get()).endHttpGet();
            }
        }
        if (builder == null) {
            builder = new ProbeBuilder().withNewTcpSocket().withNewPort(deployableContainer.getPrimaryPort())
                    .withHost("localhost").endTcpSocket();
        }
        return builder;
    }

    private List<EnvVar> determineEnvironmentVariables(DeployableContainer container) {
        ArrayList<EnvVar> vars = new ArrayList<>();
        if (container instanceof SsoAwareContainer) {
            SsoAwareContainer keycloakAware = (SsoAwareContainer) container;
            vars.addAll(keycloakAware.getSsoVariables());
        }
        if (container instanceof DbAwareContainer) {
            vars.addAll(((DbAwareContainer) container).getDatabaseConnectionVariables());
        }
        if (container instanceof HasWebContext) {
            vars.add(new EnvVar("SERVER_SERVLET_CONTEXT_PATH", ((HasWebContext) container).getWebContextPath(), null));
        }
        if (container instanceof TrustStoreAwareContainer && EntandoOperatorSpiConfig.getCertificateAuthoritySecretName().isPresent()) {
            vars.addAll(((TrustStoreAwareContainer) container).getTrustStoreVariables());
        }
        vars.add(new EnvVar("CONNECTION_CONFIG_ROOT", DeployableContainer.ENTANDO_SECRET_MOUNTS_ROOT, null));
        vars.addAll(container.getEnvironmentVariables());
        if (container instanceof ParameterizableContainer) {
            ParameterizableContainer parameterizableContainer = (ParameterizableContainer) container;
            overrideFromCustomResource(vars, parameterizableContainer.getEnvironmentVariableOverrides());
        }
        return vars;
    }

    private void overrideFromCustomResource(List<EnvVar> vars, List<EnvVar> envVars) {
        for (EnvVar envVar : envVars) {
            vars.removeIf(envVarToEvaluate -> envVarToEvaluate.getName().equals(envVar.getName()));
            vars.add(new EnvVar(envVar.getName(), envVar.getValue(), envVar.getValueFrom()));
        }
    }

    private String volumeName(DeployableContainer container) {
        return resolveName(container.getNameQualifier(), VOLUME_SUFFIX);
    }

    protected Deployment newDeployment(EntandoImageResolver imageResolver, Deployable<?> deployable, boolean supportStartupProbes) {
        return new DeploymentBuilder()
                .withMetadata(
                        fromCustomResource(true, resolveName(deployable.getQualifier().orElse(null), NameUtils.DEFAULT_DEPLOYMENT_SUFFIX),
                                deployable.getQualifier().orElse(null)))
                .withSpec(buildDeploymentSpec(imageResolver, deployable, supportStartupProbes))
                .build();
    }

}
