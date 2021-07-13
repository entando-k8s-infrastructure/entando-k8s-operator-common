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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfigProperty;

public class CertificateSecretHelper {

    public static final String TEST_CA_SECRET = "test-ca-secret";
    public static final String TEST_TLS_SECRET = "test-tls-secret";

    private CertificateSecretHelper() {

    }

    public static List<Secret> buildCertificateSecretsFromDirectory(String namespace, Path tlsPath) {
        try {
            List<Secret> secrets = new ArrayList<>();
            Path caCert = tlsPath.resolve("ca.crt");

            final Path path = caCert.toAbsolutePath();
            if (path.toFile().exists()) {
                if (EntandoOperatorSpiConfig.getCertificateAuthoritySecretName().isEmpty()) {
                    System.setProperty(EntandoOperatorSpiConfigProperty.ENTANDO_CA_SECRET_NAME.getJvmSystemProperty(),
                            TEST_CA_SECRET);
                    final Secret caSecret = new SecretBuilder()
                            .withNewMetadata()
                            .withName(TEST_CA_SECRET)
                            .withNamespace(namespace)
                            .endMetadata()
                            .addToData(caCert.toFile().getName(), Base64.getEncoder().encodeToString(Files.readAllBytes(caCert)))
                            .build();
                    secrets.add(caSecret);
                    secrets.add(TrustStoreHelper.newTrustStoreSecret(caSecret));
                }
            }
            if (tlsPath.resolve("tls.crt").toFile().exists() && tlsPath.resolve("tls.key").toFile().exists()) {
                System.setProperty(EntandoOperatorConfigProperty.ENTANDO_TLS_SECRET_NAME.getJvmSystemProperty(),
                        TEST_TLS_SECRET);
                secrets.add(
                        new SecretBuilder().withNewMetadata().withName(TEST_TLS_SECRET).withNamespace(namespace)
                                .endMetadata()
                                .withType("kubernetes.io/tls")
                                .addToData("tls.crt", Base64.getEncoder().encodeToString(Files.readAllBytes(tlsPath.resolve("tls.crt"))))
                                .addToData("tls.key", Base64.getEncoder().encodeToString(Files.readAllBytes(tlsPath.resolve("tls.key"))))
                                .build());
            }
            return secrets;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
