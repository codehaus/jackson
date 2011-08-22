package org.codehaus.jackson.map.introspect;

import java.util.*;

import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.util.BeanUtil;
import org.codehaus.jackson.type.JavaType;


/* TODO:
 * 
 * - Naming strategy: probably only after otherwise reconciling (i.e. done by BasicBeanDescription)?
 * 
 */

/**
 * Helper class used for aggregating information about all possible
 * properties of a POJO.
 * 
 * @since 1.9
 */
public class POJOPropertiesCollector
{
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    
    /**
     * Configuration settings
     */
    protected final MapperConfig<?> _config;

    /**
     * True if introspection is done for serialization (giving
     *   precedence for serialization annotations), or not (false, deserialization)
     */
    protected final boolean _forSerialization;
    
    /**
     * Type of POJO for which properties are being collected.
     */
    protected final JavaType _type;

    /**
     * Low-level introspected class information (methods, fields etc)
     */
    protected final AnnotatedClass _classDef;

    protected final VisibilityChecker<?> _visibilityChecker;

    protected final AnnotationIntrospector _annotationIntrospector;
    
    /*
    /**********************************************************
    /* Collected information
    /**********************************************************
     */

    /**
     * Set of logical property information collected so far
     */
    protected final LinkedHashMap<String, POJOPropertyCollector> _properties
        = new LinkedHashMap<String, POJOPropertyCollector>();

    protected LinkedList<AnnotatedMethod> _anyGetters = null;

    protected LinkedList<AnnotatedMethod> _anySetters = null;

    /**
     * Method(s) marked with 'JsonValue' annotation
     */
    protected LinkedList<AnnotatedMethod> _jsonValueGetters = null;

    /**
     * Lazily collected list of properties that can be implicitly
     * ignored during serialization; only updated when collecting
     * information for deserialization purposes
     */
    protected HashSet<String> _ignoredPropertyNames;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    private POJOPropertiesCollector(MapperConfig<?> config, boolean forSerialization,
            JavaType type, AnnotatedClass classDef)
    {
        _config = config;
        _forSerialization = forSerialization;
        _type = type;
        _classDef = classDef;
        _annotationIntrospector = config.isAnnotationProcessingEnabled() ?
                _config.getAnnotationIntrospector() : null;
        if (_annotationIntrospector == null) {
            _visibilityChecker = _config.getDefaultVisibilityChecker();
        } else {
            _visibilityChecker = _annotationIntrospector.findAutoDetectVisibility(classDef,
                    _config.getDefaultVisibilityChecker());
        }
    }
    
    /*
    /**********************************************************
    /* Public API
    /**********************************************************
     */

    public static POJOPropertiesCollector collect(MapperConfig<?> config, boolean forSerialization,
            JavaType type, AnnotatedClass classDef)
    {
        POJOPropertiesCollector coll = new POJOPropertiesCollector(config, forSerialization, type, classDef);
        coll._collect();
        return coll;
    }

    public MapperConfig<?> getConfig() {
        return _config;
    }

    public JavaType getType() {
        return _type;
    }
    
    public AnnotatedClass getClassDef() {
        return _classDef;
    }

    public AnnotationIntrospector getAnnotationIntrospector() {
        return _annotationIntrospector;
    }
    
    public List<POJOPropertyCollector> getProperties() {
        return new ArrayList<POJOPropertyCollector>(_properties.values());
    }

    public AnnotatedMethod getJsonValueMethod()
    {
        // If @JsonValue defined, must have a single one
        if (_jsonValueGetters != null) {
            if (_jsonValueGetters.size() > 1) {
                reportProblem("Multiple value properties defined ("+_jsonValueGetters.get(0)+" vs "
                        +_jsonValueGetters.get(1)+")");
            }
            // otherwise we won't greatly care
            return _jsonValueGetters.get(0);
        }
        return null;
    }

    public AnnotatedMethod getAnyGetterMethod()
    {
        if (_anyGetters != null) {
            if (_anyGetters.size() > 1) {
                reportProblem("Multiple 'any-getters' defined ("+_anyGetters.get(0)+" vs "
                        +_anyGetters.get(1)+")");
            }
            return _anyGetters.getFirst();
        }        
        return null;
    }

    public AnnotatedMethod getAnySetterMethod()
    {
        if (_anySetters != null) {
            if (_anySetters.size() > 1) {
                reportProblem("Multiple 'any-setters' defined ("+_anySetters.get(0)+" vs "
                        +_anySetters.get(1)+")");
            }
            return _anySetters.getFirst();
        }
        return null;
    }

    public Set<String> getIgnoredPropertyNames() {
        return _ignoredPropertyNames;
    }
    
    // for unit tests:
    protected Map<String, POJOPropertyCollector> getPropertyMap() {
        return _properties;
    }

    
    /*
    /**********************************************************
    /* Internal methods; main-level collection
    /**********************************************************
     */

