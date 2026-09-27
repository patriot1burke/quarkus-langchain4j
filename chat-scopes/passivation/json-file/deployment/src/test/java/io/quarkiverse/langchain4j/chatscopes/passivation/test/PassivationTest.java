package io.quarkiverse.langchain4j.chatscopes.passivation.test;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;
import io.quarkiverse.langchain4j.chatscopes.internal.ChatScopeManagedContext;
import io.quarkus.test.QuarkusUnitTest;

public class PassivationTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            ScopedCounterBean.class));

    @ChatScoped
    public static class ScopedCounterBean {
        private int counter = 0;

        public void increment() {
            counter++;
        }

        public int getCounter() {
            return counter;
        }
    }

    @Inject
    ScopedCounterBean scopedCounterBean;

    @Test
    public void testPassivation() throws Exception {
        ChatScope.begin();
        String id = ChatScope.id();
        Assertions.assertEquals(0, scopedCounterBean.getCounter());
        scopedCounterBean.increment();
        Assertions.assertEquals(1, scopedCounterBean.getCounter());
        ChatScope.deactivate();

        ChatScopeManagedContext.INSTANCE.clear();

        ChatScope.activate(id);
        Assertions.assertEquals(1, scopedCounterBean.getCounter());
        ChatScope.end();

        try {
            ChatScope.activate(id);
            Assertions.fail("scope should be removed");
        } catch (ContextNotActiveException ex) {
        }

    }
}
