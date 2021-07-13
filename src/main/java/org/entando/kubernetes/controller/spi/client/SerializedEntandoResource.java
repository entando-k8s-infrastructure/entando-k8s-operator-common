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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.entando.kubernetes.model.common.EntandoCustomResource;
import org.entando.kubernetes.model.common.EntandoCustomResourceStatus;

@JsonSerialize
@JsonDeserialize
@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(
        fieldVisibility = Visibility.ANY,
        isGetterVisibility = Visibility.NONE,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE
)
@RegisterForReflection
@JsonIgnoreProperties(
        ignoreUnknown = true
)

public class SerializedEntandoResource implements EntandoCustomResource {

    private EntandoCustomResourceStatus status;
    private ObjectMeta metadata;
    private String kind;
    @SuppressWarnings("java:S1948")
    //because it is serializable but can't control the implementation class
    private Map<String, Object> spec;
    @JsonIgnore
    private transient CustomResourceDefinitionContext definition;
    private String apiVersion;

    public void setDefinition(CustomResourceDefinitionContext definition) {
        this.definition = definition;
        this.kind = definition.getKind();
    }

    public CustomResourceDefinitionContext getDefinition() {
        return definition;
    }

    @Override
    public EntandoCustomResourceStatus getStatus() {
        if (this.status == null) {
            this.status = new EntandoCustomResourceStatus();
        }
        return this.status;
    }

    public Map<String, Object> getSpec() {
        return Collections.unmodifiableMap(Objects.requireNonNullElse(spec, Collections.emptyMap()));
    }

    @Override
    public void setStatus(EntandoCustomResourceStatus status) {
        this.status = status;
    }

    @Override
    public String getDefinitionName() {
        return null;
    }

    @Override
    public ObjectMeta getMetadata() {
        return metadata;
    }

    @Override
    public String getApiVersion() {
        return Optional.ofNullable(definition).map(d -> d.getGroup() + "/" + d.getVersion()).orElse(apiVersion);
    }

    @Override
    public String getKind() {
        return kind;
    }

    @Override
    public void setMetadata(ObjectMeta metadata) {
        this.metadata = metadata;
    }

    @Override
    public void setApiVersion(String version) {
        this.apiVersion = version;
    }
}
