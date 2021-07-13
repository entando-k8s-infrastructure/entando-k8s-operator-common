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

package org.entando.kubernetes.controller.spi.common;

import java.nio.file.Paths;
import java.util.Optional;

public class EntandoOperatorSpiConfig extends EntandoOperatorConfigBase {

    private EntandoOperatorSpiConfig() {
    }

    public static String getControllerPodName() {
        //Absolutely essential. Fail if not set
        return lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME).orElseThrow(IllegalStateException::new);
    }

    public static boolean assumeExternalHttpsProvider() {
        return lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER)
                .map(Boolean::valueOf).orElse(false);
    }

    public static EntandoOperatorComplianceMode getComplianceMode() {
        return lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE)
                .map(EntandoOperatorComplianceMode::resolve).orElse(EntandoOperatorComplianceMode.COMMUNITY);
    }

    public static boolean forceExternalAccessToKeycloak() {
        return lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_FORCE_EXTERNAL_ACCESS_TO_KEYCLOAK)
                .map(Boolean::valueOf).orElse(false);
    }

    public static Optional<String> getDefaultClusteredStorageClass() {
        return EntandoOperatorConfigBase
                .lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_DEFAULT_CLUSTERED_STORAGE_CLASS);
    }

    public static Optional<String> getPvcAccessModeOverride() {
        return lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_PVC_ACCESSMODE_OVERRIDE);
    }

    public static Optional<String> getDefaultNonClusteredStorageClass() {
        return EntandoOperatorConfigBase
                .lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_DEFAULT_NON_CLUSTERED_STORAGE_CLASS);
    }

    public static Optional<String> getCertificateAuthoritySecretName() {
        return lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CA_SECRET_NAME);
    }

    public static String getSafeTempFileDirectory() {
        if (Paths.get("/deployments").toFile().exists()) {
            return "/deployments";
        } else {
            return "/tmp";
        }
    }

    public static int getPodCompletionTimeoutSeconds() {
        return Math.round(lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS).map(Integer::valueOf)
                .orElse(600) * getTimeoutAdjustmentRatio());
    }

    public static float getTimeoutAdjustmentRatio() {
        return EntandoOperatorConfigBase
                .lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_TIMEOUT_ADJUSTMENT_RATIO).map(Float::valueOf).orElse(1F);
    }

    public static int getPodReadinessTimeoutSeconds() {
        return Math.round(lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_POD_READINESS_TIMEOUT_SECONDS).map(Integer::valueOf)
                .orElse(600) * getTimeoutAdjustmentRatio());
    }

    public static int getPodShutdownTimeoutSeconds() {
        return Math.round(lookupProperty(EntandoOperatorSpiConfigProperty.ENTANDO_POD_SHUTDOWN_TIMEOUT_SECONDS).map(Integer::valueOf)
                .orElse(120) * getTimeoutAdjustmentRatio());
    }
}
