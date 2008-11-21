/* Jackson JSON-processor.
 *
 * Copyright (c) 2007- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code and binary code bundles.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.jackson;

import java.io.*;
import java.math.BigDecimal;

/**
 * This is the public API implemented by concrete JSON parser
 * sub-classes.
 *
 * @author Tatu Saloranta
 */
public abstract class JsonParser
{
    /**
     * Enumeration of possible "native" (optimal) types that can be
     * used for numbers.
     */
    public enum NumberType {
        INT, LONG, BIG_INTEGER, FLOAT, DOUBLE, BIG_DECIMAL
    };

    protected JsonParser() { }

    /*
    ////////////////////////////////////////////////////
    // Public API, traversal
    ////////////////////////////////////////////////////
     */

    /**
     * Main iteration method, which will advance stream enough
     * to determine type of the next token, if any. If none
     * remaining (stream has no content other than possible
     * white space before ending), null will be returned.
     *
     * @return Next token from the stream, if any found, or null
     *   to indicate end-of-input
     */
    public abstract JsonToken nextToken()
        throws IOException, JsonParseException;

    /**
     * Method that will skip all child tokens of an array or
     * object token that the parser currently points to,
     * iff stream points to 
     * {@link JsonToken#START_OBJECT} or {@link JsonToken#START_ARRAY}.
     * If not, it will do nothing.
     * After skipping, stream will point to <b>matching</b>
     * {@link JsonToken#END_OBJECT} or {@link JsonToken#END_ARRAY}
     * (possibly skipping nested pairs of START/END OBJECT/ARRAY tokens
     * as well as value tokens).
     */
    public abstract void skipChildren()
        throws IOException, JsonParseException;

    /**
     * @return Type of the token this parser currently points to,
     *   if any: null both before any tokens have been read, and
     *   after end-of-input has been encountered.
     */
    public abstract JsonToken getCurrentToken();

    /**
     * @return True if the parser just returned a valid
     *   token via {@link #nextToken}; false otherwise (parser
     *   was just constructed, or encountered end-of-input
     *   and returned null from {@link #nextToken}.
     */
    public abstract boolean hasCurrentToken();

    /**
     * Method that can be called to get the name associated with
     * the current event. Will return null for all token types
     * except for {@link JsonToken#FIELD_NAME}.
     */
    public abstract String getCurrentName()
        throws IOException, JsonParseException;

    /**
     * Closes the parser so that no iteration or access methods
     * can be called.
     *<p>
     * Method will also close the underlying input source,
     * if (and only if) parser <b>owns</b> the input source.
     * Whether parser owns the input source depends on factory
     * method that was used to construct instance (so check
     * {@link org.codehaus.jackson.JsonFactory} for details,
     * but the general
     * idea is that if caller passes in closable resource (such
     * as {@link InputStream} or {@link Reader}) parser does NOT
     * own the source; but if it passes a reference (such as
     * {@link java.io.File} or {@link java.net.URL} and creates
     * stream or reader it does own them.
     */
    public abstract void close()
        throws IOException;

    /**
     * Method that can be used to access current parsing context reader
     * is in. There are 3 different types: root, array and object contexts,
     * with slightly different available information. Contexts are
     * hierarchically nested, and can be used for example for figuring
     * out part of the input document that correspond to specific
     * array or object (for highlighting purposes, or error reporting).
     * Contexts can also be used for simple xpath-like matching of
     * input, if so desired.
     */
    public abstract JsonReadContext getParsingContext();

    /**
     * Method that return the <b>starting</b> location of the current
     * token; that is, position of the first character from input
     * that starts the current token.
     */
    public abstract JsonLocation getTokenLocation();

    /**
     * Method that returns location of the last processed character;
     * usually for error reporting purposes.
     */
    public abstract JsonLocation getCurrentLocation();

    /*
    ////////////////////////////////////////////////////
    // Public API, access to token information, text
    ////////////////////////////////////////////////////
     */

    /**
     * Method for accessing textual representation of the current event;
     * if no current event (before first call to {@link #nextToken}, or
     * after encountering end-of-input), returns null.
     * Method can be called for any event.
     */
    public abstract String getText()
        throws IOException, JsonParseException;

    public abstract char[] getTextCharacters()
        throws IOException, JsonParseException;

    public abstract int getTextLength()
        throws IOException, JsonParseException;

    public abstract int getTextOffset()
        throws IOException, JsonParseException;

    /*
    ////////////////////////////////////////////////////
    // Public API, access to token information, numeric
    ////////////////////////////////////////////////////
     */

    /**
     * Generic number value accessor method that will work for
     * all kinds of numeric values. It will return the optimal
     * (simplest/smallest possibl) wrapper object that can
     * express the numeric value just parsed.
     */
    public abstract Number getNumberValue()
        throws IOException, JsonParseException;

    /**
     * If current event is of type 
     * {@link JsonToken#VALUE_NUMBER_INT} or
     * {@link JsonToken#VALUE_NUMBER_FLOAT}, returns
     * one of {@link NumberType} constants; otherwise returns null.
     */
    public abstract NumberType getNumberType()
        throws IOException, JsonParseException;

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_INT} and
     * it can be expressed as a Java int primitive type.
     *<p>
     * Note: if the token is an integer, but its value falls
     * outside of range of Java int, a {@link JsonParseException}
     * will be thrown to indicate numeric overflow/underflow.
     */
    public abstract int getIntValue()
        throws IOException, JsonParseException;

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_INT} and
     * it can be expressed as a Java long primitive type.
     *<p>
     * Note: if the token is an integer, but its value falls
     * outside of range of Java long, a {@link JsonParseException}
     * will be thrown to indicate numeric overflow/underflow.
     */
    public abstract long getLongValue()
        throws IOException, JsonParseException;

    public abstract double getDoubleValue()
        throws IOException, JsonParseException;

    public abstract BigDecimal getDecimalValue()
        throws IOException, JsonParseException;

    /*
    ////////////////////////////////////////////////////
    // Public API, access to token information, binary
    ////////////////////////////////////////////////////
     */

    /**
     * Method that can be used to read (and consume -- results
     * may not be accessible using other methods after the call)
     * base64-encoded binary data
     * included in the current textual json value. It is equivalent
     * to getting String value via {@link #getText} and decoding
     * result, but should be significantly more performant.
     *<p>
     * Note that the contents may be consumed by this call, and thus
     * only first call to method will produce any output. Likewise,
     * calls to methods like {@link #getText} are not guaranteed
     * to return anything.
     *
     * @param results Output stream used for returning decoded binary
     *   data
     *
     * @return Number of bytes decoded and written to <b>results</b>
     */
    public abstract int readBinaryValue(OutputStream results)
        throws IOException, JsonParseException;
}
