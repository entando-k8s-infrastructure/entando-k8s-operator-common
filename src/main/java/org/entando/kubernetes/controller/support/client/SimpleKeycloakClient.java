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

import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.model.common.Permission;

public interface SimpleKeycloakClient {

    void login(String address, String username, String password);

    void ensureRealm(String realm);

    void createPublicClient(String realm, String clientId, String domain);

    String prepareClientAndReturnSecret(SsoClientConfig config);

    void updateClient(SsoClientConfig config);

    void assignRoleToClientServiceAccount(String realm, String serviceAccountClientId, Permission serviceRole);
}
