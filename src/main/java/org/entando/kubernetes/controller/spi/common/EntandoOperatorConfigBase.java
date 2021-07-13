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

import io.fabric8.kubernetes.api.model.ConfigMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public abstract class EntandoOperatorConfigBase {

    public static final String SEPERATOR_PATTERN = "[\\s,:]+";

    private static AtomicReference<ConfigMap> configMap = new AtomicReference<>();

    protected EntandoOperatorConfigBase() {
    }

    public static void setConfigMap(ConfigMap configMap) {
        EntandoOperatorConfigBase.configMap.set(configMap);
    }

    public static Optional<String> lookupProperty(ConfigProperty property) {
        return Optional.ofNullable(lookupProperty(property.getJvmSystemProperty()).orElse(lookupProperty(property.name()).orElse(null)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Optional<String> lookupProperty(String name) {
        Optional<String> fromConfigMap = Optional.ofNullable(EntandoOperatorConfigBase.configMap.get())
                .flatMap(map -> findFirstMatch(name, map.getData()));
        if (fromConfigMap.isEmpty()) {
            Optional<String> fromEnv = findFirstMatch(name, System.getenv());
            if (fromEnv.isEmpty()) {
                return findFirstMatch(name, new HashMap(System.getProperties()));
            } else {
                return fromEnv;
            }
        }
        return fromConfigMap;
    }

    private static Optional<String> findFirstMatch(String name, Map<String, String> map) {
        return map.entrySet().stream()
                .filter(entry -> isMatch(name, entry))
                .map(Entry::getValue)
                .findFirst();
    }

    private static boolean isMatch(String n, Entry<?, ?> entry) {
        if (entry.getValue() == null || ((String) entry.getValue()).trim().isEmpty()) {
            return false;
        }
        String name = n.toLowerCase(Locale.getDefault());
        String key = ((String) entry.getKey()).toLowerCase(Locale.getDefault());
        return NameUtils.snakeCaseOf(name).equals(NameUtils.snakeCaseOf(key));
    }
}
