package org.codehaus.jsonpex;

import org.stringtree.json.JSONReader;

import com.sun.japex.*;

/**
 * @author cowtowncoder@yahoo.com
 */
public class StringTreeDriver extends BaseJsonDriver
{
    public StringTreeDriver() { super(); }

    @Override
    public void initializeDriver() {
        // No factories for StringTree
    }   
    
    @Override
    public void run(TestCase testCase)
    {
        try {
            // StringTree impl only accepts Strings:
            String input = new String(_inputData, "UTF-8");
            Object ob = new JSONReader().read(input);
            _hashCode = ob.hashCode(); // just to get some non-optimizable number
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
