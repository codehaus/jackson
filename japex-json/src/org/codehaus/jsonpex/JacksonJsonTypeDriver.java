package org.codehaus.jsonpex;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;

import com.sun.japex.*;

/**
 * @author Tatu Saloranta
 */
public class JacksonJsonTypeDriver extends BaseJsonDriver
{
    protected ObjectMapper _mapper;
    
    public JacksonJsonTypeDriver() { super(); }

    @Override
    public void initializeDriver() {
        _mapper = new ObjectMapper();
    }   
    
    @Override
    public void run(TestCase testCase)
    {
        try {
            _inputStream.reset();            
            // Parser could be created in the prepare phase too
            JsonNode root = _mapper.readTree(_inputStream);
            _hashCode = root.hashCode(); // just to get some non-optimizable number
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
