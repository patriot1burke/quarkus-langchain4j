package io.quarkiverse.langchain4j.agentic.runtime;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.planner.PlannerBasedServiceImpl;
import dev.langchain4j.agentic.supervisor.SupervisorAgentServiceImpl;
import dev.langchain4j.model.chat.ChatModel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.agentic.declarative.DeclarativeUtil.*;
import static dev.langchain4j.agentic.internal.AgentUtil.*;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

public class DeclarativeAgenticServices {
    static Method createSubagent;

    static {
        try {
            createSubagent = AgenticServices.class.getDeclaredMethod("createSubagent", Class.class, ChatModel.class, AgenticServices.AgentConfigurator.class);
            createSubagent.setAccessible(true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }
    private static List<AgentExecutor> constructSubagents(
            Class<?>[] subAgents, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        List<AgentExecutor> list = new ArrayList<>();
        for (Class<?> subAgent : subAgents) {
            list.add(constructSubagent(subAgent, chatModel, agentConfigurator));
        }
        return list;
    }
    public static AgentExecutor constructSubagent(Class<?> agentServiceClass, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        try {
            return (AgentExecutor) createSubagent.invoke(null, new Object[]{agentServiceClass, chatModel, agentConfigurator});
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }
    public static AgenticService createAgenticSystem(
            Class<?> agentServiceClass, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {

        Optional<Method> sequenceMethod = getAnnotatedMethodOnClass(agentServiceClass, SequenceAgent.class);
        if (sequenceMethod.isPresent()) {
            return buildSequentialAgent(agentServiceClass, sequenceMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> loopMethod = getAnnotatedMethodOnClass(agentServiceClass, LoopAgent.class);
        if (loopMethod.isPresent()) {
            return buildLoopAgent(agentServiceClass, loopMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> conditionalMethod = getAnnotatedMethodOnClass(agentServiceClass, ConditionalAgent.class);
        if (conditionalMethod.isPresent()) {
            return buildConditionalAgent(agentServiceClass, conditionalMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> parallelMethod = getAnnotatedMethodOnClass(agentServiceClass, ParallelAgent.class);
        if (parallelMethod.isPresent()) {
            return buildParallelAgent(agentServiceClass, parallelMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> parallelMapperMethod = getAnnotatedMethodOnClass(agentServiceClass, ParallelMapperAgent.class);
        if (parallelMapperMethod.isPresent()) {
            return buildParallelMapperAgent(
                    agentServiceClass, parallelMapperMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> supervisorMethod =
                getAnnotatedMethodOnClass(agentServiceClass, dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        if (supervisorMethod.isPresent()) {
            return buildSupervisorAgent(agentServiceClass, supervisorMethod.get(), chatModel, agentConfigurator);
        }

        Optional<Method> plannerMethod = getAnnotatedMethodOnClass(agentServiceClass, PlannerAgent.class);
        if (plannerMethod.isPresent()) {
            return buildPlannerAgent(agentServiceClass, plannerMethod.get(), chatModel, agentConfigurator);
        }

        return null;

    }

    private static AgenticService buildSequentialAgent(
            Class<?> agentServiceClass, Method agentMethod, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        SequenceAgent annotation = agentMethod.getAnnotation(SequenceAgent.class);
        var builder = AgenticServices.sequenceBuilder(agentServiceClass)
                .subAgents(constructSubagents(annotation.subAgents(), chatModel, agentConfigurator));

        setAgentInstanceFactory(builder, agentConfigurator);

        buildAgentSpecs(
                agentMethod,
                annotation.name(),
                annotation.description(),
                AgentUtil.outputKey(annotation.outputKey(), annotation.typedOutputKey()),
                annotation.compensateOnError(),
                builder);

        return builder;
    }

    private static AgenticService buildLoopAgent(
            Class<?> agentServiceClass, Method agentMethod, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        LoopAgent annotation = agentMethod.getAnnotation(LoopAgent.class);
        var builder = AgenticServices.loopBuilder(agentServiceClass)
                .subAgents(constructSubagents(annotation.subAgents(), chatModel, agentConfigurator))
                .maxIterations(annotation.maxIterations());

        setAgentInstanceFactory(builder, agentConfigurator);

        buildAgentSpecs(
                agentMethod,
                annotation.name(),
                annotation.description(),
                AgentUtil.outputKey(annotation.outputKey(), annotation.typedOutputKey()),
                annotation.compensateOnError(),
                builder);

        return builder;
    }

    private static AgenticService buildConditionalAgent(
            Class<?> agentServiceClass, Method agentMethod, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        ConditionalAgent annotation = agentMethod.getAnnotation(ConditionalAgent.class);
        var builder = AgenticServices.conditionalBuilder(agentServiceClass);

        setAgentInstanceFactory(builder, agentConfigurator);

        buildAgentSpecs(
                agentMethod,
                annotation.name(),
                annotation.description(),
                AgentUtil.outputKey(annotation.outputKey(), annotation.typedOutputKey()),
                annotation.compensateOnError(),
                builder);

        for (Class<?> subagent : annotation.subAgents()) {
            predicateMethod(agentServiceClass, method -> {
                ActivationCondition activationCondition = method.getAnnotation(ActivationCondition.class);
                return activationCondition != null
                        && Arrays.asList(activationCondition.value()).contains(subagent);
            })
                    .ifPresent(method -> builder.subAgent(
                            method.getAnnotation(ActivationCondition.class).description(),
                            agenticScopePredicate(method),
                            constructSubagent(subagent, chatModel, agentConfigurator)));
        }

        return builder;
    }

    private static AgenticService buildParallelAgent(
            Class<?> agentServiceClass, Method agentMethod, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        ParallelAgent annotation = agentMethod.getAnnotation(ParallelAgent.class);
        var builder = AgenticServices.parallelBuilder(agentServiceClass)
                .subAgents(constructSubagents(annotation.subAgents(), chatModel, agentConfigurator));

        setAgentInstanceFactory(builder, agentConfigurator);

        buildAgentSpecs(
                agentMethod,
                annotation.name(),
                annotation.description(),
                AgentUtil.outputKey(annotation.outputKey(), annotation.typedOutputKey()),
                annotation.compensateOnError(),
                builder);

        return builder;
    }

    private static AgenticService buildParallelMapperAgent(
            Class<?> agentServiceClass, Method agentMethod, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        ParallelMapperAgent annotation = agentMethod.getAnnotation(ParallelMapperAgent.class);
        var builder = AgenticServices.parallelMapperBuilder(agentServiceClass)
                .subAgents(List.of(constructSubagent(annotation.subAgent(), chatModel, agentConfigurator)))
                .itemsProvider(annotation.itemsProvider());

        setAgentInstanceFactory(builder, agentConfigurator);

        buildAgentSpecs(
                agentMethod,
                annotation.name(),
                annotation.description(),
                AgentUtil.outputKey(annotation.outputKey(), annotation.typedOutputKey()),
                annotation.compensateOnError(),
                builder);

        return builder;
    }

    private static AgenticService buildPlannerAgent(
            Class<?> agentServiceClass, Method agentMethod, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        PlannerAgent annotation = agentMethod.getAnnotation(PlannerAgent.class);
        var builder = new PlannerBasedServiceImpl<>(agentServiceClass, agentMethod)
                .subAgents(constructSubagents(annotation.subAgents(), chatModel, agentConfigurator));

        setAgentInstanceFactory(builder, agentConfigurator);

        buildAgentSpecs(
                agentMethod,
                annotation.name(),
                annotation.description(),
                AgentUtil.outputKey(annotation.outputKey(), annotation.typedOutputKey()),
                annotation.compensateOnError(),
                builder);

        return builder;
    }

    private static AgenticService buildSupervisorAgent(
            Class<?> agentServiceClass, Method agentMethod, ChatModel chatModel, AgenticServices.AgentConfigurator agentConfigurator) {
        dev.langchain4j.agentic.declarative.SupervisorAgent supervisorAgent =
                agentMethod.getAnnotation(dev.langchain4j.agentic.declarative.SupervisorAgent.class);
        var builder = new SupervisorAgentServiceImpl<>(agentServiceClass, agentMethod, chatModel)
                .maxAgentsInvocations(supervisorAgent.maxAgentsInvocations())
                .contextGenerationStrategy(supervisorAgent.contextStrategy())
                .responseStrategy(supervisorAgent.responseStrategy())
                .subAgents(constructSubagents(supervisorAgent.subAgents(), chatModel, agentConfigurator));

        setAgentInstanceFactory(builder, agentConfigurator);

        if (!isNullOrBlank(supervisorAgent.name())) {
            builder.name(supervisorAgent.name());
        } else {
            builder.name(agentMethod.getName());
        }
        if (!isNullOrBlank(supervisorAgent.description())) {
            builder.description(supervisorAgent.description());
        }
        builder.outputKey(AgentUtil.outputKey(supervisorAgent.outputKey(), supervisorAgent.typedOutputKey()));
        if (supervisorAgent.compensateOnError()) {
            builder.compensateOnError(true);
        }

        return builder;
    }

    private static void setAgentInstanceFactory(Object builder, AgenticServices.AgentConfigurator agentConfigurator) {
        if (agentConfigurator.agentInstanceFactory() != null) {
            ((AbstractServiceBuilder<?, ?>) builder).agentInstanceFactory(agentConfigurator.agentInstanceFactory());
        }
        if (agentConfigurator.defaultMemoryIdSupplier() != null) {
            ((AbstractServiceBuilder<?, ?>) builder)
                    .defaultMemoryIdSupplier(agentConfigurator.defaultMemoryIdSupplier());
        }
    }

    private static void buildAgentSpecs(
            Method agentMethod,
            String name,
            String description,
            String outputKey,
            boolean compensateOnError,
            AgenticService<?, ?> builder) {
        if (!isNullOrBlank(name)) {
            builder.name(name);
        } else {
            builder.name(agentMethod.getName());
        }
        if (!isNullOrBlank(description)) {
            builder.description(description);
        }
        if (!isNullOrBlank(outputKey)) {
            builder.outputKey(outputKey);
        }
        if (compensateOnError) {
            builder.compensateOnError(true);
        }
    }

}
