package net.populatorscanner.agent;

import net.populatorscanner.log.AgentLoggerFacade;
import net.populatorscanner.log.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public final class ConverterCallNode {

    private static final ReentrantLock loggerInitLock = new ReentrantLock();
    public static volatile AgentLoggerFacade logger = null;
    public static volatile ClassLoader appClassLoader = null;

    public static void initClassLoaderIfNeeded(ClassLoader appClassLoader) {
        if (ConverterCallNode.appClassLoader == null) {
            ConverterCallNode.appClassLoader = appClassLoader;
        }
    }

    public String clazz;
    public String callSource; // source code file name + line number
    public String sourceType;
    public String targetType;
    public int iterations = 1;
    public boolean isDuplicate = false;
    public List<ConverterCallNode> childCalls = new ArrayList<>();
    public ConverterCallNode parentCall;

    public ConverterCallNode(String clazz, String callSource, String sourceType, String targetType) {
        this.clazz = clazz;
        this.callSource = callSource;
        this.sourceType = sourceType;
        this.targetType = targetType;
    }

    private ConverterCallNode() {
    }

    public static final class Builder {

        private final ConverterCallNode obj;

        public Builder() {
            this.obj = new ConverterCallNode();
        }

        public Builder withClass(String clazz) {
            this.obj.clazz = clazz;
            return this;
        }

        public Builder withCallSource(String callSource) {
            this.obj.callSource = callSource;
            return this;
        }

        public Builder withSourceType(String sourceType) {
            this.obj.sourceType = sourceType;
            return this;
        }

        public Builder withTargetType(String targetType) {
            this.obj.targetType = targetType;
            return this;
        }

        public ConverterCallNode build() {
            return this.obj;
        }

    }

    public boolean deepEquals(ConverterCallNode callNode) {
        if (!this.equals(callNode)) {
            return false;
        }

        boolean result = this.childCalls.size() == callNode.childCalls.size();
        if (!result) {
            return false;
        }

        if (this.childCalls.size() == 0) {
            return true;
        }

        for (int i = 0; i < this.childCalls.size(); i++) {
            ConverterCallNode childCall = this.childCalls.get(i);
            result = result && childCall.deepEquals(callNode.childCalls.get(i));

        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConverterCallNode node = (ConverterCallNode) o;
        return Objects.equals(clazz, node.clazz) && Objects.equals(callSource, node.callSource) && Objects.equals(sourceType, node.sourceType) && Objects.equals(targetType, node.targetType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, callSource, sourceType, targetType);
    }

    @Override
    public String toString() {

        return (iterations > 1 ? "(\uD83D\uDD01" + iterations + ") " : "") + (isDuplicate ? "(d) " : "") + LogUtils.abbreviateClassName(clazz)
                + "<" + (sourceType != null ? LogUtils.abbreviateClassName(sourceType) : "?")
                + ", " + (targetType != null ? LogUtils.abbreviateClassName(targetType) : "?") + ">"
                + " ← " + (callSource != null ? callSource : "(Unknown Source)");
    }

    private void initLoggerIfNeeded() {
        if (logger == null) {
            try {
                loggerInitLock.lock();
                if (logger == null) {
                    logger = new AgentLoggerFacade(ConverterCallNode.appClassLoader);
                }
            } finally {
                loggerInitLock.unlock();
            }
        }
    }

    public void logRecursively() {
        final StringBuilder sb = new StringBuilder("\n");
        logRecursivelyInternal(this, 0, false, sb, "", this.iterations > 1);
        initLoggerIfNeeded();
        logger.debug(sb.toString());
    }

    private void logRecursivelyInternal(ConverterCallNode nextNode, int level, boolean last, StringBuilder logBuilder,
                                        String interimSeparator, boolean isDup) {
        logBuilder.append(interimSeparator);
        if (level > 0) {
            if (last) {
                logBuilder.append("└╴");
            } else {
                logBuilder.append("├╴");
            }

            if (!last) {
                interimSeparator += "│ ";
            } else {
                interimSeparator += "  ";
            }
        }

        logBuilder.append(nextNode).append('\n');
        for (int i = 0; i < nextNode.childCalls.size(); i++) {
            ConverterCallNode childCall = nextNode.childCalls.get(i);
            if (!childCall.isDuplicate) {
                logRecursivelyInternal(childCall, level + 1, i == (nextNode.childCalls.size() - childCall.iterations), logBuilder, interimSeparator,
                        isDup || childCall.iterations > 1);
            }
        }


    }
}
