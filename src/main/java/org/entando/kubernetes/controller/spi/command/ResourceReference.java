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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.entando.kubernetes.model.common.EntandoCustomResource;

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
public class ResourceReference implements HasMetadata {

    private String apiVersion;
    private String kind;
    private final ObjectMeta metadata = new ObjectMeta();
    private boolean isCustomResource = false;

    public ResourceReference() {

    }

    public ResourceReference(HasMetadata hasMetadata) {
        this.apiVersion = hasMetadata.getApiVersion();
        this.kind = hasMetadata.getKind();
        this.metadata.setName(hasMetadata.getMetadata().getName());
        this.metadata.setNamespace(hasMetadata.getMetadata().getNamespace());
        this.isCustomResource = (hasMetadata instanceof CustomResource || hasMetadata instanceof EntandoCustomResource);
    }

    @Override
    public ObjectMeta getMetadata() {
        return metadata;
    }

    public boolean isCustomResource() {
        return isCustomResource;
    }

    @Override
    public void setMetadata(ObjectMeta metadata) {
        //This is a read-only class. Don't override metadata
    }

    @Override
    public String getKind() {
        return kind;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public void setApiVersion(String version) {
        //This is a read-only class. Don't override apiVersion
    }
}
