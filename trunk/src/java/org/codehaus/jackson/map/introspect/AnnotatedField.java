package org.codehaus.jackson.map.introspect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

import org.codehaus.jackson.map.util.ClassUtil;

public final class AnnotatedField
    extends Annotated
{
    final Field _field;

    final AnnotationMap _annotations;

    /*
    //////////////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////////////
     */

    public AnnotatedField(Field field, AnnotationMap annMap)
    {
        _field = field;
        _annotations = annMap;
    }

    /*
    //////////////////////////////////////////////////////
    // Annotated impl
    //////////////////////////////////////////////////////
     */

    public Field getAnnotated() { return _field; }

    public int getModifiers() { return _field.getModifiers(); }

    public String getName() { return _field.getName(); }

    public <A extends Annotation> A getAnnotation(Class<A> acls)
    {
        return _annotations.get(acls);
    }

    /*
    //////////////////////////////////////////////////////
    // Extended API, generic
    //////////////////////////////////////////////////////
     */

    public Type getGenericType() {
        return _field.getGenericType();
    }

    public Class<?> getType()
    {
        return _field.getType();
    }

    public Class<?> getDeclaringClass() { return _field.getDeclaringClass(); }

    public String getFullName() {
        return getDeclaringClass().getName() + "#" + getName();
    }

    public int getAnnotationCount() { return _annotations.size(); }

    /**
     * Method that can be called to modify access rights, by calling
     * {@link java.lang.reflect.AccessibleObject#setAccessible} on
     * the underlying annotated element.
     */
    public void fixAccess()
    {
        ClassUtil.checkAndFixAccess(_field);
    }

    public String toString()
    {
        return "[field "+getName()+", annotations: "+_annotations+"]";
    }
}

