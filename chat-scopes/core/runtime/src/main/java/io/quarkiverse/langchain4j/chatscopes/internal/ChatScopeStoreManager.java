package io.quarkiverse.langchain4j.chatscopes.internal;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ContextInstanceHandle;
import io.quarkus.arc.impl.LazyValue;

public class ChatScopeStoreManager {
    static LazyValue<ChatScopeStore> current = new LazyValue<>(
            () -> Arc.container().instance(ChatScopeStore.class).get());

    public static boolean activate(String scopeId) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return false;
        }
        return store.activate(scopeId);
    }

    public static Object activateBean(ChatScope scope, String beanId, Object bean) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return bean;
        }
        Object newBean = store.activateBean(scope, beanId, bean);
        return newBean != null ? newBean : bean;
    }

    public static void passivate(ChatScope chatScope) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return;
        }
        ChatScopeStore.PassivateTransaction tx = store.beginPassivate();
        ChatScopeManagedContext.ChatScopeImpl scope = (ChatScopeManagedContext.ChatScopeImpl) chatScope;
        try {
            do {
                for (ContextInstanceHandle<?> handle : scope.getBeans()) {
                    // todo call prepassivate
                    tx.passivate(scope, handle.getBean(), handle.get());
                }
                scope = scope.parent;
            } while (scope != null);
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw new RuntimeException(e);
        }
    }

    public static void destroy(ChatScope scope) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return;
        }
        store.destroy(scope);
    }
}
