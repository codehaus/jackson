package org.codehaus.jackson.sym;

import java.util.Arrays;

import org.codehaus.jackson.util.InternCache;

/**
 * This class is basically a caching symbol table implementation used for
 * canonicalizing {@link Name}s, constructed directly from a byte-based
 * input source.
 *
 * @author Tatu Saloranta
 */
public final class BytesToNameCanonicalizer
{
    protected static final int DEFAULT_TABLE_SIZE = 64;

    /**
     * Let's not expand symbol tables past some maximum size;
     * this should protected against OOMEs caused by large documents
     * with unique (~= random) names.
     */
    protected static final int MAX_TABLE_SIZE = 0x10000; // 64k entries == 256k mem
    
    /**
     * Let's only share reasonably sized symbol tables. Max size set to 3/4 of 16k;
     * this corresponds to 64k main hash index. This should allow for enough distinct
     * names for almost any case.
     */
    final static int MAX_ENTRIES_FOR_REUSE = 6000;

    /**
     * Also: to thwart attacks based on hash collisions (which may or may not
     * be cheap to calculate), we will need to detect "too long"
     * collision chains. Let's start with static value of 255 entries
     * for the longest legal chain.
     *<p>
     * Note: longest chain we have been able to produce without malicious
     * intent has been 60 (with "com.fasterxml.jackson.core.main.TestWithTonsaSymbols");
     * our setting should be reasonable here.
     * 
     * @since 1.9.9
     */
    final static int MAX_COLL_CHAIN_LENGTH = 255;

    /**
     * And to support reduce likelihood of accidental collisions causing
     * exceptions, let's prevent reuse of tables with long collision
     * overflow lists as well.
     * 
     * @since 1.9.9
     */
    final static int MAX_COLL_CHAIN_FOR_REUSE  = 63;

    final static int MIN_HASH_SIZE = 16;

    final static int INITIAL_COLLISION_LEN = 32;

    /**
     * Bucket index is 8 bits, and value 0 is reserved to represent
     * 'empty' status.
     */
    final static int LAST_VALID_BUCKET = 0xFE;
    
    /*
    /**********************************************************
    /* Linkage, needed for merging symbol tables
    /**********************************************************
     */

    final protected BytesToNameCanonicalizer _parent;

    /**
     * Seed value we use as the base to make hash codes non-static between
     * different runs, but still stable for lifetime of a single symbol table
     * instance.
     * This is done for security reasons, to avoid potential DoS attack via
     * hash collisions.
     * 
     * @since 1.9.9
     */
    final private int _hashSeed;
    
    /*
    /**********************************************************
    /* Main table state
    /**********************************************************
     */

    /**
     * Whether canonial symbol Strings are to be intern()ed before added
     * to the table or not
     */
    private final boolean _intern;
    
    // // // First, global information

    /**
     * Total number of Names in the symbol table
     */
    private int _count;

    /**
     * We need to keep track of the longest collision list; this is needed
     * both to indicate problems with attacks and to allow flushing for
     * other cases.
     * 
     * @since 1.9.9
     */
    protected int _longestCollisionList;
    
    // // // Then information regarding primary hash array and its
    // // // matching Name array

    /**
     * Mask used to truncate 32-bit hash value to current hash array
     * size; essentially, hash array size - 1 (since hash array sizes
     * are 2^N).
     */
    private int _mainHashMask;

    /**
     * Array of 2^N size, which contains combination
     * of 24-bits of hash (0 to indicate 'empty' slot),
     * and 8-bit collision bucket index (0 to indicate empty
     * collision bucket chain; otherwise subtract one from index)
     */
    private int[] _mainHash;

    /**
     * Array that contains <code>Name</code> instances matching
     * entries in <code>_mainHash</code>. Contains nulls for unused
     * entries.
     */
    private Name[] _mainNames;

    // // // Then the collision/spill-over area info

    /**
     * Array of heads of collision bucket chains; size dynamically
     */
    private Bucket[] _collList;

    /**
     * Total number of Names in collision buckets (included in
     * <code>_count</code> along with primary entries)
     */
    private int _collCount;

