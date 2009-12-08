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
     * Class name of the value type (that this serializer is used for);
     * used for error reporting and debugging.
     */
    final protected String _className;

    /**
     * Writers used for outputting actual property values
     */
    final protected BeanPropertyWriter[] _props;

    /**
     * Optional filters used to suppress output of properties that
     * are only to be included in certain views
     */
    protected SerializationViewFilter[] _viewFilters;
    
    /**
     * 
     * @param type Nominal type of values handled by this serializer
     * @param props Property writers used for actual serialization
     */
    public BeanSerializer(Class<?> type, BeanPropertyWriter[] props)
    {
        _props = props;
        // let's store this for debugging
        _className = type.getName();
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
     * Method used for assigning definitions for view-based filtering.
     * 
     * @since 1.4
     */
    public void setViewFilters(SerializationViewFilter[] filters) {
        _viewFilters = filters;
    }

    /*
    ******************************************************************
    * JsonSerializer implementation
    ******************************************************************
     */
    
    public void serialize(Object bean, JsonGenerator jgen, SerializerProvider provider)
        throws IOException, JsonGenerationException
    {
        if (_viewFilters != null) {
            Class<?> view = provider.getSerializationView();
            if (view != null) {
                serializeWithView(bean, jgen, provider, view);
                return;
            }
        }
        
        jgen.writeStartObject();

        int i = 0;
        try {
            for (final int len = _props.length; i < len; ++i) {
                _props[i].serializeAsField(bean, jgen, provider);
            }
            jgen.writeEndObject();
        } catch (Exception e) {
            wrapAndThrow(e, bean, _props[i].getName());
        } catch (StackOverflowError e) {
            /* 04-Sep-2009, tatu: Dealing with this is tricky, since we do not
             *   have many stack frames to spare... just one or two; can't
             *   make many calls.
             */
            JsonMappingException mapE = new JsonMappingException("Infinite recursion (StackOverflowError)");
            mapE.prependPath(new JsonMappingException.Reference(bean, _props[i].getName()));
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
        return "BeanSerializer for "+_className;
    }

    /*
    ***************************************************************8
    * Internal methods
    ***************************************************************8
     */
    
    /**
     * @param view View to use for filtering out properties
     */
    protected void serializeWithView(Object bean, JsonGenerator jgen, SerializerProvider provider,
            Class<?> view)
        throws IOException, JsonGenerationException
    {
        jgen.writeStartObject();
        int i = 0;
        final SerializationViewFilter[] filters = _viewFilters;
        try {
            for (final int len = _props.length; i < len; ++i) {
                // Included in this view?
                SerializationViewFilter filter = filters[i];
                if (filter == null || filter.includeInView(view)) {
                    _props[i].serializeAsField(bean, jgen, provider);
                }
            }
            jgen.writeEndObject();
        } catch (Exception e) {
            wrapAndThrow(e, bean, _props[i].getName());
        } catch (StackOverflowError e) {
            /* 04-Sep-2009, tatu: Dealing with this is tricky, since we do not
             *   have many stack frames to spare... just one or two; can't
             *   make many calls.
             */
            JsonMappingException mapE = new JsonMappingException("Infinite recursion (StackOverflowError)");
            mapE.prependPath(new JsonMappingException.Reference(bean, _props[i].getName()));
            throw mapE;
        }
    }
}
