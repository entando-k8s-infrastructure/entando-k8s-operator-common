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

package org.entando.kubernetes.controller.spi.container;

import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;

public class KeycloakName {

    public static final String CLIENT_SECRET_KEY = "clientSecret";
    public static final String CLIENT_ID_KEY = "clientId";
    public static final String DEFAULT_KEYCLOAK_ADMIN_SECRET = "keycloak-admin-secret";
    public static final String DEFAULT_KEYCLOAK_CONNECTION_CONFIG = "keycloak-connection-config";
    public static final String PUBLIC_CLIENT_ID = "entando-web";
    public static final String ENTANDO_DEFAULT_KEYCLOAK_REALM = "entando";

    private KeycloakName() {
        //because this is a utility class
    }

    public static String forTheClientSecret(SsoClientConfig keycloakConfig) {
        return keycloakConfig.getClientId() + "-secret";
    }

}
