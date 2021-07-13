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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FormatUtils {

    @SuppressWarnings("java:S5164")
    //there is no memory leak here - DateTimeFormatter is a self contained, immutable object.
    private static final ThreadLocal<DateTimeFormatter> dateTimeFormatter = ThreadLocal
            .withInitial(() -> DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'"));

    private FormatUtils() {

    }

    public static String format(LocalDateTime now) {
        return dateTimeFormatter.get().format(now);
    }
}
