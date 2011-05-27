package org.codehaus.jsonpex;

import org.json.*;

import com.sun.japex.*;

/**
 * @author Santiago.PericasGeertsen@sun.com
 * @author Tatu Saloranta
 */
public class JsonOrgDriver extends BaseJsonDriver
{
    public JsonOrgDriver() { }

    @Override public void initializeDriver() { }

    @Override
    public void run(TestCase testCase) {
        try {
            String input = new String(_inputData, "UTF-8");
            JSONTokener tok = new JSONTokener(input);
            @SuppressWarnings("unused")
            Object ob = tok.nextValue();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