    /**
     * Index of the first unused collision bucket entry (== size of
     * the used portion of collision list): less than
     * or equal to 0xFF (255), since max number of entries is 255
     * (8-bit, minus 0 used as 'empty' marker)
     */
    private int _collEnd;

    // // // Info regarding pending rehashing...

    /**
     * This flag is set if, after adding a new entry, it is deemed
     * that a rehash is warranted if any more entries are to be added.
     */
    private transient boolean _needRehash;

    /*
    /**********************************************************
    /* Sharing, versioning
    /**********************************************************
     */

    // // // Which of the buffers may be shared (and are copy-on-write)?

    /**
     * Flag that indicates whether underlying data structures for
     * the main hash area are shared or not. If they are, then they
     * need to be handled in copy-on-write way, i.e. if they need
     * to be modified, a copy needs to be made first; at this point
     * it will not be shared any more, and can be modified.
     *<p>
     * This flag needs to be checked both when adding new main entries,
     * and when adding new collision list queues (i.e. creating a new
     * collision list head entry)
     */
    private boolean _mainHashShared;

    private boolean _mainNamesShared;

    /**
     * Flag that indicates whether underlying data structures for
     * the collision list are shared or not. If they are, then they
     * need to be handled in copy-on-write way, i.e. if they need
     * to be modified, a copy needs to be made first; at this point
     * it will not be shared any more, and can be modified.
     *<p>
     * This flag needs to be checked when adding new collision entries.
     */
    private boolean _collListShared;

    /*
    /**********************************************************
    /* Construction, merging
    /**********************************************************
     */

    /**
     * Factory method to call to create a symbol table instance with a
     * randomized seed value.
     */
    public static BytesToNameCanonicalizer createRoot()
    {
        /* [Issue-21]: Need to use a variable seed, to thwart hash-collision
         * based attacks.
         */
        long now = System.currentTimeMillis();
        // ensure it's not 0; and might as well require to be odd so:
        int seed = (((int) now) + ((int) now >>> 32)) | 1;
        return createRoot(seed);
    }

    /**
     * Factory method that should only be called from unit tests, where seed
     * value should remain the same.
     */
    protected static BytesToNameCanonicalizer createRoot(int hashSeed) {
        return new BytesToNameCanonicalizer(DEFAULT_TABLE_SIZE, true, hashSeed);
    }
    
    /**
     * @param intern Whether canonical symbol Strings should be interned
     *   or not
     */
    public synchronized BytesToNameCanonicalizer makeChild(boolean canonicalize,
        boolean intern)
    {
        return new BytesToNameCanonicalizer(this, intern, _hashSeed);
    }

    /**
     * Method called by the using code to indicate it is done
     * with this instance. This lets instance merge accumulated
     * changes into parent (if need be), safely and efficiently,
     * and without calling code having to know about parent
     * information
     */
    public void release()
    {
        if (maybeDirty() && _parent != null) {
            _parent.mergeChild(this);
            /* Let's also mark this instance as dirty, so that just in
             * case release was too early, there's no corruption
             * of possibly shared data.
             */
            markAsShared();
        }
    }

    private BytesToNameCanonicalizer(int hashSize, boolean intern, int seed)
    {
        _parent = null;
        _hashSeed = seed;
        _intern = intern;
        // Sanity check: let's now allow hash sizes below certain minimum value
        if (hashSize < MIN_HASH_SIZE) {
            hashSize = MIN_HASH_SIZE;
        } else {
            /* Also; size must be 2^N; otherwise hash algorithm won't
             * work... so let's just pad it up, if so
             */
            if ((hashSize & (hashSize - 1)) != 0) { // only true if it's 2^N
                int curr = MIN_HASH_SIZE;
                while (curr < hashSize) {
                    curr += curr;
                }
                hashSize = curr;
            }
        }
        initTables(hashSize);
    }

