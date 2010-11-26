package org.codehaus.jackson.xml;

import java.io.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.ObjectMapper;

public class TestXmlParser extends main.BaseTest
{
    /*
    /**********************************************************
    /* Some sample documents:
    /**********************************************************
     */


    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */
    
    public void testSimplest() throws Exception
    {
        assertEquals("{\"leaf\":\"abc\"}",
                _readXmlWriteJson("<root><leaf>abc</leaf></root>"));
    }

    public void testSimpleWithEmpty() throws Exception
    {
        assertEquals("{\"leaf\":null}",
                _readXmlWriteJson("<root><leaf /></root>"));
    }

    public void testSimpleNested() throws Exception
    {
        assertEquals("{\"a\":{\"b\":{\"c\":\"xyz\"}}}",
                _readXmlWriteJson("<root><a><b><c>xyz</c></b></a></root>"));
    }

    /**
     * Unit test that verifies that we can write sample document from JSON
     * specification as XML, and read it back in "as JSON", with
     * expected transformation.
     */
    public void testRoundTripWithSample() throws Exception
    {
        // First: let's convert from sample JSON doc to default xml output
        JsonNode root = new ObjectMapper().readTree(SAMPLE_DOC_JSON_SPEC);
        XmlFactory xf = new XmlFactory();
        String xml = new ObjectMapper(xf).writeValueAsString(root);

        /* Here we would ideally use base class test method. Alas, it won't
         * work due to couple of problems;
         * (a) All values are reported as Strings (not ints, for example
         * (b) XML mangles arrays, so all we see are objects.
         * Former could be worked around; latter less so at this point.
         */

        // So, for now, let's just do sort of minimal verification, manually
        JsonParser jp = xf.createJsonParser(xml);
        
        assertToken(JsonToken.START_OBJECT, jp.nextToken()); // main object

        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Image'
        verifyFieldName(jp, "Image");
        assertToken(JsonToken.START_OBJECT, jp.nextToken()); // 'image' object
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Width'
        verifyFieldName(jp, "Width");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(String.valueOf(SAMPLE_SPEC_VALUE_WIDTH), jp.getText());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Height'
        verifyFieldName(jp, "Height");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(String.valueOf(SAMPLE_SPEC_VALUE_HEIGHT), jp.getText());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Title'
        verifyFieldName(jp, "Title");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(SAMPLE_SPEC_VALUE_TITLE, getAndVerifyText(jp));
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Thumbnail'
        verifyFieldName(jp, "Thumbnail");
        assertToken(JsonToken.START_OBJECT, jp.nextToken()); // 'thumbnail' object
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Url'
        verifyFieldName(jp, "Url");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(SAMPLE_SPEC_VALUE_TN_URL, getAndVerifyText(jp));
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Height'
        verifyFieldName(jp, "Height");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(String.valueOf(SAMPLE_SPEC_VALUE_TN_HEIGHT), jp.getText());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Width'
        verifyFieldName(jp, "Width");
        // Width value is actually a String in the example
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(SAMPLE_SPEC_VALUE_TN_WIDTH, getAndVerifyText(jp));

        assertToken(JsonToken.END_OBJECT, jp.nextToken()); // 'thumbnail' object
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'IDs'

        assertToken(JsonToken.START_OBJECT, jp.nextToken()); // 'ids' array

        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Height'
        verifyFieldName(jp, "IDs");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(String.valueOf(SAMPLE_SPEC_VALUE_TN_ID1), getAndVerifyText(jp));
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Height'
        verifyFieldName(jp, "IDs");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(String.valueOf(SAMPLE_SPEC_VALUE_TN_ID2), getAndVerifyText(jp));
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Height'
        verifyFieldName(jp, "IDs");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(String.valueOf(SAMPLE_SPEC_VALUE_TN_ID3), getAndVerifyText(jp));
        assertToken(JsonToken.FIELD_NAME, jp.nextToken()); // 'Height'
        verifyFieldName(jp, "IDs");
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        assertEquals(String.valueOf(SAMPLE_SPEC_VALUE_TN_ID4), getAndVerifyText(jp));
        assertToken(JsonToken.END_OBJECT, jp.nextToken()); // 'ids' array

        assertToken(JsonToken.END_OBJECT, jp.nextToken()); // 'image' object

        assertToken(JsonToken.END_OBJECT, jp.nextToken()); // main object
        
        jp.close();
    }

    /**
     * Unit test to ensure that we can succesfully also roundtrip
     * example Bean used in Jackson tutorial
     */
    public void testRoundTripWithJacksonExample() throws Exception
    {
        FiveMinuteUser user = new FiveMinuteUser("Joe", "Sixpack",
                true, FiveMinuteUser.Gender.MALE, new byte[] { 1, 2, 3 , 4, 5 });
        XmlFactory xf = new XmlFactory();
        ObjectMapper mapper = new ObjectMapper(xf);
        String xml = mapper.writeValueAsString(user);
        FiveMinuteUser result = mapper.readValue(xml, FiveMinuteUser.class);
        assertEquals(user, result);
    }
        
    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private String _readXmlWriteJson(String xml) throws IOException
    {
        JsonFactory jf = new JsonFactory();
        XmlFactory xf = new XmlFactory();
        StringWriter w = new StringWriter();

        JsonParser jp = xf.createJsonParser(xml);
        JsonGenerator jg = jf.createJsonGenerator(w);
        while (jp.nextToken() != null) {
            jg.copyCurrentEvent(jp);
        }
        jg.close();
        return w.toString();
    }
}
