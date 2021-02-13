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

package org.entando.kubernetes.controller.support.spibase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.ForInternalUseOnly;
import org.entando.kubernetes.controller.spi.container.KeycloakAwareContainer;
import org.entando.kubernetes.model.KeycloakAwareSpec;
import org.entando.kubernetes.model.KeycloakToUse;

public interface KeycloakAwareContainerBase extends KeycloakAwareContainer {

    default Optional<KeycloakToUse> getPreferredKeycloakToUse() {
        return getKeycloakAwareSpec().getKeycloakToUse();
    }

    @JsonIgnore
    @ForInternalUseOnly
    KeycloakAwareSpec getKeycloakAwareSpec();

}