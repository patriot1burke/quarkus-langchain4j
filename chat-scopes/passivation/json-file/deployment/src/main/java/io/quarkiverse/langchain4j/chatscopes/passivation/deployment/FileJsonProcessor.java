package io.quarkiverse.langchain4j.chatscopes.passivation.deployment;

import io.quarkiverse.langchain4j.chatscopes.deployment.ChatScopeStoreBuildItem;
import io.quarkiverse.langchain4j.chatscopes.passivation.FileJsonChatScopeStore;
import io.quarkus.deployment.annotations.BuildStep;

public class FileJsonProcessor {
    @BuildStep
    public ChatScopeStoreBuildItem store() {
        return new ChatScopeStoreBuildItem(FileJsonChatScopeStore.class);
    }
}
