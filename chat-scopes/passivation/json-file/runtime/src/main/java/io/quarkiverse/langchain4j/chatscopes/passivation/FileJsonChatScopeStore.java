package io.quarkiverse.langchain4j.chatscopes.passivation;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;

import java.util.Map;

public class FileJsonChatScopeStore extends AbstractJsonChatScopeStore {
    @Override
    protected void save(Map<String, Map<String, String>> scopes) {

    }

    @Override
    public boolean activate(String chatScopeId) {
        return false;
    }

    @Override
    public Object activateBean(ChatScope scope, String beanId, Object instance) {
        return null;
    }

    @Override
    public void destroy(ChatScope scope) {

    }
}
