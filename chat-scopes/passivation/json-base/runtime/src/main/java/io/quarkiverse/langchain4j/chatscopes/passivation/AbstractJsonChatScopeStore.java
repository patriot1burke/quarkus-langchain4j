package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.Subclass;

public abstract class AbstractJsonChatScopeStore implements ChatScopeStore {
    class JsonPassivationTransaction implements PassivateTransaction {
        Map<String, Map<String, String>> scopes = new HashMap();

        @Override
        public void passivate(ChatScope scope, InjectableBean<?> bean, Object instance) {
            Map<String, String> beans = scopes.computeIfAbsent(scope.getId(), k -> new HashMap<>());
            String json = null;
            try {
                if (instance instanceof Subclass) {
                    json = JsonPassivation.mapper.writerFor(instance.getClass().getSuperclass()).writeValueAsString(instance);
                } else {
                    json = JsonPassivation.mapper.writeValueAsString(instance);

                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            beans.put(bean.getIdentifier(), json);
        }

        @Override
        public void commit() {
            save(scopes);
        }

        @Override
        public void rollback() {

        }
    }

    protected abstract void save(Map<String, Map<String, String>> scopes);

    @Override
    public PassivateTransaction beginPassivate() {
        return new JsonPassivationTransaction();
    }

    protected Object activateBean(Object bean, String json) {
        try {
            return JsonPassivation.mapper.readerForUpdating(bean).readValue(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
