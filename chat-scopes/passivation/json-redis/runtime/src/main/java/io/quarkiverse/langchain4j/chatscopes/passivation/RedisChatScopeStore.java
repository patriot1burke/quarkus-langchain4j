package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

public class RedisChatScopeStore extends AbstractJsonChatScopeStore implements ChatScopeStore {
    private final ValueCommands<String, byte[]> valueCommands;
    private final KeyCommands<String> keyCommands;

    public RedisChatScopeStore(RedisDataSource redisDataSource) {
        this.valueCommands = redisDataSource.value(new TypeReference<>() {
        });
        this.keyCommands = redisDataSource.key(String.class);
    }

    ObjectMapper mapper = new ObjectMapper();

    ConcurrentHashMap<String, ChatScopeRepresentation> chatScopeEntries = new ConcurrentHashMap<>();

    @Override
    public void save(Map<String, ChatScopeRepresentation> scopes) {
        for (Map.Entry<String, ChatScopeRepresentation> entry : scopes.entrySet()) {
            ChatScopeRepresentation chatScopeEntry = chatScopeEntries.computeIfAbsent(entry.getKey(),
                    ChatScopeRepresentation::new);
            synchronized (chatScopeEntry) {
                chatScopeEntry.parent = entry.getValue().parent;
                chatScopeEntry.route = entry.getValue().route;
                chatScopeEntry.beans.putAll(entry.getValue().beans);
                try {
                    byte[] val = mapper.writeValueAsBytes(chatScopeEntry);
                    valueCommands.set(entry.getKey(), val);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public ChatScopeRepresentation load(String chatScopeId) {
        if (chatScopeEntries.containsKey(chatScopeId)) {
            return chatScopeEntries.get(chatScopeId);
        }
        byte[] bytes = valueCommands.get(chatScopeId);

        if (bytes == null) {
            return null;
        }

        ChatScopeRepresentation entry = chatScopeEntries.computeIfAbsent(chatScopeId, id -> {
            try {
                ChatScopeRepresentation chatScopeEntry = mapper.readValue(bytes, ChatScopeRepresentation.class);
                chatScopeEntries.put(id, chatScopeEntry);
                return chatScopeEntry;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return entry;
    }

    @Override
    public Object activateBean(ChatScope scope, String beanId, Object instance) {
        ChatScopeRepresentation chatScopeEntry = chatScopeEntries.get(scope.getId());
        if (chatScopeEntry == null) {
            return instance;
        }
        String bean = chatScopeEntry.beans.get(beanId);
        if (bean == null) {
            return instance;
        }
        return activateBean(instance, bean);
    }

    @Override
    public void destroy(ChatScope scope) {
        chatScopeEntries.remove(scope.getId());
        keyCommands.del(scope.getId());
    }

}
