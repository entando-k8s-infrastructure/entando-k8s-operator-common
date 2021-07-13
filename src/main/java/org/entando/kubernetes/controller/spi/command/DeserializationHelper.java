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

package org.entando.kubernetes.controller.spi.command;

import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.common.SerializeByReference;
import org.entando.kubernetes.model.capability.ProvidedCapability;

public class DeserializationHelper implements InvocationHandler {

    private final Map<String, Object> map;
    private final KubernetesClientForControllers kubernetesClient;
    private final ObjectMapper objectMapper;

    private DeserializationHelper(KubernetesClientForControllers kubernetesClient, Map<String, Object> map, ObjectMapper objectMapper) {
        this.kubernetesClient = kubernetesClient;
        this.map = map;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public static <S> S deserialize(KubernetesClientForControllers kubernetesClient, String json) {
        return ioSafe(() -> {
            final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return fromMap(kubernetesClient, map, objectMapper);
        });
    }

    @SuppressWarnings("unchecked")
    private static <S> S fromMap(KubernetesClientForControllers kubernetesClient, Map<String, Object> map,
            ObjectMapper objectMapper) {
        return (S) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                getImplementedInterfaces(map),
                new DeserializationHelper(kubernetesClient, map, objectMapper)
        );
    }

    @SuppressWarnings("unchecked")
    public static Class<?>[] getImplementedInterfaces(Map<String, Object> map) {
        List<String> mixins = (List<String>) map.get("mixins");
        return ReflectionUtil.KNOWN_INTERFACES.stream().filter(aClass -> mixins.contains(aClass.getSimpleName())).toArray(Class<?>[]::new);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
        if (method.getName().equals("createResult")) {
            return createResult(objects);
        }
        Object result = map.get(ReflectionUtil.propertyName(method));
        if (result == null) {
            if (Optional.class.isAssignableFrom(method.getReturnType())) {
                return Optional.empty();
            } else {
                return null;
            }
        } else {
            if (method.getReturnType() == List.class) {
                Class<?> typeArgument = resolveTypeArgument(method, 0);
                return ((List<?>) result).stream()
                        .map(deserializedMap -> resolveRawObjectOrMap(method, deserializedMap, typeArgument))
                        .collect(Collectors.toList());
            } else if (method.getReturnType() == Map.class) {
                final Class<?> type = resolveTypeArgument(method, 1);
                return ((Map<String, ?>) result).entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> resolveRawObjectOrMap(method, entry.getValue(), type)));
            } else if (method.getReturnType() == Optional.class) {
                final Class<?> type = resolveTypeArgument(method, 0);
                return Optional.of(resolveRawObjectOrMap(method, result, type));
            } else {
                return resolveRawObjectOrMap(method, result, method.getReturnType());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolveRawObjectOrMap(Method method, Object rawObjectOrMap, Class<?> type) {
        if (ReflectionUtil.getAnnotationFromInterfaces(getImplementedInterfaces(map), method.getName(), SerializeByReference.class)
                != null) {
            return resolveByReference(rawObjectOrMap);
        } else if (type.getAnnotation(JsonDeserialize.class) != null) {
            return ioSafe(() -> objectMapper.readValue(objectMapper.writeValueAsString(rawObjectOrMap), type));
        } else if (ReflectionUtil.KNOWN_INTERFACES.contains(type)) {
            return fromMap(kubernetesClient, (Map<String, Object>) rawObjectOrMap, objectMapper);
        } else if (type.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) type, rawObjectOrMap.toString().toUpperCase(Locale.ROOT));
        } else {
            //Could be a simple type. Look for String constructor
            try {
                return type.getConstructor(String.class).newInstance(rawObjectOrMap.toString());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                return rawObjectOrMap;
            }
        }
    }

    private Class<?> resolveTypeArgument(Method method, int i) {
        return (Class<?>) ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[i];
    }

    private Object createResult(Object[] objects)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Constructor<?> selectedConstructor = DefaultSerializableDeploymentResult.class.getConstructors()[0];
        final Object[] arguments = Arrays.stream(selectedConstructor.getParameterTypes())
                .map(type -> Arrays.stream(objects).filter(type::isInstance).findFirst().orElse(null))
                .toArray(Object[]::new);
        return selectedConstructor.newInstance(arguments);
    }

    private HasMetadata resolveByReference(Object result) {
        return ioSafe(() -> {
            final ResourceReference resourceReference = objectMapper.readValue(new StringReader(objectMapper.writeValueAsString(result)),
                    ResourceReference.class);
            if (resourceReference.isCustomResource()) {
                if (resourceReference.getKind().equals(ProvidedCapability.class.getSimpleName())) {
                    return kubernetesClient.load(ProvidedCapability.class, resourceReference.getMetadata().getNamespace(),
                            resourceReference.getMetadata().getName());
                } else {
                    return kubernetesClient.loadCustomResource(resourceReference.getApiVersion(),
                            resourceReference.getKind(), resourceReference.getMetadata().getNamespace(),
                            resourceReference.getMetadata().getName());
                }
            } else {
                return kubernetesClient.loadStandardResource(resourceReference.getKind(), resourceReference.getMetadata().getNamespace(),
                        resourceReference.getMetadata().getName());
            }
        });
    }

}



