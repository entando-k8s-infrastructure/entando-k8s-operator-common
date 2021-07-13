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

package org.entando.kubernetes.controller.support.common;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import org.assertj.core.api.Assertions;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorComplianceMode;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class EntandoOperatorConfigTest {

    @AfterEach
    void resetPropertiesTested() {
        System.clearProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty());
    }

    @Test
    void testDeploymentType() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty());
        Assertions.assertThat(EntandoOperatorConfig.getOperatorDeploymentType()).isEqualTo(OperatorDeploymentType.HELM);
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(),
                OperatorDeploymentType.OLM.getName());
        Assertions.assertThat(EntandoOperatorConfig.getOperatorDeploymentType()).isEqualTo(OperatorDeploymentType.OLM);
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(),
                OperatorDeploymentType.HELM.getName());
        Assertions.assertThat(EntandoOperatorConfig.getOperatorDeploymentType()).isEqualTo(OperatorDeploymentType.HELM);
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(), "invalid");
        Assertions.assertThat(EntandoOperatorConfig.getOperatorDeploymentType()).isEqualTo(OperatorDeploymentType.HELM);
    }

    @Test
    void testIsClusterScope() {
        //Because it will use the current namespace
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty());
        Assertions.assertThat(EntandoOperatorConfig.isClusterScopedDeployment()).isFalse();
        //OLM contract
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(),
                OperatorDeploymentType.OLM.getName());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty());
        Assertions.assertThat(EntandoOperatorConfig.isClusterScopedDeployment()).isTrue();
        //Using current namespace again
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(),
                OperatorDeploymentType.HELM.getName());
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty());
        Assertions.assertThat(EntandoOperatorConfig.isClusterScopedDeployment()).isFalse();
        //The Helm deployment expects "*" for cluster scope
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DEPLOYMENT_TYPE.getJvmSystemProperty(),
                OperatorDeploymentType.HELM.getName());
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty(), "*");
        Assertions.assertThat(EntandoOperatorConfig.isClusterScopedDeployment()).isTrue();
    }

    @Test
    void testAccessibleNamespaces() {
        //The Helm deployment expects "*" for cluster scope
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.getJvmSystemProperty(), "namespace1, namespace2");
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_OF_INTEREST.getJvmSystemProperty(), "namespace2, namespace3");
        assertThat(EntandoOperatorConfig.getAllAccessibleNamespaces(), containsInAnyOrder("namespace1", "namespace2", "namespace3"));
        assertThat(EntandoOperatorConfig.getAllAccessibleNamespaces().size(), is(3));
    }

    @Test
    void testComplianceMode() {
        System.clearProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty());
        assertThat(EntandoOperatorSpiConfig.getComplianceMode(), is(EntandoOperatorComplianceMode.COMMUNITY));
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty(),
                EntandoOperatorComplianceMode.REDHAT.getName());
        assertThat(EntandoOperatorSpiConfig.getComplianceMode(), is(EntandoOperatorComplianceMode.REDHAT));
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty(),
                EntandoOperatorComplianceMode.COMMUNITY.getName());
        assertThat(EntandoOperatorSpiConfig.getComplianceMode(), is(EntandoOperatorComplianceMode.COMMUNITY));
        System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE.getJvmSystemProperty(), "invalid");
        assertThat(EntandoOperatorSpiConfig.getComplianceMode(), is(EntandoOperatorComplianceMode.COMMUNITY));
    }

    @Test
    void testSecurityMode() {
        System.clearProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE.getJvmSystemProperty());
        assertThat(EntandoOperatorConfig.getOperatorSecurityMode(), is(SecurityMode.LENIENT));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE.getJvmSystemProperty(),
                SecurityMode.STRICT.getName());
        assertThat(EntandoOperatorConfig.getOperatorSecurityMode(), is(SecurityMode.STRICT));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE.getJvmSystemProperty(),
                SecurityMode.LENIENT.getName());
        assertThat(EntandoOperatorConfig.getOperatorSecurityMode(), is(SecurityMode.LENIENT));
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_SECURITY_MODE.getJvmSystemProperty(), "invalid");
        assertThat(EntandoOperatorConfig.getOperatorSecurityMode(), is(SecurityMode.LENIENT));
    }

}
