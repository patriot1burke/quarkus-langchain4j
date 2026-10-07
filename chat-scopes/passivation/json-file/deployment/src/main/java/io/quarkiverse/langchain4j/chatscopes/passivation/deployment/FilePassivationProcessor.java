package io.quarkiverse.langchain4j.chatscopes.passivation.deployment;

import io.quarkiverse.langchain4j.chatscopes.deployment.ChatScopeStoreBuildItem;
import io.quarkiverse.langchain4j.chatscopes.passivation.FileChatScopeStore;
import io.quarkus.deployment.annotations.BuildStep;

public class FilePassivationProcessor {
    @BuildStep
    public ChatScopeStoreBuildItem store() {
        return new ChatScopeStoreBuildItem(FileChatScopeStore.class);
    }
}
