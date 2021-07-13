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

package org.entando.kubernetes.controller.support.common;

import org.entando.kubernetes.controller.spi.common.ConfigProperty;

public enum EntandoOperatorConfigProperty implements ConfigProperty {
    /*
    Config to resolve Entando Docker Images
     */
    ENTANDO_DOCKER_IMAGE_VERSION_FALLBACK,
    ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE,
    ENTANDO_DOCKER_REGISTRY_FALLBACK,
    ENTANDO_DOCKER_REGISTRY_OVERRIDE,
    ENTANDO_DOCKER_IMAGE_ORG_FALLBACK,
    ENTANDO_DOCKER_IMAGE_ORG_OVERRIDE,
    ENTANDO_DOCKER_IMAGE_INFO_CONFIGMAP,
    ENTANDO_DOCKER_IMAGE_INFO_NAMESPACE,

    /*
    K8S Operator operational config
     */
    ENTANDO_K8S_OPERATOR_GC_CONTROLLER_PODS,
    ENTANDO_K8S_OPERATOR_SECURITY_MODE,
    ENTANDO_K8S_OPERATOR_IMAGE_PULL_SECRETS,
    ENTANDO_K8S_OPERATOR_DISABLE_PVC_GARBAGE_COLLECTION,
    ENTANDO_K8S_OPERATOR_SERVICEACCOUNT,
    ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS,
    ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO,
    ENTANDO_K8S_OPERATOR_FORCE_DB_PASSWORD_RESET,
    ENTANDO_K8S_OPERATOR_PULL_POLICY_OVERRIDE,

    /*
    TLS config
     */
    ENTANDO_TLS_SECRET_NAME,
    ENTANDO_ASSUME_EXTERNAL_HTTPS_PROVIDER,
    ENTANDO_USE_AUTO_CERT_GENERATION,
    ENTANDO_DISABLE_KEYCLOAK_SSL_REQUIREMENT,
    /*
    Misc config
     */
    // creates a dns mapping from the specified hostname *.*.*.*.nip.io to the corresponding ip *.*.*.* . typically it should point to
    // hosting machine ip address (https://nip.io/)
    ENTANDO_DEFAULT_ROUTING_SUFFIX,
    ENTANDO_CLUSTER_INFRASTRUCTURE_SECRET_NAME,
    ENTANDO_REQUIRES_FILESYSTEM_GROUP_OVERRIDE,
    ENTANDO_INGRESS_CLASS,
    ENTANDO_FORCE_EXTERNAL_ACCESS_TO_KEYCLOAK,
    ENTANDO_NAMESPACES_TO_OBSERVE,
    ENTANDO_NAMESPACES_OF_INTEREST, ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE, ENTANDO_NUMBER_OF_READINESS_FAILURES;

}
