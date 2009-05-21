package org.codehaus.jackson.map.introspect;

import java.lang.annotation.Annotation;

import org.codehaus.jackson.annotate.*;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.annotate.JsonCachable;

/**
 * {@link AnnotationIntrospector} implementation that handles standard
 * Jackson annotations.
 */
public class JacksonAnnotationIntrospector
    extends AnnotationIntrospector
{
    public JacksonAnnotationIntrospector() { }

    /*
    ////////////////////////////////////////////////////
    // General annotation properties
    ////////////////////////////////////////////////////
     */

    public boolean isHandled(Annotation ann)
    {
        Class<? extends Annotation> acls = ann.annotationType();

        /* 16-May-2009, tatu: used to check this like so...
           final String JACKSON_PKG_PREFIX = "org.codehaus.jackson";

           Package pkg = acls.getPackage();
           return (pkg != null) && (pkg.getName().startsWith(JACKSON_PKG_PREFIX));
        */

        // but this is more reliable, now that we have tag annotation:
        return acls.getAnnotation(JacksonAnnotation.class) != null;
    }

    @SuppressWarnings("unchecked")
	public Class<JsonSerializer<?>> findSerializerClass(Annotated a)
    {
        JsonUseSerializer ann = a.getAnnotation(JsonUseSerializer.class);
        if (ann == null) {
            return null;
        }
        Class<?> serClass = ann.value();
        /* 21-Feb-2009, tatu: There is now a way to indicate "no class"
         *   (to essentially denote a 'dummy' annotation, needed for
         *   overriding in some cases), need to check:
         */
        if (serClass == NoClass.class || serClass == JsonSerializer.None.class) {
            return null;
        }
        if (!JsonSerializer.class.isAssignableFrom(serClass)) {
            throw new IllegalArgumentException("Invalid @JsonUseSerializer annotation: Class "+serClass.getName()+" not a JsonSerializer");
        }
        return (Class<JsonSerializer<?>>)serClass;
    }

    @SuppressWarnings("unchecked")
	public Class<JsonDeserializer<?>> findDeserializerClass(Annotated a)
    {
        JsonUseDeserializer ann = a.getAnnotation(JsonUseDeserializer.class);
        if (ann == null) {
            return null;
        }
        Class<?> deserClass = ann.value();
        if (deserClass == NoClass.class || deserClass == JsonDeserializer.None.class) {
            return null;
        }
        if (!JsonDeserializer.class.isAssignableFrom(deserClass)) {
            throw new IllegalArgumentException("Invalid @JsonUseDeserializer annotation: Class "+deserClass.getName()+" not a JsonDeserializer");
        }
        return (Class<JsonDeserializer<?>>)deserClass;
    }

    /*
    ///////////////////////////////////////////////////////
    // Class annotations, general
    ///////////////////////////////////////////////////////
    */

    public Boolean findCachability(AnnotatedClass ac)
    {
        JsonCachable ann = ac.getAnnotation(JsonCachable.class);
        if (ann == null) {
            return null;
        }
        return ann.value() ? Boolean.TRUE : Boolean.FALSE;
    }

    /*
    ////////////////////////////////////////////////////
    // Class annotations: Serialization
    ////////////////////////////////////////////////////
     */

    public Boolean findGetterAutoDetection(AnnotatedClass ac)
    {
        JsonAutoDetect cann = ac.getAnnotation(JsonAutoDetect.class);
        if (cann != null) {
            JsonMethod[] methods = cann.value();
            if (methods != null) {
                for (JsonMethod jm : methods) {
                    if (jm.getterEnabled()) {
                        return Boolean.TRUE;
                    }
                }
            }
            return Boolean.FALSE;
        }
        return null;
    }

    public boolean willWriteNullProperties(AnnotatedClass am, boolean defValue)
    {
        JsonWriteNullProperties ann = am.getAnnotation(JsonWriteNullProperties.class);
        return (ann == null) ? defValue : ann.value();
    }

    /*
    ////////////////////////////////////////////////////
    // Class annotations: Deserialization
    ////////////////////////////////////////////////////
     */

    public Boolean findSetterAutoDetection(AnnotatedClass ac)
    {
        JsonAutoDetect cann = ac.getAnnotation(JsonAutoDetect.class);
        if (cann != null) {
            JsonMethod[] methods = cann.value();
            if (methods != null) {
                for (JsonMethod jm : methods) {
                    if (jm.setterEnabled()) {
                        return Boolean.TRUE;
                    }
                }
            }
            return Boolean.FALSE;
        }
        return null;
    }

    public Boolean findCreatorAutoDetection(AnnotatedClass ac)
    {
        JsonAutoDetect cann = ac.getAnnotation(JsonAutoDetect.class);
        if (cann != null) {
            JsonMethod[] methods = cann.value();
            if (methods != null) {
                for (JsonMethod jm : methods) {
                    if (jm.creatorEnabled()) {
                        return Boolean.TRUE;
                    }
                }
            }
            return Boolean.FALSE;
        }
        return null;
    }

    /*
    ////////////////////////////////////////////////////
    // Method annotations: general
    ////////////////////////////////////////////////////
     */

    public boolean isIgnorableMethod(AnnotatedMethod m)
    {
        JsonIgnore ann = m.getAnnotation(JsonIgnore.class);
        return (ann != null && ann.value());
    }

    /*
    ///////////////////////////////////////////////////////
    // Method annotations: serialization
    ///////////////////////////////////////////////////////
    */

    public String findGettablePropertyName(AnnotatedMethod am)
    {
        JsonGetter ann = am.getAnnotation(JsonGetter.class);
        if (ann == null) {
            return null;
        }
        String propName = ann.value();
        // can it ever be null? I don't think so, but just in case:
        if (propName == null) {
            propName = "";
        }
        return propName;
    }

    public Class<?> findConcreteSerializationType(AnnotatedMethod am)
    {
        // !!! TBI for [JACKSON-120]
        return null;
    }


    public boolean hasAsValueAnnotation(AnnotatedMethod am)
    {
        JsonValue ann = am.getAnnotation(JsonValue.class);
        // value of 'false' means disabled...
        return (ann != null && ann.value());
    }

    public boolean willWriteNullProperties(AnnotatedMethod am, boolean defValue)
    {
        JsonWriteNullProperties ann = am.getAnnotation(JsonWriteNullProperties.class);
        return (ann == null) ? defValue : ann.value();
    }

    /*
    ///////////////////////////////////////////////////////
    // Method annotations: deserialization
    ///////////////////////////////////////////////////////
    */

    public String findSettablePropertyName(AnnotatedMethod am)
    {
        JsonSetter ann = am.getAnnotation(JsonSetter.class);
        if (ann == null) {
            return null;
        }
        String propName = ann.value();
        // can it ever be null? I don't think so, but just in case:
        if (propName == null) {
            propName = "";
        }
        return propName;
    }

    public boolean hasAnySetterAnnotation(AnnotatedMethod am)
    {
        /* No dedicated disabling; regular @JsonIgnore used
         * if needs to be ignored (and if so, is handled prior
         * to this method getting called)
         */
        return am.hasAnnotation(JsonAnySetter.class);
    }

    public boolean hasCreatorAnnotation(AnnotatedMethod am)
    {
        /* No dedicated disabling; regular @JsonIgnore used
         * if needs to be ignored (and if so, is handled prior
         * to this method getting called)
         */
        return am.hasAnnotation(JsonCreator.class);
    }

    public Class<?> findConcreteDeserializationType(AnnotatedMethod am)
    {
        JsonClass ann = am.getAnnotation(JsonClass.class);
        if (ann != null) {
            Class<?> cls = ann.value();
            if(cls != NoClass.class) {
                return cls;
            }
        }
        return null;
    }

    public Class<?> findKeyType(AnnotatedMethod am)
    {
        JsonKeyClass ann = am.getAnnotation(JsonKeyClass.class);
        if (ann != null) {
            Class<?> cls = ann.value();
            if(cls != NoClass.class) {
                return cls;
            }
        }
        return null;
    }

    public Class<?> findContentType(AnnotatedMethod am)
    {
        JsonContentClass ann = am.getAnnotation(JsonContentClass.class);
        if (ann != null) {
            Class<?> cls = ann.value();
            if(cls != NoClass.class) {
                return cls;
            }
        }
        return null;
    }

    /*
    ////////////////////////////////////////////////////
    // Field annotations: general
    ////////////////////////////////////////////////////
     */

    public boolean isIgnorableField(AnnotatedField f)
    {
        JsonIgnore ann = f.getAnnotation(JsonIgnore.class);
        return (ann != null && ann.value());
    }
}
