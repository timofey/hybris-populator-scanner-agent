package net.populatorscanner.log.adapter;

import net.populatorscanner.log.AgentLogLevel;

import java.util.Date;

public class ConsoleAdapter implements LoggerAdapter {

    @Override
    public void log(AgentLogLevel logLevel, String pattern, Object... args) {
        System.out.println("[" + logLevel.name() + "] [" + Thread.currentThread().getName() + "] (" + new Date() + ") "
            + ParameterFormatter.format(pattern, args));
    }

    @Override
    public LoggerAdapter init() {
        return this;
    }

    @Override
    public LoggerAdapter init(ClassLoader classLoader) {
        return this;
    }
}
