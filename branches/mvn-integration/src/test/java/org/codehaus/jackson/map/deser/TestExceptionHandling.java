package org.codehaus.jackson.map.deser;

import java.io.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;

/**
 * Unit test for verifying that exceptions are properly handled (caught,
 * re-thrown or wrapped, depending)
 * with Object deserialization.
 */
public class TestExceptionHandling
    extends BaseMapTest
{
    public void testExceptionWithIncomplete()
        throws Exception
    {
        BrokenStringReader r = new BrokenStringReader("[ 1, ", "TEST");
        JsonFactory f = new JsonFactory();
        JsonParser jp = f.createJsonParser(r);
        ObjectMapper mapper = new ObjectMapper();
        try {
            Object ob = mapper.readValue(jp, Object.class);
            fail("Should have gotten an exception");
        } catch (IOException e) {
            verifyException(e, IOException.class, "TEST");
        }
        /* Would be good to test state, but since IOException occurs
         * at the end of content, parser is not to clear the state
         */
    }

    public void testExceptionWithEOF()
        throws Exception
    {
        StringReader r = new StringReader("  3");
        JsonFactory f = new JsonFactory();
        JsonParser jp = f.createJsonParser(r);
        ObjectMapper mapper = new ObjectMapper();

        Integer I = mapper.readValue(jp, Integer.class);
        assertEquals(3, I.intValue());

        // and then end-of-input...
        try {
            I = mapper.readValue(jp, Integer.class);
            fail("Should have gotten an exception");
        } catch (IOException e) {
            verifyException(e, JsonMappingException.class, "No content available");
        }
        // also: should have no current token after end-of-input
        JsonToken t = jp.getCurrentToken();
        if (t != null) {
            fail("Expected current token to be null after end-of-stream, was: "+t);
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////////////////
     */

    void verifyException(Exception e, Class<?> expType, String expMsg)
        throws Exception
    {
        if (e.getClass() != expType) {
            fail("Expected exception of type "+expType.getName()+", got "+e.getClass().getName());
        }
        if (expMsg != null) {
            verifyException(e, expMsg);
        }
    }
}