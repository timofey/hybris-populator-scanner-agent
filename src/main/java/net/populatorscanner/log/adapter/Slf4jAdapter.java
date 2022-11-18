package net.populatorscanner.log.adapter;

import net.populatorscanner.log.AgentLogLevel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Slf4jAdapter implements LoggerAdapter {

    private static final String LOGGER_NAME = "PopulatorScannerAgent";

    private volatile Object loggerInstance;

    @Override
    public void log(AgentLogLevel logLevel, String pattern, Object... args) {
       String methodName = logLevel.name().toLowerCase();
        try {
            Method logMethod = loggerInstance.getClass().getMethod(methodName, String.class, Object[].class);
            logMethod.invoke(loggerInstance, pattern, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public LoggerAdapter init() {
        return init(null);
    }

    @Override
    public LoggerAdapter init(ClassLoader classLoader) {
        try {
            Class<?> factoryClass;
            if (classLoader == null) {
                factoryClass = Class.forName("org.slf4j.LoggerFactory");
            } else {
                factoryClass = classLoader.loadClass("org.slf4j.LoggerFactory");
            }
            Method getLoggerMethod = factoryClass.getDeclaredMethod("getLogger", String.class);
            getLoggerMethod.setAccessible(true);
            this.loggerInstance = getLoggerMethod.invoke(null, LOGGER_NAME);
            return this;
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}
