package org.gnit.lucenekmp.jdkport

/**
 * A subclass of RuleBasedBreakIterator that adds the ability to use a dictionary
 * to further subdivide ranges of text beyond what is possible using just the
 * state-table-based algorithm.  This is necessary, for example, to handle
 * word and line breaking in Thai, which doesn't use spaces between words.  The
 * state-table-based algorithm used by RuleBasedBreakIterator is used to divide
 * up text as far as possible, and then contiguous ranges of letters are
 * repeatedly compared against a list of known words (i.e., the dictionary)
 * to divide them up into words.
 *
 * DictionaryBasedBreakIterator uses the same rule language as RuleBasedBreakIterator,
 * but adds one more special substitution name: &lt;dictionary&gt;.  This substitution
 * name is used to identify characters in words in the dictionary.  The idea is that
 * if the iterator passes over a chunk of text that includes two or more characters
 * in a row that are included in &lt;dictionary&gt;, it goes back through that range and
 * derives additional break positions (if possible) using the dictionary.
 *
 * DictionaryBasedBreakIterator is also constructed with the filename of a dictionary
 * file.  It follows a prescribed search path to locate the dictionary (right now,
 * it looks for it in /com/ibm/text/resources in each directory in the classpath,
 * and won't find it in JAR files, but this location is likely to change).  The
 * dictionary file is in a serialized binary format.  We have a very primitive (and
 * slow) BuildDictionaryFile utility for creating dictionary files, but aren't
 * currently making it public.  Contact us for help.
 */
