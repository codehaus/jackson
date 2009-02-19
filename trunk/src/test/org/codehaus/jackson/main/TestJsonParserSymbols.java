package org.codehaus.jackson.main;

import java.io.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.impl.*;

/**
 * Unit tests for verifying that {@link JsonParser} instances properly
 * merge back symbols to the root symbol table
 */
public class TestJsonParserSymbols
    extends main.BaseTest
{
    final static String JSON = "{ \"a\" : 3, \"aaa\" : 4, \"_a\" : 0 }";

    public void testByteSymbolsWithClose() throws Exception
    {
        _testWithClose(true);
    }

    public void testByteSymbolsWithEOF() throws Exception
    {
        MyJsonFactory f = new MyJsonFactory();
        assertEquals(0, f.byteSymbolCount());
        JsonParser jp = f.createJsonParser(JSON.getBytes("UTF-8"));
        assertEquals(Utf8StreamParser.class, jp.getClass());
        while (jp.nextToken() != null) {
            // shouldn't update before hitting end
            assertEquals(0, f.byteSymbolCount());
        }
        // but now should have it after hitting EOF
        assertEquals(3, f.byteSymbolCount());
        jp.close();
        assertEquals(3, f.byteSymbolCount());
    }

    public void testCharSymbolsWithClose() throws Exception
    {
        _testWithClose(false);
    }

    public void testCharSymbolsWithEOF() throws Exception
    {
        MyJsonFactory f = new MyJsonFactory();
        assertEquals(0, f.charSymbolCount());
        JsonParser jp = f.createJsonParser(JSON);
        assertEquals(ReaderBasedParser.class, jp.getClass());
        while (jp.nextToken() != null) {
            // shouldn't update before hitting end
            assertEquals(0, f.charSymbolCount());
        }
        // but now should have it
        assertEquals(3, f.charSymbolCount());
        jp.close();
        assertEquals(3, f.charSymbolCount());
    }

    /*
    ////////////////////////////////////
    // Helper methods
    ////////////////////////////////////
     */

    private void _testWithClose(boolean useBytes) throws Exception
    {
        MyJsonFactory f = new MyJsonFactory();
        assertEquals(0, useBytes ? f.byteSymbolCount() : f.charSymbolCount());

        JsonParser jp = useBytes
            ? f.createJsonParser(JSON.getBytes("UTF-8"))
            : f.createJsonParser(JSON);
        assertEquals(Utf8StreamParser.class, jp.getClass());

        // Let's check 2 names
        assertToken(JsonToken.START_OBJECT, jp.nextToken());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken());
        assertToken(JsonToken.VALUE_NUMBER_INT, jp.nextToken());
        assertToken(JsonToken.FIELD_NAME, jp.nextToken());

        // shouldn't update before close or EOF:
        assertEquals(0, useBytes ? f.byteSymbolCount() : f.charSymbolCount());
        jp.close();
        // but should after close
        assertEquals(2, useBytes ? f.byteSymbolCount() : f.charSymbolCount());
    }

    /*
    ////////////////////////////////////
    // Helper classes
    ////////////////////////////////////
     */

    /**
     * To peek into state of "root" symbol tables (parent of all symbol
     * tables for parsers constructed by this factory) we need to
     * add some methods.
     */
    final static class MyJsonFactory extends JsonFactory
    {
        public int byteSymbolCount() { return _rootByteSymbols.size(); }
        public int charSymbolCount() { return _rootCharSymbols.size(); }
    }
}
