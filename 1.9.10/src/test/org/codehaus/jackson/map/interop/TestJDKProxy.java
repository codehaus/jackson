package org.codehaus.jackson.map.interop;

import java.lang.reflect.*;

import org.codehaus.jackson.map.*;

// mostly for [Issue#57]
public class TestJDKProxy extends BaseMapTest
{
    final ObjectMapper MAPPER = new ObjectMapper();

    public interface IPlanet {
        String getName();
        String setName(String s);
    }

    // bit silly example; usually wouldn't implement interface (no need to proxy if it did)
    static class Planet implements IPlanet {
        private String name;

        public Planet() { }
        public Planet(String s) { name = s; }
        
        public String getName(){return name;}
        public String setName(String iName) {name = iName;
            return name;
        }
    }    
    
    /*
    /********************************************************
    /* Test methods
    /********************************************************
     */
    
    public void testSimple() throws Exception
    {
        IPlanet input = getProxy(IPlanet.class, new Planet("Foo"));
        String json = MAPPER.writeValueAsString(input);
        assertEquals("{\"name\":\"Foo\"}", json);
        
        // and just for good measure
        Planet output = MAPPER.readValue(json, Planet.class);
        assertEquals("Foo", output.getName());
    }

    /*
    /********************************************************
    /* Helper methods
    /********************************************************
     */

    public static <T> T getProxy(Class<T> type, Object toProxy) {
        class ProxyUtil implements InvocationHandler {
            Object obj;
            public ProxyUtil(Object o) {
                obj = o;
            }
            public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                Object result = null;
                result = m.invoke(obj, args);
                return result;
            }
        }
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[] { type },
                new ProxyUtil(toProxy));
        return proxy;
    }
}
