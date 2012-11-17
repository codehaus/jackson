package org.codehaus.jackson.map.deser.std;

import java.io.IOException;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.annotate.JacksonStdImpl;
import org.codehaus.jackson.map.util.ClassUtil;

/**
 * 
 * @since 1.9 (renamed from 'org.codehaus.jackson.map.deser.StdDeserializer#ClassDeserializer')
 */
@JacksonStdImpl
public class ClassDeserializer
    extends StdScalarDeserializer<Class<?>>
{
    public ClassDeserializer() { super(Class.class); }

    @Override
    public Class<?> deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        JsonToken curr = jp.getCurrentToken();
        // Currently will only accept if given simple class name
        if (curr == JsonToken.VALUE_STRING) {
            String className = jp.getText();
            try {
                return ClassUtil.findClass(className);
            } catch (ClassNotFoundException e) {
                throw ctxt.instantiationException(_valueClass, e);
            }
        }
        throw ctxt.mappingException(_valueClass, curr);
    }
}
