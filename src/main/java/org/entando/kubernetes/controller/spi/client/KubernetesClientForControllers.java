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

package org.entando.kubernetes.controller.spi.client;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.interruptionSafe;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.retry;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.capability.SerializedCapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.spi.common.ExceptionUtils;
import org.entando.kubernetes.controller.spi.common.FormatUtils;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ServerStatus;

public interface KubernetesClientForControllers {

    String ENTANDO_CRD_NAMES_CONFIG_MAP = "entando-crd-names";
    String ENTANDO_OPERATOR_CONFIG_CONFIGMAP_NAME = "entando-operator-config";

    void prepareConfig();

    <T extends EntandoCustomResource> T waitForCompletion(T customResource, int timeoutSeconds)
            throws TimeoutException;

    String getNamespace();

    Service loadControllerService(String name);

    <T extends EntandoCustomResource> T createOrPatchEntandoResource(T r);

    <T extends EntandoCustomResource> T load(Class<T> clzz, String resourceNamespace, String resourceName);

    default EntandoCustomResource resolveCustomResourceToProcess(Collection<Class<? extends EntandoCustomResource>> supportedTypes) {
        prepareConfig();
        String resourceName = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAME);
        String resourceNamespace = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_NAMESPACE);
        String kind = resolveProperty(EntandoOperatorSpiConfigProperty.ENTANDO_RESOURCE_KIND);
        return load(supportedTypes.stream().filter(c -> c.getSimpleName().equals(kind)).findAny().orElseThrow(() ->
                        new IllegalArgumentException(format("The resourceKind %s was not found in the list of supported types", kind))),
                resourceNamespace, resourceName);

    }

    private String resolveProperty(EntandoOperatorSpiConfigProperty name) {
        return EntandoOperatorConfigBase.lookupProperty(name)
                .orElseThrow(() -> new IllegalStateException(
                        format("No %s specified. Please set either the Environment Variable %s or the System Property %s", name
                                .getCamelCaseName(), name.name(), name.getJvmSystemProperty())));
    }

    SerializedEntandoResource loadCustomResource(String apiVersion, String kind, String namespace, String name);

    HasMetadata loadStandardResource(String kind, String namespace, String name);

    @SuppressWarnings({"java:S106"})
    default ExecutionResult executeAndWait(PodResource<Pod> podResource, String containerName, int timeoutSeconds,
            String... script) throws TimeoutException {
        StringBuilder sb = new StringBuilder();
        for (String s : script) {
            sb.append(s);
            sb.append(" || exit $?\n");
        }
        sb.append("exit $?\n");
        ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes());
        final CompletableFuture<ExecutionResult> future = new CompletableFuture<>();
        ExecutionResult listener = new ExecutionResult(future);
        podResource.inContainer(containerName)
                .readingInput(in)
                .writingOutput(listener.getOutput())
                .writingError(listener.getOutput())
                .writingErrorChannel(listener.getErrorChannel())
                .withTTY()
                .usingListener(listener).exec();
        return interruptionSafe(() -> future.get(timeoutSeconds, TimeUnit.SECONDS));
    }

    default <T extends EntandoCustomResource> Event populateEvent(T customResource, UnaryOperator<EventBuilder> eventPopulator) {
        final EventBuilder doneableEvent = new EventBuilder()
                .withNewMetadata()
                .withNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName() + "-" + NameUtils.randomNumeric(8))
                .withLabels(ResourceUtils.labelsFromResource(customResource))
                .withOwnerReferences(ResourceUtils.buildOwnerReference(customResource))
                .endMetadata()
                .withCount(1)
                .withFirstTimestamp(FormatUtils.format(LocalDateTime.now()))
                .withLastTimestamp(FormatUtils.format(LocalDateTime.now()))
                .withNewSource(NameUtils.controllerNameOf(customResource), null)
                .withNewInvolvedObject()
                .withApiVersion(customResource.getApiVersion())
                .withKind(customResource.getKind())
                .withNamespace(customResource.getMetadata().getNamespace())
                .withName(customResource.getMetadata().getName())
                .withUid(customResource.getMetadata().getUid())
                .withResourceVersion(customResource.getMetadata().getResourceVersion())
                .withFieldPath("status")
                .endInvolvedObject();
        return eventPopulator.apply(doneableEvent).build();
    }

    default <T extends EntandoCustomResource> T issueEventAndPerformStatusUpdate(T customResource, Consumer<T> consumer,
            UnaryOperator<EventBuilder> eventPopulator) {
        final Event event = populateEvent(customResource, eventPopulator);
        issueEvent(customResource, event);
        //Could be updating the status concurrently from multiple Deployables
        return retry(() -> performStatusUpdate(customResource, consumer),
                e -> e instanceof KubernetesClientException
                        && ((KubernetesClientException) e).getCode() == HttpURLConnection.HTTP_CONFLICT,
                4);
    }

    <T extends EntandoCustomResource> T performStatusUpdate(T customResource, Consumer<T> consumer);

    default <T extends EntandoCustomResource> T updateStatus(T customResource, ServerStatus status) {
        return issueEventAndPerformStatusUpdate(customResource,
                t -> t.getStatus().putServerStatus(status),
                e -> e.withType("Normal")
                        .withNewRelated()
                        .withApiVersion("v1")
                        .withKind("Pod")
                        .withNamespace(status.getOriginatingControllerPod().getNamespace().orElse(getNamespace()))
                        .withName(status.getOriginatingControllerPod().getName())
                        .endRelated()
                        .withReason("StatusUpdate")
                        .withMessage(format("The %s  %s/%s received status update for %s ",
                                customResource.getKind(),
                                customResource.getMetadata().getNamespace(),
                                customResource.getMetadata().getName(),
                                status.getQualifier()))
                        .withAction("STATUS_CHANGE")
        );
    }

    default <T extends EntandoCustomResource> T deploymentStarted(T customResource) {
        return updatePhase(customResource, EntandoDeploymentPhase.STARTED);
    }

    default <T extends EntandoCustomResource> T deploymentEnded(T customResource) {
        return issueEventAndPerformStatusUpdate(customResource,
                t -> t.getStatus().updateDeploymentPhase(t.getStatus().calculateFinalPhase(), t.getMetadata().getGeneration()),
                e -> e.withType("Normal")
                        .withReason("PhaseUpdated")
                        .withMessage(format("The deployment of %s  %s/%s was updated  to %s",
                                customResource.getKind(),
                                customResource.getMetadata().getNamespace(),
                                customResource.getMetadata().getName(),
                                customResource.getStatus().calculateFinalPhase().name()))
                        .withAction("PHASE_CHANGE")
        );
    }

    default <T extends EntandoCustomResource> T updatePhase(T customResource, EntandoDeploymentPhase phase) {
        return issueEventAndPerformStatusUpdate(customResource,
                t -> {
                    t.getStatus().updateDeploymentPhase(phase, t.getMetadata().getGeneration());
                    if (phase == EntandoDeploymentPhase.STARTED && t.getStatus().hasFailed()) {
                        t.getStatus().getServerStatuses().forEach(s -> s.setEntandoControllerFailure(null));
                    }
                },
                e -> e.withType("Normal")
                        .withReason("PhaseUpdated")
                        .withMessage(format("The deployment of %s  %s/%s was updated  to %s",
                                customResource.getKind(),
                                customResource.getMetadata().getNamespace(),
                                customResource.getMetadata().getName(),
                                phase.name()))
                        .withAction("PHASE_CHANGE")
        );
    }

    default <T extends EntandoCustomResource> T deploymentFailed(T customResource, Exception cause, String serverQualifier) {
        final EntandoControllerFailure entandoControllerFailure = ExceptionUtils.failureOf(customResource, cause);
        return issueEventAndPerformStatusUpdate(customResource,
                t -> {
                    String qualifierToUse = ofNullable(serverQualifier).orElse(NameUtils.MAIN_QUALIFIER);
                    if (t.getStatus().getServerStatus(qualifierToUse).isEmpty()) {
                        t.getStatus().putServerStatus(new ServerStatus(qualifierToUse));
                    }
                    //Never overwrite existing exceptions. Exceptions are cleared on start event.
                    //More specific exceptions should be set in the commands and creators.
                    //This is more for use from the Controllers
                    t.getStatus().getServerStatus(qualifierToUse).filter(status -> !status.hasFailed())
                            .ifPresent(
                                    newStatus -> newStatus.finishWith(entandoControllerFailure));
                    t.getStatus().updateDeploymentPhase(EntandoDeploymentPhase.FAILED, t.getMetadata().getGeneration());
                },
                e -> e.withType("Error")
                        .withReason("Failed")
                        .withMessage(
                                format("The deployment of %s %s/%s failed due to %s. Fix the root cause and then trigger a "
                                                + "redeployment "
                                                + "by adding the annotation 'entando.org/processing-instruction: force'",
                                        customResource.getKind(),
                                        customResource.getMetadata().getNamespace(),
                                        customResource.getMetadata().getName(),
                                        cause.getMessage()))
                        .withAction("FAILED")
                        .withNewRelated()
                        .withApiVersion("v1")
                        .withKind("Pod")
                        .withNamespace(getNamespace())
                        .withName(EntandoOperatorSpiConfig.getControllerPodName())
                        .endRelated()

        );
    }

    default CapabilityProvisioningResult loadCapabilityProvisioningResult(ServerStatus serverStatus) {
        final String capabilityNamespace = serverStatus.getOriginatingCustomResource().getNamespace()
                .orElseThrow(IllegalArgumentException::new);
        Service service = serverStatus.getServiceName().map(s -> (Service) loadStandardResource("Service", capabilityNamespace, s))
                .orElse(null);
        Secret adminSecret = serverStatus.getAdminSecretName()
                .map(s -> (Secret) loadStandardResource("Secret", capabilityNamespace, s))
                .orElse(null);
        Ingress ingress = serverStatus.getIngressName()
                .map(i -> (Ingress) loadStandardResource("Ingress", capabilityNamespace, i))
                .orElse(null);
        ProvidedCapability providedCapability = load(ProvidedCapability.class, capabilityNamespace,
                serverStatus.getOriginatingCustomResource().getName());
        return new SerializedCapabilityProvisioningResult(providedCapability, service, ingress, adminSecret);

    }

    <T extends EntandoCustomResource> void issueEvent(T customResource, Event event);

    List<Event> listEventsFor(EntandoCustomResource resource);

    ExecutionResult executeOnPod(Pod pod, String containerName, int timeoutSeconds, String... commands) throws TimeoutException;
}
