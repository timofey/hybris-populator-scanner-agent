package net.populatorscanner.log;

import net.populatorscanner.log.adapter.ConsoleAdapter;
import net.populatorscanner.log.adapter.LoggerAdapter;
import net.populatorscanner.log.adapter.Slf4jAdapter;

public class LoggerDiscoveryService {

    private static final LoggerAdapter CONSOLE_ADAPTER = new ConsoleAdapter();
    private static final LoggerAdapter SLF4J_ADAPTER = new Slf4jAdapter();

    public LoggerAdapter discoverLogger() {
        return this.discoverLogger(null);
    }

    public LoggerAdapter discoverLogger(ClassLoader classLoader) {
        try {
            // try to load Slf4j' logger factory
            LoggerAdapter adapter;
            if (classLoader == null) {
                Class.forName("org.slf4j.LoggerFactory");
                adapter = SLF4J_ADAPTER.init();
            } else {
                classLoader.loadClass("org.slf4j.LoggerFactory");
                adapter = SLF4J_ADAPTER.init(classLoader);
            }

            if (adapter != null) {
                return adapter;
            } else {
                System.out.println("Couldn't initialize SLF4J, falling back to console adapter");
                return CONSOLE_ADAPTER.init();
            }
        } catch (ClassNotFoundException e) {
            // couldn't find Slf4j in the classpath, callback to console logger
            return CONSOLE_ADAPTER.init();
        }
    }
}
