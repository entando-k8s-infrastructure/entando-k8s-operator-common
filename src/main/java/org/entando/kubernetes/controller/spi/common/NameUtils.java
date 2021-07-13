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

import java.beans.Introspector;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class NameUtils {

    public static final String URL_KEY = "url";
    public static final String INTERNAL_URL_KEY = "internalUrl";
    public static final String DB_NAME_QUALIFIER = "db";
    public static final String DEFAULT_SERVICE_SUFFIX = "service";
    public static final String DEFAULT_SERVER_QUALIFIER = "server";
    public static final String DEFAULT_INGRESS_SUFFIX = "ingress";
    public static final String MAIN_QUALIFIER = "main";
    public static final String DB_QUALIFIER = "db";
    public static final String SSO_QUALIFIER = "sso";
    public static final String DEFAULT_DEPLOYMENT_SUFFIX = "deployment";
    public static final String DEFAULT_PVC_SUFFIX = "pvc";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern pattern = Pattern.compile("(?=[A-Z][a-z])");

    private NameUtils() {
    }

    /**
     * Useful for labelvalues and container names.
     */
    public static String shortenTo63Chars(String s) {
        if (s.length() > 63) {
            s = s.substring(0, 63 - 4) + randomNumeric(4);
        }
        return s;
    }

    public static String randomNumeric(int size) {
        String suffix;
        do {
            //+1 to avoid Long.MIN_VALUE that stays negative after Math.abs
            suffix = String.valueOf(Math.abs(SECURE_RANDOM.nextLong() + 1));
        } while (suffix.length() < size);
        return suffix.substring(suffix.length() - size);
    }

    public static String camelCaseToDashDelimited(String in) {
        final String replacement = "-";
        return camelCaseToDelimited(in, replacement).toLowerCase(Locale.ROOT);
    }

    private static String camelCaseToDelimited(String in, String delimiter) {
        return pattern.matcher(Introspector.decapitalize(in)).replaceAll(delimiter);
    }

    public static String snakeCaseOf(String in) {
        return in.replace("-", "_").replace(".", "_");
    }

    public static String controllerNameOf(EntandoCustomResource customResource) {
        return "entando-k8s-" + camelCaseToDashDelimited(customResource.getKind().substring("Entando".length())) + "-controller";
    }

    public static String upperSnakeCaseOf(String camelCase) {
        return camelCaseToDelimited(camelCase, "_").toUpperCase(Locale.ROOT);
    }

    public static String databaseCompliantName(EntandoCustomResource resource, String nameQualifier, DbmsVendorConfig dbmsVendorConfig) {
        StringBuilder idealDatabaseName = new StringBuilder(NameUtils.snakeCaseOf(resource.getMetadata().getName()));
        if (nameQualifier != null) {
            idealDatabaseName.append("_").append(NameUtils.snakeCaseOf(nameQualifier));
        }
        if (idealDatabaseName.length() > dbmsVendorConfig.getMaxNameLength()) {
            return idealDatabaseName.substring(0, dbmsVendorConfig.getMaxNameLength() - 3) + randomNumeric(3);
        }
        return idealDatabaseName.toString();

    }

    public static String standardIngressName(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-" + DEFAULT_INGRESS_SUFFIX;
    }

    public static String standardServiceName(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-" + DEFAULT_SERVICE_SUFFIX;
    }

    public static String standardServiceName(EntandoCustomResource resource, String qualifier) {
        if (NameUtils.MAIN_QUALIFIER.equals(qualifier)) {
            return standardServiceName(resource);
        } else {
            return resource.getMetadata().getName() + "-" + qualifier + "-" + DEFAULT_SERVICE_SUFFIX;
        }
    }

    public static String standardDeployment(EntandoCustomResource resource) {
        return resource.getMetadata().getName() + "-" + DEFAULT_DEPLOYMENT_SUFFIX;
    }

    public static String standardPersistentVolumeClaim(EntandoCustomResource resource, String containerQualifier) {
        return resource.getMetadata().getName() + "-" + containerQualifier + "-" + DEFAULT_PVC_SUFFIX;
    }

    public static String lowerDashDelimitedOf(String name) {
        return name.replace("_", "-").toLowerCase(Locale.ROOT);
    }

    public static String standardAdminSecretName(EntandoCustomResource keycloakServer) {
        return keycloakServer.getMetadata().getName() + "-admin-secret";
    }
}
