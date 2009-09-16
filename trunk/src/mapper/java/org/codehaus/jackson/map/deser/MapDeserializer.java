package org.codehaus.jackson.map.deser;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.*;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.KeyDeserializer;

/**
 * Basic serializer that can take Json "Object" structure and
 * construct a {@link java.util.Map} instance, with typed contents.
 *<p>
 * Note: for untyped content (one indicated by passing Object.class
 * as the type), {@link UntypedObjectDeserializer} is used instead.
 * It can also construct {@link java.util.Map}s, but not with specific
 * POJO types, only other containers and primitives/wrappers.
 */
public class MapDeserializer
    extends StdDeserializer<Map<Object,Object>>
{
    // // Configuration: typing, deserializers

    final Class<Map<Object,Object>> _mapClass;

    /**
     * Key deserializer used, if not null. If null, String from json
     * content is used as is.
     */
    final KeyDeserializer _keyDeserializer;

    /**
     * Value deserializer.
     */
    final JsonDeserializer<Object> _valueDeserializer;

    // // Instance construction settings:

    final Constructor<Map<Object,Object>> _defaultCtor;

    /**
     * If the Map is to be instantiated using non-default constructor
     * or factory method
     * that takes one or more named properties as argument(s),
     * this creator is used for instantiation.
     */
    protected Creator.PropertyBased _propertyBasedCreator;

    /*
    ////////////////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////////////////
     */

    @SuppressWarnings("unchecked") 
    public MapDeserializer(Class<?> mapClass, Constructor<Map<Object,Object>> defCtor,
                           KeyDeserializer keyDeser, JsonDeserializer<Object> valueDeser)
    {
        super(Map.class);
        _mapClass = (Class<Map<Object,Object>>) mapClass;
        _defaultCtor = defCtor;
        // For now, must have the default constructor...
        if (defCtor == null) {
            throw new IllegalArgumentException("No default constructor for Map class "+mapClass.getName());
        }
        _keyDeserializer = keyDeser;
        _valueDeserializer = valueDeser;
    }

    /**
     * Method called to add constructor and/or factory method based
     * creators to be used with Map, instead of default constructor.
     */
    public void setCreators(CreatorContainer creators)
    {
        _propertyBasedCreator = creators.propertyBasedCreator();
    }

    /*
    ////////////////////////////////////////////////////////////
    // Deserializer API
    ////////////////////////////////////////////////////////////
     */

    @Override
    public Map<Object,Object> deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException
    {
        Map<Object,Object> result;
        try {
            result = _defaultCtor.newInstance();
        } catch (Exception e) {
            throw ctxt.instantiationException(_mapClass, e);
        }
        return deserialize(jp, ctxt, result);
    }

    @Override
    public Map<Object,Object> deserialize(JsonParser jp, DeserializationContext ctxt,
                                          Map<Object,Object> result)
        throws IOException, JsonProcessingException
    {
        // Ok: must point to START_OBJECT
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw ctxt.mappingException(_mapClass);
        }

        KeyDeserializer keyDes = _keyDeserializer;
        JsonDeserializer<Object> valueDes = _valueDeserializer;

        while ((jp.nextToken()) != JsonToken.END_OBJECT) {
            // Must point to field name
            String fieldName = jp.getCurrentName();
            Object key = (keyDes == null) ? fieldName : keyDes.deserializeKey(fieldName, ctxt);
            // And then the value...
            JsonToken t = jp.nextToken();
            // Note: must handle null explicitly here; value deserializers won't
            Object value = (t == JsonToken.VALUE_NULL) ? null : valueDes.deserialize(jp, ctxt);
            /* !!! 23-Dec-2008, tatu: should there be an option to verify
             *   that there are no duplicate field names? (and/or what
             *   to do, keep-first or keep-last)
             */
            result.put(key, value);
        }
        return result;
    }
}

