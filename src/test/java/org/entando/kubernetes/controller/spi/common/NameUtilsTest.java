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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
class NameUtilsTest {

    private static final String FIFTY_NINE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567";

    @Test
    void testShortenTo63Chars() {
        Set<String> existing = new HashSet<>();
        //We are unlikely to have more than 10 resources with similar names in a namespace
        for (int i = 0; i < 10; i++) {
            String found = NameUtils.shortenTo63Chars(FIFTY_NINE_CHARS + "asdfasdfasdfasdfasdfasdfasdfasdfasdf");
            assertThat(found, startsWith(FIFTY_NINE_CHARS));
            assertThat(found.length(), is(63));
            char[] suffix = found.substring(59).toCharArray();
            for (char c : suffix) {
                assertTrue(Character.isDigit(c));
            }
            assertFalse(existing.contains(found));
            existing.add(found);
        }
    }

    @Test
    void testCamelCaseToDashDelimited() {
        assertThat(NameUtils.camelCaseToDashDelimited("EntandoCompositeAppController"), is("entando-composite-app-controller"));
        assertThat(NameUtils.camelCaseToDashDelimited("entandoCompositeAppController"), is("entando-composite-app-controller"));
        assertThat(NameUtils.camelCaseToDashDelimited("entandoComposite-appController"), is("entando-composite-app-controller"));
    }

    @Test
    void testControllerNameOf() {
        EntandoApp entandoApp = new EntandoApp();
        entandoApp.setKind("EntandoApp");
        assertThat(NameUtils.controllerNameOf(entandoApp), is("entando-k8s-app-controller"));
        EntandoDatabaseService entandoDatabaseService = new EntandoDatabaseService();
        entandoApp.setKind("EntandoDatabaseService");
        assertThat(NameUtils.controllerNameOf(entandoDatabaseService), is("entando-k8s-database-service-controller"));
    }
}
