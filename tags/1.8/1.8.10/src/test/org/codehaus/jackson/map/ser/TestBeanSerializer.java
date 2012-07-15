package org.codehaus.jackson.map.ser;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import org.codehaus.jackson.*;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.io.SerializedString;
import org.codehaus.jackson.map.*;
import org.codehaus.jackson.map.introspect.AnnotatedMember;
import org.codehaus.jackson.map.introspect.BasicBeanDescription;
import org.codehaus.jackson.map.module.SimpleModule;
import org.codehaus.jackson.map.util.Annotations;
import org.codehaus.jackson.type.JavaType;

/**
 * Unit tests for verifying that it is possible to configure
 * construction of {@link BeanSerializer} instances.
 * 
 * @since 1.7
 */
public class TestBeanSerializer extends BaseMapTest
{
    /*
    /********************************************************
    /* Helper types
    /********************************************************
     */

    static class ModuleImpl extends SimpleModule
    {
        protected BeanSerializerModifier modifier;
        
        public ModuleImpl(BeanSerializerModifier modifier)
        {
            super("test", Version.unknownVersion());
            this.modifier = modifier;
        }
        
        @Override
        public void setupModule(SetupContext context)
        {
            super.setupModule(context);
            if (modifier != null) {
                context.addBeanSerializerModifier(modifier);
            }
        }
    }

    @JsonPropertyOrder({"b", "a"})
    static class Bean {
        public String b = "b";
        public String a = "a";
    }

    static class RemovingModifier extends BeanSerializerModifier
    {
        private final String _removedProperty;
        
        public RemovingModifier(String remove) { _removedProperty = remove; }
        
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BasicBeanDescription beanDesc,
                List<BeanPropertyWriter> beanProperties)
        {
            Iterator<BeanPropertyWriter> it = beanProperties.iterator();
            while (it.hasNext()) {
                BeanPropertyWriter bpw = it.next();
                if (bpw.getName().equals(_removedProperty)) {
                    it.remove();
                }
            }
            return beanProperties;
        }
    }
    
    static class ReorderingModifier extends BeanSerializerModifier
    {
        @Override
        public List<BeanPropertyWriter> orderProperties(SerializationConfig config, BasicBeanDescription beanDesc, List<BeanPropertyWriter> beanProperties)
        {
            TreeMap<String,BeanPropertyWriter> props = new TreeMap<String,BeanPropertyWriter>();
            for (BeanPropertyWriter bpw : beanProperties) {
                props.put(bpw.getName(), bpw);
            }
            return new ArrayList<BeanPropertyWriter>(props.values());
        }
    }

    static class ReplacingModifier extends BeanSerializerModifier
    {
        private final JsonSerializer<?> _serializer;
        
        public ReplacingModifier(JsonSerializer<?> s) { _serializer = s; }
        
        @Override
        public JsonSerializer<?> modifySerializer(SerializationConfig config, BasicBeanDescription beanDesc,
                JsonSerializer<?> serializer) {
            return _serializer;
        }
    }

    static class BuilderModifier extends BeanSerializerModifier
    {
        private final JsonSerializer<?> _serializer;
        
        public BuilderModifier(JsonSerializer<?> ser) {
            _serializer = ser;
        }
        
        @Override
        public BeanSerializerBuilder updateBuilder(SerializationConfig config,
                BasicBeanDescription beanDesc, BeanSerializerBuilder builder) {
            return new BogusSerializerBuilder(beanDesc, _serializer);
        }
    }

    static class BogusSerializerBuilder extends BeanSerializerBuilder
    {
        private final JsonSerializer<?> _serializer;
        
        public BogusSerializerBuilder(BasicBeanDescription beanDesc,
                JsonSerializer<?> ser) {
            super(beanDesc);
            _serializer = ser;
        }

        @Override
        public JsonSerializer<?> build() {
            return _serializer;
        }
    }
    
    static class BogusBeanSerializer extends JsonSerializer<Object>
    {
        private final int _value;
        
        public BogusBeanSerializer(int v) { _value = v; }
        
        @Override
        public void serialize(Object value, JsonGenerator jgen,
                SerializerProvider provider) throws IOException {
            jgen.writeNumber(_value);
        }
    }

    // for [JACKSON-670]
    
    static class EmptyBean {
        @JsonIgnore
        public String name = "foo";
    }
    
    static class EmptyBeanModifier extends BeanSerializerModifier
    {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                BasicBeanDescription beanDesc, List<BeanPropertyWriter> beanProperties)
        {
            JavaType strType = config.constructType(String.class);
            try {
                beanProperties.add(new BeanPropertyWriter(
                        null, null,
                        "bogus", strType,
                        null, null, strType,
                        null, EmptyBean.class.getDeclaredField("name"),
                        false, null
                        ));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e.getMessage());
            }
            return beanProperties;
        }
    }

    // For [JACKSON-694]: error message for conflicting getters sub-optimal
    static class BeanWithConflict
    {
        public int getX() { return 3; }
        public boolean isX() { return false; }
    }

    class Issue701Bean { // important: non-static!
        private int i;

        // annotation does not matter -- just need one on the last argument
        public Issue701Bean(@JsonProperty int i) { this.i = i; }

        public int getI() { return i; }
    }
    
    /*
    /********************************************************
    /* Unit tests: success
    /********************************************************
     */

    public void testPropertyRemoval() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ModuleImpl(new RemovingModifier("a")));
        Bean bean = new Bean();
        assertEquals("{\"b\":\"b\"}", mapper.writeValueAsString(bean));
    }

    public void testPropertyReorder() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ModuleImpl(new ReorderingModifier()));
        Bean bean = new Bean();
        assertEquals("{\"a\":\"a\",\"b\":\"b\"}", mapper.writeValueAsString(bean));
    }

    public void testBuilderReplacement() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ModuleImpl(new BuilderModifier(new BogusBeanSerializer(17))));
        Bean bean = new Bean();
        assertEquals("17", mapper.writeValueAsString(bean));
    }    
    public void testSerializerReplacement() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ModuleImpl(new ReplacingModifier(new BogusBeanSerializer(123))));
        Bean bean = new Bean();
        assertEquals("123", mapper.writeValueAsString(bean));
    }

    // for [JACKSON-670]
    public void testEmptyBean() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new SimpleModule("test", Version.unknownVersion()) {
            @Override
            public void setupModule(SetupContext context)
            {
                super.setupModule(context);
                context.addBeanSerializerModifier(new EmptyBeanModifier());
            }
        });
        String json = mapper.writeValueAsString(new EmptyBean());
        assertEquals("{\"bogus\":\"foo\"}", json);
    }

    // for [JACKSON-701]
    public void testInnerClassWithAnnotationsInCreator() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new Issue701Bean(3));
        assertEquals("{\"i\":3}", json);
    }

    /*
    /********************************************************
    /* Unit tests: failure handling
    /********************************************************
     */
    
    // for [JACKSON-694]
    public void testFailWithDupProps() throws Exception
    {
        BeanWithConflict bean = new BeanWithConflict();
        ObjectMapper mapper = new ObjectMapper();
        try {
            String json = mapper.writeValueAsString(bean);
            fail("Should have failed due to conflicting accessor definitions; got JSON = "+json);
        } catch (JsonProcessingException e) {
            verifyException(e, "Conflicting getter definitions");
        }

        try {
            String json = mapper.writeValueAsString(bean);
            fail("Should have failed due to conflicting accessor definitions; got JSON = "+json);
        } catch (JsonProcessingException e) {
            verifyException(e, "Conflicting getter definitions");
        }
    }        
}
