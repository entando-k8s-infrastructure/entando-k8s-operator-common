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

package org.entando.kubernetes.controller.support.client.doubles;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.support.client.DoneableServiceAccount;
import org.entando.kubernetes.controller.support.client.ServiceAccountClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class ServiceAccountClientDouble extends AbstractK8SClientDouble implements ServiceAccountClient {

    public ServiceAccountClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
    }

    @Override
    public String createRoleBindingIfAbsent(EntandoCustomResource peerInNamespace, RoleBinding roleBinding) {
        getNamespace(peerInNamespace).putRoleBinding(roleBinding);
        return roleBinding.getMetadata().getName();
    }

    @Override
    public RoleBinding loadRoleBinding(EntandoCustomResource peerInNamespace, String name) {
        return getNamespace(peerInNamespace).getRoleBinding(name);
    }

    @Override
    public String createRoleIfAbsent(EntandoCustomResource peerInNamespace, Role role) {
        getNamespace(peerInNamespace).putRole(role);
        return role.getMetadata().getName();
    }

    @Override
    public Role loadRole(EntandoCustomResource peerInNamespace, String name) {
        return getNamespace(peerInNamespace).getRole(name);
    }

    @Override
    public DoneableServiceAccount findOrCreateServiceAccount(EntandoCustomResource peerInNamespace,
            String name) {
        ServiceAccount serviceAccount = getNamespace(peerInNamespace).getServiceAccount(name);
        if (serviceAccount == null) {
            serviceAccount = new ServiceAccountBuilder().withNewMetadata().withName(name)
                    .withNamespace(peerInNamespace.getMetadata().getNamespace())
                    .endMetadata().build();
            getNamespace(peerInNamespace).putServiceAccount(serviceAccount);
        }
        return new DoneableServiceAccount(serviceAccount, sa -> {
            getNamespace(peerInNamespace).putServiceAccount(sa);
            return sa;

        });
    }
}
