package org.codehaus.jackson.map;

import org.codehaus.jackson.JsonParser;

/**
 * Context for deserialization process. Used to allow passing in configuration
 * settings and reusable temporary objects (scrap arrays, containers).
 */
public abstract class DeserializationContext
{
	public abstract JsonParser getParser();
	
    // // Methods for constructing exceptions

    public abstract JsonMappingException mappingException(Class<?> targetClass);
    public abstract JsonMappingException instantiationException(Class<?> instClass, Exception e);
    
    public abstract JsonMappingException weirdStringException(Class<?> instClass, String msg);
    public abstract JsonMappingException weirdNumberException(Class<?> instClass, String msg);
}
