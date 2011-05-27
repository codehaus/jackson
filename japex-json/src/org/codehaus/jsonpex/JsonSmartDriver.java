package org.codehaus.jsonpex;

import net.minidev.json.JSONValue;

import com.sun.japex.TestCase;

/**
 * Driver for "json-smart", see [http://code.google.com/p/json-smart/]
 */
public class JsonSmartDriver extends BaseJsonDriver
{
    @Override
    public void initializeDriver() { }
    
    @Override
    public void run(TestCase testCase) {
        try {
            // Bleh: they don't even have InputStream parse method...
            // can either use InputStreamReader via ByteArrayInputStream, or construct String
            String input = new String(_inputData, "UTF-8");
            @SuppressWarnings("unused")
            Object ob = JSONValue.parse(input);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
