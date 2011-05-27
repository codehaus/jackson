package org.codehaus.jsonpex;

import jsonij.json.*;

import com.sun.japex.TestCase;

public class JsonIJDriver extends BaseJsonDriver
{
    
    @Override public void initializeDriver() { }
    
    @Override
    public void run(TestCase testCase) {
        try {
            // no InputStream or byte[] input?
            String input = new String(_inputData, "UTF-8");
            @SuppressWarnings("unused")
            JSON json = JSON.parse(input);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
