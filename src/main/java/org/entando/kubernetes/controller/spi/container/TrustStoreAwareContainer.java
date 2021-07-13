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

package org.entando.kubernetes.controller.spi.container;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;

public interface TrustStoreAwareContainer extends DeployableContainer {

    SecretToMount DEFAULT_TRUSTSTORE_SECRET_TO_MOUNT = new SecretToMount(TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET,
            TrustStoreHelper.CERT_SECRET_MOUNT_ROOT + File.separatorChar + TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET);

    default List<EnvVar> getTrustStoreVariables() {
        List<EnvVar> vars = new ArrayList<>();
        vars.add(new EnvVar(TrustStoreHelper.JAVA_TOOL_OPTIONS,
                null,
                SecretUtils.secretKeyRef(TrustStoreHelper.DEFAULT_TRUSTSTORE_SECRET, TrustStoreHelper.TRUSTSTORE_SETTINGS_KEY)));
        return vars;
    }
}