    /**
     * Constructor used when creating a child instance
     */
    private BytesToNameCanonicalizer(BytesToNameCanonicalizer parent, boolean intern,
            int seed)
    {
        _parent = parent;
        _hashSeed = seed;
        _intern = intern;

        // First, let's copy the state as is:
        _count = parent._count;
        _mainHashMask = parent._mainHashMask;
        _mainHash = parent._mainHash;
        _mainNames = parent._mainNames;
        _collList = parent._collList;
        _collCount = parent._collCount;
        _collEnd = parent._collEnd;
        _longestCollisionList = parent._longestCollisionList;
        _needRehash = false;
        // And consider all shared, so far:
        _mainHashShared = true;
        _mainNamesShared = true;
        _collListShared = true;
    }

    private void initTables(int hashSize)
    {
        _count = 0;
        _longestCollisionList = 0;
        _mainHash = new int[hashSize];
        _mainNames = new Name[hashSize];
        _mainHashShared = false;
        _mainNamesShared = false;
        _mainHashMask = hashSize - 1;

        _collListShared = true; // just since it'll need to be allocated
        _collList = null;
        _collEnd = 0;

        _needRehash = false;
    }

    private synchronized void mergeChild(BytesToNameCanonicalizer child)
    {
        // Only makes sense if child has more entries
        int childCount = child._count;
        if (childCount <= _count) {
            return;
        }

        /* One caveat: let's try to avoid problems with
         * degenerate cases of documents with generated "random"
         * names: for these, symbol tables would bloat indefinitely.
         * One way to do this is to just purge tables if they grow
         * too large, and that's what we'll do here.
         */
        if (child.size() > MAX_ENTRIES_FOR_REUSE
                || child._longestCollisionList > MAX_COLL_CHAIN_FOR_REUSE) {
            /* Should there be a way to get notified about this
             * event, to log it or such? (as it's somewhat abnormal
             * thing to happen)
             */
            // At any rate, need to clean up the tables, then:
            initTables(DEFAULT_TABLE_SIZE);
        } else {
            _count = child._count;
            _longestCollisionList = child._longestCollisionList;
            _mainHash = child._mainHash;
            _mainNames = child._mainNames;
            _mainHashShared = true; // shouldn't matter for parent
            _mainNamesShared = true; // - "" -
            _mainHashMask = child._mainHashMask;
            _collList = child._collList;
            _collCount = child._collCount;
            _collEnd = child._collEnd;
        }
    }

    private void markAsShared()
    {
        _mainHashShared = true;
        _mainNamesShared = true;
        _collListShared = true;
    }

    /*
    /**********************************************************
    /* API, accessors
    /**********************************************************
     */

    public int size() { return _count; }

    /**
     * @since 1.9.9
     */
    public int bucketCount() { return _mainHash.length; }
    
    /**
     * Method called to check to quickly see if a child symbol table
     * may have gotten additional entries. Used for checking to see
     * if a child table should be merged into shared table.
     */
    public boolean maybeDirty() {
        return !_mainHashShared;
    }

    /**
     * @since 1.9.9
     */
    public int hashSeed() { return _hashSeed; }
    
    /**
     * Method mostly needed by unit tests; calculates number of
     * entries that are in collision list. Value can be at most
     * ({@link #size} - 1), but should usually be much lower, ideally 0.
     * 
     * @since 1.9.9
     */
    public int collisionCount() {
        return _collCount;
    }

    /**
     * Method mostly needed by unit tests; calculates length of the
     * longest collision chain. This should typically be a low number,
     * but may be up to {@link #size} - 1 in the pathological case
     * 
     * @since 1.9.9
     */
    public int maxCollisionLength() {
        return _longestCollisionList;
    }

    /*
    /**********************************************************
    /* Public API, accessing symbols:
    /**********************************************************
     */
    
    public static Name getEmptyName()
    {
        return Name1.getEmptyName();
    }

