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

package org.entando.kubernetes.controller.spi.command;

public enum SupportedCommand {
    PROCESS_DEPLOYABLE("Input Deployable", "Ouput DeploymentResult"), PROVIDE_CAPABILITY("Required Capability", "Provided Capability");

    private final String inputName;
    private final String outputName;

    SupportedCommand(String inputName, String outputName) {

        this.inputName = inputName;
        this.outputName = outputName;
    }

    public String getInputName() {
        return inputName;
    }

    public String getOutputName() {
        return outputName;
    }
}
