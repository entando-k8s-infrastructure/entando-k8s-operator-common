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

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.Map.Entry;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class TrustStoreHelper {

    public static final String DEFAULT_TRUSTSTORE_SECRET = "entando-default-truststore";
    public static final String CERT_SECRET_MOUNT_ROOT = "/etc/entando/certs";
    public static final String TRUSTSTORE_SETTINGS_KEY = "TRUSTSTORE_SETTINGS";
    public static final String TRUST_STORE_FILE = "store.jks";
    public static final String TRUST_STORE_PATH =
            CERT_SECRET_MOUNT_ROOT + File.separatorChar + DEFAULT_TRUSTSTORE_SECRET + File.separatorChar + TRUST_STORE_FILE;
    public static final String JAVA_TOOL_OPTIONS = "JAVA_TOOL_OPTIONS";

    private TrustStoreHelper() {
    }

    @SuppressWarnings("squid:S2068")//Because it is not a hardcoded password
    public static Secret newTrustStoreSecret(Secret caCertSecret) {
        char[] trustStorePassword = SecretUtils.randomAlphanumeric(20).toCharArray();
        return new SecretBuilder()
                .withNewMetadata()
                .withNamespace(caCertSecret.getMetadata().getNamespace())
                .withName(DEFAULT_TRUSTSTORE_SECRET)
                .endMetadata()
                .addToData(TRUST_STORE_FILE, buildBase64TrustStoreFrom(caCertSecret, trustStorePassword))
                .addToStringData(
                        TRUSTSTORE_SETTINGS_KEY,
                        format("-Djavax.net.ssl.trustStore=%s -Djavax.net.ssl.trustStorePassword=%s", TRUST_STORE_PATH,
                                new String(trustStorePassword)))
                .build();
    }

    public static void trustCertificateAuthoritiesIn(Secret secret) {
        safely(() -> {
            char[] trustStorePassword = SecretUtils.randomAlphanumeric(20).toCharArray();
            KeyStore keyStore = buildKeystoreFrom(secret.getData());
            Path tempFile = Files.createTempFile(Path.of(EntandoOperatorSpiConfig.getSafeTempFileDirectory()), "trust-store", ".jks");
            try (OutputStream stream = Files.newOutputStream(tempFile)) {
                keyStore.store(stream, trustStorePassword);
            }
            System.setProperty("javax.net.ssl.trustStore", tempFile.normalize().toAbsolutePath().toString());
            System.setProperty("javax.net.ssl.trustStorePassword", new String(trustStorePassword));
            return null;
        });
    }

    private static <T> T safely(ExceptionSafe<T> t) {
        try {
            return t.invoke();
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new IllegalStateException(e);
        }
    }

    interface ExceptionSafe<T> {

        T invoke() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException;
    }

    private static String buildBase64TrustStoreFrom(Secret caSecret, char[] trustStorePassword) {
        return safely(() -> {
            KeyStore keyStore = buildKeystoreFrom(caSecret.getData());
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                keyStore.store(outputStream, trustStorePassword);
                return new String(Base64.getEncoder().encode(outputStream.toByteArray()), StandardCharsets.UTF_8);
            }
        });
    }

    private static KeyStore buildKeystoreFrom(Map<String, String> certs)
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        certs.entrySet().forEach(cert -> importCert(keyStore, cert));
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
        trustManagerFactory.init((KeyStore) null);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
        for (X509Certificate certificate : trustManager.getAcceptedIssuers()) {
            keyStore.setCertificateEntry(certificate.getSubjectX500Principal().getName(), certificate);
        }
        return keyStore;
    }

    private static void importCert(KeyStore keyStore, Entry<String, String> certPath) {
        safely(() -> {
            try (InputStream stream = new ByteArrayInputStream(Base64.getDecoder().decode(certPath.getValue()))) {
                for (Certificate cert : CertificateFactory.getInstance("x.509").generateCertificates(stream)) {
                    keyStore.setCertificateEntry(((X509Certificate) cert).getSubjectX500Principal().getName(), cert);
                }
            }
            return null;
        });
    }

}