    /**
     * Finds and returns name matching the specified symbol, if such
     * name already exists in the table.
     * If not, will return null.
     *<p>
     * Note: separate methods to optimize common case of
     * short element/attribute names (4 or less ascii characters)
     *
     * @param firstQuad int32 containing first 4 bytes of the name;
     *   if the whole name less than 4 bytes, padded with zero bytes
     *   in front (zero MSBs, ie. right aligned)
     *
     * @return Name matching the symbol passed (or constructed for
     *   it)
     */
    public Name findName(int firstQuad)
    {
        int hash = calcHash(firstQuad);
        int ix = (hash & _mainHashMask);
        int val = _mainHash[ix];
        
        /* High 24 bits of the value are low 24 bits of hash (low 8 bits
         * are bucket index)... match?
         */
        if ((((val >> 8) ^ hash) << 8) == 0) { // match
            // Ok, but do we have an actual match?
            Name name = _mainNames[ix];
            if (name == null) { // main slot empty; can't find
                return null;
            }
            if (name.equals(firstQuad)) {
                return name;
            }
        } else if (val == 0) { // empty slot? no match
            return null;
        }
        // Maybe a spill-over?
        val &= 0xFF;
        if (val > 0) { // 0 means 'empty'
            val -= 1; // to convert from 1-based to 0...
            Bucket bucket = _collList[val];
            if (bucket != null) {
                return bucket.find(hash, firstQuad, 0);
            }
        }
        // Nope, no match whatsoever
        return null;
    }

    /**
     * Finds and returns name matching the specified symbol, if such
     * name already exists in the table.
     * If not, will return null.
     *<p>
     * Note: separate methods to optimize common case of relatively
     * short element/attribute names (8 or less ascii characters)
     *
     * @param firstQuad int32 containing first 4 bytes of the name.
     * @param secondQuad int32 containing bytes 5 through 8 of the
     *   name; if less than 8 bytes, padded with up to 3 zero bytes
     *   in front (zero MSBs, ie. right aligned)
     *
     * @return Name matching the symbol passed (or constructed for
     *   it)
     */
    public Name findName(int firstQuad, int secondQuad)
    {
        int hash = calcHash(firstQuad, secondQuad);
        int ix = (hash & _mainHashMask);
        int val = _mainHash[ix];
        
        /* High 24 bits of the value are low 24 bits of hash (low 8 bits
         * are bucket index)... match?
         */
        if ((((val >> 8) ^ hash) << 8) == 0) { // match
            // Ok, but do we have an actual match?
            Name name = _mainNames[ix];
            if (name == null) { // main slot empty; can't find
                return null;
            }
            if (name.equals(firstQuad, secondQuad)) {
                return name;
            }
        } else if (val == 0) { // empty slot? no match
            return null;
        }
        // Maybe a spill-over?
        val &= 0xFF;
        if (val > 0) { // 0 means 'empty'
            val -= 1; // to convert from 1-based to 0...
            Bucket bucket = _collList[val];
            if (bucket != null) {
                return bucket.find(hash, firstQuad, secondQuad);
            }
        }
        // Nope, no match whatsoever
        return null;
    }

    /**
     * Finds and returns name matching the specified symbol, if such
     * name already exists in the table; or if not, creates name object,
     * adds to the table, and returns it.
     *<p>
     * Note: this is the general purpose method that can be called for
     * names of any length. However, if name is less than 9 bytes long,
     * it is preferable to call the version optimized for short
     * names.
     *
     * @param quads Array of int32s, each of which contain 4 bytes of
     *   encoded name
     * @param qlen Number of int32s, starting from index 0, in quads
     *   parameter
     *
     * @return Name matching the symbol passed (or constructed for it)
     */
    public Name findName(int[] quads, int qlen)
    {
        if (qlen < 3) { // another sanity check
            return findName(quads[0], (qlen < 2) ? 0 : quads[1]);
        }
        int hash = calcHash(quads, qlen);
        // (for rest of comments regarding logic, see method above)
        int ix = (hash & _mainHashMask);
        int val = _mainHash[ix];
        if ((((val >> 8) ^ hash) << 8) == 0) {
            Name name = _mainNames[ix];
            if (name == null // main slot empty; no collision list then either
                || name.equals(quads, qlen)) { // should be match, let's verify
                return name;
            }
        } else if (val == 0) { // empty slot? no match
            return null;
        }
        val &= 0xFF;
        if (val > 0) { // 0 means 'empty'
            val -= 1; // to convert from 1-based to 0...
            Bucket bucket = _collList[val];
            if (bucket != null) {
                return bucket.find(hash, quads, qlen);
            }
        }
        return null;
    }

