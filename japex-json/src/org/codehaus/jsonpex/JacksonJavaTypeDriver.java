package org.codehaus.jsonpex;

import org.codehaus.jackson.map.ObjectMapper;

import com.sun.japex.*;

/**
 * @author Tatu Saloranta
 */
public class JacksonJavaTypeDriver extends BaseJsonDriver
{
    protected ObjectMapper _mapper;
    
    public JacksonJavaTypeDriver() { super(); }

    @Override
    public void initializeDriver() {
        _mapper = new ObjectMapper();
    }   
    
    @Override
    public void run(TestCase testCase) {
        try {
            _inputStream.reset();            
            // By passing Object.class, we'll get Maps/Lists/wrappers:
            Object ob = _mapper.readValue(_inputStream, Object.class);
            _hashCode = ob.hashCode(); // just to get some non-optimizable number
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
