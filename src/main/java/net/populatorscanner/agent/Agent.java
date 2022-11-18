package net.populatorscanner.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import net.populatorscanner.log.LogUtils;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import static net.populatorscanner.log.LogUtils.LOGGER;

public class Agent {

    public static final String CONVERTER_CLASS_PREFIX = "de.hybris.platform.servicelayer.dto.converter.Converter<";
    public static final String POPULATOR_CLASS_PREFIX = "de.hybris.platform.converters.Populator<";

    public static final String CONVERTER_METHOD_NAME = "convert";
    public static final String POPULATOR_METHOD_NAME = "populate";

    public static final Predicate<String> CLASS_MATCH_PREDICATE =
            (typeName) -> typeName.startsWith(CONVERTER_CLASS_PREFIX) || typeName.startsWith(POPULATOR_CLASS_PREFIX);

    public static void premain(String arg, Instrumentation inst) throws Exception {
        LOGGER.info("Agent is loaded!");

        File temp = Files.createTempDirectory("tmp").toFile();
        ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, inst).inject(Collections.singletonMap(
                new TypeDescription.ForLoadedType(ConverterCallsInterceptor.class),
                ClassFileLocator.ForClassLoader.read(ConverterCallsInterceptor.class)));

        new AgentBuilder.Default()
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
                .with(new AgentBuilder.InjectionStrategy.UsingInstrumentation(inst, temp))
//                .with(AgentBuilder.Listener.StreamWriting.toSystemError())
                .type((typeDescription, classLoader, module, classBeingRedefined, protectionDomain) -> {
                    return typeDescription.getInterfaces().stream().map(TypeDefinition::getTypeName).anyMatch(CLASS_MATCH_PREDICATE);
                })
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader,
                                                            JavaModule module) {
                        return builder.visit(Advice.to(ConverterCallsInterceptor.class).on(ElementMatchers.named(CONVERTER_METHOD_NAME)
                                .or(ElementMatchers.named(POPULATOR_METHOD_NAME))));
                    }

                }).installOn(inst);
    }

    public static class WorkContext {
        public static final ThreadLocal<ConverterCallNode> lastRootCallTl = new ThreadLocal<>();

        public static final ThreadLocal<ConverterCallNode> lastSubtreeForMatching = new ThreadLocal<>();
        public static final ThreadLocal<ConverterCallNode> skipUntilNode = new ThreadLocal<>();
        public static final ThreadLocal<ConverterCallNode> prevLastRootCall = new ThreadLocal<>();
        public static final ThreadLocal<Boolean> setSkipUntilNodeToNull = ThreadLocal.withInitial(() -> Boolean.FALSE);
    }

    public static class ConverterCallsInterceptor {


        @Advice.OnMethodEnter
        public static void pre(@Advice.AllArguments(readOnly = true) Object[] wraps, @Advice.This Object thiz) throws Exception {
            ConverterCallNode.initClassLoaderIfNeeded(thiz.getClass().getClassLoader());

            StackTraceElement[] stackTrace = new Exception().getStackTrace();
            ConverterCallNode lastRootCall;

            String sourceType = null;
            String targetType = null;
            if (wraps.length > 0) {
                sourceType = wraps[0].getClass().getTypeName();
            }

            if (thiz != null) {
                for (Type type : thiz.getClass().getGenericInterfaces()) {
                    if (CLASS_MATCH_PREDICATE.test(type.getTypeName()) && type instanceof ParameterizedType) {
                        targetType = ((ParameterizedType) type).getActualTypeArguments()[1].getTypeName();
                    }
                }
            }

            if (WorkContext.lastRootCallTl.get() == null) {
                StackTraceElement callSource = LogUtils.getCallSource(stackTrace);
                lastRootCall = new ConverterCallNode.Builder()
                        .withClass(stackTrace[0].getClassName())
                        .withCallSource(callSource != null ?
                                callSource.getFileName() + ":" + callSource.getLineNumber()
                                : null)
                        .withSourceType(sourceType)
                        .withTargetType(targetType)
                        .build();

                WorkContext.lastRootCallTl.set(lastRootCall);
            } else {
                lastRootCall = WorkContext.lastRootCallTl.get();
                StackTraceElement callSource = LogUtils.getCallSource(stackTrace);
                ConverterCallNode node = new ConverterCallNode.Builder()
                        .withClass(stackTrace[0].getClassName())
                        .withCallSource(callSource != null ?
                                callSource.getFileName() + ":" + callSource.getLineNumber()
                                : null)
                        .withSourceType(sourceType)
                        .withTargetType(targetType)
                        .build();
                lastRootCall.childCalls.add(node);
                node.parentCall = lastRootCall;
                WorkContext.lastRootCallTl.set(node);
            }


            if (WorkContext.prevLastRootCall.get() != null && WorkContext.prevLastRootCall.get().parentCall != WorkContext.lastRootCallTl.get().parentCall) {
                if (WorkContext.skipUntilNode.get() == null) {
                    WorkContext.lastSubtreeForMatching.set(WorkContext.prevLastRootCall.get());
                }

                if (WorkContext.setSkipUntilNodeToNull.get() || Objects.equals(WorkContext.prevLastRootCall.get(), WorkContext.skipUntilNode.get())) {
                    WorkContext.skipUntilNode.set(null);
                }
            }
        }

        @Advice.OnMethodExit
        public static void post(@Advice.AllArguments(readOnly = true) Object[] wraps) throws Exception {
            if (WorkContext.lastRootCallTl.get() != null) {

                ConverterCallNode lastRootCall = WorkContext.lastRootCallTl.get();
                if (lastRootCall.parentCall != null) {
                    WorkContext.lastRootCallTl.set(lastRootCall.parentCall);
                    WorkContext.prevLastRootCall.set(lastRootCall);

                    WorkContext.setSkipUntilNodeToNull.set(false);

                    if (WorkContext.lastSubtreeForMatching.get() != null && !lastRootCall.equals(WorkContext.skipUntilNode.get())) {
                        ConverterCallNode commonBeforeParent;
                        if (lastRootCall.parentCall.childCalls.contains(WorkContext.lastSubtreeForMatching.get())) {
                            commonBeforeParent = lastRootCall;
                        } else {
                            commonBeforeParent = lastRootCall.parentCall;
                        }

                        while (commonBeforeParent != null && !Objects.equals(WorkContext.lastSubtreeForMatching.get(), commonBeforeParent)) {
                            commonBeforeParent = commonBeforeParent.parentCall;
                        }

                        // now if it exists, let's compare the trees
                        if (commonBeforeParent != null) {
                            boolean isDuplicate = WorkContext.lastSubtreeForMatching.get().deepEquals(commonBeforeParent);

                            if (isDuplicate) {
                                WorkContext.lastSubtreeForMatching.get().iterations += 1;
                                if (WorkContext.lastSubtreeForMatching.get().equals(lastRootCall)) {
                                    WorkContext.skipUntilNode.set(commonBeforeParent.parentCall);
                                } else {
                                    WorkContext.skipUntilNode.set(commonBeforeParent);
                                }
                                commonBeforeParent.isDuplicate = true;
                            } else if (WorkContext.prevLastRootCall.get().parentCall != lastRootCall.parentCall) {
                                WorkContext.lastSubtreeForMatching.set(null);
                                WorkContext.skipUntilNode.set(null);
                            }
                        } else {
                            WorkContext.setSkipUntilNodeToNull.set(true);
                                WorkContext.lastSubtreeForMatching.set(lastRootCall);
                        }
                    }

                } else {
                    WorkContext.lastRootCallTl.set(null);
                    lastRootCall.logRecursively();
                }
            }
        }
    }
}
