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

package org.entando.kubernetes.controller.support.client.doubles;

import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.ExecListenable;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorChannelable;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorable;
import io.fabric8.kubernetes.client.dsl.TtyExecOutputErrorable;
import io.fabric8.kubernetes.client.dsl.TtyExecable;
import io.fabric8.kubernetes.client.dsl.internal.PodOperationContext;
import io.fabric8.kubernetes.client.dsl.internal.core.v1.PodOperationsImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class PodResourceDouble extends PodOperationsImpl {

    public PodResourceDouble() {
        this(new PodOperationContext());
    }

    public PodResourceDouble(PodOperationContext podOperationContext) {
        super(podOperationContext);
    }

    @Override
    public ContainerResource<LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch, Boolean,
            InputStream, Boolean> inContainer(
            String containerId) {
        return new PodResourceDouble(getContext().withContainerId(containerId));
    }

    @Override
    public TtyExecOutputErrorable<String, OutputStream, PipedInputStream, ExecWatch> readingInput(InputStream in) {
        return new PodResourceDouble(getContext().withIn(in));
    }

    @Override
    public TtyExecErrorable<String, OutputStream, PipedInputStream, ExecWatch> writingOutput(OutputStream out) {
        return new PodResourceDouble(getContext().withOut(out));
    }

    @Override
    public TtyExecErrorChannelable<String, OutputStream, PipedInputStream, ExecWatch> writingError(OutputStream err) {
        return new PodResourceDouble(getContext().withErr(err));
    }

    @Override
    public TtyExecErrorChannelable<String, OutputStream, PipedInputStream, ExecWatch> redirectingError() {
        return new PodResourceDouble(getContext().withErrPipe(new PipedInputStream()));
    }

    @Override
    public TtyExecable<String, ExecWatch> writingErrorChannel(OutputStream errChannel) {
        return new PodResourceDouble(getContext().withErrChannel(errChannel));
    }

    @Override
    public Execable<String, ExecWatch> usingListener(ExecListener execListener) {
        return new PodResourceDouble(getContext().withExecListener(execListener));
    }

    @Override
    public ExecListenable<String, ExecWatch> withTTY() {
        return new PodResourceDouble(getContext().withTty(true));
    }

    @Override
    @SuppressWarnings("java:S2925")
    public ExecWatch exec(String... command) {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            getContext().getExecListener().onClose(0, "Success");
        }).start();
        return new ExecWatchDouble(Arrays.asList(command));
    }

    public class ExecWatchDouble implements ExecWatch {

        private final List<String> commands;
        private PodResourceDouble podResourceDouble = PodResourceDouble.this;

        public ExecWatchDouble(List<String> asList) {
            this.commands = asList;
        }

        public PodResourceDouble getPodResourceDouble() {
            return podResourceDouble;
        }

        @Override
        public OutputStream getInput() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getOutput() {
            return new ByteArrayInputStream(String.join("\n", commands).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getError() {
            return null;
        }

        @Override
        public InputStream getErrorChannel() {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public void resize(int cols, int rows) {

        }
    }
}
