package org.codehaus.jackson.map.ser;

import java.io.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

public class TestNullSerialization
    extends BaseMapTest
{
    static class NullSerializer extends JsonSerializer<Object>
    {
        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonProcessingException
        {
            jgen.writeString("foobar");
        }
    }

    static class NullBean {
        public String value = null;
        
        public NullBean(String str) { value = str; }
    }
    
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */
    
    public void testSimple() throws Exception
    {
        assertEquals("null", new ObjectMapper().writeValueAsString(null));
    }

    public void testCustom() throws Exception
    {
        StdSerializerProvider sp = new StdSerializerProvider();
        sp.setNullValueSerializer(new NullSerializer());
        ObjectMapper m = new ObjectMapper();
        m.setSerializerProvider(sp);
        assertEquals("\"foobar\"", m.writeValueAsString(null));
    }

    public void testDefaultNonNull() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getSerializationConfig().setSerializationInclusion(Inclusion.NON_NULL);
        assertEquals("{\"value\":\"abc\"}", mapper.writeValueAsString(new NullBean("abc")));
        assertEquals("{}", mapper.writeValueAsString(new NullBean(null)));
    }
}
