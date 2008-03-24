package org.codehaus.jackson.sym;

/**
 * Specialized implementation of PName: can be used for short Strings
 * that consists of at most 4 bytes. Usually this means short
 * ascii-only names.
 *<p>
 * The reason for such specialized classes is mostly space efficiency;
 * and to a lesser degree performance. Both are achieved for short
 * Strings by avoiding another level of indirection (via quad arrays)
 */
public final class Name1
    extends Name
{
    final int mQuad;

    Name1(String name, int hash, int quad)
    {
        super(name, hash);
        mQuad = quad;
    }

    public boolean equals(int quad1, int quad2)
    {
        return (quad1 == mQuad) && (quad2 == 0);
    }

    public boolean equals(int[] quads, int qlen)
    {
        return (qlen == 1 && quads[0] == mQuad);
    }

    public int getFirstQuad() {
        return mQuad;
    }

    public int getLastQuad() {
        return mQuad;
    }

    public int getQuad(int index) {
        return (index == 0) ? mQuad : 0;
    }

    public int sizeInQuads() { return 1; }
}
