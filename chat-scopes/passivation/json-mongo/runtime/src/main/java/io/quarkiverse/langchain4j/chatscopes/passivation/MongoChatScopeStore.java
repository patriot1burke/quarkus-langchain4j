package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.Document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;

public class MongoChatScopeStore extends AbstractJsonChatScopeStore implements ChatScopeStore {

    private static final String CONTEXT_STATE_FIELD = "context_state";
    private static final String ID_FIELD = "_chatscope_id";

    private final MongoCollection<Document> collection;

    public MongoChatScopeStore(MongoClient mongoClient, String database, String collection) {
        this.collection = mongoClient.getDatabase(database).getCollection(collection);
    }

    final ObjectMapper mapper = new ObjectMapper();

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
                    String val = mapper.writeValueAsString(chatScopeEntry);
                    Document document = new Document()
                            .append(ID_FIELD, entry.getKey())
                            .append(CONTEXT_STATE_FIELD, val);
                    collection.replaceOne(
                            Filters.eq(ID_FIELD, entry.getKey()),
                            document,
                            new ReplaceOptions().upsert(true));
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
        Document document = collection.find(Filters.eq(ID_FIELD, chatScopeId)).first();
        if (document == null || !document.containsKey(CONTEXT_STATE_FIELD)) {
            return null;
        }

        ChatScopeRepresentation entry = chatScopeEntries.computeIfAbsent(chatScopeId, id -> {
            try {
                String contextStateJson = document.getString(CONTEXT_STATE_FIELD);
                ChatScopeRepresentation chatScopeEntry = mapper.readValue(contextStateJson, ChatScopeRepresentation.class);
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
        collection.deleteOne(Filters.eq(ID_FIELD, scope.getId()));
    }

}
