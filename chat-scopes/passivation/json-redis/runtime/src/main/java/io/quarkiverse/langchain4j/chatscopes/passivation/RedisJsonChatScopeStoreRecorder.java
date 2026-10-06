package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.util.function.Function;

import jakarta.enterprise.inject.Default;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RedisJsonChatScopeStoreRecorder {
    public Function<SyntheticCreationalContext<RedisJsonChatScopeStore>, RedisJsonChatScopeStore> chatMemoryStoreFunction(
            String clientName) {
        return new Function<>() {
            @Override
            public RedisJsonChatScopeStore apply(SyntheticCreationalContext<RedisJsonChatScopeStore> context) {
                RedisDataSource dataSource;
                if (clientName == null) {
                    dataSource = context.getInjectedReference(RedisDataSource.class, new Default.Literal());
                } else {
                    dataSource = context.getInjectedReference(RedisDataSource.class,
                            new RedisClientName.Literal(clientName));
                }
                return new RedisJsonChatScopeStore(dataSource);
            }
        };
    }
}
