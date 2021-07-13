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

package org.entando.kubernetes.fluentspi;

import org.entando.kubernetes.controller.spi.deployable.SsoAwareDeployable;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;

public class SsoAwareDeployableFluent<N extends SsoAwareDeployableFluent<N>> extends SecretiveDeployableFluent<N> implements
        SsoAwareDeployable<DefaultExposedDeploymentResult> {

    private SsoConnectionInfo ssoConnectionInfo;
    private SsoClientConfig ssoClientConfig;

    public N withSsoClientConfig(SsoClientConfig ssoClientConfig) {
        this.ssoClientConfig = ssoClientConfig;
        getContainers().stream()
                .filter(IngressingContainerFluent.class::isInstance)
                .map(IngressingContainerFluent.class::cast)
                .forEach(c -> c.withSsoClientConfig(ssoClientConfig));
        return thisAsN();
    }

    @Override
    public SsoConnectionInfo getSsoConnectionInfo() {
        return this.ssoConnectionInfo;
    }

    @Override
    public SsoClientConfig getSsoClientConfig() {
        return ssoClientConfig;
    }

    @Override
    public <C extends DeployableContainerFluent<C>> C withContainer(C container) {
        if (this.ssoClientConfig != null && container instanceof SsoAwareContainerFluent) {
            ((SsoAwareContainerFluent<?>) container).withSsoClientConfig(this.ssoClientConfig);
        }
        return super.withContainer(container);
    }

    public N withSsoConnectionInfo(SsoConnectionInfo ssoConnectionInfo) {
        this.ssoConnectionInfo = ssoConnectionInfo;
        getContainers().stream()
                .filter(SsoAwareContainerFluent.class::isInstance)
                .map(SsoAwareContainerFluent.class::cast)
                .forEach(c -> c.withSsoConnectionInfo(ssoConnectionInfo));
        return thisAsN();
    }

}
