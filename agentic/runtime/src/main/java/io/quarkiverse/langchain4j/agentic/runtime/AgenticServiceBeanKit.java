package io.quarkiverse.langchain4j.agentic.runtime;

import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.SyntheticCreationalContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import static dev.langchain4j.agentic.internal.AgentUtil.*;

public class AgenticServiceBeanKit {
    // make sure that beanKit classes are registered as injection points for the synthetic
    public static void augment(Class<?> agentClass, AgenticService builder, SyntheticCreationalContext context) {
        BeanKit beanKit = agentClass.getAnnotation(BeanKit.class);
        if (beanKit == null) {
            return;
        }
        for (Class<?> beanClass : beanKit.value()) {
            Object ref = context.getInjectedReference(beanClass);
            addAgentCallbacks(beanClass, ref, builder);
        }
    }

    public static void addAgentCallbacks(Class<?> beanClass, Object bean, AgenticService builder) {
        addBaseAgentCallbacks(beanClass, bean, builder);

        if (builder instanceof LoopAgentService<?> loop) {
            Optional<Method> exitCondition = getAnnotatedMethodOnClass(beanClass, ExitCondition.class);
            if (exitCondition.isPresent()) {
                loopExitConditionPredicate(beanClass, bean, exitCondition.get(), loop);
            }
        }

    }

    public static void addBaseAgentCallbacks(Class<?> beanClass, Object bean, AgenticService builder) {
        Optional<Method> method = getAnnotatedMethodOnClass(beanClass, BeforeCall.class);
        if (method.isPresent()) {
            Method beforeCall = method.get();
            var callback = agenticScopeFunction(beforeCall, beanClass);
            Consumer<AgenticScope> function = scope ->
                callback.apply(scope);
            builder.beforeCall(function);
        }

        method = getAnnotatedMethodOnClass(beanClass, ErrorHandler.class);
        if (method.isPresent()) {
            Method errorHandler = method.get();
            builder.errorHandler(errorContext -> {
                try {
                    return (ErrorRecoveryResult)errorHandler.invoke(bean, errorContext);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e);
                }

            });
        }
        if (bean instanceof AgentListener listener) {
            builder.listener(listener);
        }

    }

    public static void loopExitConditionPredicate(Class<?> beanClass, Object bean, Method method, LoopAgentService builder) {
        List<AgentArgument> agentArguments = argumentsFromMethod(method);
        BiPredicate<AgenticScope, Integer> function = (agenticScope, loopCounter) -> {
            try {
                Object[] args = agentInvocationArguments(
                        agenticScope,
                        agentArguments,
                        Map.of(AGENTIC_SCOPE_ARG_NAME, agenticScope, LOOP_COUNTER_ARG_NAME, loopCounter))
                        .positionalArgs();
                return (boolean) method.invoke(bean, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking exit predicate method: " + method.getName(), e);
            }
        };
        builder.testExitAtLoopEnd(method.getAnnotation(ExitCondition.class).testExitAtLoopEnd());
        builder.exitCondition(method.getAnnotation(ExitCondition.class).description(), function);
    }

    public static <T> Function<AgenticScope, T> agenticScopeFunction(Method functionMethod, Class<T> bean) {
        List<AgentArgument> agentArguments = argumentsFromMethod(functionMethod);
        return agenticScope -> {
            try {
                Object target = Arc.container().select(bean);
                Object[] args = agentInvocationArguments(
                        agenticScope, agentArguments, Map.of(AGENTIC_SCOPE_ARG_NAME, agenticScope))
                        .positionalArgs();
                return (T) functionMethod.invoke(target, args);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking method: " + functionMethod.getName(), e);
            }
        };
    }
}
