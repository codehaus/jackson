package org.codehaus.jackson.main;

import java.io.IOException;

import org.codehaus.jackson.*;

public class TestUnicode extends main.BaseTest
{
    public void testSurrogates() throws Exception
    {
        JsonFactory f = new JsonFactory();
        _testSurrogates(f, true);
        _testSurrogates(f, false);
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    private void _testSurrogates(JsonFactory f, boolean checkText) throws IOException
    {
        byte[] json = "{\"text\":\"\uD83D\uDE03\"}".getBytes("UTF-8");
        // first
        JsonParser jp = f.createJsonParser(json);
        assertToken(JsonToken.START_OBJECT, jp.nextToken());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        if (checkText) {
            assertEquals("text", jp.getText());
        }
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        if (checkText) {
            assertEquals("\uD83D\uDE03", jp.getText());
        }
        assertToken(JsonToken.END_OBJECT, jp.nextToken());
    }
}
