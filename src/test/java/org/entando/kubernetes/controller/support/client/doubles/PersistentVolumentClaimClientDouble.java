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

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import java.util.concurrent.ConcurrentHashMap;
import org.entando.kubernetes.controller.support.client.PersistentVolumeClaimClient;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class PersistentVolumentClaimClientDouble extends AbstractK8SClientDouble implements
        PersistentVolumeClaimClient {

    public PersistentVolumentClaimClientDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces, ClusterDouble cluster) {
        super(namespaces, cluster);
    }

    @Override
    public PersistentVolumeClaim createPersistentVolumeClaimIfAbsent(EntandoCustomResource peerInNamespace,
            PersistentVolumeClaim persistentVolumeClaim) {
        if (peerInNamespace == null) {
            return null;
        }
        getNamespace(peerInNamespace).putPersistentVolumeClaim(persistentVolumeClaim);
        return persistentVolumeClaim;
    }

    @Override
    public PersistentVolumeClaim loadPersistentVolumeClaim(EntandoCustomResource peerInNamespace,
            String name) {
        if (name == null) {
            return null;
        }
        return getNamespace(peerInNamespace).getPersistentVolumeClaim(name);
    }
}
