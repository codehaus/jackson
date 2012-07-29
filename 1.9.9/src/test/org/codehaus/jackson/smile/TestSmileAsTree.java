package org.codehaus.jackson.smile;

import org.junit.Assert;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.node.*;

public class TestSmileAsTree extends SmileTestBase
{
    public void testSimple() throws Exception
    {
         // create the serialized JSON with byte array
         ObjectMapper mapper = new ObjectMapper(new SmileFactory());
    
         ObjectNode top1 = mapper.createObjectNode();
         ObjectNode foo1 = top1.putObject("foo");
         foo1.put("bar", "baz");
         final String TEXT =  "Caf\u00e9 1\u20ac";
         final byte[] TEXT_BYTES =  TEXT.getBytes("UTF-8");
         foo1.put("dat", TEXT_BYTES);
    
         byte[] doc = mapper.writeValueAsBytes(top1);
         // now, deserialize
         JsonNode top2 = mapper.readValue(doc, JsonNode.class);
         JsonNode foo2 = top2.get("foo");
         assertEquals("baz", foo2.get("bar").getTextValue());
    
         JsonNode datNode = foo2.get("dat");
         if (!datNode.isBinary()) {
             fail("Expected binary node; got "+datNode.getClass().getName());
         }
         byte[] bytes = datNode.getBinaryValue();
         Assert.assertArrayEquals(TEXT_BYTES, bytes);
     }
}