    /*
    /**********************************************************
    /* API, mutators
    /**********************************************************
     */

    public Name addName(String symbolStr, int q1, int q2)
    {
        if (_intern) {
            symbolStr = InternCache.instance.intern(symbolStr);
        }
        int hash = (q2 == 0) ? calcHash(q1) : calcHash(q1, q2);
        Name symbol = constructName(hash, symbolStr, q1, q2);
        _addSymbol(hash, symbol);
        return symbol;
    }
    
    public Name addName(String symbolStr, int[] quads, int qlen)
    {
        if (_intern) {
            symbolStr = InternCache.instance.intern(symbolStr);
        }
        int hash;
        if (qlen < 3) {
            hash = (qlen == 1) ? calcHash(quads[0]) : calcHash(quads[0], quads[1]);
        } else {
            hash = calcHash(quads, qlen);
        }
        Name symbol = constructName(hash, symbolStr, quads, qlen);
        _addSymbol(hash, symbol);
        return symbol;
    }
    
    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    /* Note on hash calculation: we try to make it more difficult to
     * generate collisions automatically; part of this is to avoid
     * simple "multiply-add" algorithm (like JDK String.hashCode()),
     * and add bit of shifting. And other part is to make this
     * non-linear, at least for shorter symbols.
     */
    
    // JDK uses 31; other fine choices are 33 and 65599, let's use 33
    // as it seems to give fewest collisions for us
    // (see [http://www.cse.yorku.ca/~oz/hash.html] for details)
    private final static int MULT = 33;
    
    public final int calcHash(int firstQuad)
    {
        int hash = firstQuad ^ _hashSeed;
        hash += (hash >>> 15); // to xor hi- and low- 16-bits
        hash ^= (hash >>> 9); // as well as lowest 2 bytes
        return hash;
    }

    public final int calcHash(int firstQuad, int secondQuad)
    {
        /* For two quads, let's change algorithm a bit, to spice
         * things up (can do bit more processing anyway)
         */
        
        int hash = firstQuad;
        hash += (hash >>> 15); // try mixing first and second byte pairs first
        hash ^= ((secondQuad + _hashSeed) * MULT); // then add second quad
        hash += (hash >>> 9); // and shuffle some more
        return hash;
    }

    public final int calcHash(int[] quads, int qlen)
    {
        // Note: may be called for qlen < 3; but has at least one int
        if (qlen < 3) {
            throw new IllegalArgumentException();
        }

        /* And then change handling again for "multi-quad" case; mostly
         * to make calculation of collisions less fun. For example,
         * add seed bit later in the game, and switch plus/xor around,
         * use different shift lengths.
         */
        int hash = quads[0];
        hash ^= (hash >>> 9);
        hash += ((quads[1] + _hashSeed) * MULT);
        hash ^= (hash >>> 15);
        hash ^= (quads[2] * MULT);
        hash += (hash >>> 17);
        
        for (int i = 3; i < qlen; ++i) {
            hash = (hash * MULT) + (quads[i] + 1);
            // for longer entries, mess a bit in-between too
            hash ^= (hash >>> 3);
        }

        // and finally shuffle some more once done
        hash += (hash >>> 15); // to get high-order bits to mix more
        hash ^= (hash >>> 9); // as well as lowest 2 bytes
        return hash;
    }

    // Method only used by unit tests
    protected static int[] calcQuads(byte[] wordBytes)
    {
        int blen = wordBytes.length;
        int[] result = new int[(blen + 3) / 4];
        for (int i = 0; i < blen; ++i) {
            int x = wordBytes[i] & 0xFF;

            if (++i < blen) {
                x = (x << 8) | (wordBytes[i] & 0xFF);
                if (++i < blen) {
                    x = (x << 8) | (wordBytes[i] & 0xFF);
                    if (++i < blen) {
                        x = (x << 8) | (wordBytes[i] & 0xFF);
                    }
                }
            }
            result[i >> 2] = x;
        }
        return result;
    }

