package org.codehaus.jackson.map.introspect;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;

import org.codehaus.jackson.type.JavaType;
import org.codehaus.jackson.map.type.TypeFactory;

/**
 * Object that represents method parameters, mostly so that associated
 * annotations can be processed conveniently.
 */
public final class AnnotatedParameter
    extends Annotated
{
    final Type _type;

    final AnnotationMap _annotations;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public AnnotatedParameter(Type type,  AnnotationMap ann)
    {
        _type = type;
        _annotations = ann;
    }

    public void addOrOverride(Annotation a)
    {
        _annotations.add(a);
    }

    /*
    /**********************************************************
    /* Annotated impl
    /**********************************************************
     */

    /// Unfortunately, there is no matching JDK type...
    @Override
    public AnnotatedElement getAnnotated() { return null; }

    /// Unfortunately, there is no matching JDK type...
    @Override
    public int getModifiers() { return 0; }

    @Override
    public String getName() { return ""; }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> acls)
    {
        return _annotations.get(acls);
    }

    @Override
    public Type getGenericType() {
	return _type;
    }

    @Override
    public Class<?> getRawType() {
        JavaType t = TypeFactory.type(_type);
        return t.getRawClass();
    }
    
    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    public Type getParameterType() { return _type; }
}
