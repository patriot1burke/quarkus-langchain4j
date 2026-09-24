package io.quarkiverse.langchain4j.chatscopes.spi;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkus.arc.InjectableBean;

public interface ChatScopeStore {

    public interface PassivateTransaction {
        void passivate(ChatScope scope, InjectableBean<?> bean, Object instance);

        void commit();

        void rollback();
    }

    PassivateTransaction beginPassivate();

    boolean activate(String chatScopeId);

    Object activateBean(ChatScope scope, String beanId, Object instance);

    void destroy(ChatScope scope);
}
