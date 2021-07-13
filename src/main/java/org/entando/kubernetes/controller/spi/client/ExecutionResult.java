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

package org.entando.kubernetes.controller.spi.client;

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;

public class ExecutionResult implements ExecListener {

    private final CompletableFuture<ExecutionResult> future;
    private ByteArrayOutputStream output = new ByteArrayOutputStream();
    private ByteArrayOutputStream errorChannel = new ByteArrayOutputStream();

    public ExecutionResult(CompletableFuture<ExecutionResult> future) {
        this.future = future;
    }

    @Override
    public void onOpen(Response response) {
        //no implementation required
    }

    @Override
    public void onFailure(Throwable t, Response response) {
        future.completeExceptionally(t);
    }

    @Override
    public void onClose(int code, String reason) {
        future.complete(this);
    }

    @SuppressWarnings("unchecked")
    public int getCode() {
        final List<String> strings = toLines(errorChannel);
        if (!strings.isEmpty()) {
            try {
                final Map<String, Object> map = new ObjectMapper().readValue(strings.get(0), Map.class);

                if ("Success".equals(map.get("status"))) {
                    return 0;
                }

                final Map<String, Object> details = (Map<String, Object>) map.get("details");
                final List<Map<String, Object>> causes = (List<Map<String, Object>>) details.get("causes");
                final String message = (String) causes.get(0).get("message");
                return Integer.parseInt(message);
            } catch (IOException | NullPointerException e) {
                return -1;
            }
        } else {
            return 0;
        }
    }

    public boolean hasFailed() {
        return getCode() != 0;
    }

    public ByteArrayOutputStream getOutput() {
        return output;
    }

    public ByteArrayOutputStream getErrorChannel() {
        return errorChannel;
    }

    private List<String> toLines(ByteArrayOutputStream error) {
        return ioSafe(() -> IOUtils.readLines(new ByteArrayInputStream(error.toByteArray()), StandardCharsets.UTF_8));
    }

    public List<String> getOutputLines() {
        return toLines(output);
    }
}
