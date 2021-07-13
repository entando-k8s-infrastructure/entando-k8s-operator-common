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

import java.util.concurrent.TimeoutException;

public interface CommandStream {

    /**
     * This method should not through any exceptions other than TimeoutException for timeouts on the client side, and exceptions related to
     * IO and/or Serialization. Exceptions produced on the other side will be logged on the status of the EntandoCustomResource in question.
     * The string returned also has a controllerFailure attached to it directly or indirectly
     */

    String process(SupportedCommand command, String target, int timeoutSeconds) throws TimeoutException;
}
