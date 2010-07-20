package org.codehaus.jackson.map;

import java.util.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.util.ArrayBuilders;
import org.codehaus.jackson.map.util.ObjectBuffer;

/**
 * Context for deserialization process. Used to allow passing in configuration
 * settings and reusable temporary objects (scrap arrays, containers).
 */
public abstract class DeserializationContext
{
    public abstract JsonParser getParser();

    /*
    //////////////////////////////////////////////////////////////
    // Config methods
    //////////////////////////////////////////////////////////////
    */

    /**
     * Method called during deserialization if Base64 encoded content
     * needs to be decoded. Default version just returns default Jackson
     * uses, which is modified-mime which does not add linefeeds (because
     * those would have to be escaped in Json strings).
     */
    public Base64Variant getBase64Variant() {
        return Base64Variants.getDefaultVariant();
    }

    /*
    //////////////////////////////////////////////////////////////
    // Methods for accessing reusable/recyclable helper objects
    //////////////////////////////////////////////////////////////
    */

    /**
     * Method that can be used to get access to a reusable ObjectBuffer,
     * useful for constructing Object arrays and Lists.
     */
    public abstract ObjectBuffer leaseObjectBuffer();

    public abstract void returnObjectBuffer(ObjectBuffer buf);

    /**
     * Method for accessing object useful for building arrays of
     * primitive types (such as int[]).
     */
    public abstract ArrayBuilders getArrayBuilders();

    /*
    //////////////////////////////////////////////////////////////
    // Parsing methods that may use reusable/-cyclable objects
    //////////////////////////////////////////////////////////////
    */

    public abstract java.util.Date parseDate(String dateStr)
        throws IllegalArgumentException;

    public abstract Calendar constructCalendar(Date d);

    /*
    //////////////////////////////////////////////////////////////
    // Methods for constructing exceptions
    //////////////////////////////////////////////////////////////
    */

    public abstract JsonMappingException mappingException(Class<?> targetClass);
    public abstract JsonMappingException instantiationException(Class<?> instClass, Exception e);
    
    public abstract JsonMappingException weirdStringException(Class<?> instClass, String msg);
    public abstract JsonMappingException weirdNumberException(Class<?> instClass, String msg);

    public abstract JsonMappingException weirdKeyException(Class<?> keyClass, String keyValue, String msg);

    public abstract JsonMappingException unknownFieldException(Object instance, String filedName);
}