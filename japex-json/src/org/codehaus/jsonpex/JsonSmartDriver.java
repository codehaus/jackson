package org.codehaus.jsonpex;

import java.io.File;
import java.io.FileInputStream;

import net.minidev.json.JSONValue;

import com.sun.japex.JapexDriverBase;
import com.sun.japex.TestCase;
import com.sun.japex.Util;

/**
 * Driver for "json-smart", see [http://code.google.com/p/json-smart/]
 */
public class JsonSmartDriver extends JapexDriverBase
{
    int mHashCode;
    byte[] mInputData;
    
    public JsonSmartDriver() { }

    @Override
    public void initializeDriver() { }
    
    @Override
    public void prepare(TestCase testCase) {
        String xmlFile = testCase.getParam("japex.inputFile");
        
        if (xmlFile == null) {
            throw new RuntimeException("japex.inputFile not specified");
        }
        try {
            // Load file to factor out I/O
            mInputData = Util.streamToByteArray(new FileInputStream(new File(xmlFile)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    
    @Override
    public void warmup(TestCase testCase) {
        run(testCase);
    }
    
    @Override
    public void run(TestCase testCase) {
        try {
            // Bleh: they don't even have InputStream parse method...
            // can either use InputStreamReader via ByteArrayInputStream, or construct String
            String input = new String(mInputData, "UTF-8");
            Object ob = JSONValue.parse(input);
            mHashCode = ob.hashCode(); // just to get some non-optimizable number
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void finish(TestCase testCase) {
        // Set file size in KB on X axis
        testCase.setDoubleParam("japex.resultValueX", mInputData.length / 1024.0);
        getTestSuite().setParam("japex.resultUnitX", "KB");
    }
    
}
