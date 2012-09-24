package org.codehaus.jackson.map.jsontype;

import org.codehaus.jackson.annotate.*;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.annotate.JsonDeserialize;

public class TestNoTypeInfo extends BaseMapTest
{
    @JsonTypeInfo(use=JsonTypeInfo.Id.NONE)
    @JsonDeserialize(as=NoType.class)
    private static interface NoTypeInterface {
    }
    
    private final static class NoType implements NoTypeInterface {
        public int a = 3;
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // for [JACKSON-746]
    public void testWithIdNone() throws Exception
    {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTyping();
        // serialize without type info
        String json = mapper.writeValueAsString(new NoType());
        assertEquals("{\"a\":3}", json);

        // and deserialize successfully
        NoTypeInterface bean = mapper.readValue("{\"a\":6}", NoTypeInterface.class);
        assertNotNull(bean);
        NoType impl = (NoType) bean;
        assertEquals(6, impl.a);
    }
}
