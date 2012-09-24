package org.codehaus.jackson.sym;

import java.io.IOException;

public class TestSymbolTables extends main.BaseTest
{
    // 11 3-char snippets that hash to 0xFFFF (with default JDK hashCode() calc),
    // and which can be combined as
    // sequences, like, say, 11x11x11 (1331) 9-character thingies
    final static String[] CHAR_COLLISION_SNIPPETS = {
        /*
         // with MULT of 31, seed 0, we'd get:
        "@~}", "@^", "A_}", "A`^", 
        "Aa?", "B@}", "BA^", "BB?", 
        "C!}", "C\"^", "C#?"
        */

        "*!\u0804", "*\"\u07E3", "*#\u07C2", "*$\u07A1", "*%\u0780", "*&\u075F", 
        "*'\u073E", "*(\u071D", "*)\u06FC", "**\u06DB"

        // With MULT of 33, seed of 0:

    };
    final static String[] CHAR_COLLISIONS;
    static {
        final int len = CHAR_COLLISION_SNIPPETS.length;
        CHAR_COLLISIONS = new String[len*len*len];
        int ix = 0;
        for (int i1 = 0; i1 < len; ++i1) {
            for (int i2 = 0; i2 < len; ++i2) {
                for (int i3 = 0; i3 < len; ++i3) {
                    CHAR_COLLISIONS[ix++] = CHAR_COLLISION_SNIPPETS[i1]
                            +CHAR_COLLISION_SNIPPETS[i2] + CHAR_COLLISION_SNIPPETS[i3];
                }
            }
        }
    }
    
    /**
     * Test to see what happens with pre-computed collisions; should
     * get an exception.
     */
    public void testCharBasedCollisions()
    {
        CharsToNameCanonicalizer sym = CharsToNameCanonicalizer.createRoot();

        // first, verify that we'd get a few collisions...
        try {
            int firstHash = 0;
            for (String str : CHAR_COLLISIONS) {
                int hash = sym.calcHash(str);
                if (firstHash == 0) {
                    firstHash = hash;
                } else {
                    assertEquals(firstHash, hash); 
                }
                sym.findSymbol(str.toCharArray(), 0, str.length(), hash);
            }
            fail("Should have thrown exception");
        } catch (IllegalStateException e) {
            verifyException(e, "exceeds maximum");
            // should fail right after addition:
            assertEquals(CharsToNameCanonicalizer.MAX_COLL_CHAIN_LENGTH+1, sym.maxCollisionLength());
            assertEquals(CharsToNameCanonicalizer.MAX_COLL_CHAIN_LENGTH+1, sym.collisionCount());
            // one "non-colliding" entry (head of collision chain), thus:
            assertEquals(CharsToNameCanonicalizer.MAX_COLL_CHAIN_LENGTH+2, sym.size());
        }
    }

    // Test for verifying stability of hashCode, wrt collisions, using
    // synthetic field name generation and character-based input
    public void testSyntheticWithChars()
    {
        // pass seed, to keep results consistent:
        CharsToNameCanonicalizer symbols = CharsToNameCanonicalizer.createRoot(1);
        final int COUNT = 6000;
        for (int i = 0; i < COUNT; ++i) {
            String id = fieldNameFor(i);
            char[] ch = id.toCharArray();
            symbols.findSymbol(ch, 0, ch.length, symbols.calcHash(id));
        }

        assertEquals(8192, symbols.bucketCount());
        assertEquals(COUNT, symbols.size());
        
//System.out.printf("Char stuff: collisions %d, max-coll %d\n", symbols.collisionCount(), symbols.maxCollisionLength());
        
        // original hashCode calc gave very high rate (3567), but with shuffling comes down a bit
        assertEquals(1401, symbols.collisionCount());
        // esp. with collisions; first got about 30
        assertEquals(4, symbols.maxCollisionLength());
    }

    // Test for verifying stability of hashCode, wrt collisions, using
    // synthetic field name generation and byte-based input (UTF-8)
    public void testSyntheticWithBytes() throws IOException
    {
        // pass seed, to keep results consistent:
        BytesToNameCanonicalizer symbols = BytesToNameCanonicalizer.createRoot(1)
                .makeChild(true, true);
        final int COUNT = 6000;
        for (int i = 0; i < COUNT; ++i) {
            String id = fieldNameFor(i);
            int[] quads = BytesToNameCanonicalizer.calcQuads(id.getBytes("UTF-8"));
            symbols.addName(id, quads, quads.length);
        }
        assertEquals(COUNT, symbols.size());
        assertEquals(8192, symbols.bucketCount());

//System.out.printf("Byte stuff: collisions %d, max-coll %d\n", symbols.collisionCount(), symbols.maxCollisionLength());
    
        // Fewer collisions than with chars, but still quite a few
        assertEquals(1700, symbols.collisionCount());
        // but not super long collision chains:
        assertEquals(10, symbols.maxCollisionLength());
    }

    protected void fieldNameFor(StringBuilder sb, int index)
    {
        /* let's do something like "f1.1" to exercise different
         * field names (important for byte-based codec)
         * Other name shuffling done mostly just for fun... :)
         */
        sb.append("f");
        sb.append(index);
        if (index > 50) {
            sb.append('.');
            if (index > 200) {
                sb.append(index);
                if (index > 4000) { // and some even longer symbols...
                    sb.append(".").append(index);
                }
            } else {
                sb.append(index >> 3); // divide by 8
            }
        }
    }

    protected String fieldNameFor(int index)
    {
        StringBuilder sb = new StringBuilder(16);
        fieldNameFor(sb, index);
        return sb.toString();
    }
}
