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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.entando.kubernetes.controller.spi.examples.springboot.SampleSpringBootDeployableContainer;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.model.app.EntandoAppSpecBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class ConfigurableResourceCalculatorTest {

    @AfterEach
    @BeforeEach
    void resetProvidedRatio() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty());
    }

    @Test
    void calculateRequestsWithDefaultRatioAndNoOverride() {
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpec()), null, null, null));
        assertThat(resourceCalculator.getCpuLimit(), is("1000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("1024Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("250m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("256Mi"));
    }

    @Test
    void calculateRequestsWithProvidedRatioNoOverride() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty(), "0.2");
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpec()), null, null, null));
        assertThat(resourceCalculator.getCpuLimit(), is("1000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("1024Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("200m"));
        //rounded
        assertThat(resourceCalculator.getMemoryRequest(), is("205Mi"));
    }

    @Test
    void calculateRequestsWithDefaultRatioButWithALimitOverride() {
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withMemoryLimit("2Gi")
                        .withCpuLimit("10.4")
                        .endResourceRequirements().build()), null, null, null));
        assertThat(resourceCalculator.getCpuLimit(), is("10.4"));
        assertThat(resourceCalculator.getMemoryLimit(), is("2Gi"));
        //Note: decrease the UOM here by one level
        assertThat(resourceCalculator.getCpuRequest(), is("2600m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("500Mi"));
    }

    @Test
    void calculateRequestsWithProvidedRatioButWithALimitOverride() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_REQUEST_TO_LIMIT_RATIO.getJvmSystemProperty(), "0.2");
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withMemoryLimit("2048Mi")
                        .withCpuLimit("2000m")
                        .endResourceRequirements().build()), null, null, null));
        assertThat(resourceCalculator.getCpuLimit(), is("2000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("2048Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("400m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("410Mi"));
    }

    @Test
    void calculateRequestsWithDefaultRatioButWithRequestAndLimitOverrides() {
        ConfigurableResourceCalculator resourceCalculator = new ConfigurableResourceCalculator(
                new SampleSpringBootDeployableContainer<>(new EntandoApp(new EntandoAppSpecBuilder().withNewResourceRequirements()
                        .withMemoryLimit("2048Mi")
                        .withMemoryRequest("256Mi")
                        .withCpuLimit("2000m")
                        .withCpuRequest("50m")
                        .endResourceRequirements().build()), null, null, null));
        assertThat(resourceCalculator.getCpuLimit(), is("2000m"));
        assertThat(resourceCalculator.getMemoryLimit(), is("2048Mi"));
        assertThat(resourceCalculator.getCpuRequest(), is("50m"));
        assertThat(resourceCalculator.getMemoryRequest(), is("256Mi"));
    }
}