@Ported(from = "sun.text.DictionaryBasedBreakIterator")
class DictionaryBasedBreakIterator(
    /*ruleFile: String,*/ ruleData: ByteArray,
    /*dictionaryFile: String,*/ dictionaryData: ByteArray
) : RuleBasedBreakIterator(/*ruleFile,*/ ruleData) {// lucene-kmp needs to run without reading file system, so data needs to be embedded and gain via memory, no files.
    /**
     * a list of known words that is used to divide up contiguous ranges of letters,
     * stored in a compressed, indexed, format that offers fast access
     */
    private val dictionary: BreakDictionary

    /**
     * a list of flags indicating which character categories are contained in
     * the dictionary file (this is used to determine which ranges of characters
     * to apply the dictionary to)
     */
    private lateinit var categoryFlags: BooleanArray

    /**
     * a temporary hiding place for the number of dictionary characters in the
     * last range passed over by next()
     */
    private var dictionaryCharCount = 0

    /**
     * when a range of characters is divided up using the dictionary, the break
     * positions that are discovered are stored here, preventing us from having
     * to use either the dictionary or the state table again until the iterator
     * leaves this range of text
     */
    private var cachedBreakPositions: IntArray? = null

    /**
     * if cachedBreakPositions is not null, this indicates which item in the
     * cache the current iteration position refers to
     */
    private var positionInCache = 0

    /**
     * Constructs a DictionaryBasedBreakIterator.
     *
     * @param ruleFile       the name of the rule data file
     * @param ruleData       the rule data loaded from the rule data file
     * @param dictionaryFile the name of the dictionary file
     * @param dictionaryData the dictionary data loaded from the dictionary file
     * @throws MissingResourceException if rule data or dictionary initialization failed
     */
    init {
        val tmp: ByteArray? = super.additionalData
        if (tmp != null) {
            prepareCategoryFlags(tmp)
            super.additionalData = null
        }
        dictionary = BreakDictionary(/*dictionaryFile,*/ dictionaryData)
    }

    private fun prepareCategoryFlags(data: ByteArray) {
        categoryFlags = BooleanArray(data.size)
        for (i in data.indices) {
            categoryFlags[i] = if (data[i] == 1.toByte()) true else false
        }
    }

    override fun setText(newText: CharacterIterator) {
        super.setText(newText)
        cachedBreakPositions = null
        dictionaryCharCount = 0
        positionInCache = 0
    }

    /**
     * Sets the current iteration position to the beginning of the text.
     * (i.e., the CharacterIterator's starting offset).
     * @return The offset of the beginning of the text.
     */
    override fun first(): Int {
        cachedBreakPositions = null
        dictionaryCharCount = 0
        positionInCache = 0
        return super.first()
    }

    /**
     * Sets the current iteration position to the end of the text.
     * (i.e., the CharacterIterator's ending offset).
     * @return The text's past-the-end offset.
     */
    override fun last(): Int {
        cachedBreakPositions = null
        dictionaryCharCount = 0
        positionInCache = 0
        return super.last()
    }

    /**
     * Advances the iterator one step backwards.
     * @return The position of the last boundary position before the
     * current iteration position
     */
    override fun previous(): Int {
        val text: CharacterIterator = text

        // if we have cached break positions and we're still in the range
        // covered by them, just move one step backward in the cache
        if (cachedBreakPositions != null && positionInCache > 0) {
            --positionInCache
            text.setIndex(cachedBreakPositions!![positionInCache])
            return cachedBreakPositions!![positionInCache]
        } else {
            cachedBreakPositions = null
            val result: Int = super.previous()
            if (cachedBreakPositions != null) {
                positionInCache = cachedBreakPositions!!.size - 2
            }
            return result
        }
    }

    /**
     * Sets the current iteration position to the last boundary position
     * before the specified position.
     * @param offset The position to begin searching from
     * @return The position of the last boundary before "offset"
     */
    override fun preceding(offset: Int): Int {
        val text: CharacterIterator = text
        checkOffset(offset, text)

        // if we have no cached break positions, or "offset" is outside the
        // range covered by the cache, we can just call the inherited routine
        // (which will eventually call other routines in this class that may
        // refresh the cache)
        if (cachedBreakPositions == null || offset <= cachedBreakPositions!![0] || offset > cachedBreakPositions!![cachedBreakPositions!!.size - 1]) {
            cachedBreakPositions = null
            return super.preceding(offset)
        } else {
            positionInCache = 0
            while (positionInCache < cachedBreakPositions!!.size
                && offset > cachedBreakPositions!![positionInCache]
            ) {
                ++positionInCache
            }
            --positionInCache
            text.setIndex(cachedBreakPositions!![positionInCache])
            return text.index
        }
    }

    /**
     * Sets the current iteration position to the first boundary position after
     * the specified position.
     * @param offset The position to begin searching forward from
     * @return The position of the first boundary after "offset"
     */
    override fun following(offset: Int): Int {
        val text: CharacterIterator = text
        checkOffset(offset, text)

        // if we have no cached break positions, or if "offset" is outside the
        // range covered by the cache, then dump the cache and call our
        // inherited following() method.  This will call other methods in this
        // class that may refresh the cache.
        if (cachedBreakPositions == null || offset < cachedBreakPositions!![0] || offset >= cachedBreakPositions!![cachedBreakPositions!!.size - 1]) {
            cachedBreakPositions = null
            return super.following(offset)
        } else {
            positionInCache = 0
            while (positionInCache < cachedBreakPositions!!.size
                && offset >= cachedBreakPositions!![positionInCache]
            ) {
                ++positionInCache
            }
            text.setIndex(cachedBreakPositions!![positionInCache])
            return text.index
        }
    }

    /**
     * This is the implementation function for next().
     */
    override fun handleNext(): Int {
        val text: CharacterIterator = text

        // if there are no cached break positions, or if we've just moved
        // off the end of the range covered by the cache, we have to dump
        // and possibly regenerate the cache
        if (cachedBreakPositions == null ||
            positionInCache == cachedBreakPositions!!.size - 1
        ) {
            // start by using the inherited handleNext() to find a tentative return
            // value.   dictionaryCharCount tells us how many dictionary characters
            // we passed over on our way to the tentative return value

            val startPos: Int = text.index
            dictionaryCharCount = 0
            val result: Int = super.handleNext()

            // if we passed over more than one dictionary character, then we use
            // divideUpDictionaryRange() to regenerate the cached break positions
            // for the new range
            if (dictionaryCharCount > 1 && result - startPos > 1) {
                divideUpDictionaryRange(startPos, result)
            } else {
                cachedBreakPositions = null
                return result
            }
        }

        // if the cache of break positions has been regenerated (or existed all
        // along), then just advance to the next break position in the cache
        // and return it
        if (cachedBreakPositions != null) {
            ++positionInCache
            text.setIndex(cachedBreakPositions!![positionInCache])
            return cachedBreakPositions!![positionInCache]
        }
        return -9999 // SHOULD NEVER GET HERE!
    }

    /**
     * Looks up a character category for a character.
     */
    override fun lookupCategory(c: Int): Int {
        // this override of lookupCategory() exists only to keep track of whether we've
        // passed over any dictionary characters.  It calls the inherited lookupCategory()
        // to do the real work, and then checks whether its return value is one of the
        // categories represented in the dictionary.  If it is, bump the dictionary-
        // character count.
        val result: Int = super.lookupCategory(c)
        if (result != IGNORE.toInt() && categoryFlags[result]) {
            ++dictionaryCharCount
        }
        return result
    }

    /**
     * This is the function that actually implements the dictionary-based
     * algorithm.  Given the endpoints of a range of text, it uses the
     * dictionary to determine the positions of any boundaries in this
     * range.  It stores all the boundary positions it discovers in
     * cachedBreakPositions so that we only have to do this work once
     * for each time we enter the range.
     */
    private fun divideUpDictionaryRange(startPos: Int, endPos: Int) {
        val text: CharacterIterator = text

        // the range we're dividing may begin or end with non-dictionary characters
        // (i.e., for line breaking, we may have leading or trailing punctuation
        // that needs to be kept with the word).  Seek from the beginning of the
        // range to the first dictionary character
        text.setIndex(startPos)
        var c: Int = current
        var category = lookupCategory(c)
        while (category == IGNORE.toInt() || !categoryFlags[category]) {
            c = next
            category = lookupCategory(c)
        }

        // initialize.  We maintain two stacks: currentBreakPositions contains
        // the list of break positions that will be returned if we successfully
        // finish traversing the whole range now.  possibleBreakPositions lists
        // all other possible word ends we've passed along the way.  (Whenever
        // we reach an error [a sequence of characters that can't begin any word
        // in the dictionary], we back up, possibly delete some breaks from
        // currentBreakPositions, move a break from possibleBreakPositions
        // to currentBreakPositions, and start over from there.  This process
        // continues in this way until we either successfully make it all the way
        // across the range, or exhaust all of our combinations of break
        // positions.)
        var currentBreakPositions: ArrayDeque<Int> = ArrayDeque()
        val possibleBreakPositions: ArrayDeque<Int> = ArrayDeque()
        val wrongBreakPositions: MutableList<Int> = mutableListOf()

        // the dictionary is implemented as a trie, which is treated as a state
        // machine.  -1 represents the end of a legal word.  Every word in the
        // dictionary is represented by a path from the root node to -1.  A path
        // that ends in state 0 is an illegal combination of characters.
        var state = 0

        // these two variables are used for error handling.  We keep track of the
        // farthest we've gotten through the range being divided, and the combination
        // of breaks that got us that far.  If we use up all possible break
        // combinations, the text contains an error or a word that's not in the
        // dictionary.  In this case, we "bless" the break positions that got us the
        // farthest as real break positions, and then start over from scratch with
        // the character where the error occurred.
        var farthestEndPoint: Int = text.index
        var bestBreakPositions: ArrayDeque<Int>? = null

        // initialize (we always exit the loop with a break statement)
        c = current
        while (true) {
            // if we can transition to state "-1" from our current state, we're
            // on the last character of a legal word.  Push that position onto
            // the possible-break-positions stack

            if (dictionary.getNextState(state, 0).toInt() == -1) {
                possibleBreakPositions.push(text.index)
            }

            // look up the new state to transition to in the dictionary
            state = dictionary.getNextStateFromCharacter(state, c).toInt()

            // if the character we're sitting on causes us to transition to
            // the "end of word" state, then it was a non-dictionary character
            // and we've successfully traversed the whole range.  Drop out
            // of the loop.
            if (state == -1) {
                currentBreakPositions.push(text.index)
                break
            } else if (state == 0 || text.index >= endPos) {
                // if this is the farthest we've gotten, take note of it in
                // case there's an error in the text

                if (text.index > farthestEndPoint) {
                    farthestEndPoint = text.index

                    val currentBreakPositionsCopy: ArrayDeque<Int> = ArrayDeque()
                        /*currentBreakPositions.clone() as ArrayDeque<Int>*/
                    currentBreakPositionsCopy.addAll(currentBreakPositions)

                    bestBreakPositions = currentBreakPositionsCopy
                }

                // wrongBreakPositions is a list of all break positions
                // we've tried starting that didn't allow us to traverse
                // all the way through the text.  Every time we pop a
                // break position off of currentBreakPositions, we put it
                // into wrongBreakPositions to avoid trying it again later.
                // If we make it to this spot, we're either going to back
                // up to a break in possibleBreakPositions and try starting
                // over from there, or we've exhausted all possible break
                // positions and are going to do the fallback procedure.
                // This loop prevents us from messing with anything in
                // possibleBreakPositions that didn't work as a starting
                // point the last time we tried it (this is to prevent a bunch of
                // repetitive checks from slowing down some extreme cases)
                while (!possibleBreakPositions.isEmpty()
                    && wrongBreakPositions.contains(possibleBreakPositions.peek())
                ) {
                    possibleBreakPositions.pop()
                }

                // if we've used up all possible break-position combinations, there's
                // an error or an unknown word in the text.  In this case, we start
                // over, treating the farthest character we've reached as the beginning
                // of the range, and "blessing" the break positions that got us that
                // far as real break positions
                if (possibleBreakPositions.isEmpty()) {
                    if (bestBreakPositions != null) {
                        currentBreakPositions = bestBreakPositions
                        if (farthestEndPoint < endPos) {
                            text.setIndex(farthestEndPoint + 1)
                        } else {
                            break
                        }
                    } else {
                        if ((currentBreakPositions.isEmpty() ||
                                    currentBreakPositions.peek() != text.index)
                            && text.index != startPos
                        ) {
                            currentBreakPositions.push(text.index)
                        }
                        next() // was getNext() is it ok to port like this? not sure
                        currentBreakPositions.push(text.index)
                    }
                } else {
                    val temp: Int = possibleBreakPositions.pop()
                    var temp2: Int?
                    while (!currentBreakPositions.isEmpty() && temp <
                        currentBreakPositions.peek()!!
                    ) {
                        temp2 = currentBreakPositions.pop()
                        wrongBreakPositions.add(temp2)
                    }
                    currentBreakPositions.push(temp)
                    text.setIndex(currentBreakPositions.peek()!!)
                }

                // re-sync "c" for the next go-round, and drop out of the loop if
                // we've made it off the end of the range
                c = current
                if (text.index >= endPos) {
                    break
                }
            } else {
                c = next
            }
        }

        // dump the last break position in the list, and replace it with the actual
        // end of the range (which may be the same character, or may be further on
        // because the range actually ended with non-dictionary characters we want to
        // keep with the word)
        if (!currentBreakPositions.isEmpty()) {
            currentBreakPositions.pop()
        }
        currentBreakPositions.push(endPos)

        // create a regular array to hold the break positions and copy
        // the break positions from the stack to the array (in addition,
        // our starting position goes into this array as a break position).
        // This array becomes the cache of break positions used by next()
        // and previous(), so this is where we actually refresh the cache.
        cachedBreakPositions = IntArray(currentBreakPositions.size + 1)
        cachedBreakPositions!![0] = startPos

        for (i in currentBreakPositions.indices) {
            cachedBreakPositions!![i + 1] = currentBreakPositions.elementAt(i)
        }
        positionInCache = 0
    }
}
