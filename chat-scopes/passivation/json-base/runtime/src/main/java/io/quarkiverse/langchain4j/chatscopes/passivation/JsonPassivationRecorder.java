package io.quarkiverse.langchain4j.chatscopes.passivation;

import static io.quarkiverse.langchain4j.chatscopes.passivation.JsonPassivation.ignoredAnnotations;
import static io.quarkiverse.langchain4j.chatscopes.passivation.JsonPassivation.ignoredClasses;

import java.lang.annotation.Annotation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JsonPassivationRecorder {
    public void initMapper() {
        JsonPassivation.mapper = new ObjectMapper();
        JsonPassivation.mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        JsonPassivation.mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setDefaultFilter(new PassivationBeanPropertyFilter())
                .setFailOnUnknownId(false);
        JsonPassivation.mapper.setFilterProvider(filterProvider);
        JsonPassivation.mapper.setDefaultMergeable(true);
        // hack to force filter to run on every serialization
        JsonPassivation.mapper.addMixIn(Object.class, PassivationFilterMixIn.class);
    }

    public void ignore(Class<?> clazz) {
        ignoredClasses.add(clazz);
    }

    public void ignoreAnnotation(Class<? extends Annotation> annotation) {
        ignoredAnnotations.add(annotation);
    }

    @JsonFilter("passivationFilter")
    public interface PassivationFilterMixIn {
    }
}
