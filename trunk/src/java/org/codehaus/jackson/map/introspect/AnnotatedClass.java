package org.codehaus.jackson.map.introspect;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.util.ClassUtil;

public final class AnnotatedClass
    extends Annotated
{
    /*
    ///////////////////////////////////////////////////////
    // Helper classes
    ///////////////////////////////////////////////////////
     */

    /**
     * Filter used to only include methods that have signature that is
     * compatible with "factory" methods: are static, take a single
     * argument, and returns something.
     *<p>
     * <b>NOTE</b>: in future we will probably allow more than one
     * argument, when multi-arg constructors and factory methods
     * are supported (with accompanying annotations to bind args
     * to properties).
     */
    public final static class FactoryMethodFilter
        implements MethodFilter
    {
        public final static FactoryMethodFilter instance = new FactoryMethodFilter();

        public boolean includeMethod(Method m)
        {
            if (!Modifier.isStatic(m.getModifiers())) {
                return false;
            }
            int argCount = m.getParameterTypes().length;
            if (argCount != 1) {
                return false;
            }
            // Can't be a void method
            Class<?> rt = m.getReturnType();
            if (rt == Void.TYPE) {
                return false;
            }
            // Otherwise, potentially ok
            return true;
        }
    }

    /*
    ///////////////////////////////////////////////////////
    // Configuration
    ///////////////////////////////////////////////////////
     */

    /**
     * Class for which annotations apply, and that owns other
     * components (constructors, methods)
     */
    final Class<?> _class;

    /**
     * Ordered set of super classes and interfaces of the
     * class itself: included in order of precedence
     */
    final Collection<Class<?>> _superTypes;

    /**
     * Filter used to determine which annotations to gather; used
     * to optimize things so that unnecessary annotations are
     * ignored.
     */
    final AnnotationIntrospector _annotationIntrospector;

    /*
    ///////////////////////////////////////////////////////
    // Gathered information
    ///////////////////////////////////////////////////////
     */

    /**
     * Combined list of Jackson annotations that the class has,
     * including inheritable ones from super classes and interfaces
     */
    AnnotationMap _classAnnotations;

    /**
     * Default constructor of the annotated class, if it has one.
     */
    AnnotatedConstructor _defaultConstructor;

    /**
     * Single argument constructors the class has, if any.
     */
    List<AnnotatedConstructor> _singleArgConstructors;

    /**
     * Single argument static methods that might be usable
     * as factory methods
     */
    List<AnnotatedMethod> _singleArgStaticMethods;

    /**
     * Member methods of interest; for now ones with 0 or 1 arguments
     * (just optimization, since others won't be used now)
     */
    AnnotatedMethodMap  _memberMethods;

    /**
     * Member fields of interest: ones that are either public,
     * or have at least one annotation.
     */
    List<AnnotatedField> _fields;

    /*
    ///////////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////////
     */

    /**
     * Constructor will not do any initializations, to allow for
     * configuring instances differently depending on use cases
     */
    private AnnotatedClass(Class<?> cls, List<Class<?>> superTypes,
                           AnnotationIntrospector aintr)
    {
        _class = cls;
        _superTypes = superTypes;
        _annotationIntrospector = aintr;
    }

    /**
     * @param annotationFilter Filter used to define which annotations to
     *    include (for class and member annotations). Can not be null.
     * @param includeCreators Whether to include information about
     *   potential creators (constructors and static factory methods)
     * @param memberFilter Optional filter that defines which member methods
     *   to include; if null, no member method information is to be included.
     * @param includeFields Whether to include non-static fields that are
     *   either public, or have at least a single annotation
     */
    public static AnnotatedClass constructFull(Class<?> cls,
                                               AnnotationIntrospector aintr,
                                               boolean includeCreators,
                                               MethodFilter memberFilter,
                                               boolean includeFields)
    {
        List<Class<?>> st = ClassUtil.findSuperTypes(cls, null);
        AnnotatedClass ac = new AnnotatedClass(cls, st, aintr);
        ac.resolveClassAnnotations();
        if (includeCreators) {
            ac.resolveCreators();
        }
        if (memberFilter != null) {
            ac.resolveMemberMethods(memberFilter);
        }
        if (includeFields) {
            ac.resolveFields();
        }
        return ac;
    }

    /*
    ///////////////////////////////////////////////////////
    // Init methods
    ///////////////////////////////////////////////////////
     */

    /**
     * Initialization method that will recursively collect Jackson
     * annotations for this class and all super classes and
     * interfaces.
     */
    private void resolveClassAnnotations()
    {
        _classAnnotations = new AnnotationMap();
        // first, annotations from the class itself:
        for (Annotation a : _class.getDeclaredAnnotations()) {
            if (_annotationIntrospector.isHandled(a)) {
                _classAnnotations.add(a);
            }
        }
        // and then from super types
        for (Class<?> cls : _superTypes) {
            for (Annotation a : cls.getDeclaredAnnotations()) {
                if (_annotationIntrospector.isHandled(a)) {
                    _classAnnotations.addIfNotPresent(a);
                }
            }
        }
    }

    /**
     * Initialization method that will find out all constructors
     * and potential static factory methods the class has.
     */
    private void resolveCreators()
    {
        // Then see which constructors we have
        _singleArgConstructors = null;
        for (Constructor<?> ctor : _class.getDeclaredConstructors()) {
            switch (ctor.getParameterTypes().length) {
            case 0:
                _defaultConstructor = new AnnotatedConstructor(ctor, _annotationIntrospector);
                break;
            case 1:
                if (_singleArgConstructors == null) {
                    _singleArgConstructors = new ArrayList<AnnotatedConstructor>();
                }
                _singleArgConstructors.add(new AnnotatedConstructor(ctor, _annotationIntrospector));
                break;
            }
        }

        _singleArgStaticMethods = null;
        /* Then methods: single-arg static methods (potential factory
         * methods), and 0/1-arg member methods (getters, setters)
         */
        for (Method m : _class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())) {
                int argCount = m.getParameterTypes().length;
                if (argCount == 1) {
                    if (_singleArgStaticMethods == null) {
                        _singleArgStaticMethods = new ArrayList<AnnotatedMethod>();
                    }
                    _singleArgStaticMethods.add(new AnnotatedMethod(m, _annotationIntrospector));
                }
            }
        }
    }

    private void resolveMemberMethods(MethodFilter methodFilter)
    {
        _memberMethods = new AnnotatedMethodMap();
        for (Method m : _class.getDeclaredMethods()) {
            /* 07-Apr-2009, tatu: Looks like generics can introduce hidden
             *   bridge and/or synthetic methods. I don't think we want to
             *   consider those...
             */
            if (m.isSynthetic() || m.isBridge()) {
                continue;
            }
            if (methodFilter.includeMethod(m)) {
                _memberMethods.add(new AnnotatedMethod(m, _annotationIntrospector));
            }
        }
        /* and then augment these with annotations from
         * super-types:
         */
        for (Class<?> cls : _superTypes) {
            for (Method m : cls.getDeclaredMethods()) {
                // as with above, these are bogus methods, to be ignored:
                if (m.isSynthetic() || m.isBridge()) {
                    continue;
                }
                if (methodFilter.includeMethod(m)) {
                    AnnotatedMethod am = _memberMethods.find(m);
                    if (am == null) {
                        am = new AnnotatedMethod(m, _annotationIntrospector);
                        _memberMethods.add(am);
                    } else {
                        am.addAnnotationsNotPresent(m);
                    }
                }
            }
        }
    }

    /**
     * Method that will collect all member (non-static) fields
     * that are either public, or have at least a single annotation
     * associated with them.
     */
    private void resolveFields()
    {
        _fields = new ArrayList<AnnotatedField>();
        _addFields(_fields, _class);
    }

    private void _addFields(List<AnnotatedField> fields, Class<?> c)
    {
        /* First, a quick test: we only care for regular classes (not
         * interfaces, primitive types etc), except for Object.class.
         * A simple check to rule out other cases is to see if there
         * is a super class or not.
         */
        Class<?> parent = c.getSuperclass();
        if (parent != null) {
            /* Let's add super-class' fields first, then ours.
             * Also: we won't be checking for masking (by name); it
             * can happen, if very rarely, but will be handled later
             * on when resolving masking between methods and fields
             */
            _addFields(fields, parent);
            for (Field f : c.getDeclaredFields()) {
                /* I'm pretty sure synthetic fields are to be skipped...
                 * (methods definitely are)
                 */
                if (f.isSynthetic()) {
                    continue;
                }
                // First: static fields are never included
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods)) {
                    continue;
                }
                /* Need to be public, or have an annotation
                 * (these are required, but not sufficient checks)
                 */
                Annotation[] anns = f.getAnnotations();
                if (anns.length > 0 || Modifier.isPublic(mods)) {
                    fields.add(new AnnotatedField(f, _annotationIntrospector, anns));
                }
            }
        }
    }

    /*
    ///////////////////////////////////////////////////////
    // Annotated impl 
    ///////////////////////////////////////////////////////
     */

    public Class<?> getAnnotated() { return _class; }

    public int getModifiers() { return _class.getModifiers(); }

    public String getName() { return _class.getName(); }

    public <A extends Annotation> A getAnnotation(Class<A> acls)
    {
        if (_classAnnotations == null) {
            return null;
        }
        return _classAnnotations.get(acls);
    }

    /*
    ///////////////////////////////////////////////////////
    // Public API, generic accessors
    ///////////////////////////////////////////////////////
     */

    public AnnotatedConstructor getDefaultConstructor() { return _defaultConstructor; }

    public List<AnnotatedConstructor> getSingleArgConstructors()
    {
        if (_singleArgConstructors == null) {
            return Collections.emptyList();
        }
        return _singleArgConstructors;
    }

    public List<AnnotatedMethod> getSingleArgStaticMethods()
    {
        if (_singleArgStaticMethods == null) {
            return Collections.emptyList();
        }
        return _singleArgStaticMethods;
    }

    public Collection<AnnotatedMethod> getMemberMethods()
    {
        return _memberMethods.getMethods();
    }

    public AnnotatedMethod findMethod(String name, Class<?>[] paramTypes)
    {
        return _memberMethods.find(name, paramTypes);
    }

    public List<AnnotatedField> getFields()
    {
        if (_fields == null) {
            return Collections.emptyList();
        }
        return _fields;
    }

    /*
    ///////////////////////////////////////////////////////
    // Other methods
    ///////////////////////////////////////////////////////
     */

    @Override
    public String toString()
    {
        return "[AnnotedClass "+_class.getName()+"]";
    }
}

