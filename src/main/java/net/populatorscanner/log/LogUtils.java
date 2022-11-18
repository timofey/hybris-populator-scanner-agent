package net.populatorscanner.log;

import java.util.Objects;

public final class LogUtils {

    public static final AgentLoggerFacade LOGGER = new AgentLoggerFacade();

    public static String abbreviateClassName(String fqcn) {
        Objects.requireNonNull(fqcn);

        String[] splitted = fqcn.split("\\.");
        if (splitted.length > 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < splitted.length; i++) {
                if (i == 0 || i >= (splitted.length - 2)) {
                    sb.append(splitted[i]);
                } else {
                    sb.append(splitted[i].charAt(0));
                }

                if (i < (splitted.length - 1)) {
                    sb.append('.');
                }
            }
            return sb.toString();
        }
        return fqcn;
    }

    public static StackTraceElement getCallSource(StackTraceElement[] stackTrace) {
        StackTraceElement top = null;
        for (StackTraceElement stackTraceElement : stackTrace) {
            if (top == null) {
                top = stackTraceElement;
                continue;
            }

            if (!stackTraceElement.getClassName().equals(top.getClassName())) {
                return stackTraceElement;
            }
        }
        return null;
    }
}
