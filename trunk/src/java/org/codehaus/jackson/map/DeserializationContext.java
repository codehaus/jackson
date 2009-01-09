package org.codehaus.jackson.map;

import java.util.*;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.util.ObjectBuffer;

/**
 * Context for deserialization process. Used to allow passing in configuration
 * settings and reusable temporary objects (scrap arrays, containers).
 */
public abstract class DeserializationContext
{
    public abstract JsonParser getParser();

    /*
    //////////////////////////////////////////////////////////////
    // Methods for accessing reusable/recyclable helper objects
    //////////////////////////////////////////////////////////////
    */

    /**
     * Method that can be used to get access to a reusable 
     */
    public abstract ObjectBuffer leaseObjectBuffer();

    public abstract void returnObjectBuffer(ObjectBuffer buf);

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