    /*
    /**********************************************************
    /* Standard methods
    /**********************************************************
     */

    /*
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[BytesToNameCanonicalizer, size: ");
        sb.append(_count);
        sb.append('/');
        sb.append(_mainHash.length);
        sb.append(", ");
        sb.append(_collCount);
        sb.append(" coll; avg length: ");

        // Average length: minimum of 1 for all (1 == primary hit);
        // and then 1 per each traversal for collisions/buckets
        //int maxDist = 1;
        int pathCount = _count;
        for (int i = 0; i < _collEnd; ++i) {
            int spillLen = _collList[i].length();
            for (int j = 1; j <= spillLen; ++j) {
                pathCount += j;
            }
        }
        double avgLength;

        if (_count == 0) {
            avgLength = 0.0;
        } else {
            avgLength = (double) pathCount / (double) _count;
        }
        // let's round up a bit (two 2 decimal places)
        //avgLength -= (avgLength % 0.01);

        sb.append(avgLength);
        sb.append(']');
        return sb.toString();
    }
    */

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    private void _addSymbol(int hash, Name symbol)
    {
        if (_mainHashShared) { // always have to modify main entry
            unshareMain();
        }
        // First, do we need to rehash?
        if (_needRehash) {
            rehash();
        }

        ++_count;

        /* Ok, enough about set up: now we need to find the slot to add
         * symbol in:
         */
        int ix = (hash & _mainHashMask);
        if (_mainNames[ix] == null) { // primary empty?
            _mainHash[ix] = (hash << 8);
            if (_mainNamesShared) {
                unshareNames();
            }
            _mainNames[ix] = symbol;
        } else { // nope, it's a collision, need to spill over
            /* How about spill-over area... do we already know the bucket
             * (is the case if it's not the first collision)
             */
            if (_collListShared) {
                unshareCollision(); // also allocates if list was null
            }
            ++_collCount;
            int entryValue = _mainHash[ix];
            int bucket = entryValue & 0xFF;
            if (bucket == 0) { // first spill over?
                if (_collEnd <= LAST_VALID_BUCKET) { // yup, still unshared bucket
                    bucket = _collEnd;
                    ++_collEnd;
                    // need to expand?
                    if (bucket >= _collList.length) {
                        expandCollision();
                    }
                } else { // nope, have to share... let's find shortest?
                    bucket = findBestBucket();
                }
                // Need to mark the entry... and the spill index is 1-based
                _mainHash[ix] = (entryValue & ~0xFF) | (bucket + 1);
            } else {
                --bucket; // 1-based index in value
            }
            
            // And then just need to link the new bucket entry in
            Bucket newB = new Bucket(symbol, _collList[bucket]);
            _collList[bucket] = newB;
            // but, be careful wrt attacks
            _longestCollisionList = Math.max(newB.length(), _longestCollisionList);
            if (_longestCollisionList > MAX_COLL_CHAIN_LENGTH) {
                reportTooManyCollisions(MAX_COLL_CHAIN_LENGTH);
            }
        }

        /* Ok. Now, do we need a rehash next time? Need to have at least
         * 50% fill rate no matter what:
         */
        {
            int hashSize = _mainHash.length;
            if (_count > (hashSize >> 1)) {
                int hashQuarter = (hashSize >> 2);
                /* And either strictly above 75% (the usual) or
                 * just 50%, and collision count >= 25% of total hash size
                 */
                if (_count > (hashSize - hashQuarter)) {
                    _needRehash = true;
                } else if (_collCount >= hashQuarter) {
                    _needRehash = true;
                }
            }
        }
    }

