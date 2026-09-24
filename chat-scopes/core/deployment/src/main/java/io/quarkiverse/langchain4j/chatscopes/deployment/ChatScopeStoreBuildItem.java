package io.quarkiverse.langchain4j.chatscopes.deployment;

import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.builder.item.SimpleBuildItem;

final public class ChatScopeStoreBuildItem extends SimpleBuildItem {
    private Class<? extends ChatScopeStore> storeClass;

    public ChatScopeStoreBuildItem(Class<? extends ChatScopeStore> storeClass) {
        this.storeClass = storeClass;
    }

    public Class<? extends ChatScopeStore> getStoreClass() {
        return storeClass;
    }
}