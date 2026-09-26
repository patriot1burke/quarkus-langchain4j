package io.quarkiverse.langchain4j.chatscopes.internal;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ContextNotActiveException;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ContextInstanceHandle;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.impl.LazyValue;

public class ChatScopeStoreManager {
    static LazyValue<ChatScopeStore> current = new LazyValue<>(
            () -> Arc.container().instance(ChatScopeStore.class).get());

    public static ChatScopeManagedContext.ChatScopeImpl activate(String scopeId) {
        ChatScopeStore store = current.get();
        if (store == null) {
            return null;
        }
        return activate(store, scopeId);
    }

    private static ChatScopeManagedContext.ChatScopeImpl activate(ChatScopeStore store, String id) {
        ChatScopeStore.PassivatedScope scope = store.activate(id);
        if (scope == null) {
            return null;
        }
        ChatScopeManagedContext.ChatScopeImpl parent = null;
        if (scope.parent() != null) {
            parent = activate(store, scope.parent());
            if (parent == null) {
                throw new ContextNotActiveException("Parent scope with id " + scope.parent() + " is not active");
            }
        }
        return ChatScopeManagedContext.INSTANCE.create(scope.id(), scope.route(), parent);
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
                Map<InjectableBean<?>, Object> beans = new HashMap<>();
                for (ContextInstanceHandle<?> handle : scope.getBeans()) {
                    // todo call prepassivate
                    beans.put(handle.getBean(), handle.get());
                }
                tx.passivate(scope, beans);
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
