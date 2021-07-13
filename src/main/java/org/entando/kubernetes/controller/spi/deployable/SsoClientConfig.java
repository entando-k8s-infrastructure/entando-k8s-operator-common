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

package org.entando.kubernetes.controller.spi.deployable;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.ArrayList;
import java.util.List;
import org.entando.kubernetes.model.common.ExpectedRole;
import org.entando.kubernetes.model.common.Permission;

@JsonSerialize
@JsonDeserialize
@JsonInclude(Include.NON_NULL)
@JsonAutoDetect(
        fieldVisibility = Visibility.ANY,
        isGetterVisibility = Visibility.NONE,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE
)
public class SsoClientConfig {

    private final String realm;
    private final String clientId;
    private final String clientName;
    private final List<ExpectedRole> roles;
    private final List<Permission> permissions;
    private List<String> redirectUri = new ArrayList<>();
    private List<String> webOrigins = new ArrayList<>();

    @JsonCreator
    public SsoClientConfig(@JsonProperty("realm") String realm,
            @JsonProperty("clientId") String clientId,
            @JsonProperty("clientName") String clientName,
            @JsonProperty("roles") List<ExpectedRole> roles,
            @JsonProperty("permissions") List<Permission> permissions) {
        this.realm = realm;
        this.clientId = clientId;
        this.clientName = clientName;
        this.roles = ofNullable(roles).map(ArrayList::new).orElseGet(ArrayList::new);
        this.permissions = ofNullable(permissions).map(ArrayList::new).orElseGet(ArrayList::new);
    }

    public SsoClientConfig(String realm, String clientId, String clientName) {
        this(realm, clientId, clientName, null, null);
    }

    public SsoClientConfig withRedirectUri(String redirectUri) {
        this.redirectUri.add(redirectUri);
        return this;
    }

    public SsoClientConfig withWebOrigin(String webOrigin) {
        this.webOrigins.add(webOrigin);
        return this;
    }

    public List<ExpectedRole> getRoles() {
        return roles;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getRealm() {
        return realm;
    }

    public SsoClientConfig withPermission(String clientId, String role) {
        permissions.add(new Permission(clientId, role));
        return this;
    }

    public SsoClientConfig withRole(String role) {
        roles.add(new ExpectedRole(role));
        return this;
    }

    public List<String> getRedirectUris() {
        return redirectUri;
    }

    public List<String> getWebOrigins() {
        return webOrigins;
    }
}
