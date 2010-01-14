package org.codehaus.jackson.map.jsontype;

import java.io.*;
import java.util.*;

import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.annotate.JsonTypeName;

import static org.codehaus.jackson.map.annotate.JsonTypeInfo.*;

public class TestTypedSerialization
    extends BaseMapTest
{
    /*
     ****************************************************** 
     * Helper types
     ****************************************************** 
     */

    /**
     * Polymorphic base class
     */
    @JsonTypeInfo(use=Id.CLASS, include=As.PROPERTY)
    static class Animal {
        public String name;
        
        protected Animal(String n)  { name = n; }
    }

    @JsonTypeName("doggie")
    static class Dog extends Animal
    {
        public int boneCount;
        
        private Dog() { super(null); }
        public Dog(String name, int b) {
            super(name);
            boneCount = b;
        }
    }
    
    @JsonTypeName("kitty")
    static class Cat extends Animal
    {
        public String furColor;
        
        private Cat() { super(null); }
        public Cat(String name, String c) {
            super(name);
            furColor = c;
        }
    }

    public class AnimalWrapper {
        public Animal animal;
        
        public AnimalWrapper(Animal a) { animal = a; }
    }
    
    /**
     * Mix-ins for trying out different type serialization
     * mechanisms
     */
    @JsonTypeInfo(use=Id.CLASS, include=As.NAME_OF_PROPERTY)
    interface TypeWithMinimalClass { }

    @JsonTypeInfo(use=Id.MINIMAL_CLASS, include=As.WRAPPER)
    interface TypeWithWrapper { }

    @JsonTypeInfo(use=Id.CLASS, include=As.ARRAY)
    interface TypeWithArray { }
    
    /*
     ****************************************************** 
     * Unit tests
     ****************************************************** 
     */
    
    /**
     * First things first, let's ensure we can serialize using
     * class name, written as main-level property name
     */
    public void testSimpleClassAsProperty() throws Exception
    {
        Map<String,Object> result = writeAndMap(new Cat("Beelzebub", "tabby"));
        assertEquals(3, result.size());
        assertEquals("Beelzebub", result.get("name"));
        assertEquals("tabby", result.get("furColor"));
        // should we try customized class name?
        String classProp = Id.CLASS.getDefaultPropertyName();
        assertEquals(Cat.class.getName(), result.get(classProp));
    }

    /**
     * Then another combination, including minimal class, but as property name
     */
    public void testMinimalClassAsPropertyName() throws IOException
    {
        ObjectMapper m = new ObjectMapper();
        m.getSerializationConfig().addMixInAnnotations(Animal.class, TypeWithMinimalClass.class);
        Object bean = new AnimalWrapper(new Dog("Dug", 13));
        
        StringWriter sb = new StringWriter();
        try {
            m.writeValue(sb, bean);
        } finally {
            System.err.println("Doc = '"+sb+"'");
        }
        Map<String,Object> result = writeAndMap(m, bean);
        assertEquals(1, result.size());
        // twist: here we should actually get subtype name as key
        String cls = Dog.class.getName();
        Map<?,?> doggie = (Map<?,?>) result.get(cls);
        assertNotNull(doggie);
        assertEquals(2, doggie.size());
        assertEquals("Dug", doggie.get("name"));
        assertEquals(Integer.valueOf(13), doggie.get("boneCount"));
    }

    /**
     * Test inclusion using wrapper style
     */
    public void testTypeAsWrapper() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        m.getSerializationConfig().addMixInAnnotations(Animal.class, TypeWithWrapper.class);
        Map<String,Object> result = writeAndMap(m, new Cat("Venla", "black"));
        // should get a wrapper; keyed by minimal class name ("Cat" here)
        assertEquals(1, result.size());
        // minimal class name is prefixed by dot, and for inner classes it's bit longer
        Map<?,?> cat = (Map<?,?>) result.get(".TestTypedSerialization$Cat");
        assertNotNull(cat);
        assertEquals(2, cat.size());
        assertEquals("Venla", cat.get("name"));
        assertEquals("black", cat.get("furColor"));
    }

    /**
     * Test inclusion using 2-element array
     */
    public void testTypeAsArray() throws Exception
    {
        ObjectMapper m = new ObjectMapper();
        m.getSerializationConfig().addMixInAnnotations(Animal.class, TypeWithArray.class);
        // hmmh. Not good idea to rely on exact output, order may change. But...
        Map<String,Object> result = writeAndMap(m, new AnimalWrapper(new Dog("Amadeus", 7)));
        // First level, wrapper
        assertEquals(1, result.size());
        List<?> l = (List<?>) result.get("animal");
        assertNotNull(l);
        assertEquals(2, l.size());
        assertEquals(Dog.class.getName(), l.get(0));
        Map<?,?> doggie = (Map<?,?>) l.get(1);
        assertNotNull(doggie);
        assertEquals(2, doggie.size());
        assertEquals("Amadeus", doggie.get("name"));
        assertEquals(Integer.valueOf(7), doggie.get("boneCount"));
    }
}


