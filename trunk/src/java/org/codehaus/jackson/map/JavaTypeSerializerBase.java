package org.codehaus.jackson.map;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParseException;

/**
 * An empty base implementation of the {@link JavaTypeSerializer} interface. All methods
 * do nothing and return <code>false</code>. Concrete implementation can choose which
 * methods to override.
 *
 * @author Stanislaw Osinski
 */
public class JavaTypeSerializerBase<T> implements JavaTypeSerializer<T>
{
    public boolean writeAny(JavaTypeSerializer<Object> defaultSerializer, JsonGenerator jgen,
        T value) throws IOException, JsonParseException
    {
        return false;
    }

    public boolean writeValue(JavaTypeSerializer<Object> defaultSerializer,
        JsonGenerator jgen, Map<?, ? extends T> value) throws IOException,
        JsonParseException
    {
        return false;
    }

    public boolean writeValue(JavaTypeSerializer<Object> defaultSerializer,
        JsonGenerator jgen, Collection<? extends T> value) throws IOException,
        JsonParseException
    {
        return false;
    }

    public boolean writeValue(JavaTypeSerializer<Object> defaultSerializer,
        JsonGenerator jgen, T [] value) throws IOException, JsonParseException
    {
        return false;
    }
}
