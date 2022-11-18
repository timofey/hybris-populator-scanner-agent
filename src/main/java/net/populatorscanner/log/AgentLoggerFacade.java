package net.populatorscanner.log;

import net.populatorscanner.log.adapter.LoggerAdapter;

import static net.populatorscanner.log.AgentLogLevel.*;

public class AgentLoggerFacade {

    private final LoggerAdapter loggerAdapter;

    public AgentLoggerFacade() {
        this.loggerAdapter = new LoggerDiscoveryService().discoverLogger();
    }

    public AgentLoggerFacade(ClassLoader classLoader) {
        this.loggerAdapter = new LoggerDiscoveryService().discoverLogger(classLoader);
    }

    public void log(AgentLogLevel logLevel, String pattern, Object... args) {
        loggerAdapter.log(logLevel, pattern, args);
    }

    public void debug(String pattern, Object... args) {
        log(DEBUG, pattern, args);
    }

    public void info(String pattern, Object... args) {
        log(INFO, pattern, args);
    }

    public void warn(String pattern, Object... args) {
        log(WARN, pattern, args);
    }

    public void error(String pattern, Object... args) {
        log(ERROR, pattern, args);
    }
}
