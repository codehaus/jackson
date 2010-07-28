package org.codehaus.jsonpex;

import java.io.*;

import com.sun.japex.*;

import org.codehaus.jackson.*;

/**
 * Shared base class for driver implementations
 *
 * @author tatus (cowtowncoder@yahoo.com)
 * @author Santiago.PericasGeertsen@sun.com
 */
public class BaseJsonDriver extends JapexDriverBase
{
    protected ByteArrayInputStream _inputStream;

    protected byte[] _inputData;
    protected int _dataLen;
    
    protected int _hashCode;

    public BaseJsonDriver() { }
    
    @Override
    public void prepare(TestCase testCase) {
        String xmlFile = testCase.getParam("japex.inputFile");
        
        if (xmlFile == null) {
            throw new RuntimeException("japex.inputFile not specified");
        }
        
        try {
            // Load XML file to factor out I/O
            _inputData = Util.streamToByteArray(new FileInputStream(new File(xmlFile)));
            // also, squeeze out white space, unnecessary quoting
            _inputData = removeWhitespace(_inputData);
            _dataLen = _inputData.length;
            _inputStream = new ByteArrayInputStream(_inputData);
        }        
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void warmup(TestCase testCase) {
        run(testCase);
    }
    
    @Override
    public void finish(TestCase testCase) {
        // Set file size in KB on X axis
        _inputStream.reset();
        testCase.setDoubleParam("japex.resultValueX", 
                _inputStream.available() / 1024.0);
        getTestSuite().setParam("japex.resultUnitX", "KB");

        /* 30-Sep-2007, tatus: Let's measure throughput in MBps,
         *   instead of tps
         */
        //getTestSuite().setParam("japex.resultUnit", "tps");
        getTestSuite().setParam("japex.resultUnit", "mbps");
    }

    private byte[] removeWhitespace(byte[] data) throws IOException
    {
        JsonFactory f = new JsonFactory();
        JsonParser jp = f.createJsonParser(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        JsonGenerator jg = f.createJsonGenerator(out, JsonEncoding.UTF8);

        while (jp.nextToken() != null) {
            jg.copyCurrentEvent(jp);
        }
        jp.close();
        jg.close();
        return out.toByteArray();
    }    
}
