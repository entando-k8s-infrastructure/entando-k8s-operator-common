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

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;

import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.extensions.IngressFluentImpl;
import java.util.function.UnaryOperator;

public class DoneableIngress extends IngressFluentImpl<DoneableIngress> {

    private final UnaryOperator<Ingress> action;
    private final Object hashCode = new Object();

    public DoneableIngress(Ingress ingress, UnaryOperator<Ingress> action) {
        super(ingress);
        this.action = action;
    }

    public Ingress done() {
        Ingress built = new Ingress(getApiVersion(), getKind(), buildMetadata(), buildSpec(), buildStatus());
        return withDiagnostics(() -> action.apply(built), () -> built);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return hashCode.hashCode();
    }
}
