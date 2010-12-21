package org.codehaus.jackson.map.ser;

import java.util.*;

import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.introspect.AnnotatedClass;
import org.codehaus.jackson.map.introspect.AnnotatedField;
import org.codehaus.jackson.map.introspect.AnnotatedMember;
import org.codehaus.jackson.map.introspect.AnnotatedMethod;
import org.codehaus.jackson.map.introspect.BasicBeanDescription;
import org.codehaus.jackson.map.introspect.VisibilityChecker;
import org.codehaus.jackson.map.jsontype.NamedType;
import org.codehaus.jackson.map.jsontype.TypeResolverBuilder;
import org.codehaus.jackson.map.type.TypeBindings;
import org.codehaus.jackson.map.util.ArrayBuilders;
import org.codehaus.jackson.map.util.ClassUtil;
import org.codehaus.jackson.type.JavaType;
 
/**
 * Factory class that can provide serializers for any regular Java beans
 * (as defined by "having at least one get method recognizable as bean
 * accessor" -- where {@link Object#getClass} does not count);
 * as well as for "standard" JDK types. Latter is achieved
 * by delegating calls to {@link BasicSerializerFactory} 
 * to find serializers both for "standard" JDK types (and in some cases,
 * sub-classes as is the case for collection classes like
 * {@link java.util.List}s and {@link java.util.Map}s) and bean (value)
 * classes.
 *<p>
 * Note about delegating calls to {@link BasicSerializerFactory}:
 * although it would be nicer to use linear delegation
 * for construction (to essentially dispatch all calls first to the
 * underlying {@link BasicSerializerFactory}; or alternatively after
 * failing to provide bean-based serializer}, there is a problem:
 * priority levels for detecting standard types are mixed. That is,
 * we want to check if a type is a bean after some of "standard" JDK
 * types, but before the rest.
 * As a result, "mixed" delegation used, and calls are NOT done using
 * regular {@link SerializerFactory} interface but rather via
 * direct calls to {@link BasicSerializerFactory}.
 *<p>
 * Finally, since all caching is handled by the serializer provider
 * (not factory) and there is no configurability, this
 * factory is stateless.
 * This means that a global singleton instance can be used.
 *<p>
 * Notes for version 1.7 (and above): the new module registration system
 * required addition of {@link #withAdditionalSerializers}, which has to
 * be redefined by sub-classes so that they can work properly with
 * pluggable additional serializer providing components.
 */
