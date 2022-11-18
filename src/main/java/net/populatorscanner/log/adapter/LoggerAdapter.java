package net.populatorscanner.log.adapter;

import net.populatorscanner.log.AgentLogLevel;

public interface LoggerAdapter {
    void log(AgentLogLevel logLevel, String pattern, Object... args);
    LoggerAdapter init();
    LoggerAdapter init(ClassLoader classLoader);
}
