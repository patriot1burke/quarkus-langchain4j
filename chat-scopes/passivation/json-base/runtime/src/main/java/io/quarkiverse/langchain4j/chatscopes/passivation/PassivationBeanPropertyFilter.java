package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;

import io.quarkus.arc.Subclass;

public class PassivationBeanPropertyFilter implements PropertyFilter {
    @Override
    public void serializeAsField(Object pojo, JsonGenerator gen, SerializerProvider prov, PropertyWriter writer)
            throws Exception {
        Object target = writer.getMember().getValue(pojo);
        if (target == null) {
            return;
        }
        AnnotatedElement annotatedElement = writer.getMember().getAnnotated();
        if (!annotatedElement.isAnnotationPresent(Passivate.class)) {
            // if passivation isn't forced
            for (Class<?> ignored : JsonPassivation.ignoredClasses) {
                if (ignored.isInstance(target)) {
                    return;
                }
            }
            for (Class<? extends Annotation> ignored : JsonPassivation.ignoredAnnotations) {
                if (annotatedElement.isAnnotationPresent(ignored)) {
                    return;
                }
            }
            if (annotatedElement.isAnnotationPresent(Inject.class)) {
                return;
            }
            for (Annotation annotation : annotatedElement.getAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(Qualifier.class)) {
                    return;
                }
            }
        }
        if (target instanceof Subclass) {
            Class superClass = target.getClass().getSuperclass();
            gen.writeFieldName(writer.getName());
            JsonSerializer<Object> valueSerializer = prov.findValueSerializer(superClass, writer);
            valueSerializer.serialize(target, gen, prov);
        } else {
            writer.serializeAsField(pojo, gen, prov);
        }
    }

    @Override
    public void serializeAsElement(Object elementValue, JsonGenerator gen, SerializerProvider prov, PropertyWriter writer)
            throws Exception {
        writer.serializeAsElement(elementValue, gen, prov);

    }

    @Override
    public void depositSchemaProperty(PropertyWriter writer, ObjectNode propertiesNode, SerializerProvider provider)
            throws JsonMappingException {
        writer.depositSchemaProperty(propertiesNode, provider);

    }

    @Override
    public void depositSchemaProperty(PropertyWriter writer, JsonObjectFormatVisitor objectVisitor, SerializerProvider provider)
            throws JsonMappingException {
        writer.depositSchemaProperty(objectVisitor, provider);
    }
}
