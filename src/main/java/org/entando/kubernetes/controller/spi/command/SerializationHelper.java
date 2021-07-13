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

import static java.util.Optional.ofNullable;
import static org.entando.kubernetes.controller.spi.common.ExceptionUtils.ioSafe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.entando.kubernetes.controller.spi.common.SerializeByReference;

public class SerializationHelper {

    private SerializationHelper() {

    }

    public static String serialize(Object deployable) {
        return ioSafe(() -> {
            Map<String, Object> map = toJsonFriendlyMap(deployable);
            return new ObjectMapper(new YAMLFactory()).writerWithDefaultPrettyPrinter().writeValueAsString(map);
        });
    }

    private static Map<String, Object> toJsonFriendlyMap(Object nonSerializableObject) {
        Map<String, Object> map = new HashMap<>();
        map.put("mixins", Arrays.stream(getAllImplementedInterfaces(nonSerializableObject.getClass()))
                .filter(ReflectionUtil.KNOWN_INTERFACES::contains)
                .map(Class::getSimpleName)
                .collect(Collectors.toList()));
        Arrays.stream(nonSerializableObject.getClass().getMethods())
                .filter(method -> (method.getName().startsWith("get") || method.getName().startsWith("is"))
                        && method.getReturnType() != void.class
                        && method.getParameterCount() == 0)
                .map(method -> processGetter(nonSerializableObject, method))
                .filter(Objects::nonNull)
                .forEach(stringObjectPair -> map.put(stringObjectPair.getKey(), stringObjectPair.getValue()));
        return map;
    }

    private static Class<?>[] getAllImplementedInterfaces(Class<?> clazz) {
        Set<Class<?>> result = new HashSet<>();
        while (clazz != null && clazz != Object.class) {
            result.addAll(Arrays.asList(clazz.getInterfaces()));
            for (Class<?> intf : clazz.getInterfaces()) {
                result.addAll(List.of(getAllImplementedInterfaces(intf)));
            }
            clazz = clazz.getSuperclass();
        }
        return result.toArray(Class[]::new);
    }

    private static Pair<String, Object> processGetter(Object nonSerializableObject, Method method) {
        try {
            Object value = method.invoke(nonSerializableObject);
            if (value instanceof Optional) {
                value = ((Optional<?>) value).orElse(null);
            }
            if (value != null) {
                if (ReflectionUtil.getAnnotation(nonSerializableObject.getClass(), method.getName(), SerializeByReference.class) != null) {
                    return new ImmutablePair<>(ReflectionUtil.propertyName(method), new ResourceReference((HasMetadata) value));
                } else if (ReflectionUtil.getAnnotation(nonSerializableObject.getClass(), method.getName(), JsonIgnore.class) == null) {
                    return ofNullable(toJsonSafeValue(value))
                            .map(jsonSafeValue -> new ImmutablePair<>(ReflectionUtil.propertyName(method), jsonSafeValue))
                            .orElse(null);
                }
            }
            return null;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException) {
                throw (RuntimeException) e.getTargetException();
            } else {
                throw new IllegalStateException(e.getTargetException());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object toJsonSafeValue(Object value) {
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        } else if (value instanceof Enum) {
            return ((Enum<?>) value).name().toUpperCase(Locale.ROOT);
        } else if (value instanceof Optional) {
            return ((Optional<?>) value).map(SerializationHelper::toJsonSafeValue).orElse(null);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream().map(SerializationHelper::toJsonSafeValue).collect(Collectors.toList());
        } else if (value instanceof Map) {
            Map<String, ?> map = (Map<String, ?>) value;
            return map.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> ofNullable(toJsonSafeValue(entry.getValue())).orElse("")));
        } else if (value != null) {
            if (value.getClass().getAnnotation(JsonDeserialize.class) != null) {
                //We know how to serialize this
                return value;
            } else if (ReflectionUtil.implementsKnownInterface(value)) {
                //Can't serialize, but is known, so translate to map
                return toJsonFriendlyMap(value);
            }
        }
        return null;
    }

}
