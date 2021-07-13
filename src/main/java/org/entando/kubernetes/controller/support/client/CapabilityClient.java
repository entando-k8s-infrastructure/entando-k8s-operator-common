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

package org.entando.kubernetes.controller.support.client;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.entando.kubernetes.controller.spi.capability.SerializedCapabilityProvisioningResult;
import org.entando.kubernetes.model.capability.ProvidedCapability;

public interface CapabilityClient {

    Optional<ProvidedCapability> providedCapabilityByName(String namespace, String name);

    Optional<ProvidedCapability> providedCapabilityByLabels(Map<String, String> labels);

    Optional<ProvidedCapability> providedCapabilityByLabels(String namespace, Map<String, String> labels);

    ProvidedCapability createOrPatchCapability(ProvidedCapability providedCapability);

    ProvidedCapability waitForCapabilityCompletion(ProvidedCapability capability, int timeoutSeconds) throws TimeoutException;

    ProvidedCapability waitForCapabilityCommencement(ProvidedCapability capability, int timeoutSeconds) throws TimeoutException;

    String getNamespace();

    SerializedCapabilityProvisioningResult buildCapabilityProvisioningResult(ProvidedCapability providedCapability);

}