    /**
     * Method that orchestrates collection activities
     */
    public void _collect()
    {
        // First: gather basic data
        _addFields();
        _addMethods();

        // Remove ignored properties, individual entries
        _removeUnwantedProperties();

        // Rename remaining properties
        _renameProperties();
        // And use custom naming strategy, if applicable...
        PropertyNamingStrategy naming = _config.getPropertyNamingStrategy();
        if (naming != null) {
            _renameUsing(naming);
        }

        /* Sort by visibility (explicit over implicit); drop all but first
         * of member type (getter, setter etc) if there is visibility
         * difference
         */
        for (POJOPropertyCollector property : _properties.values()) {
            property.trimByVisibility();
        }

        // and then the final step, "merge" annotations
        for (POJOPropertyCollector property : _properties.values()) {
            property.mergeAnnotations(_forSerialization);
        }
    }
    
    /**
     * Method for collecting basic information on all fields found
     */
    protected void _addFields()
    {
        final AnnotationIntrospector ai = _annotationIntrospector;
        
        for (AnnotatedField f : _classDef.fields()) {
            String implName = f.getName();

            String explName;
            if (ai == null) {
                explName = null;
            } else if (_forSerialization) {
                /* 18-Aug-2011, tatu: As per existing unit tests, we should only
                 *   use serialization annotation (@JsonSerializer) when serializing
                 *   fields, and similarly for deserialize-only annotations... so
                 *   no fallbacks in this particular case.
                 */
                explName = ai.findSerializablePropertyName(f);
            } else {
                explName = ai.findDeserializablePropertyName(f);
            }
            if ("".equals(explName)) { // empty String meaning "use default name", here just means "same as field name"
                explName = implName;
            }
            // having explicit name means that field is visible; otherwise need to check the rules
            boolean visible = (explName != null);
            if (!visible) {
                visible = _visibilityChecker.isFieldVisible(f);
            }
            // and finally, may also have explicit ignoral
            boolean ignored = (ai != null) && ai.hasIgnoreMarker(f);
            _property(implName).addField(f, explName, visible, ignored);
        }
    }

    /**
     * Method for collecting basic information on constructor(s) found
     */
    @SuppressWarnings("unused")
    protected void _addConstructors()
    {
        for (AnnotatedConstructor ctor : _classDef.getConstructors()) {
        }
        for (AnnotatedMethod factory : _classDef.getStaticMethods()) {
        }
    }

    /**
     * Method for collecting basic information on all fields found
     */
    protected void _addMethods()
    {
        final AnnotationIntrospector ai = _annotationIntrospector;
        
        for (AnnotatedMethod m : _classDef.memberMethods()) {
            String explName; // from annotation(s)
            String implName; // from naming convention
            
            /* For methods, handling differs between getters and setters; and
             * we will also only consider entries that either follow the bean
             * naming convention or are explicitly marked: just being visible
             * is not enough (unlike with fields)
             */
            int argCount = m.getParameterCount();
            boolean visible;
            
            if (argCount == 0) { // getters (including 'any getter')
                // any getter?
                if (ai != null) {
                    if (ai.hasAnyGetterAnnotation(m)) {
                        if (_anyGetters == null) {
                            _anyGetters = new LinkedList<AnnotatedMethod>();
                        }
                        _anyGetters.add(m);
                        continue;
                    }
                    // @JsonValue?
                    if (ai.hasAsValueAnnotation(m)) {
                        if (_jsonValueGetters == null) {
                            _jsonValueGetters = new LinkedList<AnnotatedMethod>();
                        }
                        _jsonValueGetters.add(m);
                        continue;
                    }
                }
                
                explName = (ai == null) ? null : ai.findGettablePropertyName(m);
                if (explName == null) { // no explicit name; must follow naming convention
                    implName = BeanUtil.okNameForRegularGetter(m, m.getName());
                    if (implName == null) { // if not, must skip
                        implName = BeanUtil.okNameForIsGetter(m, m.getName());
                        if (implName == null) {
                            continue;
                        }
                        visible = _visibilityChecker.isIsGetterVisible(m);
                    } else {
                        visible = _visibilityChecker.isGetterVisible(m);
                    }
                } else { // explicit indication of inclusion, but may be empty
                    // we still need implicit name to link with other pieces
                    implName = BeanUtil.okNameForGetter(m);
                    // if not regular getter name, use method name as is
                    if (implName == null) {
                        implName = m.getName();
                    }
                    if (explName.length() == 0) {
                        explName = implName;
                    }
                    visible = true;
                }
                boolean ignore = (ai == null) ? false : ai.hasIgnoreMarker(m);
                _property(implName).addGetter(m, explName, visible, ignore);
            } else if (argCount == 1) { // setters
                explName = (ai == null) ? null : ai.findSettablePropertyName(m);
                if (explName == null) { // no explicit name; must follow naming convention
                    implName = BeanUtil.okNameForSetter(m);
                    if (implName == null) { // if not, must skip
                        continue;
                    }
                    visible = _visibilityChecker.isSetterVisible(m);
                } else { // explicit indication of inclusion, but may be empty
                    // we still need implicit name to link with other pieces
                    implName = BeanUtil.okNameForSetter(m);
                    // if not regular getter name, use method name as is
                    if (implName == null) {
                        implName = m.getName();
                    }
                    if (explName.length() == 0) { 
                        explName = implName;
                    }
                    visible = true;
                }
                boolean ignore = (ai == null) ? false : ai.hasIgnoreMarker(m);
                _property(implName).addSetter(m, explName, visible, ignore);

            } else if (argCount == 2) { // any getter?
                if (ai != null  && ai.hasAnySetterAnnotation(m)) {
                    if (_anySetters == null) {
                        _anySetters = new LinkedList<AnnotatedMethod>();
                    }
                    _anySetters.add(m);
                }
            }
        }
    }

