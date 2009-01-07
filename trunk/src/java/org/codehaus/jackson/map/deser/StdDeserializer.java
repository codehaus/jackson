package org.codehaus.jackson.map.deser;

import java.io.IOException;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.DeserializationContext;

/**
 * Base class for simple standard deserializers
 */
public abstract class StdDeserializer<T>
    extends JsonDeserializer<T>
{
    final static double MIN_FLOAT = (double) Float.MIN_VALUE;
    final static double MAX_FLOAT = (double) Float.MAX_VALUE;

    final Class<?> _valueClass;

    protected StdDeserializer(Class<?> vc) {
        _valueClass = vc;
    }

    public Class<?> getValueClass() { return _valueClass; }

    /*
    /////////////////////////////////////////////////////////////
    // Helper methods for sub-classes
    /////////////////////////////////////////////////////////////
    */

    protected int _parseInt(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        JsonToken t = jp.getCurrentToken();
        int value;
        
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return jp.getIntValue();
        }
        if (t == JsonToken.VALUE_NUMBER_FLOAT) { // coercing should work too
            return jp.getIntValue();
        }
        if (t == JsonToken.VALUE_STRING) { // let's do implicit re-parse
            // !!! 05-Jan-2009, tatu: Should we try to limit value space, JDK is too lenient?
            String text = jp.getText().trim();
            try {
                return Integer.parseInt(text);
            } catch (IllegalArgumentException iae) {
                throw ctxt.weirdStringException(_valueClass, "not a valid representation of integral number value");
            }
        }
        // Otherwise, no can do:
        throw ctxt.mappingException(_valueClass);
    }

    protected double _parseDouble(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
            // We accept couple of different types; obvious ones first:
            JsonToken t = jp.getCurrentToken();

            if (t == JsonToken.VALUE_NUMBER_FLOAT) {
                return jp.getDoubleValue();
            }
            if (t == JsonToken.VALUE_NUMBER_INT) {
                return jp.getDoubleValue();
            }
            // And finally, let's allow Strings to be converted too
            if (t == JsonToken.VALUE_STRING) {
                // !!! 05-Jan-2009, tatu: Should we try to limit value space, JDK is too lenient?
                String text = jp.getText().trim();
                try {
                    return Double.parseDouble(text);
                } catch (IllegalArgumentException iae) { }
                throw ctxt.weirdStringException(_valueClass, "not a valid double value");
            }
            // Otherwise, no can do:
            throw ctxt.mappingException(_valueClass);
    }

    /*
    /////////////////////////////////////////////////////////////
    // First, generic (Object, String, String-like) deserializers
    /////////////////////////////////////////////////////////////
    */

    public final static class StringDeserializer
        extends StdDeserializer<String>
    {
        public StringDeserializer() { super(String.class); }

        public String deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            JsonToken curr = jp.getCurrentToken();
            // Usually should just get string value:
            if (curr == JsonToken.VALUE_STRING) {
                return jp.getText();
            }
            // Can deserialize any scaler value, but not markers
            if (curr.isScalarValue()) {
                return jp.getText();
            }
            throw ctxt.mappingException(_valueClass);
        }
    }

    /*
    /////////////////////////////////////////////////////////////
    // Then primitive/wrapper types
    /////////////////////////////////////////////////////////////
    */

    public final static class BooleanDeserializer
        extends StdDeserializer<Boolean>
    {
        public BooleanDeserializer() { super(Boolean.class); }
        
        public Boolean deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            // We accept couple of different types; obvious ones first:
            JsonToken t = jp.getCurrentToken();

            if (t == JsonToken.VALUE_TRUE) {
                return Boolean.TRUE;
            }
            if (t == JsonToken.VALUE_FALSE) {
                return Boolean.FALSE;
            }
            // And finally, let's allow Strings to be converted too
            if (t == JsonToken.VALUE_STRING) {
                String text = jp.getText();
                if ("true".equals(text)) {
                    return Boolean.TRUE;
                }
                if ("false".equals(text)) {
                    return Boolean.FALSE;
                }
                throw ctxt.weirdStringException(_valueClass, "only \"true\" or \"false\" recognized");
            }
            
            // Otherwise, no can do:
            throw ctxt.mappingException(_valueClass);
        }
    }

    public final static class ByteDeserializer
        extends StdDeserializer<Byte>
    {
        public ByteDeserializer() { super(Byte.class); }

        public Byte deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            int value = _parseInt(jp, ctxt);
            // So far so good: but does it fit?
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw ctxt.weirdStringException(_valueClass, "overflow, value can not be represented as 8-bit value");
            }
            return Byte.valueOf((byte) value);
        }
    }

    public final static class ShortDeserializer
        extends StdDeserializer<Short>
    {
        public ShortDeserializer() { super(Short.class); }

        public Short deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            int value = _parseInt(jp, ctxt);
            // So far so good: but does it fit?
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw ctxt.weirdStringException(_valueClass, "overflow, value can not be represented as 16-bit value");
            }
            return Short.valueOf((short) value);
        }
    }

    public final static class CharacterDeserializer
        extends StdDeserializer<Character>
    {
        public CharacterDeserializer() { super(Character.class); }

        public Character deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            JsonToken t = jp.getCurrentToken();
            int value;

            if (t == JsonToken.VALUE_NUMBER_INT) { // ok iff ascii value
                value = jp.getIntValue();
                if (value >= 0 && value <= 0xFFFF) {
                    return Character.valueOf((char) value);
                }
            } else if (t == JsonToken.VALUE_STRING) { // this is the usual type
                // But does it have to be exactly one char?
                String text = jp.getText();
                if (text.length() == 1) {
                    return Character.valueOf(text.charAt(0));
                }
            }
            throw ctxt.mappingException(_valueClass);
        }
    }

    public final static class IntegerDeserializer
        extends StdDeserializer<Integer>
    {
        public IntegerDeserializer() { super(Integer.class); }

        public Integer deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            return _parseInt(jp, ctxt);
        }
    }

    public final static class LongDeserializer
        extends StdDeserializer<Long>
    {
        public LongDeserializer() { super(Long.class); }

        public Long deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            // We accept couple of different types; obvious ones first:
            JsonToken t = jp.getCurrentToken();

            if (t == JsonToken.VALUE_NUMBER_INT) {
                return jp.getLongValue();
            }
            // it should be ok to coerce (although may fail, too)
            if (t == JsonToken.VALUE_NUMBER_FLOAT) {
                return jp.getLongValue();
            }
            // And finally, let's allow Strings to be converted too
            if (t == JsonToken.VALUE_STRING) {
                // !!! 05-Jan-2009, tatu: Should we try to limit value space, JDK is too lenient?
                String text = jp.getText().trim();
                try {
                    return Long.parseLong(text);
                } catch (IllegalArgumentException iae) { }
                throw ctxt.weirdStringException(_valueClass, "not a valid long value");
            }
            // Otherwise, no can do:
            throw ctxt.mappingException(_valueClass);
        }
    }

    public final static class FloatDeserializer
        extends StdDeserializer<Float>
    {
        public FloatDeserializer() { super(Float.class); }

        public Float deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            double d = _parseDouble(jp, ctxt);
            if (d < MIN_FLOAT || d > MAX_FLOAT) {
                throw ctxt.weirdStringException(_valueClass, "overflow/underflow, value can not be represented as a 32-bit float");
            }
            return Float.valueOf((float) d);
        }
    }

    public final static class DoubleDeserializer
        extends StdDeserializer<Double>
    {
        public DoubleDeserializer() { super(Double.class); }

        public Double deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException
        {
            return _parseDouble(jp, ctxt);
        }
    }

}
