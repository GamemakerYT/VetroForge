package com.vetroforge;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PrefixedLogger extends Logger {

    private final Logger delegate;
    private final String prefix;

    public PrefixedLogger(Logger delegate, String prefix) {
        super(delegate.getName(), delegate.getResourceBundleName());
        this.delegate = delegate;
        this.prefix = prefix == null ? "" : prefix + " ";
    }

    @Override
    public void log(Level level, String msg) {
        delegate.log(level, prefix + msg);
    }

    @Override
    public void log(Level level, String msg, Object param1) {
        delegate.log(level, prefix + msg, param1);
    }

    @Override
    public void log(Level level, String msg, Object[] params) {
        delegate.log(level, prefix + msg, params);
    }

    @Override
    public void log(Level level, String msg, Throwable thrown) {
        delegate.log(level, prefix + msg, thrown);
    }
}
