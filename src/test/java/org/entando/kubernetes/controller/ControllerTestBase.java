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

package org.entando.kubernetes.controller;

import static io.qameta.allure.Allure.step;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.test.common.ControllerTestHelper;
import org.junit.jupiter.api.BeforeEach;

public abstract class ControllerTestBase implements ControllerTestHelper {

    private final SimpleK8SClientDouble clientDouble = new SimpleK8SClientDouble();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    @BeforeEach
    final void beforeEach() {
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CONTROLLER_POD_NAME.getJvmSystemProperty(), "test-pod");
        step("Given I have registered a CustomResourceDefinition for the resource kind 'TestResource'", () -> {
            getClient().entandoResources().registerCustomResourceDefinition("testresources.test.org.crd.yaml");

        });
    }

    @Override
    public final SimpleK8SClientDouble getClient() {
        return this.clientDouble;
    }

    @Override
    public final ScheduledExecutorService getScheduler() {
        return this.scheduler;
    }
}
