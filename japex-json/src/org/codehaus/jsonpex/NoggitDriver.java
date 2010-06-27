package org.codehaus.jsonpex;

import java.io.*;

import org.apache.noggit.JSONParser;
import org.apache.noggit.CharArr;

import com.sun.japex.*;

/**
 * Test driver for accessing JSON via streaming API of Noggit parser.
 * All data is accessed (to ensure proper processing is done, which is
 * especially important for floating point numbers), but the most
 * efficient accessors are used all event types.
 *
 * @author Tatu Saloranta (cowtowncoder@yahoo.com)
 * @author Santiago.PericasGeertsen@sun.com
 */
public class NoggitDriver extends BaseJsonDriver
{
    int mBufferLen;

    public NoggitDriver() { super(); }

    @Override
    public void initializeDriver() {
    }   
    
    @Override
    public void run(TestCase testCase)
    {
        try {
            _inputStream.reset();           
            int total = 0;
            /* Let's use fixed buffer, except for cases where that'd be
             * excessive. 4k memory seems reasonable (== 2000 chars)
             */
            char[] buf = new char[Math.min(_dataLen, 2000)];

            JSONParser jp = new JSONParser(new InputStreamReader(_inputStream, "UTF-8"), buf);
            int t;
            CharArr chars = new CharArr();

            while ((t = jp.nextEvent()) != JSONParser.EOF) {
                switch (t) {
                case JSONParser.STRING:
                    jp.getString(chars);
                    total += chars.size();
                    break;
                case JSONParser.LONG:
                    total += (int) jp.getLong();
                    break;
                case JSONParser.NUMBER:
                    total += (int) jp.getDouble();
                    break;
                case JSONParser.BIGNUMBER:
                    // Hmmmh... shouldn't get it... but:
                    total += (int) jp.getDouble();
                    break;
                case JSONParser.BOOLEAN:
                    if (jp.getBoolean()) {
                        ++total;
                    }
                    break;
                case JSONParser.NULL:
                    ++total;
                    break;
                }
            }
            _hashCode = total; // just to get some non-optimizable number
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
