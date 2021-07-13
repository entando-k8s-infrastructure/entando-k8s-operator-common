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

package org.entando.kubernetes.controller.support.command;

import org.entando.kubernetes.controller.spi.capability.CapabilityForResource;
import org.entando.kubernetes.controller.spi.command.CommandStream;
import org.entando.kubernetes.controller.spi.command.DefaultSerializableDeploymentResult;
import org.entando.kubernetes.controller.spi.command.DeserializationHelper;
import org.entando.kubernetes.controller.spi.command.SerializationHelper;
import org.entando.kubernetes.controller.spi.command.SupportedCommand;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.deployable.Deployable;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;

public class InProcessCommandStream implements CommandStream {

    private final SimpleK8SClient<?> simpleK8SClient;
    private final SimpleKeycloakClient keycloakClient;

    /**
     * This class is mainly for some initial testing. It will eventually be replaced by a CommandStream that runs the CLI. NB!!!! Keep the
     * SimpleK8SClient there for testing purposes to ensure the test uses the same podWatcherQueue.
     */
    public InProcessCommandStream(SimpleK8SClient<?> simpleK8SClient, SimpleKeycloakClient keycloakClient) {
        this.simpleK8SClient = simpleK8SClient;
        this.keycloakClient = keycloakClient;
    }

    @Override
    public String process(SupportedCommand supportedCommand, String data, int timeoutSeconds) {
        final int adjustedTimeOutSeconds = Math.round(timeoutSeconds * EntandoOperatorSpiConfig.getTimeoutAdjustmentRatio());
        if (supportedCommand == SupportedCommand.PROCESS_DEPLOYABLE) {
            return processDeployment(data, adjustedTimeOutSeconds);
        } else {
            return provideCapability(data, adjustedTimeOutSeconds);
        }
    }

    private String provideCapability(String capability, int timeoutSeconds) {
        ProvideCapabilityCommand capabilityProvider = new ProvideCapabilityCommand(simpleK8SClient.capabilities());
        final CapabilityForResource r = DeserializationHelper.deserialize(simpleK8SClient.entandoResources(), capability);
        return SerializationHelper
                .serialize(capabilityProvider.execute(r.getResourceInNeed(), r.getCapabilityRequirement(), timeoutSeconds));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String processDeployment(String deployable, int timeoutSeconds) {
        final Deployable<DefaultSerializableDeploymentResult> deserializedDeployable = DeserializationHelper
                .deserialize(simpleK8SClient.entandoResources(), deployable);
        DeployCommand<DefaultSerializableDeploymentResult> command = new DeployCommand(deserializedDeployable);
        final DefaultSerializableDeploymentResult result = command.execute(simpleK8SClient, keycloakClient, timeoutSeconds);
        DefaultSerializableDeploymentResult serializableDeploymentResult = new DefaultSerializableDeploymentResult(null, result.getPod(),
                result.getService(), result.getIngress()).withStatus(result.getStatus());
        return SerializationHelper.serialize(serializableDeploymentResult);
    }
}
