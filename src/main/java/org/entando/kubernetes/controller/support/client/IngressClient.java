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

import io.fabric8.kubernetes.api.model.extensions.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.Map;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public interface IngressClient {

    Ingress createIngress(EntandoCustomResource peerInNamespace, Ingress ingress);

    DoneableIngress editIngress(EntandoCustomResource peerInNamespace, String name);

    Ingress loadIngress(String namespace, String name);

    Ingress addHttpPath(Ingress ingress, HTTPIngressPath httpIngressPath, Map<String, String> annotations);

    Ingress removeHttpPath(Ingress ingress, HTTPIngressPath httpIngressPath);

    String getMasterUrlHost();
}
