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

package org.entando.kubernetes.controller.support.creators;

import org.entando.kubernetes.controller.spi.container.ConfigurableResourceContainer;
import org.entando.kubernetes.model.common.EntandoResourceRequirements;

public class ConfigurableResourceCalculator extends ResourceCalculator {

    private final EntandoResourceRequirements resourceRequirements;

    public ConfigurableResourceCalculator(ConfigurableResourceContainer container) {
        super(container);
        this.resourceRequirements = container.getResourceRequirementsOverride().orElse(new EntandoResourceRequirements());
    }

    @Override
    public String getMemoryLimit() {
        return resourceRequirements.getMemoryLimit().orElse(super.getMemoryLimit());
    }

    @Override
    public String getMemoryRequest() {
        return this.resourceRequirements.getMemoryRequest().orElse(super.getMemoryRequest());
    }

    @Override
    public String getCpuLimit() {
        return resourceRequirements.getCpuLimit().orElse(super.getCpuLimit());
    }

    @Override
    public String getCpuRequest() {
        return this.resourceRequirements.getCpuRequest().orElse(super.getCpuRequest());
    }
}
