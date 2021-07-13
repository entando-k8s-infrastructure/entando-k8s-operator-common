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

package org.entando.kubernetes.test.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LogInterceptor extends Handler {

    private static List<LogRecord> logEntries = new ArrayList<>();
    private static Map<Logger, LogInterceptor> observedLoggers = new HashMap<>();
    private final Level oldLevel;

    public LogInterceptor(Level level) {
        oldLevel = level;
    }

    public static List<String> getLogEntries() {
        return logEntries.stream().map(LogRecord::getMessage).collect(Collectors.toList());
    }

    public static List<LogRecord> getLogRecords() {
        return logEntries;
    }

    public static void listenToClass(Class<?> clss) {
        final Logger logger = Logger.getLogger(clss.getName());
        final LogInterceptor handler = new LogInterceptor(logger.getLevel());
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        observedLoggers.put(logger, handler);
    }

    @Override
    public void publish(LogRecord logRecord) {
        logEntries.add(logRecord);
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() throws SecurityException {
        logEntries.clear();
    }

    public static void reset() {
        observedLoggers.forEach((key, value) -> {
            key.removeHandler(value);
            key.setLevel(value.oldLevel);
        });
        observedLoggers.clear();
        logEntries.clear();
    }
}