    private void rehash()
    {
        _needRehash = false;        
        // Note: since we'll make copies, no need to unshare, can just mark as such:
        _mainNamesShared = false;

        /* And then we can first deal with the main hash area. Since we
         * are expanding linearly (double up), we know there'll be no
         * collisions during this phase.
         */
        int[] oldMainHash = _mainHash;
        int len = oldMainHash.length;
        int newLen = len+len;

        /* 13-Mar-2010, tatu: Let's guard against OOME that could be caused by
         *    large documents with unique (or mostly so) names
         */
        if (newLen > MAX_TABLE_SIZE) {
            nukeSymbols();
            return;
        }
        
        _mainHash = new int[newLen];
        _mainHashMask = (newLen - 1);
        Name[] oldNames = _mainNames;
        _mainNames = new Name[newLen];
        int symbolsSeen = 0; // let's do a sanity check
        for (int i = 0; i < len; ++i) {
            Name symbol = oldNames[i];
            if (symbol != null) {
                ++symbolsSeen;
                int hash = symbol.hashCode();
                int ix = (hash & _mainHashMask);
                _mainNames[ix] = symbol;
                _mainHash[ix] = hash << 8; // will clear spill index
            }
        }

        /* And then the spill area. This may cause collisions, although
         * not necessarily as many as there were earlier. Let's allocate
         * same amount of space, however
         */
        int oldEnd = _collEnd;
        if (oldEnd == 0) { // no prior collisions...
            _longestCollisionList = 0;
            return;
        }

        _collCount = 0;
        _collEnd = 0;
        _collListShared = false;

        int maxColl = 0;
        
        Bucket[] oldBuckets = _collList;
        _collList = new Bucket[oldBuckets.length];
        for (int i = 0; i < oldEnd; ++i) {
            for (Bucket curr = oldBuckets[i]; curr != null; curr = curr._next) {
                ++symbolsSeen;
                Name symbol = curr._name;
                int hash = symbol.hashCode();
                int ix = (hash & _mainHashMask);
                int val = _mainHash[ix];
                if (_mainNames[ix] == null) { // no primary entry?
                    _mainHash[ix] = (hash << 8);
                    _mainNames[ix] = symbol;
                } else { // nope, it's a collision, need to spill over
                    ++_collCount;
                    int bucket = val & 0xFF;
                    if (bucket == 0) { // first spill over?
                        if (_collEnd <= LAST_VALID_BUCKET) { // yup, still unshared bucket
                            bucket = _collEnd;
                            ++_collEnd;
                            // need to expand?
                            if (bucket >= _collList.length) {
                                expandCollision();
                            }
                        } else { // nope, have to share... let's find shortest?
                            bucket = findBestBucket();
                        }
                        // Need to mark the entry... and the spill index is 1-based
                        _mainHash[ix] = (val & ~0xFF) | (bucket + 1);
                    } else {
                        --bucket; // 1-based index in value
                    }
                    // And then just need to link the new bucket entry in
                    Bucket newB = new Bucket(symbol, _collList[bucket]);
                    _collList[bucket] = newB;
                    maxColl = Math.max(maxColl, newB.length());
                }
            } // for (... buckets in the chain ...)
        } // for (... list of bucket heads ... )

        _longestCollisionList = maxColl;
        
        if (symbolsSeen != _count) { // sanity check
            throw new RuntimeException("Internal error: count after rehash "+symbolsSeen+"; should be "+_count);
        }
    }

    /**
     * Helper method called to empty all shared symbols, but to leave
     * arrays allocated
     */
    private void nukeSymbols()
    {
        _count = 0;
        _longestCollisionList = 0;
        Arrays.fill(_mainHash, 0);
        Arrays.fill(_mainNames, null);
        Arrays.fill(_collList, null);
        _collCount = 0;
        _collEnd = 0;
    }
    
    /**
     * Method called to find the best bucket to spill a Name over to:
     * usually the first bucket that has only one entry, but in general
     * first one of the buckets with least number of entries
     */
    private int findBestBucket()
    {
        Bucket[] buckets = _collList;
        int bestCount = Integer.MAX_VALUE;
        int bestIx = -1;

        for (int i = 0, len = _collEnd; i < len; ++i) {
            int count = buckets[i].length();
            if (count < bestCount) {
                if (count == 1) { // best possible
                    return i;
                }
                bestCount = count;
                bestIx = i;
            }
        }
        return bestIx;
    }