    /*
    /**********************************************************
    /* Internal methods; removing ignored properties
    /**********************************************************
     */

    /**
     * Method called to get rid of candidate properties that are marked
     * as ignored, or that are not visible.
     */
    protected void _removeUnwantedProperties()
    {
        Iterator<Map.Entry<String,POJOPropertyCollector>> it = _properties.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, POJOPropertyCollector> entry = it.next();
            POJOPropertyCollector prop = entry.getValue();

            // First: if nothing visible, just remove altogether
            if (!prop.anyVisible()) {
                it.remove();
                continue;
            }
            // Otherwise, check ignorals
            if (prop.anyIgnorals()) {
                // first: if one or more ignorals, and no explicit markers, remove the whole thing
                if (!prop.anyExplicitNames()) {
                    it.remove();
                    _addIgnored(prop.getName());
                    continue;
                }
                // otherwise just remove ones marked to be ignored
                prop.removeIgnored();
                if (!_forSerialization && !prop.couldDeserialize()) {
                    _addIgnored(prop.getName());
                }
            }
            // and finally, handle removal of individual non-visible elements
            prop.removeNonVisible();
        }
    }
    
    private void _addIgnored(String name)
    {
        if (!_forSerialization) {
            if (_ignoredPropertyNames == null) {
                _ignoredPropertyNames = new HashSet<String>();
            }
            _ignoredPropertyNames.add(name);
        }
    }

    /*
    /**********************************************************
    /* Internal methods; renaming properties
    /**********************************************************
     */

    protected void _renameProperties()
    {
        // With renaming need to do in phases: first, find properties to rename
        Iterator<Map.Entry<String,POJOPropertyCollector>> it = _properties.entrySet().iterator();
        LinkedList<POJOPropertyCollector> renamed = null;
        while (it.hasNext()) {
            Map.Entry<String, POJOPropertyCollector> entry = it.next();
            POJOPropertyCollector prop = entry.getValue();
            String newName = prop.findNewName();
            if (newName != null) {
                if (renamed == null) {
                    renamed = new LinkedList<POJOPropertyCollector>();
                }
                renamed.add(prop.withName(newName));
                it.remove();
            }
        }
        
        // and if any were renamed, merge back in...
        if (renamed != null) {
            for (POJOPropertyCollector prop : renamed) {
                String name = prop.getName();
                POJOPropertyCollector old = _properties.get(name);
                if (old == null) {
                    _properties.put(name, prop);
                } else {
                    old.addAll(prop);
                }
            }
        }
    }

    protected void _renameUsing(PropertyNamingStrategy naming)
    {
        POJOPropertyCollector[] props = _properties.values().toArray(new POJOPropertyCollector[_properties.size()]);
        _properties.clear();
        for (POJOPropertyCollector prop : props) {
            String name = prop.getName();
            if (_forSerialization) {
                if (prop.hasGetter()) {
                    name = naming.nameForGetterMethod(_config, prop.getGetter(), name);
                } else if (prop.hasField()) {
                    name = naming.nameForField(_config, prop.getField(), name);
                }
            } else {
                if (prop.hasSetter()) {
                    name = naming.nameForSetterMethod(_config, prop.getSetter(), name);
                } else if (prop.hasConstructorParameter()) {
                    name = naming.nameForConstructorParameter(_config, prop.getConstructorParameter(), name);
                } else if (prop.hasField()) {
                    name = naming.nameForField(_config, prop.getField(), name);
                }
            }
            if (!name.equals(prop.getName())) {
                prop = prop.withName(name);
            }
            _properties.put(name, prop);
        }
    }
    
    /*
    /**********************************************************
    /* Internal methods; helpers
    /**********************************************************
     */

    protected void reportProblem(String msg) {
        throw new IllegalArgumentException("Problem with definition of "+_classDef+": "+msg);
    }
    
    protected POJOPropertyCollector _property(String implName)
    {
        POJOPropertyCollector prop = _properties.get(implName);
        if (prop == null) {
            prop = new POJOPropertyCollector(implName);
            _properties.put(implName, prop);
        }
        return prop;
    }
}
