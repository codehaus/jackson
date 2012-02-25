package org.codehaus.jackson.map.jsontype;

import java.util.*;

import org.codehaus.jackson.annotate.*;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Unit tests for [JACKSON-712]; custom handling of invalid type id embedding.
 */
public class TestTypedDeserializationWithDefault
    extends org.codehaus.jackson.map.BaseMapTest
{
  private final ObjectMapper mapper = new ObjectMapper();

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = LegacyInter.class)
  @JsonSubTypes(value = {@JsonSubTypes.Type(name = "mine", value = MyInter.class)})
  public static interface Inter { }

  public static class MyInter implements Inter
  {
    @JsonProperty("blah")
    public List<String> blah;
  }

  public static class LegacyInter extends MyInter
  {
    @JsonCreator
    LegacyInter(Object obj)
    {
      if (obj instanceof List) {
        blah = new ArrayList<String>();
        for (Object o : (List<?>) obj) {
          blah.add(o.toString());
        }
      }
      else if (obj instanceof String) {
        blah = Arrays.asList(((String) obj).split(","));
      }
      else {
        throw new IllegalArgumentException("Unknown type: " + obj.getClass());
      }
    }
  }

  public void testDeserializationWithObject() throws Exception
  {
    Inter inter = mapper.readValue("{\"type\": \"mine\", \"blah\": [\"a\", \"b\", \"c\"]}", Inter.class);

    assertTrue(inter instanceof MyInter);
    assertFalse(inter instanceof LegacyInter);
    assertEquals(Arrays.asList("a", "b", "c"), ((MyInter) inter).blah);
  }

  public void testDeserializationWithString() throws Exception
  {
    Inter inter = mapper.readValue("\"a,b,c,d\"", Inter.class);

    assertTrue(inter instanceof LegacyInter);
    assertEquals(Arrays.asList("a", "b", "c", "d"), ((MyInter) inter).blah);
  }

  public void testDeserializationWithArray() throws Exception
  {
    Inter inter = mapper.readValue("[\"a\", \"b\", \"c\", \"d\"]", Inter.class);

    assertTrue(inter instanceof LegacyInter);
    assertEquals(Arrays.asList("a", "b", "c", "d"), ((MyInter) inter).blah);
  }

  public void testDeserializationWithArrayOfSize2() throws Exception
  {
    Inter inter = mapper.readValue("[\"a\", \"b\"]", Inter.class);

    assertTrue(inter instanceof LegacyInter);
    assertEquals(Arrays.asList("a", "b"), ((MyInter) inter).blah);
  }
}
