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

package org.entando.kubernetes.test.componenttest.argumentcaptors;

import java.util.List;
import org.entando.kubernetes.controller.spi.deployable.SsoClientConfig;
import org.mockito.Mockito;
import org.mockito.internal.matchers.CapturingMatcher;
import org.mockito.internal.util.Primitives;

public final class KeycloakClientConfigArgumentCaptor {

    private final CapturingMatcher<SsoClientConfig> capturingMatcher;

    @SuppressWarnings("unchecked")
    private KeycloakClientConfigArgumentCaptor(String clientid) {
        capturingMatcher = new MyCapturingMatcher(clientid);
    }

    public static KeycloakClientConfigArgumentCaptor forClientId(String clientid) {
        return new KeycloakClientConfigArgumentCaptor(clientid);
    }

    public SsoClientConfig capture() {
        Mockito.argThat(this.capturingMatcher);
        return Primitives.defaultValue(SsoClientConfig.class);
    }

    public SsoClientConfig getValue() {
        return this.capturingMatcher.getLastValue();
    }

    public List<SsoClientConfig> getAllValues() {
        return this.capturingMatcher.getAllValues();
    }

    public static class MyCapturingMatcher extends CapturingMatcher {

        private final String clientid;

        public MyCapturingMatcher(String clientid) {
            super();
            this.clientid = clientid;
        }

        @Override
        public boolean matches(Object argument) {
            return ((SsoClientConfig) argument).getClientId().equals(clientid);
        }
    }
}
