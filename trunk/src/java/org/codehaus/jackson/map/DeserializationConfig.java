package org.codehaus.jackson.map;

import org.codehaus.jackson.Base64Variant;
import org.codehaus.jackson.Base64Variants;
import org.codehaus.jackson.annotate.*;
import org.codehaus.jackson.map.util.LinkedNode;

/**
 * Object that contains baseline configuration for deserialization
 * process. An instance is owned by {@link ObjectMapper}, which makes
 * a copy that is passed during serialization process to
 * {@link DeserializerProvider} and {@link DeserializerFactory}.
 *<p>
 * Note: although configuration settings can be changed at any time
 * (for factories and instances), they are not guaranteed to have
 * effect if called after constructing relevant mapper or deserializer
 * instance. This because some objects may be configured, constructed and
 * cached first time they are needed.
 */
public class DeserializationConfig
{
    /**
     * Enumeration that defines togglable features that guide
     * the serialization feature.
     */
    public enum Feature {
        /**
         * Feature that determines whether "setter" methods are
         * automatically detected based on standard Bean naming convention
         * or not. If yes, then all public one-argument methods that
         * start with prefix "set"
         * are considered setters. If disabled, only methods explicitly
         * annotated are considered setters.
         *<p>
         * Note that this feature has lower precedence than per-class
         * annotations, and is only used if there isn't more granular
         * configuration available.
         *<P>
         * Feature is enabled by default.
         */
        AUTO_DETECT_SETTERS(true),
        /**
         * Feature that determines whether "creator" methods are
         * automatically detected by consider public constructors,
         * and static single argument methods with name "valueOf".
         * If disabled, only methods explicitly annotated are considered
         * creator methods (except for the no-arg default constructor which
         * is always considered a factory method).
         *<p>
         * Note that this feature has lower precedence than per-class
         * annotations, and is only used if there isn't more granular
         * configuration available.
         *<P>
         * Feature is enabled by default.
         */
        AUTO_DETECT_CREATORS(true),

        /**
         * Feature that determines whether Json floating point numbers
         * are to be deserialized into {@link java.math.BigDecimal}s
         * if only generic type description (either {@link Object} or
         * {@link Number}, or within untyped {@link java.util.Map}
         * or {@link java.util.Collection} context) is available.
         * If enabled such values will be deserialized as {@link java.math.BigDecimal}s;
         * if disabled, will be deserialized as {@link Double}s.
         * <p>
         * Feature is disabled by default, meaning that "untyped" floating
         * point numbers will by default be deserialized as {@link Double}s
         * (choice is for performance reason -- BigDecimals are slower than
         * Doubles)
         */
        USE_BIG_DECIMAL_FOR_FLOATS(false)
	            ;

