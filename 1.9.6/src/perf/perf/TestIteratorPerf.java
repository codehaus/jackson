package perf;

import java.io.*;
import java.util.Iterator;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.type.JavaType;

/* Simple micro-benchmark for comparing speed of various
 * methods of iterating over a sequence of beans
 */
public class TestIteratorPerf
{
    static class Bean
    {
        public int id;
        public int b, c, d;
//        public String name;
        public boolean active;
        public int extra;

        public Bean() { }
        public Bean(int i, String n, boolean a) {
            id = i;
//            name = n;
            active = a;
            extra = 100 * i;
            
            b = i - 3;
            c = i * 7;
            d = b + c;
        }
    }

    private final static int REPS = 5000;

    private final ObjectMapper _mapper = new ObjectMapper();

    private final JavaType TYPE;
    
    private TestIteratorPerf() {
        TYPE = _mapper.constructType(Bean.class);
    }
    
    public void test() throws Exception
    {
        int i = 0;
        int sum = 0;
        int round = 0;

        byte[] seq = generateSequence();
    
        while (true) {
            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }
    
            ++i;
            round = i % 3;

//            if (true) round = 1;
            
            long curr = System.currentTimeMillis();
            String msg;
            boolean lf = (round == 0);
    
            switch (round) {
    
            case 0:
                msg = "Jackson, vanilla";
                sum = testJacksonStream(REPS, seq);
                break;
            case 1:
                msg = "Jackson, iterator";
                sum = testJacksonIterator(REPS, seq);
                break;
            case 2:
                msg = "Jackson, iterator+updating";
                sum = testJacksonUpdating(REPS, seq);
                break;
            default:
                throw new Error("Internal error");
            }
            curr = System.currentTimeMillis() - curr;
            if (lf) {
                System.out.println();
            }
            System.out.println("Test '"+msg+"' -> "+curr+" msecs"
                +"("+(sum & 0xFF)+")"
                                );
        }
    }

    private int testJacksonStream(int reps, byte[] data) throws Exception
    {
        int sum = 0;
        final JsonFactory factory = _mapper.getJsonFactory();
        while (--reps >= 0) {
            JsonParser jp = factory.createJsonParser(data, 0, data.length);
            while (jp.nextToken() != null) {
                _mapper.readValue(jp, TYPE);
                ++sum;
            }
            jp.close();
        }
        return sum;
    }
    
    private int testJacksonIterator(int reps, byte[] data) throws Exception
    {
        int sum = 0;
        while (--reps >= 0) {
            Iterator<Bean> it = _mapper.reader(TYPE).readValues(data, 0, data.length);
            while (it.hasNext()) {
                it.next();
                ++sum;
            }
        }
        return sum;
    }

    private int testJacksonUpdating(int reps, byte[] data) throws Exception
    {
        int sum = 0;
        Bean updated = new Bean();
        while (--reps >= 0) {
            Iterator<Bean> it = _mapper.reader(TYPE).withValueToUpdate(updated).readValues(data, 0, data.length);
            while (it.hasNext()) {
                it.next();
                ++sum;
            }
        }
        return sum;
    }
    
    private byte[] generateSequence() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4000);
        JsonGenerator jgen = _mapper.getJsonFactory().createJsonGenerator(bytes);
        int i = 0;
        do {
            Bean bean = new Bean(i, "value"+i, (i & 1) != 0);
            jgen.writeObject(bean);
            jgen.writeRaw('\n');
            ++i;
        } while (bytes.size() < 4000);
        jgen.close();
        return bytes.toByteArray();
    }
    
    public static void main(String[] args) throws Exception
    {
        new TestIteratorPerf().test();
    }
}
