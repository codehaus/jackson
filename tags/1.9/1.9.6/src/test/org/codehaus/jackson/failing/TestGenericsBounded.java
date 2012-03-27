package org.codehaus.jackson.failing;

import java.util.*;

import org.codehaus.jackson.map.*;

public class TestGenericsBounded
    extends BaseMapTest
{
    protected static abstract class Base<T> {
        public T inconsequential = null;
    }

    protected static abstract class BaseData<T> {
        public T dataObj;
    }
   
    protected static class Child extends Base<Long> {
        public static class ChildData extends BaseData<List<String>> { }
    }

    /*
    /*******************************************************
    /* Unit tests
    /*******************************************************
     */

    // Reproducing issue 743
    public void testIssue743() throws Exception
    {
        String s3 = "{\"dataObj\" : [ \"one\", \"two\", \"three\" ] }";
        ObjectMapper m = new ObjectMapper();
   
        Child.ChildData d = m.readValue(s3, Child.ChildData.class);
        assertNotNull(d.dataObj);
        assertEquals(3, d.dataObj.size());
    }
}
