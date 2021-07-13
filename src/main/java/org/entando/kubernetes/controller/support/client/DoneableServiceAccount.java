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

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccountFluentImpl;
import java.util.function.UnaryOperator;

public class DoneableServiceAccount extends ServiceAccountFluentImpl<DoneableServiceAccount> {

    private final UnaryOperator<ServiceAccount> action;
    private final Object hashCode = new Object();

    public DoneableServiceAccount(UnaryOperator<ServiceAccount> action) {
        this(new ServiceAccountBuilder().withNewMetadata().endMetadata().build(), action);
    }

    public DoneableServiceAccount(ServiceAccount serviceAccount, UnaryOperator<ServiceAccount> action) {
        super(serviceAccount);
        this.action = action;
    }

    //TODO get rid of this
    public ServiceAccount done() {
        ServiceAccount buildable = new ServiceAccount(getApiVersion(), getAutomountServiceAccountToken(), buildImagePullSecrets(),
                getKind(),
                buildMetadata(), buildSecrets());
        return withDiagnostics(() -> action.apply(buildable), () -> buildable);
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
