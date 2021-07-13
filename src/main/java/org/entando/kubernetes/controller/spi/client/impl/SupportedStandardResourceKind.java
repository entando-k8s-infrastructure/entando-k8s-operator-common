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

package org.entando.kubernetes.controller.spi.client.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.NameUtils;

public enum SupportedStandardResourceKind {
    SERVICE {
        @SuppressWarnings("unchecked")
        @Override
        public MixedOperation<
                Service,
                ServiceList,
                ServiceResource<Service>
                > getOperation(KubernetesClient client) {
            return client.services();
        }
    },
    DEPLOYMENT {
        @SuppressWarnings("unchecked")
        @Override
        public MixedOperation<
                Deployment,
                DeploymentList,
                RollableScalableResource<Deployment>
                > getOperation(KubernetesClient client) {
            return client.apps().deployments();
        }
    },
    POD {
        @SuppressWarnings("unchecked")
        @Override
        public MixedOperation<
                Pod,
                PodList,
                PodResource<Pod>
                > getOperation(KubernetesClient client) {
            return client.pods();
        }
    },
    INGRESS {
        @SuppressWarnings("unchecked")
        @Override
        public MixedOperation<
                Ingress,
                IngressList,
                Resource<Ingress>
                > getOperation(KubernetesClient client) {
            return client.extensions().ingresses();
        }
    },
    SECRET {
        @SuppressWarnings("unchecked")
        @Override
        public MixedOperation<
                Secret,
                SecretList,
                Resource<Secret>
                > getOperation(KubernetesClient client) {
            return client.secrets();
        }
    },
    PERSISTENT_VOLUME_CLAIM {
        @SuppressWarnings("unchecked")
        @Override
        public MixedOperation<
                PersistentVolumeClaim,
                PersistentVolumeClaimList,
                Resource<PersistentVolumeClaim>
                > getOperation(KubernetesClient client) {
            return client.persistentVolumeClaims();
        }
    };

    SupportedStandardResourceKind() {

    }

    @SuppressWarnings("java:S1172")
    //Because it should have been an abstract method but Java doesn't support that here.
    public <T extends HasMetadata,
            L extends KubernetesResourceList<T>,
            R extends Resource<T>
            > MixedOperation<T, L, R> getOperation(KubernetesClient client) {
        return null;
    }

    public static Optional<SupportedStandardResourceKind> resolveFromKind(String kind) {
        try {
            return Optional.of(valueOf(NameUtils.upperSnakeCaseOf(kind)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
