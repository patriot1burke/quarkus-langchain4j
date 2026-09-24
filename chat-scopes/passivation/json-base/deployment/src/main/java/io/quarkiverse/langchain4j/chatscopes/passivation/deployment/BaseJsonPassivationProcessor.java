package io.quarkiverse.langchain4j.chatscopes.passivation.deployment;

import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;

public class BaseJsonPassivationProcessor {

    // register for reflection every @ChatScoped class (but not interface).
    @BuildStep
    public void registerForReflection(BeanDiscoveryFinishedBuildItem reg, BuildProducer<ReflectiveHierarchyBuildItem> reflect) {
        reg.beanStream().withScope(ChatScoped.class).forEach((bean) -> {
            reflect.produce(ReflectiveHierarchyBuildItem.builder(bean.getBeanClass()).build());
        });
    }
}
