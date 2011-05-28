package org.codehaus.jsonpex;

import java.io.IOException;

import org.codehaus.jackson.*;

import com.sun.japex.*;

/**
 * Test driver for accessing JSON via "raw" Jackson streaming
 * API. All data is accessed (to avoid favoring skip-through pattern,
 * which can not be used by tree model parsers), but the most
 * efficient accessors are used all event types.
 *
 * @author Santiago.PericasGeertsen@sun.com
 * @author Tatu Saloranta (cowtowncoder@yahoo.com)
 */
public class JacksonManualDriver extends BaseJsonDriver
{
    protected JsonFactory _jsonFactory;
    
    public JacksonManualDriver() { super(); }

    @Override
    public void initializeDriver()
    {
        try {
            _jsonFactory = new JsonFactory();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }   
    
    @Override
    public void run(TestCase testCase)
    {
        try {
            _runManual();
        } catch (Exception e) {
            Throwable t = e;
            while (t.getCause() != null) {
                t = t.getCause();
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new RuntimeException(t);
        }
    }
        
    private final void _runManual() throws IOException
    {
        _inputStream.reset();            
        
        // Parser could be created in the prepare phase too
        JsonParser jp = _jsonFactory.createJsonParser(_inputStream);
        int total  = 0;
        JsonToken t;

        /* Let's exercise enough accessors to ensure all data is
         * processed; values themselves are irrelevant.
         */
        while ((t = jp.nextToken()) != null) {
            switch (t) {
            case FIELD_NAME:
                {
                    String str = jp.getText();
                    total += str.length();
                }
                break;
            case VALUE_STRING: // ensure all text is parsed
                {
                    String str = jp.getText();
                    //char[] chars = jp.getTextCharacters();
                    /*
                    int offset = jp.getTextOffset();
                    int len = jp.getTextLength();
                    total += offset + len;
                    */
                    total += str.length();
                }
                break;
            case VALUE_NUMBER_INT:
                total += jp.getIntValue();
                break;
            case VALUE_NUMBER_FLOAT:
                total += (int) jp.getDoubleValue();
                break;

            case VALUE_TRUE:
                total += 1;
                break;
            case VALUE_FALSE:
                total -= 1;
                break;
            case VALUE_NULL:
                ++total;
                break;
            }
            ;
        }
        jp.close();
        _hashCode = total; // just to get some non-optimizable number
    }
}
