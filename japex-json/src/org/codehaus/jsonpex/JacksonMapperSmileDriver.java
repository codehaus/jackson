package org.codehaus.jsonpex;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.smile.SmileFactory;

import com.sun.japex.TestCase;

public class JacksonMapperSmileDriver
    extends JacksonManualSmileDriver
{
    protected ObjectMapper _mapper;
    
    @Override
    public void initializeDriver()
    {
        _mapper = new ObjectMapper(new SmileFactory());
    }   

    // alas, need to copy frmo JacksonJavaTypeDriver (we need JSON->Smile converter from JacksonManualSmileDriver)
    @Override
    public void run(TestCase testCase) {
        try {
            _inputStream.reset();            
            // By passing Object.class, we'll get Maps/Lists/wrappers:
            Object ob = _mapper.readValue(_inputStream, Object.class);
            _hashCode = ob.hashCode(); // just to get some non-optimizable number
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
    
}
