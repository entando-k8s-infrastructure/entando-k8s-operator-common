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

import org.entando.kubernetes.model.common.NamedEnum;

public enum EntandoOperatorSpiConfigProperty implements ConfigProperty, NamedEnum {

    ENTANDO_K8S_OPERATOR_DEFAULT_CLUSTERED_STORAGE_CLASS,
    ENTANDO_K8S_OPERATOR_DEFAULT_NON_CLUSTERED_STORAGE_CLASS,
    ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE,
    ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER,
    ENTANDO_FORCE_EXTERNAL_ACCESS_TO_KEYCLOAK,
    ENTANDO_K8S_OPERATOR_PVC_ACCESSMODE_OVERRIDE,
    ENTANDO_CA_SECRET_NAME,
    ENTANDO_RESOURCE_NAMESPACE,
    ENTANDO_RESOURCE_NAME,
    ENTANDO_RESOURCE_KIND,
    ENTANDO_CONTROLLER_POD_NAME,
    ENTANDO_POD_COMPLETION_TIMEOUT_SECONDS,
    ENTANDO_POD_READINESS_TIMEOUT_SECONDS,
    ENTANDO_TIMEOUT_ADJUSTMENT_RATIO,
    ENTANDO_POD_SHUTDOWN_TIMEOUT_SECONDS;

}
