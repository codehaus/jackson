package org.codehaus.jackson.map;

import java.io.IOException;

import org.codehaus.jackson.*;

/**
 * Abstract class that defines API used by {@link JavaTypeMapper} (and
 * other chained {@link JsonSerializer}s too) to serialize Objects of
 * arbitrary types into JSON, using provided {@link JsonGenerator}.
 */
public interface JsonSerializer<T>
{
    /**
     * Method that can be called to ask implementation to serialize
     * values of type this serializer handles.
     *
     * @param value Value to serialize
     * @param gen Generator used to output resulting Json content
     * @param provider Provider that can be used to get serializers for
     *   serializing Objects value contains, if any.
     */
    public void serialize(T value, JsonGenerator jgen, JsonSerializerProvider provider)
        throws IOException, JsonGenerationException;
}
