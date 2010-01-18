package org.codehaus.jackson.map.jsontype.impl;

import org.codehaus.jackson.map.TypeDeserializer;
import org.codehaus.jackson.map.annotate.JsonTypeInfo;

/**
 * @since 1.5
 * @author tatus
 */
public abstract class TypeDeserializerBase extends TypeDeserializer
{
    protected final TypeConverter _typeConverter;
    
    protected TypeDeserializerBase(TypeConverter conv)
    {
        _typeConverter = conv;
    }

    @Override
    public abstract JsonTypeInfo.As getTypeInclusion();
    
    @Override
    public final JsonTypeInfo.Id getTypeId() { return _typeConverter.idType(); }

    // base implementation returns null; ones that use property name need to override
    @Override
    public String propertyName() { return null; }

    /*
     ************************************************************
     * Helper classes
     ************************************************************
      */

     /**
      * Interface used for concrete type information converters
      * that convert Java class to JSON String based type ids.
      */
     public abstract static class TypeConverter {
         /**
          * Method for converting given type identifier into actual
          * class.
          * 
          * @param typeId String serialized type identifier
          *
          * @return Class the type identifier identifies
          * 
          * @throws IllegalArgumentException If no class can be located
          *    for given type id
          */
         public abstract Class<?> typeFromString(String typeId)
             throws IllegalArgumentException;

         public abstract JsonTypeInfo.Id idType();
     }

     /**
      * Converter that produces fully-qualified class names as type ids.
      */
     public final static class ClassNameConverter extends TypeConverter
     {
         public final static ClassNameConverter instance = new ClassNameConverter();

         public JsonTypeInfo.Id idType() { return JsonTypeInfo.Id.CLASS; }

         public Class<?> typeFromString(String typeId)
             throws IllegalArgumentException
         {
             // !!! TBI
             return null;
         }
     }

     /**
      * Converter that produces "minimal" class names (unique suffixes, relative
      * to fully-qualified base class name) as type ids.
      */
     public final static class MinimalClassNameConverter extends TypeConverter
     {
         /**
          * Package name of the base class, to be used for determining common
          * prefix that can be omitted from included type id.
          * Does include the trailing dot.
          */
         protected final String _basePackageName;

         protected MinimalClassNameConverter(Class<?> baseClass)
         {
             String base = baseClass.getName();
             int ix = base.lastIndexOf('.');
             if (ix < 0) { // can this ever occur?
                 _basePackageName = ".";
             } else {
                 _basePackageName = base.substring(0, ix+1);
             }
         }

         public JsonTypeInfo.Id idType() { return JsonTypeInfo.Id.MINIMAL_CLASS; }

         @Override
         public Class<?> typeFromString(String typeId)
             throws IllegalArgumentException
         {
             // !!! TBI
             return null;
         }
     }

     /**
      * Converter that uses externally configured (but simple) mapping
      * from classes to type names. In case of an unrecognized class,
      * will revert to using simple non-qualified class name as
      * type name.
      */
     public final static class TypeNameConverter extends TypeConverter
     {
         public JsonTypeInfo.Id idType() { return JsonTypeInfo.Id.NAME; }

         // !!! @TODO
         @Override
         public Class<?> typeFromString(String typeId)
             throws IllegalArgumentException
         {
             // !!! TBI
             return null;
         }
     }    
}
