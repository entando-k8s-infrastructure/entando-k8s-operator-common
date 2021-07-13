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

import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public interface ServiceAccountClient {

    String createRoleBindingIfAbsent(EntandoCustomResource peerInNamespace,
            RoleBinding roleBinding);

    RoleBinding loadRoleBinding(EntandoCustomResource peerInNamespace, String name);

    String createRoleIfAbsent(EntandoCustomResource peerInNamespace, Role role);

    Role loadRole(EntandoCustomResource peerInNamespace, String name);

    DoneableServiceAccount findOrCreateServiceAccount(EntandoCustomResource peerInNamespace, String name);
}
