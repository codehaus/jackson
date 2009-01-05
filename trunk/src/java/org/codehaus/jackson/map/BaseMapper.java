package org.codehaus.jackson.map;

import org.codehaus.jackson.*;

/**
 * This base class defines API aspects that are shared
 * between different concrete mapper types.
 */
public abstract class BaseMapper
{
    private final static Base64Variant DEFAULT_BASE64_VARIANT = Base64Variants.getDefaultVariant();

    /*
    ////////////////////////////////////////////////////
    // Shared public enums for configuration
    ////////////////////////////////////////////////////
     */

    /**
     * Enumeration that defines strategies available for dealing with
     * duplicate field names (when mapping JSON to Java types).
     */
    public enum DupFields {
        ERROR /* default */
            , USE_FIRST
            , USE_LAST
            ;
    }

    /*
    ////////////////////////////////////////////////////
    // Common config settings
    ////////////////////////////////////////////////////
     */

    /**
     * This option defines how duplicate field names (from JSON input)
     * are to be handled. Default is to throw a {@link JsonParseException}.
     */
    protected DupFields _cfgDupFields = DupFields.ERROR;
    /**
     * Base64 variant to use for serializing binary data. Defaults to
     * whatever {@link Base64Variants#getDefaultVariant} returns.
     */
    protected Base64Variant _cfgBase64Variant = DEFAULT_BASE64_VARIANT;

    /*
    ////////////////////////////////////////////////////
    // Other state/config
    ////////////////////////////////////////////////////
     */

    /**
     * Factory used to create {@link JsonParser} and {@link JsonGenerator}
     * instances as necessary.
     */
    protected final JsonFactory _jsonFactory;

    /*
    ////////////////////////////////////////////////////
    // Life-cycle (construction, configuration)
    ////////////////////////////////////////////////////
     */

    public BaseMapper(JsonFactory jf)
    {
        _jsonFactory = (jf == null) ? new JsonFactory() : jf;
    }

    public void setDupFieldHandling(DupFields mode) { _cfgDupFields = mode; }
    public DupFields getDupFieldHandling() { return _cfgDupFields; }

    /**
     * Method for setting
     * Base64 variant used for reading (decoding) and writing
     * (decoding) of base64 encoded binary content
     */
    public void setBase64Variant(Base64Variant b64v) { _cfgBase64Variant = b64v; }

    /**
     * @return Base64 variant used for reading (decoding) and writing
     *    (decoding) of base64 encoded binary content
     */
    public Base64Variant getBase64Variant() { return _cfgBase64Variant; }

    /**
     * Method that can be used to get hold of Json factory that this
     * mapper uses if it needs to construct Json parsers and/or generators.
     *
     * @return Json factory that this mapper uses when it needs to
     *   construct Json parser and generators
     */
    public JsonFactory getJsonFactory() { return _jsonFactory; }

    /*
    ////////////////////////////////////////////////////
    // Methods for sub-classes
    ////////////////////////////////////////////////////
     */

    protected void _throwInternal(String msg)
    {
        throw new RuntimeException("Internal error: "+msg);
    }

    protected void _reportProblem(JsonParser jp, String msg)
        throws JsonParseException
    {
        throw new JsonParseException(msg, jp.getTokenLocation());
    }
}
