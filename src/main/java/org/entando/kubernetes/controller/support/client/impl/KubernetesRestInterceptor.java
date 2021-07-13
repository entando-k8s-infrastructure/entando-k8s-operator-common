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

package org.entando.kubernetes.controller.support.client.impl;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class KubernetesRestInterceptor implements InvocationHandler {

    private Object delegate;

    public KubernetesRestInterceptor(Object delegate) {
        this.delegate = delegate;
    }

    private static String format(HasMetadata entandoCustomResource) {
        return String.format(" with %s: %s/%s", entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getNamespace(),
                entandoCustomResource.getMetadata().getName());
    }

    @SuppressWarnings("squid:S2139")//Because it is common practice to log and rethrow an exception in a logging interceptor
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Logger logger = Logger.getLogger(method.getDeclaringClass().getName());
        logger.logp(Level.INFO, method.getDeclaringClass().getName(), method.getName(), () -> buildEnterMessage(method, args));
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            logger.logp(Level.SEVERE, method.getDeclaringClass().getName(), method.getName(), e.getTargetException(),
                    () -> String.format("Failure executing method %s in class %s",
                            method.getName(),
                            method.getDeclaringClass().getName()));
            throw e.getTargetException();
        } catch (Exception e) {
            logger.logp(Level.SEVERE, method.getDeclaringClass().getName(), method.getName(), e,
                    () -> String.format("Failure executing method %s in class %s",
                            method.getName(),
                            method.getDeclaringClass().getName()));
            throw e;
        } finally {
            logger.logp(Level.INFO, method.getDeclaringClass().getName(), method.getName(), () ->
                    String.format("Exiting method %s in class %s", method.getName(),
                            method.getDeclaringClass().getName()));

        }
    }

    private String buildEnterMessage(Method method, Object[] args) {
        StringBuilder message = new StringBuilder(
                String.format("Entering method %s in class %s", method.getName(),
                        method.getDeclaringClass().getName()));
        if (args != null) {
            Optional<EntandoCustomResource> first = Arrays.stream(args)
                    .filter(EntandoCustomResource.class::isInstance)
                    .map(EntandoCustomResource.class::cast).findFirst();
            first.ifPresent(entandoCustomResource -> message.append(format(entandoCustomResource)));
            Optional<HasMetadata> second = Arrays.stream(args)
                    .filter(o -> o instanceof HasMetadata && o.getClass().getName().startsWith("io.fabric8.kubernetes"))
                    .map(HasMetadata.class::cast).findFirst();
            if (second.isPresent()) {
                if (first.isPresent()) {
                    message.append(" and");
                }
                message.append(format(second.get()));
            }
            if (!(first.isPresent() || second.isPresent())) {
                message.append(" with ")
                        .append(Arrays.stream(args).map(o -> o == null ? "null" : o.toString()).collect(Collectors.joining(",")));
            }
        }
        return message.toString();
    }

}
