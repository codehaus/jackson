package org.codehaus.jackson.map.ser;

import org.codehaus.jackson.map.BaseMapTest;

import java.util.*;

import org.codehaus.jackson.annotate.*;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * This unit test suite tests use of @JsonClass Annotation
 * with bean serialization.
 */
public class TestAnnotationJsonSerialize
    extends BaseMapTest
{
    /*
    //////////////////////////////////////////////
    // Annotated helper classes
    //////////////////////////////////////////////
     */

    interface ValueInterface {
        public int getX();
    }

    static class ValueClass
        implements ValueInterface
    {
        public int getX() { return 3; }
        public int getY() { return 5; }
    }

    static class WrapperClass
    {
        @JsonSerialize(as=ValueInterface.class)
        public ValueClass getValue() {
            return new ValueClass();
        }
    }

    /**
     * Test bean that has an invalid {@link JsonClass} annotation.
     */
    static class BrokenClass
    {
        // invalid annotation: String not a supertype of Long
        @JsonSerialize(as=String.class)
        public Long getValue() {
            return Long.valueOf(4L);
        }
    }

    /*
    //////////////////////////////////////////////
    // Main tests
    //////////////////////////////////////////////
     */

    @SuppressWarnings("unchecked")
	public void testSimpleValueDefinition() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        Map<String,Object> result = writeAndMap(m, new WrapperClass());
        assertEquals(1, result.size());
        Object ob = result.get("value");
        // Should see only "x", not "y"
        result = (Map<String,Object>) ob;
        assertEquals(1, result.size());
        assertEquals(Integer.valueOf(3), result.get("x"));
    }

    public void testBrokenAnnotation() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        try {
            serializeAsString(m, new BrokenClass());
        } catch (Exception e) {
            verifyException(e, "not a super-type of");
        }
    }

}
