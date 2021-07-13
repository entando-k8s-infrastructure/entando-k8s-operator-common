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

import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.examples.springboot.SpringBootDeployable;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.app.EntandoAppSpec;
import org.entando.kubernetes.model.common.ServerStatus;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class IngressCreatorTest implements InProcessTestData {

    SimpleK8SClient<?> client = new SimpleK8SClientDouble();
    EntandoApp entandoApp = new EntandoAppBuilder(newTestEntandoApp())
            .editSpec()
            .withTlsSecretName("original-tls-secret")
            .withIngressHostName("originalhost.com")
            .endSpec()
            .build();

    @AfterEach
    @BeforeEach
    void cleanUp() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_LIMITS.getJvmSystemProperty());
    }

    @Test
    void testEdit() {
        SpringBootDeployable<EntandoAppSpec> deployable = new SpringBootDeployable<>(entandoApp, null, null);
        ServiceCreator serviceCreator = new ServiceCreator(entandoApp);
        serviceCreator.createService(client.services(), deployable);
        IngressCreator creator = new IngressCreator(entandoApp);
        creator.createIngress(client.ingresses(), deployable, serviceCreator.getService(), new ServerStatus(NameUtils.MAIN_QUALIFIER));
        assertThat(creator.getIngress().getSpec().getRules().get(0).getHost(), is("originalhost.com"));
        assertThat(creator.getIngress().getSpec().getTls().get(0).getHosts().get(0), is("originalhost.com"));
        assertThat(creator.getIngress().getSpec().getTls().get(0).getSecretName(), is("original-tls-secret"));
        EntandoApp newEntandoApp = new EntandoAppBuilder(newTestEntandoApp())
                .editSpec()
                .withTlsSecretName("new-tls-secret")
                .withIngressHostName("newhost.com")
                .endSpec()
                .build();
        IngressCreator editingCreator = new IngressCreator(newEntandoApp);
        editingCreator
                .createIngress(client.ingresses(), new SpringBootDeployable<>(newEntandoApp, null, null), serviceCreator.getService(),
                        new ServerStatus(NameUtils.MAIN_QUALIFIER));
        assertThat(editingCreator.getIngress().getSpec().getRules().get(0).getHost(), is("newhost.com"));
        assertThat(editingCreator.getIngress().getSpec().getTls().get(0).getHosts().get(0), is("newhost.com"));
        assertThat(editingCreator.getIngress().getSpec().getTls().get(0).getSecretName(), is("new-tls-secret"));

    }

}
