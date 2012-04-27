package org.codehaus.jackson.smile;

import java.io.IOException;

import org.codehaus.jackson.JsonToken;

public class TestSmileParserUnicode extends SmileTestBase
{
    // [Issue-2]: probs with Surrogate handling
    public void testLongUnicodeWithSurrogates() throws IOException
    {
        final String SURROGATE_CHARS = "\ud834\udd1e";
        StringBuilder sb = new StringBuilder(300);
        while (sb.length() < 300) {
            sb.append(SURROGATE_CHARS);
        }
        final String TEXT = sb.toString();
        byte[] data = _smileDoc(quote(TEXT));

        SmileParser p = _smileParser(data);
        assertNull(p.getCurrentToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(TEXT, p.getText());
        assertNull(p.nextToken());
    }

}
