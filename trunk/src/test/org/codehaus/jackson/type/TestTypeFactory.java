package org.codehaus.jackson.type;

import java.util.*;

import main.BaseTest;

import org.codehaus.jackson.map.type.*;

/**
 * Simple tests to verify that the {@link TypeFactory} constructs
 * type information as expected.
 */
public class TestTypeFactory
    extends BaseTest
{    
    public void testSimpleTypes()
    {
        Class<?>[] classes = new Class<?>[] {
            boolean.class, byte.class, char.class,
                short.class, int.class, long.class,
                float.class, double.class,

            Boolean.class, Byte.class, Character.class,
                Short.class, Integer.class, Long.class,
                Float.class, Double.class,

                String.class,
                Object.class,

                Calendar.class,
                Date.class,
        };

        for (Class<?> clz : classes) {
            assertSame(clz, TypeFactory.type(clz).getRawClass());
            assertSame(clz, TypeFactory.type(clz).getRawClass());
        }
    }

    public void testArrays()
    {
        Class<?>[] classes = new Class<?>[] {
            boolean[].class, byte[].class, char[].class,
                short[].class, int[].class, long[].class,
                float[].class, double[].class,

                String[].class, Object[].class,
                Calendar[].class,
        };

        for (Class<?> clz : classes) {
            assertSame(clz, TypeFactory.type(clz).getRawClass());
            Class<?> elemType = clz.getComponentType();
            assertSame(clz, TypeFactory.arrayType(elemType).getRawClass());
        }
    }

    public void testCollections()
    {
        // Ok, first: let's test what happens when we pass 'raw' Collection:
        JavaType t = TypeFactory.type(ArrayList.class);
        assertEquals(CollectionType.class, t.getClass());
        assertSame(ArrayList.class, t.getRawClass());

        // And then the proper way
        t = TypeFactory.type(new TypeReference<ArrayList<String>>() { });
        assertEquals(CollectionType.class, t.getClass());
        assertSame(ArrayList.class, t.getRawClass());

        JavaType elemType = ((CollectionType) t).getContentType();
        assertNotNull(elemType);
        assertSame(SimpleType.class, elemType.getClass());
        assertSame(String.class, elemType.getRawClass());

        // And alternate method too
        t = TypeFactory.collectionType(ArrayList.class, String.class);
        assertEquals(CollectionType.class, t.getClass());
        assertSame(String.class, ((CollectionType) t).getContentType().getRawClass());
    }

    public void testMaps()
    {
        // Ok, first: let's test what happens when we pass 'raw' Map:
        JavaType t = TypeFactory.type(HashMap.class);
        assertEquals(MapType.class, t.getClass());
        assertSame(HashMap.class, t.getRawClass());

        // And then the proper way
        t = TypeFactory.type(new TypeReference<HashMap<String,Integer>>() { });
        assertEquals(MapType.class, t.getClass());
        assertSame(HashMap.class, t.getRawClass());
        MapType mt = (MapType) t;
        assertEquals(TypeFactory.type(String.class), mt.getKeyType());
        assertEquals(TypeFactory.type(Integer.class), mt.getContentType());

        // And alternate method too
        t = TypeFactory.mapType(TreeMap.class, String.class, Integer.class);
        assertEquals(MapType.class, t.getClass());
        assertSame(String.class, ((MapType) t).getKeyType().getRawClass());
        assertSame(Integer.class, ((MapType) t).getContentType().getRawClass());
    }

    public void testIterator()
    {
        JavaType t = TypeFactory.type(new TypeReference<Iterator<String>>() { });
        assertEquals(SimpleType.class, t.getClass());
        assertSame(Iterator.class, t.getRawClass());
        assertEquals(1, t.containedTypeCount());
        assertEquals(TypeFactory.type(String.class), t.containedType(0));
        assertNull(t.containedType(1));
    }

    /**
     * Test for verifying that parametric types can be constructed
     * programmatically
     * 
     * @since 1.5
     */
    public void testParametricTypes()
    {
        // first, simple class based
        JavaType t = TypeFactory.parametricType(ArrayList.class, String.class); // ArrayList<String>
        assertEquals(CollectionType.class, t.getClass());
        JavaType strC = TypeFactory.type(String.class);
        assertEquals(1, t.containedTypeCount());
        assertEquals(strC, t.containedType(0));
        assertNull(t.containedType(1));

        // Then using JavaType
        JavaType t2 = TypeFactory.parametricType(Map.class, strC, t); // Map<String,ArrayList<String>>
        // should actually produce a MapType
        assertEquals(MapType.class, t2.getClass());
        assertEquals(2, t2.containedTypeCount());
        assertEquals(strC, t2.containedType(0));
        assertEquals(t, t2.containedType(1));
        assertNull(t2.containedType(2));
    }

    /**
     * Test for checking that canonical name handling works ok
     * 
     * @since 1.5
     */
    public void testCanonicalNames()
    {
        JavaType t = TypeFactory.type(java.util.Calendar.class);
        String can = t.toCanonical();
        assertEquals("java.util.Calendar", can);
        assertEquals(t, TypeFactory.fromCanonical(can));

        // Generic maps and collections will default to Object.class if type-erased
        t = TypeFactory.type(java.util.ArrayList.class);
        can = t.toCanonical();
        assertEquals("java.util.ArrayList<java.lang.Object>", can);
        assertEquals(t, TypeFactory.fromCanonical(can));

        t = TypeFactory.type(java.util.TreeMap.class);
        can = t.toCanonical();
        assertEquals("java.util.TreeMap<java.lang.Object,java.lang.Object>", can);
        assertEquals(t, TypeFactory.fromCanonical(can));

        // And then EnumMap (actual use case for us)
        t = TypeFactory.mapType(EnumMap.class, TestEnum.class, String.class);
        can = t.toCanonical();
        assertEquals("java.util.EnumMap<org.codehaus.jackson.type.TestEnum,java.lang.String>",
                can);
        assertEquals(t, TypeFactory.fromCanonical(can));
        
    }
}

enum TestEnum { YES, NO; }