    /**
     * Method that needs to be called, if the main hash structure
     * is (may be) shared. This happens every time something is added,
     * even if addition is to the collision list (since collision list
     * index comes from lowest 8 bits of the primary hash entry)
     */
    private void unshareMain()
    {
        int[] old = _mainHash;
        int len = _mainHash.length;

        _mainHash = new int[len];
        System.arraycopy(old, 0, _mainHash, 0, len);
        _mainHashShared = false;
    }

    private void unshareCollision()
    {
        Bucket[] old = _collList;
        if (old == null) {
            _collList = new Bucket[INITIAL_COLLISION_LEN];
        } else {
            int len = old.length;
            _collList = new Bucket[len];
            System.arraycopy(old, 0, _collList, 0, len);
        }
        _collListShared = false;
    }

    private void unshareNames()
    {
        Name[] old = _mainNames;
        int len = old.length;
        _mainNames = new Name[len];
        System.arraycopy(old, 0, _mainNames, 0, len);
        _mainNamesShared = false;
    }

    private void expandCollision()
    {
        Bucket[] old = _collList;
        int len = old.length;
        _collList = new Bucket[len+len];
        System.arraycopy(old, 0, _collList, 0, len);
    }


    /*
    /**********************************************************
    /* Constructing name objects
    /**********************************************************
     */

    private static Name constructName(int hash, String name, int q1, int q2)
    {     
        if (q2 == 0) { // one quad only?
            return new Name1(name, hash, q1);
        }
        return new Name2(name, hash, q1, q2);
    }

    private static Name constructName(int hash, String name, int[] quads, int qlen)
    {
        if (qlen < 4) { // Need to check for 3 quad one, can do others too
            switch (qlen) {
            case 1:
                return new Name1(name, hash, quads[0]);
            case 2:
                return new Name2(name, hash, quads[0], quads[1]);
            case 3:
                return new Name3(name, hash, quads[0], quads[1], quads[2]);
            default:
            }
        }
        // Otherwise, need to copy the incoming buffer
        int[] buf = new int[qlen];
        for (int i = 0; i < qlen; ++i) {
            buf[i] = quads[i];
        }
        return new NameN(name, hash, buf, qlen);
    }

    /*
    /**********************************************************
    /* Other helper methods
    /**********************************************************
     */
    
    /**
     * @since 1.9.9
     */
    protected void reportTooManyCollisions(int maxLen)
    {
        throw new IllegalStateException("Longest collision chain in symbol table (of size "+_count
                +") now exceeds maximum, "+maxLen+" -- suspect a DoS attack based on hash collisions");
    }
    
    /*
    /**********************************************************
    /* Helper classes
    /**********************************************************
     */

    final static class Bucket
    {
        protected final Name _name;
        protected final Bucket _next;
        private final int _length;

        Bucket(Name name, Bucket next)
        {
            _name = name;
            _next = next;
            _length = (next == null) ? 1 : next._length+1;
        }

        public int length() { return _length; }

        public Name find(int hash, int firstQuad, int secondQuad)
        {
            if (_name.hashCode() == hash) {
                if (_name.equals(firstQuad, secondQuad)) {
                    return _name;
                }
            }
            for (Bucket curr = _next; curr != null; curr = curr._next) {
                Name currName = curr._name;
                if (currName.hashCode() == hash) {
                    if (currName.equals(firstQuad, secondQuad)) {
                        return currName;
                    }
                }
            }
            return null;
        }

        public Name find(int hash, int[] quads, int qlen)
        {
            if (_name.hashCode() == hash) {
                if (_name.equals(quads, qlen)) {
                    return _name;
                }
            }
            for (Bucket curr = _next; curr != null; curr = curr._next) {
                Name currName = curr._name;
                if (currName.hashCode() == hash) {
                    if (currName.equals(quads, qlen)) {
                        return currName;
                    }
                }
            }
            return null;
        }
    }
}
