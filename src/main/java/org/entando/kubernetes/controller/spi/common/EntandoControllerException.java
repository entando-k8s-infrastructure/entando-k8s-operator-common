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

package org.entando.kubernetes.controller.spi.common;

import io.fabric8.kubernetes.api.model.HasMetadata;

/*
 * Throw this exception to disrupt the current progression of objects being installed.
 */
public class EntandoControllerException extends RuntimeException {

    private final HasMetadata resource;

    public EntandoControllerException(String message) {
        super(message);
        this.resource = null;
    }

    public EntandoControllerException(Exception cause) {
        super(cause);
        this.resource = null;

    }

    public EntandoControllerException(HasMetadata resource, String message) {
        super(message);
        this.resource = resource;
    }

    public EntandoControllerException(HasMetadata resource, Exception cause) {
        super(cause);
        this.resource = resource;
    }

    public HasMetadata getKubernetesResource() {
        return this.resource;
    }

}
