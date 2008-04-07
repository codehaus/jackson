package org.codehaus.jackson.sym;

/**
 * Specialized implementation of PName: can be used for short Strings
 * that consists of 9 to 12 bytes. It's the longest special purpose
 * implementaion; longer ones are expressed using {@link NameN}.
 */
public final class NameN
    extends Name
{
    final int[] mQuads;
    final int mQuadLen;

    NameN(String name, int hash, int[] quads, int quadLen)
    {
        super(name, hash);
        /* We have specialized implementations for shorter
         * names, so let's not allow runt instances here
         */
        if (quadLen < 3) {
            throw new IllegalArgumentException("Qlen must >= 3");
        }
        mQuads = quads;
        mQuadLen = quadLen;
    }

    // Implies quad length == 1, never matches
    public boolean equals(int quad) { return false; }

    // Implies quad length == 2, never matches
    public boolean equals(int quad1, int quad2) { return false; }

    public boolean equals(int[] quads, int qlen)
    {
        if (qlen == mQuadLen) {
            // Will always have >= 3 quads, can unroll
            /*
            if (quads[0] == mQuads[0]
                && quads[1] == mQuads[1]
                && quads[2] == mQuads[2]) {
                for (int i = 3; i < qlen; ++i) {
                    if (quads[i] != mQuads[i]) {
                        return false;
                    }
                }
                return true;
            }
            */
            for (int i = 0; i < qlen; ++i) {
                if (quads[i] != mQuads[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
