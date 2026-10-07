package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.runtime.Startup;

@ApplicationScoped
public class FileChatScopeStore extends AbstractJsonChatScopeStore implements ChatScopeStore {
    @ConfigProperty(name = "quarkiverse.langchain4j.chatscopes.passivation.file.path", defaultValue = "/tmp/chatscopes")
    String path;

    Path dir;

    ObjectMapper mapper = new ObjectMapper();

    @Startup
    public void init() throws Exception {
        dir = Path.of(this.path);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

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
                Path chatScopePath = scopePath(entry.getKey());
                try {
                    mapper.writeValue(chatScopePath.toFile(), chatScopeEntry);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    Path scopePath(String id) {
        return dir.resolve(id + ".json");
    }

    @Override
    public ChatScopeRepresentation load(String chatScopeId) {
        if (chatScopeEntries.containsKey(chatScopeId)) {
            return chatScopeEntries.get(chatScopeId);
        }
        Path chatScopePath = scopePath(chatScopeId);
        if (!Files.exists(chatScopePath)) {
            return null;
        }

        ChatScopeRepresentation entry = chatScopeEntries.computeIfAbsent(chatScopeId, id -> {
            try {
                ChatScopeRepresentation chatScopeEntry = mapper.readValue(chatScopePath.toFile(),
                        ChatScopeRepresentation.class);
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
        Path chatScopePath = scopePath(scope.getId());
        if (Files.exists(chatScopePath)) {
            try {
                Files.delete(chatScopePath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