public class BeanSerializerFactory
    extends BasicSerializerFactory
{
    /**
     * Constant for empty <code>Serializers</code> array (which by definition
     * is stateless and reusable)
     */
    protected final static Serializers[] NO_SERIALIZERS = new Serializers[0];
    
    /**
     * Like {@link BasicSerializerFactory}, this factory is stateless, and
     * thus a single shared global (== singleton) instance can be used
     * without thread-safety issues.
     */
    public final static BeanSerializerFactory instance = new BeanSerializerFactory(null);

    /**
     * Provider for additional serializers, checked before considering default
     * basic or bean serialializers.
     * 
     * @since 1.7
     */
    protected final Serializers[] _additionalSerializers;
    
    /*
    /**********************************************************
    /* Life-cycle: creation, configuration
    /**********************************************************
     */

    @Deprecated
    protected BeanSerializerFactory() { this(null); }

    /**
     * 
     * @param allAdditionalSerializers Additional serializer providers used for locating
     *   serializer implementations; starting with the highest-priority one
     */
    protected BeanSerializerFactory(Serializers[] allAdditionalSerializers)
    {
        if (allAdditionalSerializers == null) {
            allAdditionalSerializers = NO_SERIALIZERS;
        }
        _additionalSerializers = allAdditionalSerializers;
    }
    
    /**
     * Method used by module registration functionality, to attach additional
     * serializer providers into this serializer factory. This is typically
     * handled by constructing a new instance with additional serializers,
     * to ensure thread-safe access.
     * 
     * @since 1.7
     */
    @Override
    public SerializerFactory withAdditionalSerializers(Serializers additional)
    {
        if (additional == null) {
            throw new IllegalArgumentException("Can not pass null Serializers");
        }
        
        /* 22-Nov-2010, tatu: Handling of subtypes is tricky if we do immutable-with-copy-ctor;
         *    and we pretty much have to here either choose between losing subtype instance
         *    when registering additional serializers, or losing serializers.
         *    
         *    Instead, let's actually just throw an error if this method is called when subtype
         *    has not properly overridden this method, as that is better alternative than
         *    continue with what is almost certainly broken or invalid configuration.
         */
        if (getClass() != BeanSerializerFactory.class) {
            throw new IllegalStateException("Subtype of BeanSerializerFactory ("+getClass().getName()
                    +") has not properly overridden method 'withAdditionalSerializers': can not instantiate subtype with "
                    +"additional serializer definitions");
        }
        
        Serializers[] s = ArrayBuilders.insertInList(_additionalSerializers, additional);
        return new BeanSerializerFactory(s);
    }
    
    /*
    /**********************************************************
    /* JsonSerializerFactory impl
    /**********************************************************
     */

    /**
     * Main serializer constructor method. We will have to be careful
     * with respect to ordering of various method calls: essentially
     * we want to reliably figure out which classes are standard types,
     * and which are beans. The problem is that some bean Classes may
     * implement standard interfaces (say, {@link java.lang.Iterable}.
     *<p>
     * Note: sub-classes may choose to complete replace implementation,
     * if they want to alter priority of serializer lookups.
     */
    @Override
    @SuppressWarnings("unchecked")
    public JsonSerializer<Object> createSerializer(SerializationConfig config, JavaType type,
            BeanProperty property)
    {
        /* [JACKSON-220]: Very first thing, let's check annotations to
         * see if we have explicit definition
         */
        BasicBeanDescription beanDesc = config.introspect(type);
        JsonSerializer<?> ser = findSerializerFromAnnotation(config, beanDesc.getClassInfo(), property);
        if (ser == null) {
            // 22-Nov-2010, tatu: Ok: additional module-provided serializers to consider?
            ser = _findFirstSerializer(_additionalSerializers, config, type, beanDesc, property);
            if (ser == null) {
                // First, fast lookup for exact type:
                ser = super.findSerializerByLookup(type, config, beanDesc, property);
                if (ser == null) {
                    // and then introspect for some safe (?) JDK types
                    ser = super.findSerializerByPrimaryType(type, config, beanDesc, property);
                    if (ser == null) {
                        /* And this is where this class comes in: if type is
                         * not a known "primary JDK type", perhaps it's a bean?
                         * We can still get a null, if we can't find a single
                         * suitable bean property.
                         */
                        ser = this.findBeanSerializer(config, type, beanDesc, property);
                        /* Finally: maybe we can still deal with it as an
                         * implementation of some basic JDK interface?
                         */
                        if (ser == null) {
                            ser = super.findSerializerByAddonType(config, type, beanDesc, property);
                        }
                    }
                }
            }
        }
        return (JsonSerializer<Object>) ser;
    }

    /**
     * Helper method used to try to find serializer from set of registered
     * {@link Serializers} instances (provided by registered Modules),
     * and return first one found, if any.
     */
    private static JsonSerializer<?> _findFirstSerializer(Serializers[] sers,
            SerializationConfig config,
            JavaType type, BeanDescription beanDesc,
            BeanProperty property)
    {
        for (Serializers ser : sers) {
            JsonSerializer<?> js = ser.findSerializer(config, type, beanDesc, property);
            if (js != null) {
                return js;
            }
        }
        return null;
    }
    
    /*
    /**********************************************************
    /* Other public methods that are not part of
    /* JsonSerializerFactory API
    /**********************************************************
     */

    /**
     * Method that will try to construct a {@link BeanSerializer} for
     * given class. Returns null if no properties are found.
     */
    public JsonSerializer<Object> findBeanSerializer(SerializationConfig config, JavaType type,
            BasicBeanDescription beanDesc, BeanProperty property)
    {
        // First things first: we know some types are not beans...
        if (!isPotentialBeanType(type.getRawClass())) {
            return null;
        }
        return constructBeanSerializer(config, beanDesc, property);
    }

    /**
     * Method called to create a type information serializer for values of given
     * non-container property
     * if one is needed. If not needed (no polymorphic handling configured), should
     * return null.
     *
     * @param baseType Declared type to use as the base type for type information serializer
     * 
     * @return Type serializer to use for property values, if one is needed; null if not.
     * 
     * @since 1.5
     */
    public TypeSerializer findPropertyTypeSerializer(JavaType baseType, SerializationConfig config,
            AnnotatedMember accessor, BeanProperty property)
    {
        AnnotationIntrospector ai = config.getAnnotationIntrospector();
        TypeResolverBuilder<?> b = ai.findPropertyTypeResolver(accessor, baseType);        
        // Defaulting: if no annotations on member, check value class
        if (b == null) {
            return createTypeSerializer(config, baseType, property);
        }
        Collection<NamedType> subtypes = config.getSubtypeResolver().collectAndResolveSubtypes(accessor, config, ai);
        return b.buildTypeSerializer(baseType, subtypes, property);
    }

    /**
     * Method called to create a type information serializer for values of given
     * container property
     * if one is needed. If not needed (no polymorphic handling configured), should
     * return null.
     *
     * @param containerType Declared type of the container to use as the base type for type information serializer
     * 
     * @return Type serializer to use for property value contents, if one is needed; null if not.
     * 
     * @since 1.5
     */    
    public TypeSerializer findPropertyContentTypeSerializer(JavaType containerType, SerializationConfig config,
            AnnotatedMember accessor, BeanProperty property)
    {
        JavaType contentType = containerType.getContentType();
        AnnotationIntrospector ai = config.getAnnotationIntrospector();
        TypeResolverBuilder<?> b = ai.findPropertyContentTypeResolver(accessor, containerType);        
        // Defaulting: if no annotations on member, check value class
        if (b == null) {
            return createTypeSerializer(config, contentType, property);
        }
        Collection<NamedType> subtypes = config.getSubtypeResolver().collectAndResolveSubtypes(accessor, config, ai);
        return b.buildTypeSerializer(contentType, subtypes, property);
    }
    
    /*
    /**********************************************************
    /* Overridable non-public factory methods
    /**********************************************************
     */

    /**
     * Method called to construct serializer for serializing specified bean type.
     * 
     * @since 1.6
     */
    protected JsonSerializer<Object> constructBeanSerializer(SerializationConfig config,
            BasicBeanDescription beanDesc, BeanProperty property)
    {
        // 13-Oct-2010, tatu: quick sanity check: never try to create bean serializer for plain Object
        if (beanDesc.getBeanClass() == Object.class) {
            throw new IllegalArgumentException("Can not create bean serializer for Object.class");
        }
        
        // First: any detectable (auto-detect, annotations) properties to serialize?
        List<BeanPropertyWriter> props = findBeanProperties(config, beanDesc);
        AnnotatedMethod anyGetter = beanDesc.findAnyGetter();
        // No properties, no serializer
        // 16-Oct-2010, tatu: Except that @JsonAnyGetter needs to count as getter
        if (props == null || props.size() == 0) {
            if (anyGetter == null) {
                /* 27-Nov-2009, tatu: Except that as per [JACKSON-201], we are
                 *   ok with that as long as it has a recognized class annotation
                 *  (which may come from a mix-in too)
                 */
                if (beanDesc.hasKnownClassAnnotations()) {
                    return BeanSerializer.createDummy(beanDesc.getBeanClass());
                }
                return null;
            }
            props = Collections.emptyList();
        } else {
            // Any properties to suppress?
            props = filterBeanProperties(config, beanDesc, props);
            // Do they need to be sorted in some special way?
            props = sortBeanProperties(config, beanDesc, props);
        }
        BeanSerializer ser = instantiateBeanSerializer(config, beanDesc, props);
        if (anyGetter != null) { // since 1.6
            JavaType type = anyGetter.getType(beanDesc.bindingsForBeanType());
            // copied from BasicSerializerFactory.buildMapSerializer():
            boolean staticTyping = config.isEnabled(SerializationConfig.Feature.USE_STATIC_TYPING);
            JavaType valueType = type.getContentType();
            TypeSerializer typeSer = createTypeSerializer(config, valueType, property);
            // should we pass name for "any" property? For now, just pass null
            MapSerializer mapSer = MapSerializer.construct(/* ignored props*/ null, type, staticTyping,
                    typeSer, property);
            ser.setAnyGetter(new AnyGetterWriter(anyGetter, mapSer));
        }
        
        // One more thing: need to gather view information, if any:
        ser = processViews(config, beanDesc, ser, props);
        return ser;
    }

    /**
     * Method called to construct a filtered writer, for given view
     * definitions. Default implementation constructs filter that checks
     * active view type to views property is to be included in.
     */
    protected BeanPropertyWriter constructFilteredBeanWriter(BeanPropertyWriter writer, Class<?>[] inViews)
    {
        return FilteredBeanPropertyWriter.constructViewBased(writer, inViews);
    }
    
    protected PropertyBuilder constructPropertyBuilder(SerializationConfig config,
                                                       BasicBeanDescription beanDesc)
    {
        return new PropertyBuilder(config, beanDesc);
    }

    /**
     * Method called to construct specific subtype of {@link BeanSerializer} with
     * all information gathered so far; main reason to expose this is to allow
     * constructing an alternate sub-class.
     * 
     * @since 1.7
     */
    protected BeanSerializer instantiateBeanSerializer(SerializationConfig config,
            BasicBeanDescription beanDesc,
            List<BeanPropertyWriter> properties)
    {
        // [JACKSON-312] Support per-serialization dynamic filtering:
        return new BeanSerializer(beanDesc.getBeanClass(), properties,
                findFilterId(config, beanDesc));
    }

    /**
     * Method called to find filter that is configured to be used with bean
     * serializer being built, if any.
     * 
     * @since 1.7
     */
    protected Object findFilterId(SerializationConfig config,
            BasicBeanDescription beanDesc)
    {
        return config.getAnnotationIntrospector().findFilterId(beanDesc.getClassInfo());
    }
    
    /*
    /**********************************************************
    /* Overridable non-public introspection methods
    /**********************************************************
     */
    
    /**
     * Helper method used to skip processing for types that we know
     * can not be (i.e. are never consider to be) beans: 
     * things like primitives, Arrays, Enums, and proxy types.
     *<p>
     * Note that usually we shouldn't really be getting these sort of
     * types anyway; but better safe than sorry.
     */
    protected boolean isPotentialBeanType(Class<?> type)
    {
        return (ClassUtil.canBeABeanType(type) == null) && !ClassUtil.isProxyType(type);
    }

    /**
     * Method used to collect all actual serializable properties.
     * Can be overridden to implement custom detection schemes.
     */
    protected List<BeanPropertyWriter> findBeanProperties(SerializationConfig config, BasicBeanDescription beanDesc)
    {
        // Ok: let's aggregate visibility settings: first, baseline:
        VisibilityChecker<?> vchecker = config.getDefaultVisibilityChecker();
        if (!config.isEnabled(SerializationConfig.Feature.AUTO_DETECT_GETTERS)) {
            vchecker = vchecker.withGetterVisibility(Visibility.NONE);
        }
        // then global overrides (disabling)
        if (!config.isEnabled(SerializationConfig.Feature.AUTO_DETECT_IS_GETTERS)) {
            vchecker = vchecker.withIsGetterVisibility(Visibility.NONE);
        }
        if (!config.isEnabled(SerializationConfig.Feature.AUTO_DETECT_FIELDS)) {
            vchecker = vchecker.withFieldVisibility(Visibility.NONE);
        }
        // and finally per-class overrides:
        AnnotationIntrospector intr = config.getAnnotationIntrospector();
        vchecker = intr.findAutoDetectVisibility(beanDesc.getClassInfo(), vchecker);

        LinkedHashMap<String,AnnotatedMethod> methodsByProp = beanDesc.findGetters(vchecker, null);
        LinkedHashMap<String,AnnotatedField> fieldsByProp = beanDesc.findSerializableFields(vchecker, methodsByProp.keySet());

        // [JACKSON-429]: ignore specified types
        removeIgnorableTypes(config, beanDesc, methodsByProp);
        removeIgnorableTypes(config, beanDesc, fieldsByProp);
        
        // nothing? can't proceed (caller may or may not throw an exception)
        if (methodsByProp.isEmpty() && fieldsByProp.isEmpty()) {
            return null;
        }
        
        // null is for value type serializer, which we don't have access to from here
        boolean staticTyping = usesStaticTyping(config, beanDesc, null);
        PropertyBuilder pb = constructPropertyBuilder(config, beanDesc);

        ArrayList<BeanPropertyWriter> props = new ArrayList<BeanPropertyWriter>(methodsByProp.size());
        TypeBindings typeBind = beanDesc.bindingsForBeanType();
        // [JACKSON-98]: start with field properties, if any
        for (Map.Entry<String,AnnotatedField> en : fieldsByProp.entrySet()) {      
            // [JACKSON-235]: suppress writing of back references
            AnnotationIntrospector.ReferenceProperty prop = intr.findReferenceType(en.getValue());
            if (prop != null && prop.isBackReference()) {
                continue;
            }
            props.add(_constructWriter(config, typeBind, pb, staticTyping, en.getKey(), en.getValue()));
        }
        // and then add member properties
        for (Map.Entry<String,AnnotatedMethod> en : methodsByProp.entrySet()) {
            // [JACKSON-235]: suppress writing of back references
            AnnotationIntrospector.ReferenceProperty prop = intr.findReferenceType(en.getValue());
            if (prop != null && prop.isBackReference()) {
                continue;
            }
            props.add(_constructWriter(config, typeBind, pb, staticTyping, en.getKey(), en.getValue()));
        }
        return props;
    }

    /*
    /**********************************************************
    /* Overridable non-public methods for manipulating bean properties
    /**********************************************************
     */
    
    /**
     * Overridable method that can filter out properties. Default implementation
     * checks annotations class may have.
     */
    protected List<BeanPropertyWriter> filterBeanProperties(SerializationConfig config,
            BasicBeanDescription beanDesc, List<BeanPropertyWriter> props)
    {
        AnnotationIntrospector intr = config.getAnnotationIntrospector();
        AnnotatedClass ac = beanDesc.getClassInfo();
        String[] ignored = intr.findPropertiesToIgnore(ac);
        if (ignored != null && ignored.length > 0) {
            HashSet<String> ignoredSet = ArrayBuilders.arrayToSet(ignored);
            Iterator<BeanPropertyWriter> it = props.iterator();
            while (it.hasNext()) {
                if (ignoredSet.contains(it.next().getName())) {
                    it.remove();
                }
            }
        }
        return props;
    }

    /**
     * Overridable method that will impose given partial ordering on
     * list of discovered propertied. Method can be overridden to
     * provide custom ordering of properties, beyond configurability
     * offered by annotations (whic allow alphabetic ordering, as
     * well as explicit ordering by providing array of property names).
     *<p>
     * By default Creator properties will be ordered before other
     * properties. Explicit custom ordering will override this implicit
     * default ordering.
     */
    protected List<BeanPropertyWriter> sortBeanProperties(SerializationConfig config,
            BasicBeanDescription beanDesc, List<BeanPropertyWriter> props)
    {
        // Ok: so far so good. But do we need to (re)order these somehow?
        /* Yes; first, for [JACKSON-90] (explicit ordering and/or alphabetic)
         * and then for [JACKSON-170] (implicitly order creator properties before others)
         */
        List<String> creatorProps = beanDesc.findCreatorPropertyNames();
        // Then how about explicit ordering?
        AnnotationIntrospector intr = config.getAnnotationIntrospector();
        AnnotatedClass ac = beanDesc.getClassInfo();
        String[] propOrder = intr.findSerializationPropertyOrder(ac);
        Boolean alpha = intr.findSerializationSortAlphabetically(ac);
        boolean sort = (alpha != null) && alpha.booleanValue();
        if (sort || !creatorProps.isEmpty() || propOrder != null) {
            props = _sortBeanProperties(props, creatorProps, propOrder, sort);
        }
        return props;
    }

    /**
     * Method called to handle view information for constructed serializer,
     * based on bean property writers.
     *<p>
     * Note that this method is designed to be overridden by sub-classes
     * if they want to provide custom view handling. As such it is not
     * considered an internal implementation detail, and will be supported
     * as part of API going forward.
     * 
     * @return Resulting bean serializer, base implementation returns
     *    serializer passed in
     */
    protected BeanSerializer processViews(SerializationConfig config, BasicBeanDescription beanDesc,
                                          BeanSerializer ser, List<BeanPropertyWriter> props)
    {
        // [JACKSON-232]: whether non-annotated fields are included by default or not is configurable
        boolean includeByDefault = config.isEnabled(SerializationConfig.Feature.DEFAULT_VIEW_INCLUSION);
        if (includeByDefault) { // non-annotated are included
            final int propCount = props.size();
            BeanPropertyWriter[] filtered = null;        
            // Simple: view information is stored within individual writers, need to combine:
            for (int i = 0; i < propCount; ++i) {
                BeanPropertyWriter bpw = props.get(i);
                Class<?>[] views = bpw.getViews();
                if (views != null) {
                    if (filtered == null) {
                        filtered = new BeanPropertyWriter[props.size()];
                    }
                    filtered[i] = constructFilteredBeanWriter(bpw, views);
                }
            }        
            // Anything missing? Need to fill in
            if (filtered != null) {
                for (int i = 0; i < propCount; ++i) {
                    if (filtered[i] == null) {
                        filtered[i] = props.get(i);
                    }
                }
                return ser.withFiltered(filtered);
            }        
            // No views, return as is
            return ser;
        }
        // Otherwise: only include fields with view definitions
        ArrayList<BeanPropertyWriter> explicit = new ArrayList<BeanPropertyWriter>(props.size());
        for (BeanPropertyWriter bpw : props) {
            Class<?>[] views = bpw.getViews();
            if (views != null) {
                explicit.add(constructFilteredBeanWriter(bpw, views));
            }            
        }
        BeanPropertyWriter[] filtered = explicit.toArray(new BeanPropertyWriter[explicit.size()]);
        return ser.withFiltered(filtered);
    }

    /**
     * Method that will apply by-type limitations (as per [JACKSON-429]);
     * by default this is based on {@link org.codehaus.jackson.annotate.JsonIgnoreType} annotation but
     * can be supplied by module-provided introspectors too.
     */
    protected <T extends AnnotatedMember> void removeIgnorableTypes(SerializationConfig config, BasicBeanDescription beanDesc,
            Map<String, T> props)
    {
        if (props.isEmpty()) {
            return;
        }
        AnnotationIntrospector intr = config.getAnnotationIntrospector();
        Iterator<Map.Entry<String,T>> it = props.entrySet().iterator();
        HashMap<Class<?>,Boolean> ignores = new HashMap<Class<?>,Boolean>();
        while (it.hasNext()) {
            Map.Entry<String, T> entry = it.next();
            Class<?> type = entry.getValue().getRawType();
            Boolean result = ignores.get(type);
            if (result == null) {
                BasicBeanDescription desc = config.introspectClassAnnotations(type);
                AnnotatedClass ac = desc.getClassInfo();
                result = intr.isIgnorableType(ac);
                // default to false, non-ignorable
                if (result == null) {
                    result = Boolean.FALSE;
                }
                ignores.put(type, result);
            }
            // lotsa work, and yes, it is ignorable type, so:
            if (result.booleanValue()) {
                it.remove();
            }
        }
    }
    
    /*
    /**********************************************************
    /* Internal helper methods
    /**********************************************************
     */

    /**
     * Secondary helper method for constructing {@link BeanPropertyWriter} for
     * given member (field or method).
     */
    protected BeanPropertyWriter _constructWriter(SerializationConfig config, TypeBindings typeContext,
            PropertyBuilder pb, boolean staticTyping, String name, AnnotatedMember accessor)
    {
        if (config.isEnabled(SerializationConfig.Feature.CAN_OVERRIDE_ACCESS_MODIFIERS)) {
            accessor.fixAccess();
        }
        JavaType type = accessor.getType(typeContext);
        BeanProperty.Std property = new BeanProperty.Std(name, type, pb.getClassAnnotations(), accessor);
        
        // Does member specify a serializer? If so, let's use it.
        JsonSerializer<Object> annotatedSerializer = findSerializerFromAnnotation(config, accessor, property);
        // And how about polymorphic typing? First special to cover JAXB per-field settings:
        TypeSerializer contentTypeSer = null;
        if (ClassUtil.isCollectionMapOrArray(type.getRawClass())) {
            contentTypeSer = findPropertyContentTypeSerializer(type, config, accessor, property);
        }

        // and if not JAXB collection/array with annotations, maybe regular type info?
        TypeSerializer typeSer = findPropertyTypeSerializer(type, config, accessor, property);
        BeanPropertyWriter pbw = pb.buildWriter(name, type, annotatedSerializer,
                        typeSer, contentTypeSer, accessor, staticTyping);
        // how about views? (1.4+)
        AnnotationIntrospector intr = config.getAnnotationIntrospector();
        pbw.setViews(intr.findSerializationViews(accessor));
        return pbw;
    }
    
    /**
     * Helper method that will sort given List of properties according
     * to defined criteria (usually detected by annotations)
     */
    protected List<BeanPropertyWriter> _sortBeanProperties(List<BeanPropertyWriter> props,
            List<String> creatorProps, String[] propertyOrder, boolean sort)
    {
        int size = props.size();
        Map<String,BeanPropertyWriter> all;
        // Need to (re)sort alphabetically?
        if (sort) {
            all = new TreeMap<String,BeanPropertyWriter>();
        } else {
            all = new LinkedHashMap<String,BeanPropertyWriter>(size*2);
        }

        for (BeanPropertyWriter w : props) {
            all.put(w.getName(), w);
        }
        Map<String,BeanPropertyWriter> ordered = new LinkedHashMap<String,BeanPropertyWriter>(size*2);
        // Ok: primarily by explicit order
        if (propertyOrder != null) {
            for (String name : propertyOrder) {
                BeanPropertyWriter w = all.get(name);
                if (w != null) {
                    ordered.put(name, w);
                }
            }
        }
        // And secondly by sorting Creator properties before other unordered properties
        for (String name : creatorProps) {
            BeanPropertyWriter w = all.get(name);
            if (w != null) {
                ordered.put(name, w);
            }
        }
        // And finally whatever is left (trying to put again will not change ordering)
        ordered.putAll(all);
        return new ArrayList<BeanPropertyWriter>(ordered.values());
    }
}
