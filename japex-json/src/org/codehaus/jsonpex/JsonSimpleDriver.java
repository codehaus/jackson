package org.codehaus.jsonpex;

import org.json.simple.JSONValue;

import com.sun.japex.*;

/**
 * @author Tatu Saloranta
 */
public class JsonSimpleDriver extends BaseJsonDriver
{
    @Override public void initializeDriver() { }
    
    @Override
    public void run(TestCase testCase) {
        try {
            // Bleh: they don't even have InputStream parse method...
            String input = new String(_inputData, "UTF-8");
            @SuppressWarnings("unused")
            Object ob = JSONValue.parse(input);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