        final boolean _defaultState;

        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }
	        
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
        }
	        
        public boolean enabledByDefault() { return _defaultState; }
    
        public int getMask() { return (1 << ordinal()); }
    }

    /**
     * Bitfield (set of flags) of all Features that are enabled
     * by default.
     */
    protected final static int DEFAULT_FEATURE_FLAGS = Feature.collectDefaults();

    /*
    ///////////////////////////////////////////////////////////
    // Configured settings
    ///////////////////////////////////////////////////////////
     */

    /**
     * Introspector used to figure out Bean properties needed for bean serialization
     * and deserialization. Overridable so that it is possible to change low-level
     * details of introspection, like adding new annotation types.
     */
    protected ClassIntrospector<? extends BeanDescription> _classIntrospector;

    /**
     * Bitset that contains all enabled features
     */
    protected int _featureFlags = DEFAULT_FEATURE_FLAGS;

    /**
     * Linked list that contains all registered problem handlers.
     * Implementation as front-added linked list allows for sharing
     * of the list (tail) without copying the list.
     */
    protected LinkedNode<DeserializationProblemHandler> _problemHandlers;

    /*
    ///////////////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////////////
     */

    public DeserializationConfig(ClassIntrospector<? extends BeanDescription> intr) {
        _classIntrospector = intr;
    }

    protected DeserializationConfig(DeserializationConfig src)
    {
        _classIntrospector = src._classIntrospector;
        _featureFlags = src._featureFlags;
        _problemHandlers = src._problemHandlers;
    }

    /**
     * Method that is called to create a non-shared copy of the configuration
     * to be used for a deserialization operation.
     * Note that if sub-classing
     * and sub-class has additional instance methods,
     * this method <b>must</b> be overridden to produce proper sub-class
     * instance.
     */
    public DeserializationConfig createUnshared()
    {
    	return new DeserializationConfig(this);
    }

    /**
     * Method that checks class annotations that the argument Object has,
     * and modifies settings of this configuration object accordingly,
     * similar to how those annotations would affect actual value classes
     * annotated with them, but with global scope. Note that not all
     * annotations have global significance, and thus only subset of
     * Jackson annotations will have any effect.
     *<p>
     * Ones that are known to have effect are:
     *<ul>
     * <li>{@link JsonWriteNullProperties}</li>
     * <li>{@link JsonAutoDetect}</li>
     *</ul>
     * 
     * @param annotatedClass Class of which class annotations to use
     *   for changing configuration settings
     */
    public void fromAnnotations(Class<?> annotatedClass)
    {
    	// no annotation for USE_BIG_DECIMAL_FOR_FLOATS...

    	JsonAutoDetect autoDetect = annotatedClass.getAnnotation(JsonAutoDetect.class);
    	if (autoDetect != null) {
            boolean setters = false;
            boolean creators = false;
            for (JsonMethod m : autoDetect.value()) {
                if (m == JsonMethod.SETTER || m == JsonMethod.ALL) {
                    setters = true;
                }
                if (m == JsonMethod.CREATOR || m == JsonMethod.ALL) {
                    creators = true;
                }
            }
            set(Feature.AUTO_DETECT_SETTERS, setters); 		
            set(Feature.AUTO_DETECT_CREATORS, creators);
    	}
    }

    /**
     * Method that can be used to add a handler that can (try to)
     * resolve non-fatal deserialization problems.
     */
    public void addHandler(DeserializationProblemHandler h)
    {
        /* Sanity check: let's prevent adding same handler multiple
         * times
         */
        if (!LinkedNode.contains(_problemHandlers, h)) {
            _problemHandlers = new LinkedNode<DeserializationProblemHandler>(h, _problemHandlers);
        }
    }
        
    
    /*
    ///////////////////////////////////////////////////////////
    // Accessors
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method for checking whether given feature is enabled or not
     */
    public final boolean isEnabled(Feature f) {
        return (_featureFlags & f.getMask()) != 0;
    }

    /**
     * Method called during deserialization if Base64 encoded content
     * needs to be decoded. Default version just returns default Jackson
     * uses, which is modified-mime which does not add linefeeds (because
     * those would have to be escaped in Json strings).
     */
    public Base64Variant getBase64Variant() {
        return Base64Variants.getDefaultVariant();
    }

    /**
     * Method for getting head of the problem handler chain. May be null,
     * if no handlers have been added.
     */
    public LinkedNode<DeserializationProblemHandler> getProblemHandlers()
    {
        return _problemHandlers;
    }

    @SuppressWarnings("unchecked")
	public <T extends BeanDescription> ClassIntrospector<T> getIntrospector() {
        return (ClassIntrospector<T>) _classIntrospector;
    }

    @SuppressWarnings("unchecked")
	public <T extends BeanDescription> T introspect(Class<?> cls) {
        return (T) getIntrospector().forDeserialization(cls);
    }

    @SuppressWarnings("unchecked")
	public <T extends BeanDescription> T introspectForCreation(Class<?> cls) {
        return (T) getIntrospector().forCreation(cls);
    }

    /**
     * Accessor for getting bean description that only contains class
     * annotations: useful if no getter/setter/creator information is needed.
     */
    @SuppressWarnings("unchecked")
	public <T extends BeanDescription> T introspectClassAnnotations(Class<?> cls) {
        return (T) getIntrospector().forClassAnnotations(cls);
    }

    /*
    ////////////////////////////////////////////////////
    // Configuration: on/off features
    ////////////////////////////////////////////////////
     */

    /**
     * Method for enabling specified  feature.
     */
    public void enable(Feature f) {
        _featureFlags |= f.getMask();
    }

    /**
     * Method for disabling specified feature.
     */
    public void disable(Feature f) {
        _featureFlags &= ~f.getMask();
    }

    /**
     * Method for enabling or disabling specified feature.
     */
    public void set(Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
    }

    //protected int getFeatures() { return _features; }

    /*
    ////////////////////////////////////////////////////
    // Configuration: other
    ////////////////////////////////////////////////////
     */

    public void setIntrospector(ClassIntrospector<? extends BeanDescription> i) {
        _classIntrospector = i;
    }
}
