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

package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.entando.kubernetes.model.EntandoCustomResource;

public interface PatchableClient {

    default <T extends HasMetadata> T patch(EntandoCustomResource peerInNamespace, T deployment,
            MixedOperation<T, ? extends KubernetesResourceList<T>, ? extends Resource<T>> operation) {
        return operation
                .inNamespace(peerInNamespace.getMetadata().getNamespace()).withName(deployment.getMetadata().getName())
                .patch(deployment);
    }

}
