package test;

import java.io.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;

public class TestJavaMapper
{
    private TestJavaMapper() { }

    public static void main(String[] args)
        throws Exception
    {
        if (args.length != 1) {
            System.err.println("Usage: java test.TestJavaMapper <file>");
            System.exit(1);
        }
        FileInputStream in = new FileInputStream(new File(args[0]));
        JsonFactory f = new JsonFactory();
        JsonParser jp = f.createJsonParser(in);
        JavaTypeMapper jmap = new JavaTypeMapper();
        Object result = jmap.read(jp);
        jp.close();
        System.out.println("Read result ("+(result.getClass())+"): <"+result+">");

        StringWriter sw = new StringWriter();
        JsonGenerator jg = f.createJsonGenerator(sw);
        try {
            jmap.writeValue(jg, result);
        } catch (Exception e) {
            try { jg.flush(); } catch (IOException ioe) { }
            System.err.println("Error, intermediate result = |"+sw+"|");
            throw e;
        }
        jg.close();

        System.out.println("Write result: <"+sw.toString()+">");
    }
}

