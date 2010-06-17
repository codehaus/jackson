package org.codehaus.jackson.smile;

import static org.codehaus.jackson.smile.SmileConstants.BYTE_MARKER_END_OF_STRING;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.codehaus.jackson.*;
import org.codehaus.jackson.impl.StreamBasedParserBase;
import org.codehaus.jackson.io.IOContext;
import org.codehaus.jackson.sym.BytesToNameCanonicalizer;

public class SmileParser
    extends StreamBasedParserBase
{
    /**
     * Enumeration that defines all togglable features for Smile generators.
     */
    public enum Feature {
        DUMMY_PLACEHOLDER(false)
        ;

        final boolean _defaultState;
        final int _mask;
        
        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults()
        {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }
        
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }
        
        public boolean enabledByDefault() { return _defaultState; }
        public int getMask() { return _mask; }
    }

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    
    /**
     * Codec used for data binding when (if) requested.
     */
    protected ObjectCodec _objectCodec;

    /*
    /**********************************************************
    /* Additional parsing state
    /**********************************************************
     */

    /**
     * Type byte of the current token
     */
    protected int _typeByte;
    
    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */
    
    public SmileParser(IOContext ctxt, int parserFeatures, int smileFeatures,
            ObjectCodec codec,
            BytesToNameCanonicalizer sym,
            InputStream in, byte[] inputBuffer, int start, int end,
            boolean bufferRecyclable)
    {
        super(ctxt, parserFeatures, in, inputBuffer, start, end, bufferRecyclable);
        _objectCodec = codec;
        _tokenInputRow = -1;
        _tokenInputCol = -1;
    }

    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }

    /**
     * Helper method called when it looks like input might contain the signature;
     * and it is necessary to detect and handle signature to get configuration
     * information it might have.
     * 
     * @return True if valid signature was found and handled; false if not
     */
	protected boolean handleSignature(boolean throwException)
		throws IOException, JsonParseException
	{
        if (++_inputPtr >= _inputEnd) {
            loadMoreGuaranteed();
        }
        if (_inputBuffer[_inputPtr] != SmileConstants.HEADER_BYTE_2) {
            if (throwException) {
            	_reportError("Malformed content: signature not valid, starts with 0x3a but followed by 0x"
            			+Integer.toHexString(_inputBuffer[_inputPtr])+", not 0x29");
            }
            return false;
        }
        if (++_inputPtr >= _inputEnd) {
        	loadMoreGuaranteed();        	
        }
        if (_inputBuffer[_inputPtr] != SmileConstants.HEADER_BYTE_3) {
            if (throwException) {
            	_reportError("Malformed content: signature not valid, starts with 0x3a, 0x29, but followed by 0x"
            			+Integer.toHexString(_inputBuffer[_inputPtr])+", not 0xA");
            }
            return false;
        }
    	// Good enough; just need version info from 4th byte...
        if (++_inputPtr >= _inputEnd) {
        	loadMoreGuaranteed();        	
        }
        int ch = _inputBuffer[_inputPtr++];
        int versionBits = (ch >> 6) & 0x03;
        // but failure with version number is fatal, can not ignore
        if (versionBits != SmileConstants.HEADER_VERSION_00) {
        	_reportError("Header version number bits (0x"+Integer.toHexString(versionBits)+") indicate unrecognized version; only 0x0 handled by parser");
        }
        return true;
	}
    
    /*
    /**********************************************************
    /* JsonParser impl
    /**********************************************************
     */

    @Override
    public JsonToken nextToken() throws IOException, JsonParseException
    {
        // For longer tokens (text, binary), we'll only read when requested
        if (_tokenIncomplete) {
            _skipIncomplete();
        }
        _tokenInputTotal = _currInputProcessed + _inputPtr - 1;

        // finally: clear any data retained so far
        _binaryValue = null;
        if (_inputPtr >= _inputEnd) {
            if (!loadMore()) {
                _handleEOF();
                /* NOTE: here we can and should close input, release buffers,
                 * since this is "hard" EOF, not a boundary imposed by
                 * header token.
                 */
                close();
                return (_currToken = null);
            }
        }
        int ch = _inputBuffer[_inputPtr++];
        _typeByte = ch;
        // Two main modes: values, and field names.
        if (_parsingContext.inObject() && _currToken != JsonToken.FIELD_NAME) {
            return (_currToken = _handleFieldName());
        }

        switch ((ch >> 5) & 0x7) {
        case 0: // short shared string value reference
        case 1: // misc literals
            switch (ch & 0x1F) {
            case 0x0: // START_OBJECT
                _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
                return (_currToken = JsonToken.START_OBJECT);
            //case 0x0: // not used
            case 0x2: // START_ARRAY
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
                return (_currToken = JsonToken.START_ARRAY);
            case 0x3: // END_ARRAY
                if (!_parsingContext.inArray()) {
                    _reportMismatchedEndMarker(']', '}');
                }
                _parsingContext = _parsingContext.getParent();
                return (_currToken = JsonToken.END_ARRAY);
            case 0x4: // false
                return (_currToken = JsonToken.VALUE_FALSE);
            case 0x5: // true
                return (_currToken = JsonToken.VALUE_TRUE);
            case 0x6:
                return (_currToken = JsonToken.VALUE_NULL);
            case 0x7:
                _textBuffer.resetWithEmpty();
                return (_currToken = JsonToken.VALUE_STRING);
            case 0x1A: // == 0x3A == ':' -> possibly header signature for next chunk?
            	if (handleSignature(false)) {
            		// Mark current token as empty, to return; but don't close input to allow more parsing
            		return (_currToken = null);
            	}
            	_reportError("Unrecognized token byte 0x3A (malformed segment header?");
            }
            // and everything else is reserved, for now
            break;
        case 2: // tiny ascii
            // fall through
        case 3: // short ascii
            // fall through
        case 4: // tiny unicode
            // fall through
        case 5: // short unicode
            // all fine; but no need to decode quite yet
            _tokenIncomplete = true;
            return (_currToken = JsonToken.VALUE_STRING);
        case 6: // small integers; zig zag encoded
            _numberInt = SmileUtil.zigzagDecode(ch & 0x1F);
            _numTypesValid = NR_INT;
            return (_currToken = JsonToken.VALUE_NUMBER_INT);
        default: // binary/longtext/var-numbers
            ch &= 0x1F;
            _tokenIncomplete = true; // none of these is fully handled yet
            // next 3 bytes define subtype
            switch (ch >> 2) {
            case 0: // long variable length ascii
            case 1: // long variable length unicode
                _tokenIncomplete = true;
                return (_currToken = JsonToken.VALUE_STRING);
            case 2: // binary, raw
            case 3: // binary, 7-bit
                _tokenIncomplete = true;
                return (_currToken = JsonToken.VALUE_EMBEDDED_OBJECT);
            case 4: // VInt (zigzag)
                _tokenIncomplete = true;
                _numTypesValid = 0;
                return (_currToken = JsonToken.VALUE_NUMBER_INT);
            case 5: // other numbers
                _numTypesValid = 0;
                _currToken = ((ch & 0x3) == 2) ?
                        JsonToken.VALUE_NUMBER_INT : JsonToken.VALUE_NUMBER_FLOAT;
                return _currToken;
            default: // 6 and 7 not used (to reserve 0xF8 - 0xFF)
            }
            break;
        }
        // If we get this far, type byte is corrupt
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(ch)+" for expected value token");
        return null;
    }

    @Override
    public String getCurrentName() throws IOException, JsonParseException
    {
        if (_tokenIncomplete) {
            _skipIncomplete();
        }
        return _parsingContext.getCurrentName();
    }

    /*
    /**********************************************************
    /* Public API, access to token information, text
    /**********************************************************
     */

    /**
     * Method for accessing textual representation of the current event;
     * if no current event (before first call to {@link #nextToken}, or
     * after encountering end-of-input), returns null.
     * Method can be called for any event.
     */
    @Override    
    public String getText()
        throws IOException, JsonParseException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                _finishToken();
            }
            switch (_currToken) {
            case VALUE_STRING:
                return _textBuffer.contentsAsString();
            case FIELD_NAME:
                return _parsingContext.getCurrentName();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                // !!! TBI
                return getNumberValue().toString();
                
            default:
                return _currToken.asString();
            }
        }
        return null;
    }

    @Override
    public char[] getTextCharacters()
        throws IOException, JsonParseException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                _finishToken();
            }
            switch (_currToken) {                
            case VALUE_STRING:
                return _textBuffer.getTextBuffer();
            case FIELD_NAME:
                if (!_nameCopied) {
                    String name = _parsingContext.getCurrentName();
                    int nameLen = name.length();
                    if (_nameCopyBuffer == null) {
                        _nameCopyBuffer = _ioContext.allocNameCopyBuffer(nameLen);
                    } else if (_nameCopyBuffer.length < nameLen) {
                        _nameCopyBuffer = new char[nameLen];
                    }
                    name.getChars(0, nameLen, _nameCopyBuffer, 0);
                    _nameCopied = true;
                }
                return _nameCopyBuffer;

                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                // !!! TBI
                return getNumberValue().toString().toCharArray();
                
            default:
                return _currToken.asCharArray();
            }
        }
        return null;
    }

    @Override    
    public int getTextLength()
        throws IOException, JsonParseException
    {
        if (_currToken != null) { // null only before/after document
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                _finishToken();
            }
            switch (_currToken) {
            case VALUE_STRING:
                return _textBuffer.size();                
            case FIELD_NAME:
                return _parsingContext.getCurrentName().length();
                // fall through
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                // !!! TBI
                return getNumberValue().toString().length();
                
            default:
                return _currToken.asCharArray().length;
            }
        }
        return 0;
    }

    @Override
    public int getTextOffset()
        throws IOException, JsonParseException
    {
        return 0;
    }

    /*
    /**********************************************************
    /* Public API, access to token information, binary
    /**********************************************************
     */

    @Override
    protected byte[] _decodeBase64(Base64Variant b64variant)
        throws IOException, JsonParseException
    {
        // !!! TBI
        return null;
    }
    
    /*
    /**********************************************************
    /* Internal methods, parsing
    /**********************************************************
     */

    /**
     * Method that handles initial token type recognition for token
     * that has to be either FIELD_NAME or END_OBJECT.
     */
    protected final JsonToken _handleFieldName() throws IOException, JsonParseException
    {    	
        int ch = _typeByte;
        switch ((ch >> 6) & 3) {
        case 0: // misc, including end marker
            switch (ch) {
            case 0x30: // long shared
            case 0x31:
            case 0x32:
            case 0x33:
	            {
	                if (_inputPtr >= _inputEnd) {
	                    loadMoreGuaranteed();
	                }
	                int index = ((ch & 0x3) << 8) | _inputBuffer[_inputPtr++];
	                // !!! TBI: fetch actual String
	                _parsingContext.setCurrentName("TBI");
	            }
                return JsonToken.FIELD_NAME;
            case 0x35: // empty String as name, legal if unusual
                _parsingContext.setCurrentName("");
                return JsonToken.FIELD_NAME;
            case 0x36:
                if (!_parsingContext.inObject()) {
                    _reportMismatchedEndMarker('}', ']');
                }
                _parsingContext = _parsingContext.getParent();
                return JsonToken.END_OBJECT;

            }
            break;
        case 1: // short shared, can fully process
            {
                int nameIndex = (ch & 0x3F);
                // !!! TBI: fetch actual String
                _parsingContext.setCurrentName("TBI");
            }
            return JsonToken.FIELD_NAME;
        case 2: // short ASCII
	        {
	            int len = 1 + (ch & 0x3F);
	            _loadToHaveAtLeast(len);
	            int outPtr = 0;
	            char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
	            int inPtr = _inputPtr;
	            _inputPtr += len;
	            for (int end = inPtr + len; inPtr < end; ) {
	                outBuf[outPtr++] = (char) _inputBuffer[inPtr++];
	            }
	            _textBuffer.setCurrentLength(len);
	            _parsingContext.setCurrentName(_textBuffer.contentsAsString());
	        }
            return JsonToken.FIELD_NAME;                
        case 3: // short Unicode
            // all valid, except for 0xBF and 0xFF
            if ((ch & 0x3F) != 0x3F) {
                int len = 1 + (ch & 0x3F);
                _decodeShortUnicode(len);
                _parsingContext.setCurrentName(_textBuffer.contentsAsString());
                return JsonToken.FIELD_NAME;                
            }
            break;
        }
        // Other byte values are illegal
        _reportError("Invalid type marker byte 0x"+Integer.toHexString(ch)+" for expected field name (or END_OBJECT marker)");
        return null;
    }    

    /**
     * Helper method used to decode short Unicode string, length for which actual
     * length (in bytes) is known
     * 
     * @param len Length between 1 and 64
     */
    protected final void _decodeShortUnicode(int len)
        throws IOException, JsonParseException
    {
        _loadToHaveAtLeast(len);

        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int inPtr = _inputPtr;
        _inputPtr += len;
        final int[] codes = SmileConstants.sUtf8UnitLengths;
        for (int end = inPtr + len; inPtr < end; ) {
            int i = _inputBuffer[inPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                // trickiest one, need surrogate handling
                switch (code) {
                case 1:
                    i = ((i & 0x1F) << 6) | (_inputBuffer[inPtr++] & 0x3F);
                    break;
                case 2:
                    i = ((i & 0x0F) << 12)
                        | ((_inputBuffer[inPtr++] & 0x3F) << 6)
                        | (_inputBuffer[inPtr++] & 0x3F);
                    break;
                case 3:
                    i = ((i & 0x07) << 18)
                    | ((_inputBuffer[inPtr++] & 0x3F) << 12)
                    | ((_inputBuffer[inPtr++] & 0x3F) << 6)
                    | (_inputBuffer[inPtr++] & 0x3F);
                    // note: this is the codepoint value; need to split, too
                    i -= 0x10000;
                    outBuf[outPtr++] = (char) (0xD800 | (i >> 10));
                    i = 0xDC00 | (i & 0x3FF);
                    break;
                default: // invalid
                    _reportError("Invalid byte "+Integer.toHexString(i)+" in short Unicode text block");
                }
            }
            outBuf[outPtr++] = (char) i;
        }
        _textBuffer.setCurrentLength(outPtr);
    }

    /*
    /**********************************************************
    /* Internal methods, secondary parsing
    /**********************************************************
     */

    @Override
    protected void _parseNumericValue(int expType)
    	throws IOException, JsonParseException
	{
    	if (_tokenIncomplete) {
    		_tokenIncomplete = false;
    		_finishToken();
    	}
	}
    
    /**
     * Method called to finish parsing of a token so that token contents
     * are retrieable
     */
    protected void _finishToken()
    	throws IOException, JsonParseException
    {
    	int tb = _typeByte;

        switch ((tb >> 5) & 0x7) {
        case 2: // tiny ascii
            // fall through
        case 3: // short ascii
        	{
        		int len = (tb - 0x3F);
	            _loadToHaveAtLeast(len);
	            int outPtr = 0;
	            char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
	            int inPtr = _inputPtr;
	            _inputPtr += len;
	            for (int end = inPtr + len; inPtr < end; ) {
	                outBuf[outPtr++] = (char) _inputBuffer[inPtr++];
	            }
	            _textBuffer.setCurrentLength(len);
        	}
        	return;

        case 4: // tiny unicode
            // fall through
        case 5: // short unicode
	        {
	            int len = (tb - 0x7F);
	            _decodeShortUnicode(len);
	        }
	        return;

        case 7:
            tb &= 0x1F;
            // next 3 bytes define subtype
            switch (tb >> 2) {
            case 0: // long variable length ascii
            case 1: // long variable length unicode
            	//
            case 2: // binary, raw
            case 3: // binary, 7-bit
            	//
            case 4: // VInt (zigzag)
	            {
	            	if (_inputPtr >= _inputEnd) {
	            		loadMoreGuaranteed();
	            	}
	            	int value = _inputBuffer[_inputPtr++];
	            	if (value < 0) { // 6 bits
	            		_finishInt(0, value);
	            		return;
	            	}
	            	if (_inputPtr >= _inputEnd) {
	            		loadMoreGuaranteed();
	            	}
	            	int i = _inputBuffer[_inputPtr++];
	            	if (i < 0) { // 13 bits
	            		_finishInt(value, i);
	            		return;
	            	}
	            	value = (value << 7) + i;
	            	if (_inputPtr >= _inputEnd) {
	            		loadMoreGuaranteed();
	            	}
	            	i = _inputBuffer[_inputPtr++];
	            	if (i < 0) { // 20 bits
	            		_finishInt(value, i);
	            		return;
	            	}
	            	value = (value << 7) + i;
	            	if (_inputPtr >= _inputEnd) {
	            		loadMoreGuaranteed();
	            	}
	            	i = _inputBuffer[_inputPtr++];
	            	if (i < 0) { // 27 bits
	            		_finishInt(value, i);
	            		return;
	            	}
	            	// Ok: likely to be a long, call...
	            	value = (value << 7) + i;
	            	_finishLong(value);
	            }
            	return;

            case 5: // other numbers
            	//
            	/*
                _currToken = ((ch & 0x3) == 2) ?
                        JsonToken.VALUE_NUMBER_INT : JsonToken.VALUE_NUMBER_FLOAT;
                        */
            }
        }
    	_throwInternal();
    }

    /**
     * Helper method that will update state for an int value
     */
	private final void  _finishInt(int base, int lastByte)
	{
		base <<= 6;
        _numberInt = SmileUtil.zigzagDecode(base + (lastByte & 0x3F));
        _numTypesValid = NR_INT;
	}

	private final void  _finishLong(long value)
	    throws IOException, JsonParseException
	{
		// First special case: if we only get a single more byte, it might be still an int
    	if (_inputPtr >= _inputEnd) {
    		loadMoreGuaranteed();
    	}
    	int i = _inputBuffer[_inputPtr++];
    	if (i < 0) { // up to 34 bits...
    		value = (value << 6) + (i & 0x3F);
    		value = SmileUtil.zigzagDecode(value);
    		// quick check to see if int has all data...
    		if ((value >>> 32) == 0) {
    	        _numberInt = (int) value;
    	        _numTypesValid = NR_INT;
    	        return;
    		}
    		_numberLong = SmileUtil.zigzagDecode(value);
    		_numTypesValid = NR_LONG;
    		return;
    	}
    	// Otherwise, let's just loop for the rest
    	while (true) {
        	value = (value << 7) + i;
        	if (_inputPtr >= _inputEnd) {
        		loadMoreGuaranteed();
        	}
        	i = _inputBuffer[_inputPtr++];
        	if (i < 0) {
        		value = (value << 6) + (i & 0x3F);
        		_numberLong = SmileUtil.zigzagDecode(value);
        		_numTypesValid = NR_LONG;
        		return;
        	}
    	}
	}
	
    /**
     * Method called to skip remainders of an incomplete token, when
     * contents themselves will not be needed any more
     */
    protected void _skipIncomplete() throws IOException, JsonParseException
    {
    	int tb = _typeByte;
        switch ((tb >> 5) & 0x7) {
        case 2: // tiny ascii
            // fall through
        case 3: // short ascii
        	_skipBytes(tb - 0x3F);
        	return;
        	
        case 4: // tiny unicode
            // fall through
        case 5: // short unicode
        	_skipBytes(tb - 0x7F);
        	return;

        case 7:
            tb &= 0x1F;
            // next 3 bytes define subtype
            switch (tb >> 2) {
            case 0: // long variable length ascii
            case 1: // long variable length unicode
            	// Doesn't matter which one, just need to find the end marker
            	while (true) {
            		final int end = _inputEnd;
            		final byte[] buf = _inputBuffer;
            		while (_inputPtr < end) {
            			if (buf[_inputPtr++] == BYTE_MARKER_END_OF_STRING) {
            				return;
            			}
            		}
            		loadMoreGuaranteed();
            	}

            	//
            case 2: // binary, raw
            case 3: // binary, 7-bit
            	//
            case 4: // VInt (zigzag)
            	// easy, just skip until we see sign bit... (should we try to limit damage?)
            	while (true) {
            		final int end = _inputEnd;
            		final byte[] buf = _inputBuffer;
            		while (_inputPtr < end) {
            			if (buf[_inputPtr++] < 0) {
            				return;
            			}
            		}
            		loadMoreGuaranteed();            		
            	}
            	//
            case 5: // other numbers
            	//
            	/*
                _currToken = ((ch & 0x3) == 2) ?
                        JsonToken.VALUE_NUMBER_INT : JsonToken.VALUE_NUMBER_FLOAT;
                        */
            }
        }
    	_throwInternal();
    }

    protected void _skipBytes(int len)  throws IOException, JsonParseException
    {
	    while (true) {
	    	int toAdd = Math.min(len, _inputEnd - _inputPtr);
	    	_inputPtr += toAdd;
	    	len -= toAdd;
	    	if (len <= 0) {
	    		return;
	    	}
	    	loadMoreGuaranteed();
	    }
    }
    
    @Override
    protected void _finishString() throws IOException, JsonParseException
    {
        // should never be called; but must be defined for superclass
        _throwInternal();
    }
    
    /*
    /**********************************************************
    /* Internal methods, other
    /**********************************************************
     */
}
