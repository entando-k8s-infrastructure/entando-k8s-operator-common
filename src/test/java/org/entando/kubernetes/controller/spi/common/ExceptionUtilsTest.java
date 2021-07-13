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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.retry;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.withDiagnostics;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.test.common.ValueHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class ExceptionUtilsTest {

    private long start;

    @Test
    void testIoSafe() {
        assertThatThrownBy(() -> ioSafe(() -> {
            throw new IOException();
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testWithDiagnostics() {
        HasMetadata pod = new PodBuilder()
                .withNewMetadata()
                .withNamespace("my-namespace")
                .withName("my-pod")
                .endMetadata()
                .build();
        assertThatThrownBy(() -> withDiagnostics(() -> {
            throw new IOException();
        }, () -> pod)).isInstanceOf(EntandoControllerException.class);
        assertThatThrownBy(() -> withDiagnostics(() -> {
            throw new IOException();
        }, () -> null)).isInstanceOf(EntandoControllerException.class);
        assertThatThrownBy(() -> withDiagnostics(() -> {
            throw new KubernetesClientException(new StatusBuilder()
                    .withMessage("my message")
                    .withCode(404)
                    .build());
        }, () -> pod)).isInstanceOf(EntandoControllerException.class);
        assertThatThrownBy(() -> withDiagnostics(() -> {
            throw new KubernetesClientException(new StatusBuilder()
                    .withMessage("my message")
                    .withCode(404)
                    .build());
        }, () -> null)).isInstanceOf(KubernetesClientException.class);
    }

    @Test
    void testWithKubernetesClientExceptionToFailure() {
        final EntandoControllerFailure failure = ExceptionUtils
                .failureOf(new PodBuilder().withNewMetadata().withNamespace("my-namespace").withName("my-name").endMetadata().build(),
                        new KubernetesClientException(new StatusBuilder()
                                .withMessage("my message")
                                .withCode(404)
                                .build()));
        assertThat(failure.getFailedObjectName()).isEqualTo("my-name");
        assertThat(failure.getFailedObjectNamespace()).isEqualTo("my-namespace");
        assertThat(failure.getFailedObjectKind()).isEqualTo("Pod");
        assertThat(failure.getFailedObjectApiVersion()).isEqualTo("v1");
        assertThat(failure.getMessage()).isEqualTo("my message");
    }

    @Test
    void testRetry() {
        final ValueHolder<Integer> count = new ValueHolder<>();
        //Success
        this.start = System.currentTimeMillis();
        assertThat(retry(() -> "123", e -> e.getMessage().equals("Now"), 3)).isEqualTo("123");
        assertThat(System.currentTimeMillis() - start).isLessThan(100L);
        //Failed twice
        count.set(0);
        this.start = System.currentTimeMillis();
        assertThat(retry(() -> {
            count.set(count.get() + 1);
            if (count.get() < 2) {
                throw new RuntimeException("Now");
            }
            return count.get().toString();
        }, e -> e.getMessage().equals("Now"), 3)).isEqualTo("2");
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(1000L);
        //Failed more than allowed
        count.set(0);
        this.start = System.currentTimeMillis();
        assertThatThrownBy(() -> retry(() -> {
            count.set(count.get() + 1);
            if (count.get() < 4) {
                throw new RuntimeException("Now");
            }
            return count.get().toString();
        }, e -> e.getMessage().equals("Now"), 3)).matches(throwable -> throwable.getMessage().equals("Now"));
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(5000L);
        //Failed with unanticipated exception
        count.set(0);
        this.start = System.currentTimeMillis();
        assertThatThrownBy(() -> retry(() -> {
            count.set(count.get() + 1);
            if (count.get() < 4) {
                throw new RuntimeException("Then");
            }
            return count.get().toString();
        }, e -> e.getMessage().equals("Now"), 3)).matches(throwable -> throwable.getMessage().equals("Then"));
        assertThat(System.currentTimeMillis() - start).isLessThan(1000L);
    }
}
