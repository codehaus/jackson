package org.codehaus.jackson.map.ser;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.schema.JsonSchema;
import org.codehaus.jackson.schema.SchemaAware;
import org.codehaus.jackson.type.JavaType;

/**
 * Dummy container class to group standard container serializers: serializers
 * that can serialize things like {@link java.util.List}s,
 * {@link java.util.Map}s and such.
 *<p>
 * TODO: as per [JACKSON-55], should try to add path info for all serializers;
 * is still missing those for some container types.
 */
public final class ContainerSerializers
{
    private ContainerSerializers() { }

    /*
     ****************************************************************
     * Factory methods
     ****************************************************************
     */
    
    public static ContainerSerializerBase<?> indexedListSerializer(JavaType elemType, boolean staticTyping)
    {
        return new IndexedListSerializer(elemType, staticTyping);
    }

    public static ContainerSerializerBase<?> collectionSerializer(JavaType elemType, boolean staticTyping)
    {
        return new CollectionSerializer(elemType, staticTyping);
    }

    public static ContainerSerializerBase<?> iteratorSerializer(JavaType elemType, boolean staticTyping)
    {
        return new IteratorSerializer(elemType, staticTyping);
    }

    public static ContainerSerializerBase<?> iterableSerializer(JavaType elemType, boolean staticTyping)
    {
        return new IterableSerializer(elemType, staticTyping);
    }

    public static JsonSerializer<?> enumSetSerializer(JavaType enumType)
    {
        return new EnumSetSerializer(enumType);
    }
    
    /*
     ****************************************************************
     * Base classes
     ****************************************************************
     */

