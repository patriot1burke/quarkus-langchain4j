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
        Map<String, ScopeRepresentation> passivated = new HashMap();

        @Override
        public void passivate(ChatScope scope, Map<InjectableBean<?>, Object> cdiBeans) {
            ScopeRepresentation rep = this.passivated.computeIfAbsent(scope.getId(), ScopeRepresentation::new);
            rep.parent = scope.parent() == null ? null : scope.parent().getId();
            rep.route = scope.getRoute();
            Map<String, String> beans = rep.beans;
            String json = null;
            for (Map.Entry<InjectableBean<?>, Object> entry : cdiBeans.entrySet()) {
                InjectableBean<?> bean = entry.getKey();
                Object instance = entry.getValue();
                try {
                    if (instance instanceof Subclass) {
                        json = JsonPassivation.mapper.writerFor(instance.getClass().getSuperclass())
                                .writeValueAsString(instance);
                    } else {
                        json = JsonPassivation.mapper.writeValueAsString(instance);

                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                beans.put(bean.getIdentifier(), json);
            }
        }

        @Override
        public void commit() {
            save(passivated);
        }

        @Override
        public void rollback() {

        }
    }

    public abstract void save(Map<String, ScopeRepresentation> state);

    @Override
    public PassivateTransaction beginPassivate() {
        return new JsonPassivationTransaction();
    }

    public Object activateBean(Object bean, String json) {
        try {
            return JsonPassivation.mapper.readerForUpdating(bean).readValue(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract ScopeRepresentation load(String chatScopeId);

    @Override
    public PassivatedScope activate(String chatScopeId) {
        ScopeRepresentation rep = load(chatScopeId);
        if (rep == null) {
            return null;
        }
        return new PassivatedScope(rep.id, rep.route, rep.parent);
    }
}
