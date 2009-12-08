package org.codehaus.jackson.map.ser;

import java.util.*;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.SerializerFactory;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.introspect.AnnotatedClass;
import org.codehaus.jackson.map.introspect.AnnotatedField;
import org.codehaus.jackson.map.introspect.AnnotatedMethod;
import org.codehaus.jackson.map.introspect.BasicBeanDescription;
import org.codehaus.jackson.map.util.ArrayBuilders;
import org.codehaus.jackson.map.util.ClassUtil;

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
 */
public class BeanSerializerFactory
    extends BasicSerializerFactory
{
    /**
     * Like {@link BasicSerializerFactory}, this factory is stateless, and
     * thus a single shared global (== singleton) instance can be used
     * without thread-safety issues.
     */
    public final static BeanSerializerFactory instance = new BeanSerializerFactory();

    /*
    ////////////////////////////////////////////////////////////
    // Life cycle
    ////////////////////////////////////////////////////////////
     */

    /**
     * We will provide default constructor to allow sub-classing,
     * but make it protected so that no non-singleton instances of
     * the class will be instantiated.
     */
    protected BeanSerializerFactory() { }

    /*
    ////////////////////////////////////////////////////////////
    // JsonSerializerFactory impl
    ////////////////////////////////////////////////////////////
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
    public <T> JsonSerializer<T> createSerializer(Class<T> type, SerializationConfig config)
    {
        // First, fast lookup for exact type:
        JsonSerializer<?> ser = super.findSerializerByLookup(type, config);
        if (ser == null) {
            // and then introspect for some safe (?) JDK types
            ser = super.findSerializerByPrimaryType(type, config);
            if (ser == null) {
                /* And this is where this class comes in: if type is
                 * not a known "primary JDK type", perhaps it's a bean?
                 * We can still get a null, if we can't find a single
                 * suitable bean property.
                 */
                ser = this.findBeanSerializer(type, config);
                /* Finally: maybe we can still deal with it as an
                 * implementation of some basic JDK interface?
                 */
                if (ser == null) {
                    ser = super.findSerializerByAddonType(type, config);
                }
            }
        }
        return (JsonSerializer<T>) ser;
    }

    /*
    ////////////////////////////////////////////////////////////
    // Other public methods that are not part of
    // JsonSerializerFactory API
    ////////////////////////////////////////////////////////////
     */

    /**
     * Method that will try to construct a {@link BeanSerializer} for
     * given class. Returns null if no properties are found.
     */
    public JsonSerializer<Object> findBeanSerializer(Class<?> type, SerializationConfig config)
    {
        // First things first: we know some types are not beans...
        if (!isPotentialBeanType(type)) {
            return null;
        }
        BasicBeanDescription beanDesc = config.introspect(type);
        JsonSerializer<Object> ser = findSerializerFromAnnotation(config, beanDesc.getClassInfo());
        if (ser != null) {
            return ser;
        }

        /* [JACKSON-80]: Should support @JsonValue, which is alternative to
         *   actual bean method introspection.
         */
        AnnotatedMethod valueMethod = beanDesc.findJsonValueMethod();
        if (valueMethod != null) {
            /* Further, method itself may also be annotated to indicate
             * exact JsonSerializer to use for whatever value is returned...
             */
            ser = findSerializerFromAnnotation(config, valueMethod);
            return new JsonValueSerializer(valueMethod.getAnnotated(), ser);
        }
        return constructBeanSerializer(config, beanDesc);
    }

    /*
    ////////////////////////////////////////////////////////////
    // Overridable non-public methods
    ////////////////////////////////////////////////////////////
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

    protected JsonSerializer<Object> constructBeanSerializer(SerializationConfig config,
                                                             BasicBeanDescription beanDesc)
    {
        // First: any detectable (auto-detect, annotations) properties to serialize?
        List<BeanPropertyWriter> props = findBeanProperties(config, beanDesc);
        if (props == null || props.size() == 0) {
            // No properties, no serializer
            /* 27-Nov-2009, tatu: Except that as per [JACKSON-201], we are
             *   ok with that as long as it has a recognized class annotation
             *  (which may come from a mix-in too)
             */
            if (beanDesc.hasKnownClassAnnotations()) {
                return BeanSerializer.createDummy(beanDesc.classDescribed());
            }
            return null;
        }
        // Any properties to suppress?
        props = filterBeanProperties(config, beanDesc, props);
        // Do they need to be sorted in some special way?
        props = sortBeanProperties(config, beanDesc, props);
        BeanSerializer ser = new BeanSerializer(beanDesc.classDescribed(), props);
        // One more thing: need to gather view information, if any:
        handleViews(ser, props);
        return ser;
    }

    /**
     * Method used to collect all actual serializable properties.
     * Can be overridden to implement custom detection schemes.
     */
    protected List<BeanPropertyWriter> findBeanProperties(SerializationConfig config, BasicBeanDescription beanDesc)
    {
        LinkedHashMap<String,AnnotatedMethod> methodsByProp = beanDesc.findGetters
            (config.isEnabled(SerializationConfig.Feature.AUTO_DETECT_GETTERS),
             config.isEnabled(SerializationConfig.Feature.AUTO_DETECT_IS_GETTERS),
             null);

        /* [JACKSON-98]: also include field-backed properties:
         *   (second arg passed to ignore anything for which there is a getter
         *   method)
         */
        LinkedHashMap<String,AnnotatedField> fieldsByProp = beanDesc.findSerializableFields(config.isEnabled(SerializationConfig.Feature.AUTO_DETECT_FIELDS), methodsByProp.keySet());

        // nothing? can't proceed (caller may or may not throw an exception)
        if (methodsByProp.isEmpty() && fieldsByProp.isEmpty()) {
            return null;
        }
        boolean fixAccess = config.isEnabled(SerializationConfig.Feature.CAN_OVERRIDE_ACCESS_MODIFIERS);
        boolean staticTyping = usesStaticTyping(config, beanDesc);
        PropertyBuilder pb = constructPropertyBuilder(config, beanDesc);

        ArrayList<BeanPropertyWriter> props = new ArrayList<BeanPropertyWriter>(methodsByProp.size());
        AnnotationIntrospector intr = config.getAnnotationIntrospector();

        // [JACKSON-98]: start with field properties, if any
        for (Map.Entry<String,AnnotatedField> en : fieldsByProp.entrySet()) {
            AnnotatedField af = en.getValue();
            if (fixAccess) {
                af.fixAccess();
            }
            // Does Method specify a serializer? If so, let's use it.
            JsonSerializer<Object> annotatedSerializer = findSerializerFromAnnotation(config, af);
            BeanPropertyWriter pbw = pb.buildProperty(en.getKey(), annotatedSerializer, af, staticTyping);
            // how about views? (1.4+)
            pbw.setViews(intr.findSerializationViews(af));
            props.add(pbw);
        }

        for (Map.Entry<String,AnnotatedMethod> en : methodsByProp.entrySet()) {
            AnnotatedMethod am = en.getValue();
            if (fixAccess) {
                am.fixAccess();
            }
            // Does Method specify a serializer? If so, let's use it.
            JsonSerializer<Object> annotatedSerializer = findSerializerFromAnnotation(config, am);
            BeanPropertyWriter pbw = pb.buildProperty(en.getKey(), annotatedSerializer, am, staticTyping);
            pbw.setViews(intr.findSerializationViews(am));
            props.add(pbw);
        }
        return props;
    }
    /**
     * Overridable method that can filter out properties. Default implementation
     * checks annotations class may have.
     */
    protected List<BeanPropertyWriter> filterBeanProperties(SerializationConfig config, BasicBeanDescription beanDesc, List<BeanPropertyWriter> props)
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
    protected List<BeanPropertyWriter> sortBeanProperties(SerializationConfig config, BasicBeanDescription beanDesc, List<BeanPropertyWriter> props)
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
     */
    protected void handleViews(BeanSerializer ser, List<BeanPropertyWriter> props)
    {
        int i = 0;
        SerializationViewFilter[] filters = null;
        /* Simple: we have stashed view information within individual writers;
         * now need combine.
         */
        for (BeanPropertyWriter bpw : props) {
            Class<?>[] views = bpw.getViews();
            if (views != null) {
                if (filters == null) {
                    filters = new SerializationViewFilter[props.size()];
                }
                filters[i] = new PropertyFilter(views);
            }
            ++i;
        }
        ser.setViewFilters(filters);
    }
        
    protected PropertyBuilder constructPropertyBuilder(SerializationConfig config,
                                                       BasicBeanDescription beanDesc)
    {
        return new PropertyBuilder(config, beanDesc);
    }

    /**
     * Helper method to check whether global settings and/or class
     * annotations for the bean class indicate that static typing
     * (declared types)  should be used for properties.
     * (instead of dynamic runtime types).
     */
    protected boolean usesStaticTyping(SerializationConfig config,
                                       BasicBeanDescription beanDesc)
    {
        JsonSerialize.Typing t = config.getAnnotationIntrospector().findSerializationTyping(beanDesc.getClassInfo());
        if (t != null) {
            return (t == JsonSerialize.Typing.STATIC);
        }
        return config.isEnabled(SerializationConfig.Feature.USE_STATIC_TYPING);
    }

    /*
    *****************************************************************
    * Internal helper methods
    *****************************************************************
     */

    /**
     * Helper method that will sort given List of properties according
     * to defined criteria (usually detected by annotations)
     */
    List<BeanPropertyWriter> _sortBeanProperties(List<BeanPropertyWriter> props,
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

    /*
     *****************************************************************
     * Helper classes
     *****************************************************************
      */

    /**
     * Simple implementation of {@link SerializationViewFilter} that will
     * just construct filter from explicit list of View classes.
     */
    public final static class PropertyFilter
        extends SerializationViewFilter
    {
        final Class<?>[] _includedInViews;
        
        public PropertyFilter(Class<?>[] views) {
            _includedInViews = views;
        }
    
        @Override
        public boolean includeInView(Class<?> activeView) {
            for (int i = 0, len = _includedInViews.length; i < len; ++i) {
                if (_includedInViews[i].isAssignableFrom(activeView)) {
                    return true;
                }
            }
            return false;
        }
    }
}
