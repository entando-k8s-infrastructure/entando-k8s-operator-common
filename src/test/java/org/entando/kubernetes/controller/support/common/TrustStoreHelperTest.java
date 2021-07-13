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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import io.fabric8.kubernetes.api.model.SecretBuilder;
import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrustStoreHelperTest {

    private static final String TRUSTED_CERT =
            "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUM2akNDQWRLZ0F3SUJBZ0lCQVRBTkJna3Foa2lHOXcwQkFRc0ZBREFtTVNRd0lnWURWUVFEREJ0"
                    + "dmNHVnUKYzJocFpuUXRjMmxuYm1WeVFERTFNall6T0RFeU5EWXdIaGNOTVRnd05URTFNVEEwTnpJMldoY05Nak13TlRFMApNVEEwTnpJM1d"
                    + "qQW1NU1F3SWdZRFZRUUREQnR2Y0dWdWMyaHBablF0YzJsbmJtVnlRREUxTWpZek9ERXlORFl3CmdnRWlNQTBHQ1NxR1NJYjNEUUVCQVFVQU"
                    + "E0SUJEd0F3Z2dFS0FvSUJBUURXZng3bFJlWUx0YjBodlNiNmorV3MKRGtGVm5yZDViOXRlUEc4T01QNVNTZDVNaG1qWGRGcytxN0xWTHZNR"
                    + "0FEVEdUZit1TzZia3NHazVwN3NoWHhJVQpJbWxGYVJsbzVwR205QWFGREY0TjJyMlVaTDVVRFl0TjFwa2NoWUt5TXlPdVpEbFZsMzdTcVRP"
                    + "a2lVRFQ2eHY4CjQ1UEtITmVmeWpGUVc3cno3UmJXdmhhWGE3OGpUNVc4REdaRXZZb0NKOUEvbE1vUm5hbTBJQXF3VCt6V0M4SEsKdzVsa0F"
                    + "RSzRuTlQrUS8zR3JLYzNSRVIrYThFRmluQS9jY2tBYWVreUdub2hsRkFJUU9zZEZTMEVZOGlvcHByVwppb1pKN3RaNjFHS2ZJZHNBNlNrek"
                    + "1PRi8zYXoyU1hxVCtWR2VyR1JnSDF2K3dNOUFpR0p4UnZ5L1pTaGJ5M2VSCkFnTUJBQUdqSXpBaE1BNEdBMVVkRHdFQi93UUVBd0lDcERBU"
                    + "EJnTlZIUk1CQWY4RUJUQURBUUgvTUEwR0NTcUcKU0liM0RRRUJDd1VBQTRJQkFRQVQxZmtrNHRnYlUxKzdwQ3hzT1pURjZVcmdjY05kdkp4"
                    + "d3JCVm4zNTlyYVpIRAp0ZnZXYldqZVJDb0NickhiV1lucC9hZkF1Y3Z6UWhwbFViMElGNUxrcGNrWHhkUzQwYWh4RjNmZDBjQUl0K3JPClE"
                    + "2cG9MeUtKbmdncHgwb0VWekliM2p6Q05QYjZLK1F0VitjUzFuV00xZTRZTStyM0RFSkpCTGxZalJDWWtiY24KeEFHMUovaHcxMDBQTWx5N0"
                    + "h5OHNIUzJYWDlsdnVpNHF0M2NwTXArVitrcHRaeUhKNEhCZFhUcGJhMzM0RXJQegpGVWlqNFRvSlE0UERMMEQ1SVlCNU9jODZXbTJCRERIO"
                    + "FdJclY4TSt6SU5iKzN4Y1pnRjhFMFR5Si9qUWpHKzB0Cm5HdGVXdk9NTXQwTC85d0xjN1B1RHg2YUtEK0E4NFF4VnN3NnhubnAKLS0tLS1F"
                    + "TkQgQ0VSVElGSUNBVEUtLS0tLQ==";

    @BeforeEach
    @AfterEach
    void resetTlsProperties() {
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
    }

    @Test
    void testInTrustStore() throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        assertThrows(SSLHandshakeException.class, this::openSelfSignedUrl);
        TrustStoreHelper.trustCertificateAuthoritiesIn(new SecretBuilder().addToData("cert1.crt", TRUSTED_CERT).build());
        try {
            openSelfSignedUrl();
        } catch (SSLHandshakeException e) {
            e.printStackTrace();
            fail();
        }
    }

    private void openSelfSignedUrl() throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        final HttpsURLConnection urlConnection = (HttpsURLConnection) new URL("https://openshift.serv.run:8443/console").openConnection();
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(new KeyManager[0], new TrustManager[]{reloadX509TrustManager()}, new SecureRandom());
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        urlConnection.setSSLSocketFactory(socketFactory);
        urlConnection.connect();
    }

    private TrustManager reloadX509TrustManager() throws NoSuchAlgorithmException, KeyStoreException {
        //Need to reload it to ensure latest file has been read
        final TrustManagerFactory pkix = TrustManagerFactory.getInstance("PKIX");
        pkix.init((KeyStore) null);
        final TrustManager[] trustManagers = pkix.getTrustManagers();
        TrustManager tm = null;
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                tm = trustManager;
            }
        }
        return tm;
    }
}
