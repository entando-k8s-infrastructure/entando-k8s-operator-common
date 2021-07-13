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

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.model.common.EntandoControllerFailureBuilder;

public class ExceptionUtils {

    public static EntandoControllerFailure failureOf(EntandoControllerException e) {
        return failureOf(e.getKubernetesResource(), e);
    }

    public static EntandoControllerFailure failureOf(HasMetadata resource, Exception e) {
        //Start with the initial resource if present
        final EntandoControllerFailureBuilder builder = ofNullable(resource)
                .map(hasMetadata -> builderFrom(e).withFailedObject(resource))
                .orElse(builderFrom(e));
        if (e instanceof EntandoControllerException) {
            //Overwrite resource details from EntandoControllerException if present because it was
            //close to the occurrence of the exception
            EntandoControllerException controllerException = (EntandoControllerException) e;
            return ofNullable(controllerException.getKubernetesResource())
                    .map(builder::withFailedObject)
                    .orElse(builder).build();
        } else if (e instanceof KubernetesClientException) {
            //Overwrite resource details from KubernetesClientException if present because it was
            //close to the occurrence of the exception
            KubernetesClientException clientException = (KubernetesClientException) e;
            return ofNullable(clientException.getStatus()).flatMap(s -> ofNullable(s.getDetails()))
                    .map(details -> builder
                            .withFailedObjectKind(details.getKind())
                            .withFailedObjectName(details.getName())
                            .withFailedObjectNamespace(
                                    ofNullable(resource).map(hasMetadata -> hasMetadata.getMetadata().getNamespace()).orElse(null)))
                    .orElse(builder).build();
        } else {
            return builder.build();
        }
    }

    private static EntandoControllerFailureBuilder builderFrom(Exception e) {
        final StringWriter stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        return new EntandoControllerFailureBuilder()
                .withMessage(e.getMessage())
                .withDetailMessage(stringWriter.toString());
    }

    public static <T> T retry(Supplier<T> supplier, Predicate<RuntimeException> ignoreExceptionWhen, int count) {
        for (int i = 0; i < count - 1; i++) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                if (!ignoreExceptionWhen.test(e)) {
                    throw e;
                }
                long actualCount = i + 1L;
                final long duration = actualCount * actualCount;
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(duration));
            }
        }
        return supplier.get();
    }

    public static <T> T interruptionSafe(Interruptable<T> i) throws TimeoutException {
        try {
            return i.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw maybeWrapAndThrow(e.getCause());
        } catch (RuntimeException e) {
            throw maybeWrapAndThrow(e);
        }
    }

    private static RuntimeException maybeWrapAndThrow(Throwable cause) throws TimeoutException {
        if (cause instanceof IllegalArgumentException && cause.getMessage().contains("matching condition not found")) {
            //NB!!! this is a risky assumption but generally the Fabric8 BaseOperation.waitUntilCondition methods throw
            //an IllegalArgumentException with a message containing the above text when the conditions times out
            throw new TimeoutException(cause.getMessage());
        } else if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        } else if (cause instanceof TimeoutException) {
            throw (TimeoutException) cause;
        }
        return new IllegalStateException(cause);
    }

    public interface IoCall<T> {

        T invoke() throws IOException;
    }

    public interface IoAction {

        void invoke() throws IOException;
    }

    private ExceptionUtils() {
    }

    public static <T> T ioSafe(IoCall<T> i) {
        try {
            return i.invoke();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void ioSafe(IoAction i) {
        try {
            i.invoke();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T withDiagnostics(Callable<T> i, Supplier<HasMetadata> supplier) {
        try {
            return i.call();
        } catch (RuntimeException e) {
            throw ofNullable(supplier.get()).map(h -> (RuntimeException) new EntandoControllerException(h, e)).orElse(e);
        } catch (Exception e) {
            throw ofNullable(supplier.get()).map(h -> new EntandoControllerException(h, e)).orElse(new EntandoControllerException(e));
        }
    }

    public interface Interruptable<T> {

        T run() throws InterruptedException, ExecutionException, TimeoutException;
    }
}
