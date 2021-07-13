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

package org.entando.kubernetes.controller.support.client.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.entando.kubernetes.controller.support.client.DuplicateExcepion;
import org.entando.kubernetes.controller.support.client.UnauthorizedExcepion;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public final class KubernetesExceptionProcessor {

    private static final int UNAUTHORIZED = 403;
    private static final int CONFLICT = 409;

    private KubernetesExceptionProcessor() {
    }

    public static <T extends HasMetadata> T verifyDuplicateExceptionOnCreate(String controllerNamespace, T resource,
            KubernetesClientException e) {
        if (e.getCode() == UNAUTHORIZED) {
            throw new UnauthorizedExcepion(String.format(
                    "Could not create a %s in namespace %s. If you are running the Entando controllers in STRICT security mode, please "
                            + "check  that you have associated the entando-operator ServiceAccount with a Role that allows the 'CREATE' "
                            + "verb against resources of kind %s ",
                    resource.getKind(), controllerNamespace, resource.getKind()), e);
        } else if (e.getCode() == CONFLICT) {
            throw new DuplicateExcepion(String.format(
                    "Could not create a %s named '%s'in namespace %s because it already exists. This could be because it has already "
                            + "been created as part of the setup. Please verify that the Kubernetes infrastructure represented "
                            + "by this specific %s is in tact. If not, please delete the existing %s and run the command again.",
                    resource.getKind(), resource.getMetadata().getName(), controllerNamespace, resource.getKind(), resource.getKind()), e);
        } else {
            throw e;
        }
    }

    public static String squashDuplicateExceptionOnCreate(EntandoCustomResource peerInNamespace, HasMetadata resource,
            KubernetesClientException e) {
        if (e.getCode() == UNAUTHORIZED) {
            throw new UnauthorizedExcepion(String.format(
                    "Could not create a %s in namespace %s. If you are running the Entando controllers in STRICT security mode,  please "
                            + "check that you have associated the entando-operator ServiceAccount with a Role that allows the 'CREATE' "
                            + "verb against resources of kind %s ",
                    resource.getKind(), peerInNamespace.getMetadata().getNamespace(), resource.getKind()), e);
        } else if (e.getCode() == CONFLICT) {
            //We're generally interested in its name here. 409 guarantees the name already exists, and it is safe to bind to.
            //The client code should not return this object if assumptions have been made about its state
            return resource.getMetadata().getName();
        } else {
            throw e;
        }
    }

    public static RuntimeException processExceptionOnLoad(EntandoCustomResource peerInNamespace, KubernetesClientException e, String kind,
            String name) {
        return processExceptionOnLoad(e, kind, peerInNamespace.getMetadata().getNamespace(), name);
    }

    public static RuntimeException processExceptionOnLoad(KubernetesClientException e, String kind, String namespace, String name) {
        if (e.getCode() == UNAUTHORIZED) {
            return new UnauthorizedExcepion(String.format(
                    "Could not load %s named %s in namespace %s. If you are running the Entando controllers in STRICT security mode, "
                            + "please check  that you have associated the entando-operator ServiceAccount with a Role that allows the "
                            + "'GET' verb against resources of kind %s ",
                    kind, name, namespace, kind), e);
        } else {
            return e;
        }
    }

}
