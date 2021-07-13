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

package org.entando.kubernetes.controller.support.client.impl.integrationtesthelpers;

import com.jayway.jsonpath.JsonPath;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.entando.kubernetes.controller.support.common.EntandoOperatorConfig;

public final class HttpTestHelper {

    private static final int CODE_200 = 200;

    private HttpTestHelper() {

    }

    public static int getStatus(String url) {
        try {
            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                try (CloseableHttpResponse response = client.execute(new HttpGet(url))) {
                    return getResponseStatus(response);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("An error occurred while retrieving status of " + url, e);
        }
    }

    private static String getWidgetsPayload(String baseUrl, String token) {
        String widgetEndpoints = baseUrl + "/api/widgets";
        try {
            try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

                HttpGet request = new HttpGet(widgetEndpoints);
                request.addHeader("Authorization", "Bearer " + token);

                try (CloseableHttpResponse response = client.execute(request)) {
                    return getResponseBody(response);
                }
            }

        } catch (IOException e) {
            throw new IllegalStateException("An error occurred while retrieving default widgets code", e);
        }
    }

    public static List<String> getPluginsWidgetsCode(String pluginUrl, String token) {

        String payload = getWidgetsPayload(pluginUrl, token);
        try {
            return JsonPath.read(payload, "$.*.code");
        } catch (Exception e) {
            throw new AssertionError("Could not read plugin widgets: " + payload);
        }
    }

    public static List<String> getEntandoCoreWidgetsCode(String entandoAppUrl, String token) {

        String payload = getWidgetsPayload(entandoAppUrl, token);
        try {
            return JsonPath.read(payload, "$.payload.*.code");
        } catch (Exception e) {
            throw new AssertionError("Could not read plugin widgets: " + payload);
        }
    }

    public static String read(String s) {
        try {
            try (InputStream inputStream = new URL(s).openConnection().getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                IOUtils.copy(inputStream, output);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return "";
        }
    }

    public static Boolean statusOk(String strUrl) {
        try {
            URL url = new URL(strUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            System.out.println(conn.getResponseCode() + ":" + conn.getResponseMessage());
            int responseCode = conn.getResponseCode();

            return Arrays.stream(new int[]{200}).anyMatch(status -> status == responseCode);

        } catch (IOException e) {
            String x = e.toString();
            System.out.println(x);
            return false;
        }
    }

    private static int getResponseStatus(HttpResponse response) {
        return response.getStatusLine().getStatusCode();
    }

    private static String getResponseBody(HttpResponse response) throws IOException {
        try (BufferedReader rd = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()))) {
            StringBuilder result = new StringBuilder();
            String line = rd.readLine();
            while (line != null) {
                result.append(line);
                line = rd.readLine();
            }
            return result.toString();
        }
    }

    public static String getDefaultProtocol() {
        return EntandoOperatorConfig.useAutoCertGeneration() || EntandoOperatorConfig.getTlsSecretName().isPresent() ? "https" : "http";
    }
}
