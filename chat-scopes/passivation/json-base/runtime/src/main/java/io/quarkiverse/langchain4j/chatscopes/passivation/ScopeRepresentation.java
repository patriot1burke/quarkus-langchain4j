package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.util.HashMap;
import java.util.Map;

public class ScopeRepresentation {
    public String id;
    public String parent;
    public String route;
    public Map<String, String> beans = new HashMap<>();

    public ScopeRepresentation() {
    }

    public ScopeRepresentation(String id) {
        this.id = id;
    }
}