    /**
     * Base class for serializers that will output contents as JSON
     * arrays.
     */
     private abstract static class AsArraySerializer<T>
        extends ContainerSerializerBase<T>
        implements ResolvableSerializer
    {
        protected final boolean _staticTyping;

        protected final JavaType _elementType;

        /**
         * Value serializer to use, if it can be statically determined
         * 
         * @since 1.5
         */
        protected JsonSerializer<Object> _elementSerializer;

        protected AsArraySerializer(Class<?> cls, JavaType et, boolean staticTyping)
        {
            // typing with generics is messy... have to resort to this:
            super(cls, false);
            _elementType = et;
            // static if explicitly requested, or we got final type
            _staticTyping = staticTyping || (et != null && et.isFinal());
        }

        @Override
        public final void serialize(T value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException
        {
            jgen.writeStartArray();
            serializeContents(value, jgen, provider);
            jgen.writeEndArray();
        }
        
        @Override
        public final void serializeWithType(T value, JsonGenerator jgen, SerializerProvider provider,
                TypeSerializer typeSer)
            throws IOException, JsonGenerationException
        {
            typeSer.writeTypePrefixForArray(value, jgen);
            serializeContents(value, jgen, provider);
            typeSer.writeTypeSuffixForArray(value, jgen);
        }

        protected abstract void serializeContents(T value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException;

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint)
            throws JsonMappingException
        {
            /* 15-Jan-2010, tatu: This should probably be rewritten, given that
             *    more information about content type is actually being explicitly
             *    passed. So there should be less need to try to re-process that
             *    information.
             */
            ObjectNode o = createSchemaNode("array", true);
            JavaType contentType = null;
            if (typeHint != null) {
                JavaType javaType = TypeFactory.type(typeHint);
                contentType = javaType.getContentType();
                if (contentType == null) { // could still be parametrized (Iterators)
                    if (typeHint instanceof ParameterizedType) {
                        Type[] typeArgs = ((ParameterizedType) typeHint).getActualTypeArguments();
                        if (typeArgs.length == 1) {
                            contentType = TypeFactory.type(typeArgs[0]);
                        }
                    }
                }
            }
            if (contentType == null && _elementType != null) {
                contentType = _elementType;
            }
            if (contentType != null) {
                JsonSerializer<Object> ser = provider.findValueSerializer(contentType.getRawClass());
                JsonNode schemaNode = (ser instanceof SchemaAware) ?
                        ((SchemaAware) ser).getSchema(provider, null) :
                        JsonSchema.getDefaultSchemaNode();
                o.put("items", schemaNode);
            }
            return o;
        }

        /**
         * Need to get callback to resolve value serializer, if static typing
         * is used (either being forced, or because value type is final)
         */
        //@Override
        public void resolve(SerializerProvider provider)
            throws JsonMappingException
        {
            if (_staticTyping && _elementType != null) {
                _elementSerializer = provider.findValueSerializer(_elementType.getRawClass());
            }
        }
    }
    
    /*
    ************************************************************
    * Concrete serializers, Lists/collections
    ************************************************************
     */

    /**
     * This is an optimizied serializer for Lists that can be efficiently
     * traversed by index (as opposed to others, such as {@link LinkedList}
     * that can not}.
     */
    public final static class IndexedListSerializer
        extends AsArraySerializer<List<?>>
    {
        public final static IndexedListSerializer instance = new IndexedListSerializer(null, false);

        public IndexedListSerializer(JavaType elemType, boolean staticTyping)
        {
            super(List.class, elemType, staticTyping);
        }
        
        @Override
        public void serializeContents(List<?> value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException
        {
            if (_elementSerializer != null) {
                serializeContentsUsing(value, jgen, provider, _elementSerializer);
                return;
            }
            if (_valueTypeSerializer != null) {
                serializeTypedContents(value, jgen, provider);
                return;
            }
            final int len = value.size();
            if (len > 0) {
                JsonSerializer<Object> prevSerializer = null;
                Class<?> prevClass = null;
                for (int i = 0; i < len; ++i) {
                    Object elem = value.get(i);
                    try {
                        if (elem == null) {
                            provider.getNullValueSerializer().serialize(null, jgen, provider);
                        } else {
                            // Minor optimization to avoid most lookups:
                            Class<?> cc = elem.getClass();
                            JsonSerializer<Object> currSerializer;
                            if (cc == prevClass) {
                                currSerializer = prevSerializer;
                            } else {
                                currSerializer = provider.findValueSerializer(cc);
                                prevSerializer = currSerializer;
                                prevClass = cc;
                            }
                            currSerializer.serialize(elem, jgen, provider);
                        }
                    } catch (Exception e) {
                        // [JACKSON-55] Need to add reference information
                        wrapAndThrow(e, value, i);
                    }
                }
             }
        }

        public void serializeContentsUsing(List<?> value, JsonGenerator jgen, SerializerProvider provider,
                JsonSerializer<Object> ser)
            throws IOException, JsonGenerationException
        {
            final int len = value.size();
            if (len > 0) {
                final TypeSerializer typeSer = _valueTypeSerializer;
                for (int i = 0; i < len; ++i) {
                    Object elem = value.get(i);
                    try {
                        if (elem == null) {
                            provider.getNullValueSerializer().serialize(null, jgen, provider);
                        } else if (typeSer == null) {
                            ser.serialize(elem, jgen, provider);
                        } else {
                            ser.serializeWithType(elem, jgen, provider, typeSer);
                        }
                    } catch (Exception e) {
                        // [JACKSON-55] Need to add reference information
                        wrapAndThrow(e, value, i);
                    }
                }
             }
        }

        public void serializeTypedContents(List<?> value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException
        {
            final int len = value.size();
            if (len > 0) {
                JsonSerializer<Object> prevSerializer = null;
                Class<?> prevClass = null;
                final TypeSerializer typeSer = _valueTypeSerializer;
                for (int i = 0; i < len; ++i) {
                    Object elem = value.get(i);
                    try {
                        if (elem == null) {
                            provider.getNullValueSerializer().serialize(null, jgen, provider);
                        } else {
                            Class<?> cc = elem.getClass();
                            JsonSerializer<Object> currSerializer;
                            if (cc == prevClass) {
                                currSerializer = prevSerializer;
                            } else {
                                currSerializer = provider.findValueSerializer(cc);
                                prevSerializer = currSerializer;
                                prevClass = cc;
                            }
                            currSerializer.serializeWithType(elem, jgen, provider, typeSer);
                        }
                    } catch (Exception e) {
                        // [JACKSON-55] Need to add reference information
                        wrapAndThrow(e, value, i);
                    }
                }
             }
        }
    }

    /**
     * Fallback serializer for cases where Collection is not known to be
     * of type for which more specializer serializer exists (such as
     * index-accessible List).
     * If so, we will just construct an {@link java.util.Iterator}
     * to iterate over elements.
     */
    public final static class CollectionSerializer
        extends AsArraySerializer<Collection<?>>
    {
        public final static CollectionSerializer instance = new CollectionSerializer(null, false);

        public CollectionSerializer(JavaType elemType, boolean staticTyping)
        {
            super(Collection.class, elemType, staticTyping);
        }
        
        @Override
        public void serializeContents(Collection<?> value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException
        {
            if (_elementSerializer != null) {
                serializeContentsUsing(value, jgen, provider, _elementSerializer);
                return;
            }
            Iterator<?> it = value.iterator();
            if (it.hasNext()) {
                JsonSerializer<Object> prevSerializer = null;
                Class<?> prevClass = null;
                TypeSerializer typeSer = _valueTypeSerializer;
    
                int i = 0;
                do {
                    Object elem = it.next();
                    try {
                        if (elem == null) {
                            provider.getNullValueSerializer().serialize(null, jgen, provider);
                        } else {
                            // Minor optimization to avoid most lookups:
                            Class<?> cc = elem.getClass();
                            JsonSerializer<Object> currSerializer;
                            if (cc == prevClass) {
                                currSerializer = prevSerializer;
                            } else {
                                currSerializer = provider.findValueSerializer(cc);
                                prevSerializer = currSerializer;
                                prevClass = cc;
                            }
                            if (typeSer == null) {
                                currSerializer.serialize(elem, jgen, provider);
                            } else {
                                currSerializer.serializeWithType(elem, jgen, provider, typeSer);
                            }
                        }
                    } catch (Exception e) {
                        // [JACKSON-55] Need to add reference information
                        wrapAndThrow(e, value, i);
                    }
                    ++i;
                } while (it.hasNext());
            }
        }

        public void serializeContentsUsing(Collection<?> value, JsonGenerator jgen, SerializerProvider provider,
                JsonSerializer<Object> ser)
            throws IOException, JsonGenerationException
        {
            Iterator<?> it = value.iterator();
            if (it.hasNext()) {
                TypeSerializer typeSer = _valueTypeSerializer;
                int i = 0;
                do {
                    Object elem = it.next();
                    try {
                        if (elem == null) {
                            provider.getNullValueSerializer().serialize(null, jgen, provider);
                        } else {
                            if (typeSer == null) {
                                ser.serialize(elem, jgen, provider);
                            } else {
                                ser.serializeWithType(elem, jgen, provider, typeSer);
                            }
                        }
                        ++i;
                    } catch (Exception e) {
                        // [JACKSON-55] Need to add reference information
                        wrapAndThrow(e, value, i);
                    }
                } while (it.hasNext());
            }
        }
    }

    public final static class IteratorSerializer
        extends AsArraySerializer<Iterator<?>>
    {
        public final static IteratorSerializer instance = new IteratorSerializer(null, false);

        public IteratorSerializer(JavaType elemType, boolean staticTyping)
        {
            super(Iterator.class, elemType, staticTyping);
        }
        
        @Override
        public void serializeContents(Iterator<?> value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException
        {
            if (value.hasNext()) {
                JsonSerializer<Object> prevSerializer = null;
                Class<?> prevClass = null;
                do {
                    Object elem = value.next();
                    if (elem == null) {
                        provider.getNullValueSerializer().serialize(null, jgen, provider);
                    } else {
                        // Minor optimization to avoid most lookups:
                        Class<?> cc = elem.getClass();
                        JsonSerializer<Object> currSerializer;
                        if (cc == prevClass) {
                            currSerializer = prevSerializer;
                        } else {
                            currSerializer = provider.findValueSerializer(cc);
                            prevSerializer = currSerializer;
                            prevClass = cc;
                        }
                        currSerializer.serialize(elem, jgen, provider);
                    }
                } while (value.hasNext());
            }
        }
    }

    public final static class IterableSerializer
        extends AsArraySerializer<Iterable<?>>
    {
        public final static IterableSerializer instance = new IterableSerializer(null, false);

        public IterableSerializer(JavaType elemType, boolean staticTyping)
        {
            super(Iterable.class, elemType, staticTyping);
        }
        
        @Override
        public void serializeContents(Iterable<?> value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException
        {
            Iterator<?> it = value.iterator();
            if (it.hasNext()) {
                JsonSerializer<Object> prevSerializer = null;
                Class<?> prevClass = null;
                
                do {
                    Object elem = it.next();
                    if (elem == null) {
                        provider.getNullValueSerializer().serialize(null, jgen, provider);
                    } else {
                        // Minor optimization to avoid most lookups:
                        Class<?> cc = elem.getClass();
                        JsonSerializer<Object> currSerializer;
                        if (cc == prevClass) {
                            currSerializer = prevSerializer;
                        } else {
                            currSerializer = provider.findValueSerializer(cc);
                            prevSerializer = currSerializer;
                            prevClass = cc;
                        }
                        currSerializer.serialize(elem, jgen, provider);
                    }
                } while (it.hasNext());
            }
        }
    }

    public final static class EnumSetSerializer
        extends AsArraySerializer<EnumSet<? extends Enum<?>>>
    {
        public EnumSetSerializer(JavaType elemType)
        {
            super(EnumSet.class, elemType, true);
        }

        @Override
        public void serializeContents(EnumSet<? extends Enum<?>> value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException
        {
            JsonSerializer<Object> enumSer = _elementSerializer;
            /* Need to dynamically find instance serializer; unfortunately
             * that seems to be the only way to figure out type (no accessors
             * to the enum class that set knows)
             */
            for (Enum<?> en : value) {
                if (enumSer == null) {
                    /* 12-Jan-2010, tatu: Since enums can not be polymorphic, let's
                     *   not bother with typed serializer variant here
                     */
                    enumSer = provider.findValueSerializer(en.getDeclaringClass());
                }
                enumSer.serialize(en, jgen, provider);
            }
        }
    }
}
