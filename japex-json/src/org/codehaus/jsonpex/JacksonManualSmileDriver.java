package org.codehaus.jsonpex;

import java.io.ByteArrayInputStream;

import com.sun.japex.TestCase;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.smile.SmileFactory;

/**
 * Variant that uses raw JsonParser that handles "Smile" format
 * 
 * @author tatu
 */
public class JacksonManualSmileDriver
    extends JacksonDriver
{
    @Override
    public void initializeDriver()
    {
        try {
            _jsonFactory = new SmileFactory();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }   

    // One more thing: convert from JSON to Binary Smile format
    @Override
    public void prepare(TestCase testCase)
    {
        super.prepare(testCase);
        ObjectMapper plainMapper = new ObjectMapper();
        ObjectMapper smileMapper = new ObjectMapper(new SmileFactory());
        int origLen = _dataLen;
        try {
            Object ob = plainMapper.readValue(_inputData, 0, origLen, Object.class);
            _inputData = smileMapper.writeValueAsBytes(ob);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not convert from JSON to Smile format: "+e.getMessage(), e);
        }
        _dataLen = _inputData.length;
        _inputStream = new ByteArrayInputStream(_inputData);
    }
    
}
