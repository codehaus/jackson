package org.codehaus.jackson.smile;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;

public class TestMapper extends SmileTestBase
{
    static class BytesBean {
        public byte[] bytes;
        
        public BytesBean() { }
        public BytesBean(byte[] b) { bytes = b; }
    }
    
    // [JACKSON-733]
    public void testBinary() throws IOException
    {
        byte[] input = new byte[] { 1, 2, 3, -1, 8, 0, 42 };
        ObjectMapper mapper = smileMapper();
        byte[] smile = mapper.writeValueAsBytes(new BytesBean(input));
        BytesBean result = mapper.readValue(smile, BytesBean.class);
        
        assertNotNull(result.bytes);
        Assert.assertArrayEquals(input, result.bytes);
    }
}
