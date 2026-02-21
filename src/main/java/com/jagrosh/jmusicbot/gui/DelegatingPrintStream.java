/*
 * Copyright 2026 THOMZY
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot.gui;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * A PrintStream that delegates all operations to another PrintStream,
 * which can be changed dynamically. This allows updating the output
 * destination without breaking existing references.
 */
public class DelegatingPrintStream extends PrintStream {
    private PrintStream delegate;

    public DelegatingPrintStream(PrintStream initialDelegate) {
        super(new OutputStream() {
            @Override
            public void write(int b) {
                // This should never be called as we override all PrintStream methods
            }
        });
        this.delegate = initialDelegate;
    }

    /**
     * Updates the delegate PrintStream that this stream writes to.
     */
    public synchronized void setDelegate(PrintStream newDelegate) {
        if (newDelegate != null) {
            this.delegate = newDelegate;
        }
    }

    @Override
    public synchronized void print(boolean b) {
        delegate.print(b);
    }

    @Override
    public synchronized void print(char c) {
        delegate.print(c);
    }

    @Override
    public synchronized void print(int i) {
        delegate.print(i);
    }

    @Override
    public synchronized void print(long l) {
        delegate.print(l);
    }

    @Override
    public synchronized void print(float f) {
        delegate.print(f);
    }

    @Override
    public synchronized void print(double d) {
        delegate.print(d);
    }

    @Override
    public synchronized void print(char[] s) {
        delegate.print(s);
    }

    @Override
    public synchronized void print(String s) {
        delegate.print(s);
    }

    @Override
    public synchronized void print(Object obj) {
        delegate.print(obj);
    }

    @Override
    public synchronized void println() {
        delegate.println();
    }

    @Override
    public synchronized void println(boolean x) {
        delegate.println(x);
    }

    @Override
    public synchronized void println(char x) {
        delegate.println(x);
    }

    @Override
    public synchronized void println(int x) {
        delegate.println(x);
    }

    @Override
    public synchronized void println(long x) {
        delegate.println(x);
    }

    @Override
    public synchronized void println(float x) {
        delegate.println(x);
    }

    @Override
    public synchronized void println(double x) {
        delegate.println(x);
    }

    @Override
    public synchronized void println(char[] x) {
        delegate.println(x);
    }

    @Override
    public synchronized void println(String x) {
        delegate.println(x);
    }

    @Override
    public synchronized void println(Object x) {
        delegate.println(x);
    }

    @Override
    public synchronized void write(int b) {
        delegate.write(b);
    }

    @Override
    public synchronized void write(byte[] buf, int off, int len) {
        delegate.write(buf, off, len);
    }

    @Override
    public synchronized void flush() {
        delegate.flush();
    }

    @Override
    public synchronized void close() {
        delegate.close();
    }
}
