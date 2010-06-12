package org.codehaus.jackson.xml;

import java.io.*;
import javax.xml.stream.*;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.ri.Stax2ReaderAdapter;
import org.codehaus.stax2.ri.Stax2WriterAdapter;

import org.codehaus.jackson.*;
import org.codehaus.jackson.io.IOContext;

/**
* Factory used for constructing {@link FromXmlParser} and {@link ToXmlGenerator}
* instances.
*<p>
* Implements {@link JsonFactory} since interface for constructing XML backed
* parsers and generators is quite similar to dealing with JSON.
* 
* @author tatu
* 
* @since 1.6
*/
public class XmlFactory extends JsonFactory
{
    /**
     * Bitfield (set of flags) of all parser features that are enabled
     * by default.
     */
    final static int DEFAULT_XML_PARSER_FEATURE_FLAGS = FromXmlParser.Feature.collectDefaults();

    /**
     * Bitfield (set of flags) of all generator features that are enabled
     * by default.
     */
    final static int DEFAULT_XML_GENERATOR_FEATURE_FLAGS = ToXmlGenerator.Feature.collectDefaults();

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    protected int _xmlParserFeatures = DEFAULT_XML_PARSER_FEATURE_FLAGS;

    protected int _xmlGeneratorFeatures = DEFAULT_XML_GENERATOR_FEATURE_FLAGS;

    protected XMLInputFactory _xmlInputFactory;

    protected XMLOutputFactory _xmlOutputFactory;

    /*
    /**********************************************************
    /* Factory construction, configuration
    /**********************************************************
     */

    /**
     * Default constructor used to create factory instances.
     * Creation of a factory instance is a light-weight operation,
     * but it is still a good idea to reuse limited number of
     * factory instances (and quite often just a single instance):
     * factories are used as context for storing some reused
     * processing objects (such as symbol tables parsers use)
     * and this reuse only works within context of a single
     * factory instance.
     */
    public XmlFactory() { this(null); }

    public XmlFactory(ObjectCodec oc) {
        this(oc, null, null);
    }

    public XmlFactory(ObjectCodec oc,
            XMLInputFactory xmlIn, XMLOutputFactory xmlOut)
    {
        super(oc);
        if (xmlIn == null) {
            xmlIn = XMLInputFactory.newFactory();
        }
        if (xmlOut == null) {
            xmlOut = XMLOutputFactory.newFactory();
        }
        _xmlInputFactory = xmlIn;
        _xmlOutputFactory = xmlOut;
    }

    /*
    /**********************************************************
    /* Configuration, parser settings
    /**********************************************************
     */

    /**
     * Method for enabling or disabling specified parser feature
     * (check {@link XmlParser.Feature} for list of features)
     */
    public final XmlFactory configure(FromXmlParser.Feature f, boolean state)
    {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }

    /**
     * Method for enabling specified parser feature
     * (check {@link FromXmlParser.Feature} for list of features)
     */
    public XmlFactory enable(FromXmlParser.Feature f) {
        _xmlParserFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified parser features
     * (check {@link JsonParser.Feature} for list of features)
     */
    public XmlFactory disable(FromXmlParser.Feature f) {
        _xmlParserFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Checked whether specified parser feature is enabled.
     */
    public final boolean isEnabled(FromXmlParser.Feature f) {
        return (_xmlParserFeatures & f.getMask()) != 0;
    }

    /*
    /******************************************************
    /* Configuration, generator settings
    /******************************************************
     */

    /**
     * Method for enabling or disabling specified generator feature
     * (check {@link JsonGenerator.Feature} for list of features)
     *
     * @since 1.2
     */
    public final XmlFactory configure(ToXmlGenerator.Feature f, boolean state) {
        if (state) {
            enable(f);
        } else {
            disable(f);
        }
        return this;
    }


    /**
     * Method for enabling specified generator features
     * (check {@link JsonGenerator.Feature} for list of features)
     */
    public XmlFactory enable(ToXmlGenerator.Feature f) {
        _xmlGeneratorFeatures |= f.getMask();
        return this;
    }

    /**
     * Method for disabling specified generator feature
     * (check {@link JsonGenerator.Feature} for list of features)
     */
    public XmlFactory disable(ToXmlGenerator.Feature f) {
        _xmlGeneratorFeatures &= ~f.getMask();
        return this;
    }

    /**
     * Check whether specified generator feature is enabled.
     */
    public final boolean isEnabled(ToXmlGenerator.Feature f) {
        return (_xmlGeneratorFeatures & f.getMask()) != 0;
    }
    
    /*
    /**********************************************************
    /* Overridden parts of public API
    /**********************************************************
     */

    /**
     *<p>
     * note: co-variant return type
     */
    public ToXmlGenerator createJsonGenerator(OutputStream out, JsonEncoding enc)
        throws IOException
    {
        // false -> we won't manage the stream unless explicitly directed to
        IOContext ctxt = _createContext(out, false);
        return _createJsonGenerator(ctxt, out, enc);
    }
    
    /*
    /**********************************************************
    /* Overridden internal factory methods
    /**********************************************************
     */

    //protected IOContext _createContext(Object srcRef, boolean resourceManaged)

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    protected FromXmlParser _createJsonParser(InputStream in, IOContext ctxt)
        throws IOException, JsonParseException
    {
        // !!! TBI
        return null;
        //return new ByteSourceBootstrapper(ctxt, in).constructParser(_parserFeatures, _objectCodec, _rootByteSymbols, _rootCharSymbols);
    }

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    protected FromXmlParser _createJsonParser(Reader r, IOContext ctxt)
        throws IOException, JsonParseException
    {
        // !!! TBI
        return null;
    }

    /**
     * Overridable factory method that actually instantiates desired
     * parser.
     */
    protected FromXmlParser _createJsonParser(byte[] data, int offset, int len, IOContext ctxt)
        throws IOException, JsonParseException
    {
        // !!! TBI
        return null;
        // true -> managed (doesn't really matter; we have no stream!)
        //return new ByteSourceBootstrapper(ctxt, data, offset, len).constructParser(_parserFeatures, _objectCodec, _rootByteSymbols, _rootCharSymbols);
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    /**
     * Overridable factory method that actually instantiates desired
     * generator.
     */
    protected JsonGenerator _createJsonGenerator(IOContext ctxt, Writer out)
        throws IOException
    {
        int feats = _xmlGeneratorFeatures;
        return new ToXmlGenerator(ctxt, feats, _objectCodec, _createXmlWriter(out));
    }
    
    protected ToXmlGenerator _createJsonGenerator(IOContext ctxt, OutputStream out, JsonEncoding enc)
        throws IOException
    {
        int feats = _xmlGeneratorFeatures;
        return new ToXmlGenerator(ctxt, feats, _objectCodec, _createXmlWriter(out));
    }

    protected XMLStreamWriter _createXmlWriter(OutputStream out) throws IOException
    {
        try {
            return _xmlOutputFactory.createXMLStreamWriter(out, "UTF-8");
        } catch (XMLStreamException e) {
            return StaxUtil.throwXmlAsIOException(e);
        }
    }

    protected XMLStreamWriter _createXmlWriter(Writer w) throws IOException
    {
        try {
            return _xmlOutputFactory.createXMLStreamWriter(w);
        } catch (XMLStreamException e) {
            return StaxUtil.throwXmlAsIOException(e);
        }
    }

}
