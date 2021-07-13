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

package org.entando.kubernetes.test.common;

import java.util.HashMap;
import java.util.Map;
import org.entando.kubernetes.controller.spi.common.LabelNames;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public interface CommonLabels {

    default Map<String, String> dbPreparationJobLabels(EntandoCustomResource resource, String deploymentQualifier) {
        Map<String, String> labelsFromResource = labelsFromResource(resource);
        labelsFromResource.put(LabelNames.JOB_KIND.getName(), LabelNames.JOB_KIND_DB_PREPARATION.getName());
        labelsFromResource.put(LabelNames.DEPLOYMENT_QUALIFIER.getName(), deploymentQualifier);
        return labelsFromResource;
    }

    default Map<String, String> labelsFromResource(EntandoCustomResource resource) {
        Map<String, String> labels = new HashMap<>();
        labels.put(resource.getKind(), resource.getMetadata().getName());
        labels.put(LabelNames.RESOURCE_KIND.getName(), resource.getKind());
        return labels;
    }
}
