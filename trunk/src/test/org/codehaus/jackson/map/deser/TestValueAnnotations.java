package org.codehaus.jackson.map.deser;

import main.BaseTest;

import java.io.*;
import java.util.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.annotate.*;
import org.codehaus.jackson.map.*;

/**
 * This unit test suite tests use of "value" Annotations;
 * annotations that define actual type (Class) to use for
 * deserialization.
 */
public class TestValueAnnotations
    extends BaseTest
{
    /*
    //////////////////////////////////////////////
    // Annotated helper classes
    //////////////////////////////////////////////
     */

    /// Class for testing valid {@link JsonClass} annotation
    final static class CollectionHolder
    {
        Collection<String> _strings;

        /* Default for 'Collection' would probably be ArrayList or so;
         * let's try to make it a TreeSet instead.
         */
        @JsonClass(TreeSet.class)
        public void setStrings(Collection<String> s)
        {
            _strings = s;
        }
    }

    /// Another class for testing valid {@link JsonClass} annotation
    final static class MapHolder
    {
        // Let's also coerce numbers into Strings here
        Map<String,String> _data;

        /* Default for 'Collection' would be HashMap,
         * let's try to make it a TreeMap instead.
         */
        @JsonClass(TreeMap.class)
        public void setStrings(Map<String,String> s)
        {
            _data = s;
        }
    }

    /// Another one for {@link JsonClass}, but for arrays
    final static class ArrayHolder
    {
        String[] _strings;

        @JsonClass(String[].class)
        public void setStrings(Object[] o)
        {
            // should be passed instances of proper type, as per annotation
            _strings = (String[]) o;
        }
    }

    /// Class for testing invalid {@link JsonClass} annotation
    final static class BrokenCollectionHolder
    {
        @JsonClass(String.class) // not assignable to Collection
            public void setStrings(Collection<String> s) { }
    }

    /*
    //////////////////////////////////////////////
    // Test methods
    //////////////////////////////////////////////
     */

    public void testOverrideClassValid() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        CollectionHolder result = m.readValue
            ("{ \"strings\" : [ \"test\" ] }", CollectionHolder.class);

        Collection<String> strs = result._strings;
        assertEquals(1, strs.size());
        assertEquals(TreeSet.class, strs.getClass());
        assertEquals("test", strs.iterator().next());
    }

    public void testOverrideMapValid() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        // note: expecting conversion from number to String, as well
        MapHolder result = m.readValue
            ("{ \"strings\" :  { \"a\" : 3 } }", MapHolder.class);

        Map<String,String> strs = result._data;
        assertEquals(1, strs.size());
        assertEquals(TreeMap.class, strs.getClass());
        String value = strs.get("a");
        assertEquals("3", value);
    }

    public void testOverrideArrayClass() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        ArrayHolder result = m.readValue
            ("{ \"strings\" : [ \"test\" ] }", ArrayHolder.class);

        String[] strs = result._strings;
        assertEquals(1, strs.length);
        assertEquals(String[].class, strs.getClass());
        assertEquals("test", strs[0]);
    }

    public void testOverrideClassInvalid() throws Exception
    {
        ObjectMapper m = new ObjectMapper();

        // should fail due to incompatible Annotation
        try {
            BrokenCollectionHolder result = m.readValue
                ("{ \"strings\" : [ ] }", BrokenCollectionHolder.class);
            fail("Expected a failure, but got results: "+result);
        } catch (JsonMappingException jme) { }
    }
}
