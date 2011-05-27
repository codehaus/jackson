package org.codehaus.jsonpex;

import java.io.InputStreamReader;

import com.sun.japex.TestCase;

import com.google.gson.stream.*;

public class GsonDriver extends BaseJsonDriver
{
    protected int _count;
    
    public GsonDriver() { }

    @Override public void initializeDriver() { }
    
    @Override
    public void run(TestCase testCase)
    {
        _count = 0;
        try {
            _inputStream.reset();            
            JsonReader reader = new JsonReader(new InputStreamReader(_inputStream, "UTF-8"));

            while (true) {
                JsonToken token = reader.peek();
                ++_count;
                switch (token) {
                case BEGIN_ARRAY:
                    reader.beginArray();
                    break;
                case END_ARRAY:
                    reader.endArray();
                    break;
                case BEGIN_OBJECT:
                    reader.beginObject();
                    break;
                case END_OBJECT:
                    reader.endObject();
                    break;
                case NAME:
                    String name = reader.nextName();
                    _count += name.length();
                    break;
                case STRING:
                    String s = reader.nextString();
                    _count += s.length();
                    break;
                case NUMBER:
                    // no distinction between float, int; can't easily access...
                    //_count += reader.nextInt();
                    reader.skipValue();
                    break;
                case BOOLEAN:
                    _count += (reader.nextBoolean() ? 2 : 1);
                    break;
                case NULL:
                    reader.nextNull();
                    break;
                case END_DOCUMENT:
                    return;
                default:
                    throw new RuntimeException("Unrecognized type: "+token);
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
