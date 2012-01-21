package org.codehaus.jackson.map.util;

import java.text.DateFormat;
import java.util.*;

import org.codehaus.jackson.map.BaseMapTest;

/**
 * @see ISO8601DateFormat
 */
public class ISO8601DateFormatTest extends BaseMapTest
{
    private ISO8601DateFormat df;
    private Date date;

    @Override
    public void setUp()
    {
        Calendar cal = new GregorianCalendar(2007, 8 - 1, 13, 19, 51, 23);
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        cal.set(Calendar.MILLISECOND, 0);
        date = cal.getTime();
        df = new ISO8601DateFormat();
    }

    public void format() {
        String result = df.format(date);
        assertEquals("2007-08-13T19:51:23Z", result);
    }

    public void parse() throws Exception {
        Date result = df.parse("2007-08-13T19:51:23Z");
        assertEquals(date, result);
    }

    public void cloneObject() throws Exception {
        DateFormat clone = (DateFormat)df.clone();
        assertSame(df, clone);
    }

}
