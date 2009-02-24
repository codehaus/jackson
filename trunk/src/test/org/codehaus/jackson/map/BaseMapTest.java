package org.codehaus.jackson.map;

import java.io.*;
import java.util.*;

import main.BaseTest;
import org.codehaus.jackson.map.*;

public abstract class BaseMapTest
    extends BaseTest
{
    protected BaseMapTest() { super(); }

    /*
    //////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////
     */

    @SuppressWarnings("unchecked")
    protected Map<String,Object> writeAndMap(ObjectMapper m, Object value)
        throws IOException
    {
        StringWriter sw = new StringWriter();
        m.writeValue(sw, value);
        return (Map<String,Object>) m.readValue(sw.toString(), Object.class);
    }
}



