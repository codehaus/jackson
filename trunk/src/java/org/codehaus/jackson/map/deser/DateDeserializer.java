package org.codehaus.jackson.map.deser;

import java.io.IOException;
import java.util.Date;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.DeserializationContext;

/**
 * Simple deserializer for handling {@link java.util.Date} values.
 *<p>
 * One way to customize Date formats accepted is to override method
 * {@lik DeserializationContext#parseDate} that this basic
 * deserializer calls.
 */
public class DateDeserializer
    extends StdDeserializer<Date>
{
    public DateDeserializer() { super(Date.class); }
    
    @Override
    public java.util.Date deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        return _parseDate(jp, ctxt);
    }
}
