package org.codehaus.jackson.map.deser;

import java.util.List;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.type.TypeReference;

import org.codehaus.jackson.map.*;

public class TestGenericSubTyping extends BaseMapTest
{
    // Types for [JACKSON-778]
    
    static class Document {}
    static class Row {}
    static class RowWithDoc<D extends Document> extends Row {
        @JsonProperty("d") D d;
    }
    static class ResultSet<R extends Row> {
        @JsonProperty("rows") List<R> rows;
    }
    static class ResultSetWithDoc<D extends Document> extends ResultSet<RowWithDoc<D>> {}

    static class MyDoc extends Document {}

    /*
    /*******************************************************
    /* Unit tests
    /*******************************************************
     */
    
    public void testIssue778() throws Exception
    {
        final ObjectMapper mapper = new ObjectMapper();
        String json = "{\"rows\":[{\"d\":{}}]}";

        final TypeReference<?> type = new TypeReference<ResultSetWithDoc<MyDoc>>() {};
        
        // type passed is correct, but somehow it gets mangled when passed...
        ResultSetWithDoc<MyDoc> rs = mapper.readValue(json, type);
        Document d = rs.rows.iterator().next().d;
    
        assertEquals(MyDoc.class, d.getClass()); //expected MyDoc but was Document
    }    
}
