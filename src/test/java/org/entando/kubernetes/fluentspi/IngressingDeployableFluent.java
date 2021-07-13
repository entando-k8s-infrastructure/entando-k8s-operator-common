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

import java.util.Optional;
import org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable;
import org.entando.kubernetes.controller.spi.result.DefaultExposedDeploymentResult;

public class IngressingDeployableFluent<N extends IngressingDeployableFluent<N>> extends SsoAwareDeployableFluent<N>
        implements PublicIngressingDeployable<DefaultExposedDeploymentResult> {

    private String ingressHostName;
    private String ingressName;
    private String ingressNamespace;
    private boolean ingressRequired;
    private String fileUploadLimit;
    private String tlsSecretName;
    private String publicClientId;

    @Override
    public String getIngressName() {
        return this.ingressName;
    }

    public N withPublicClientId(String publicClientId) {
        this.publicClientId = publicClientId;
        return thisAsN();
    }

    public N withIngressName(String ingressName) {
        this.ingressName = ingressName;
        return thisAsN();
    }

    @Override
    public String getIngressNamespace() {
        return this.ingressNamespace;
    }

    public N withIngressNamespace(String ingressNamespace) {
        this.ingressNamespace = ingressNamespace;
        return thisAsN();
    }

    @Override
    public boolean isIngressRequired() {
        return this.ingressRequired;
    }

    public N withIngressRequired(boolean ingressRequired) {
        this.ingressRequired = ingressRequired;
        return thisAsN();
    }

    @Override
    public Optional<String> getFileUploadLimit() {
        return Optional.ofNullable(this.fileUploadLimit);
    }

    public N withFileUploadLimit(String fileUploadLimit) {
        this.fileUploadLimit = fileUploadLimit;
        return thisAsN();
    }

    @Override
    public Optional<String> getTlsSecretName() {
        return Optional.ofNullable(this.tlsSecretName);
    }

    public N withTlsSecretName(String tlsSecretName) {
        this.tlsSecretName = tlsSecretName;
        return thisAsN();
    }

    @Override
    public Optional<String> getIngressHostName() {
        return Optional.ofNullable(this.ingressHostName);
    }

    public N withIngressHostName(String ingressHostName) {
        this.ingressHostName = ingressHostName;
        return thisAsN();
    }

    @Override
    public Optional<String> getPublicClientId() {
        return Optional.ofNullable(publicClientId);
    }
}
