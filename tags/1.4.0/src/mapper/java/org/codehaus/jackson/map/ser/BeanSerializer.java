package org.codehaus.jackson.map.ser;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collection;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.schema.SchemaAware;
import org.codehaus.jackson.schema.JsonSchema;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.map.*;

/**
 * Serializer class that can serialize arbitrary bean objects.
 *<p>
 * Implementation note: we will post-process resulting serializer,
 * to figure out actual serializers for final types. This must be
 * done from {@link #resolve} method, and NOT from constructor;
 * otherwise we could end up with an infinite loop.
 */
public class BeanSerializer
    extends SerializerBase<Object>
    implements ResolvableSerializer, SchemaAware
{
    final static BeanPropertyWriter[] NO_PROPS = new BeanPropertyWriter[0];

    /**
     * Value type of this serializer,
     * used for error reporting and debugging.
     */
    final protected Class<?> _class;

    /**
     * Writers used for outputting actual property values
     */
    final protected BeanPropertyWriter[] _props;

    /**
     * Optional filters used to suppress output of properties that
     * are only to be included in certain views
     */
    final protected BeanPropertyWriter[] _filteredProps;
    
    /**
     * 
     * @param type Nominal type of values handled by this serializer
     * @param writers Property writers used for actual serialization
     */
    public BeanSerializer(Class<?> type, BeanPropertyWriter[] writers)
    {
        this(type, writers, null);
    }

    /**
     * Alternate constructor used when class being serialized can
     * have dynamically enabled Json Views
     *
     * @param fprops Filtered property writers to use when there is
     *   an active view.
     */
    public BeanSerializer(Class<?> type, BeanPropertyWriter[] props,
                          BeanPropertyWriter[] fprops)
    {
        _props = props;
        // let's store this for debugging
        _class = type;
        _filteredProps = fprops;
    }

    public BeanSerializer(Class<?> type, Collection<BeanPropertyWriter> props)
    {
        this(type, props.toArray(new BeanPropertyWriter[props.size()]));
    }

    /**
     * Method for constructing dummy bean deserializer; one that
     * never outputs any properties
     */
    public static BeanSerializer createDummy(Class<?> forType)
    {
        return new BeanSerializer(forType, NO_PROPS);
    }

    /**
     * Method used for constructing a filtered serializer instance, using this
     * serializer as the base.
     */
    public BeanSerializer withFiltered(BeanPropertyWriter[] filtered) {
        // if no filters, no need to construct new instance...
        if (filtered == null) {
            return this;
        }
        return new BeanSerializer(_class, _props, filtered);
    }

    /*
    ******************************************************************
    * JsonSerializer implementation
    ******************************************************************
     */
    
    public void serialize(Object bean, JsonGenerator jgen, SerializerProvider provider)
        throws IOException, JsonGenerationException
    {
        final BeanPropertyWriter[] props;
        if (_filteredProps != null && provider.getSerializationView() != null) {
            props = _filteredProps;
        } else {
            props = _props;
        }
        
        jgen.writeStartObject();

        int i = 0;
        try {
            for (final int len = props.length; i < len; ++i) {
                BeanPropertyWriter prop = props[i];
                if (prop != null) { // can have nulls in filtered list
                    prop.serializeAsField(bean, jgen, provider);
                }
            }
            jgen.writeEndObject();
        } catch (Exception e) {
            wrapAndThrow(e, bean, props[i].getName());
        } catch (StackOverflowError e) {
            /* 04-Sep-2009, tatu: Dealing with this is tricky, since we do not
             *   have many stack frames to spare... just one or two; can't
             *   make many calls.
             */
            JsonMappingException mapE = new JsonMappingException("Infinite recursion (StackOverflowError)");
            mapE.prependPath(new JsonMappingException.Reference(bean, props[i].getName()));
            throw mapE;
        }
    }

    //@Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
        throws JsonMappingException
    {
        ObjectNode o = createSchemaNode("object", true);
        //todo: should the classname go in the title?
        //o.put("title", _className);
        ObjectNode propertiesNode = o.objectNode();
        for (int i = 0; i < _props.length; i++) {
            BeanPropertyWriter prop = _props[i];
            Type hint = prop.getSerializationType();
            if (hint == null) {
                hint = prop.getGenericPropertyType();
            }
            // Maybe it already has annotated/statically configured serializer?
            JsonSerializer<Object> ser = prop.getSerializer();
            if (ser == null) { // nope
                Class<?> serType = prop.getSerializationType();
                if (serType == null) {
                    serType = prop.getReturnType();
                }
                ser = provider.findValueSerializer(serType);
            }
            JsonNode schemaNode = (ser instanceof SchemaAware) ?
                    ((SchemaAware) ser).getSchema(provider, hint) : 
                    JsonSchema.getDefaultSchemaNode();
            o.put("items", schemaNode);
            propertiesNode.put(prop.getName(), schemaNode);
        }
        o.put("properties", propertiesNode);
        return o;
    }

    /*
   ////////////////////////////////////////////////////////
   // ResolvableSerializer impl
   ////////////////////////////////////////////////////////
    */

    public void resolve(SerializerProvider provider)
        throws JsonMappingException
    {
        //AnnotationIntrospector ai = provider.getConfig().getAnnotationIntrospector();
        for (int i = 0, len = _props.length; i < len; ++i) {
            BeanPropertyWriter prop = _props[i];
            if (prop.hasSerializer()) {
                continue;
            }
            // Was the serialization type hard-coded? If so, use it
            Class<?> type = prop.getSerializationType();
            /* It not, we can use declared return type if and only if
             * declared type is final -- if not, we don't really know
             * the actual type until we get the instance.
             */
            if (type == null) {
                Class<?> rt = prop.getReturnType();
                if (!Modifier.isFinal(rt.getModifiers())) {
                    continue;
                }
                type = rt;
            }
            _props[i] = prop.withSerializer(provider.findValueSerializer(type));
        }
    }

    /*
    ////////////////////////////////////////////////////////
    // Standard methods
    ////////////////////////////////////////////////////////
     */

    @Override public String toString() {
        return "BeanSerializer for "+_class.getName();
    }

    /*
    ***************************************************************8
    * Internal methods
    ***************************************************************8
     */

}
