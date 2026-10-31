package com.example.coverboard.engine

import android.content.Context
import android.graphics.PointF
import com.example.coverboard.data.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min

// ═════════════════════════════════════════════════════════════════════════════
//  COVERBOARD PREDICTION & LEXICON ENGINE
// ═════════════════════════════════════════════════════════════════════════════

data class Scored(val word: String, val score: Float, val cost: Float = 0f)

class Lexicon(val words: Array<String>, val zipf: FloatArray) {
    private val index = HashMap<String, Int>(words.size * 2)
    private var sortedWords: Array<String> = emptyArray()
    private var sortedIdx: IntArray = intArrayOf()
    var byLength: Array<IntArray> = emptyArray()
    var byFirst: Map<Char, IntArray> = emptyMap()

    init {
        for (i in words.indices) index[words[i]] = i
        val order = words.indices.sortedBy { words[it] }
        sortedIdx = order.toIntArray()
        sortedWords = Array(order.size) { words[order[it]] }
        val maxLen = words.maxOfOrNull { it.length } ?: 0
        val lens = Array(maxLen + 2) { ArrayList<Int>() }
        val firsts = HashMap<Char, ArrayList<Int>>()
        for (i in words.indices) {
            lens[words[i].length].add(i)
            if (words[i].isNotEmpty()) firsts.getOrPut(words[i][0]) { ArrayList() }.add(i)
        }
        byLength = Array(lens.size) { lens[it].toIntArray() }
        byFirst = firsts.mapValues { it.value.toIntArray() }
    }

    /** Most frequent words, used as the "no context" fallback for next-word prediction. */
    val topWords: List<String> by lazy { words.indices.sortedByDescending { zipf[it] }.take(80).map { words[it] } }

    fun contains(w: String) = index.containsKey(w)
    fun score(w: String): Float = index[w]?.let { zipf[it] } ?: -1f

    private fun lowerBound(prefix: String): Int {
        var lo = 0
        var hi = sortedWords.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedWords[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun completions(prefix: String, limit: Int): List<Scored> {
        val top = ArrayList<Scored>(limit + 1)
        var i = lowerBound(prefix)
        while (i < sortedWords.size && sortedWords[i].startsWith(prefix)) {
            val s = zipf[sortedIdx[i]]
            if (top.size < limit || s > top.last().score) {
                var p = top.size
                while (p > 0 && top[p - 1].score < s) p--
                top.add(p, Scored(sortedWords[i], s))
                if (top.size > limit) top.removeAt(top.size - 1)
            }
            i++
        }
        return top
    }

    companion object {
        val EMPTY = Lexicon(emptyArray(), FloatArray(0))

        fun fromRanked(list: List<String>): Lexicon {
            val seen = HashSet<String>()
            val w = ArrayList<String>()
            for (x in list) if (seen.add(x)) w.add(x)
            val z = FloatArray(w.size) { maxOf(2.0f, 7.7f - 0.58f * ln((it + 2).toFloat())) }
            return Lexicon(w.toTypedArray(), z)
        }

        fun merge(parts: List<Lexicon>): Lexicon {
            if (parts.size == 1) return parts[0]
            val best = HashMap<String, Float>()
            for (p in parts) for (i in p.words.indices) {
                val old = best[p.words[i]]
                if (old == null || p.zipf[i] > old) best[p.words[i]] = p.zipf[i]
            }
            val ordered = best.entries.sortedByDescending { it.value }
            return Lexicon(
                Array(ordered.size) { ordered[it].key },
                FloatArray(ordered.size) { ordered[it].value }
            )
        }
    }
}

object Adjacency {
    private val pos = HashMap<Char, PointF>()

    init {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val offs = floatArrayOf(0f, 0.25f, 0.75f)
        rows.forEachIndexed { r, s -> s.forEachIndexed { i, c -> pos[c] = PointF(i + offs[r], r.toFloat()) } }
    }

    fun subCost(a: Char, b: Char): Float {
        val p = pos[a] ?: return 1f
        val q = pos[b] ?: return 1f
        return if (hypot(p.x - q.x, p.y - q.y) <= 1.35f) 0.45f else 1f
    }
}

object SeedData {
    private val baseBigrams: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "have", "will", "think", "don't"),
        "i'm" to listOf("not", "going", "sorry", "so"),
        "you" to listOf("are", "can", "have", "know", "want"),
        "thank" to listOf("you"),
        "thanks" to listOf("for", "a", "so"),
        "how" to listOf("are", "to", "do", "much"),
        "what" to listOf("is", "are", "do", "time"),
        "where" to listOf("are", "is", "do"),
        "see" to listOf("you", "the", "if"),
        "let" to listOf("me", "us", "you"),
        "good" to listOf("morning", "night", "luck", "idea"),
        "have" to listOf("a", "to", "been", "you"),
        "going" to listOf("to", "on", "out"),
        "want" to listOf("to", "a", "you"),
        "do" to listOf("you", "not", "it", "we"),
        "it" to listOf("is", "was", "will", "would"),
        "this" to listOf("is", "was", "will", "one"),
        "the" to listOf("best", "same", "first", "other"),
        "in" to listOf("the", "a", "case", "this"),
        "on" to listOf("the", "my", "a", "our"),
        "to" to listOf("be", "the", "do", "get", "see"),
        "of" to listOf("the", "course", "my", "a"),
        "for" to listOf("the", "a", "you", "me"),
        "with" to listOf("you", "the", "me", "a"),
        "can" to listOf("you", "i", "we", "be"),
        "will" to listOf("be", "you", "do", "see"),
        "would" to listOf("you", "like", "be", "have"),
        "please" to listOf("let", "send", "call", "check"),
        "sorry" to listOf("for", "about", "i", "but"),
        "happy" to listOf("birthday", "new", "to", "with"),
        "see you" to listOf("soon", "later", "tomorrow"),
        "call" to listOf("me", "you", "back"),
        "talk" to listOf("to", "soon", "later", "about"),
        "just" to listOf("a", "wanted", "got", "like"),
        "not" to listOf("sure", "a", "going", "the"),
        "so" to listOf("much", "i", "we", "many"),
        "very" to listOf("much", "good", "well", "nice"),
        "a" to listOf("lot", "few", "little", "good"),
        "and" to listOf("the", "i", "then", "we"),
        "but" to listOf("i", "it", "the", "we"),
        "if" to listOf("you", "i", "we", "it"),
    )

    private val moreBigrams: Map<String, List<String>> = mapOf(
        "i" to listOf("was", "can", "just", "love", "need", "would", "want", "know", "feel", "miss", "hope", "got", "had"),
        "i'll" to listOf("be", "call", "see", "let", "send", "try", "check"),
        "i've" to listOf("been", "got", "no", "never", "just"),
        "i'd" to listOf("like", "love", "be", "say"),
        "you're" to listOf("welcome", "right", "so", "not", "a", "going"),
        "we" to listOf("are", "can", "will", "have", "should", "need", "were", "got"),
        "they" to listOf("are", "have", "were", "will", "can", "said"),
        "he" to listOf("is", "was", "has", "said", "will", "had"),
        "she" to listOf("is", "was", "has", "said", "will", "had"),
        "there" to listOf("is", "are", "was", "will", "were"),
        "that" to listOf("is", "was", "we", "i", "you", "the", "he"),
        "is" to listOf("a", "the", "not", "it", "there", "this", "that"),
        "are" to listOf("you", "we", "they", "the", "not", "going", "a"),
        "was" to listOf("a", "the", "not", "going", "very", "so", "it"),
        "were" to listOf("you", "going", "not", "the", "in"),
        "be" to listOf("able", "a", "the", "there", "careful", "sure", "back", "ready"),
        "been" to listOf("a", "working", "there", "so", "trying", "busy"),
        "my" to listOf("name", "friend", "phone", "own", "best", "god", "life", "mom"),
        "your" to listOf("name", "phone", "own", "order", "account", "help", "time"),
        "our" to listOf("team", "own", "new", "best", "company", "life"),
        "their" to listOf("own", "new", "team", "first", "way"),
        "me" to listOf("to", "a", "know", "the", "back", "about", "and"),
        "him" to listOf("to", "and", "a", "the", "about"),
        "her" to listOf("to", "and", "a", "the", "own"),
        "them" to listOf("to", "and", "in", "all", "up"),
        "us" to listOf("to", "in", "a", "the", "know"),
        "at" to listOf("the", "least", "home", "all", "first", "work", "a"),
        "from" to listOf("the", "a", "my", "now", "home", "you"),
        "as" to listOf("a", "well", "the", "soon", "possible", "much", "i"),
        "by" to listOf("the", "a", "now", "using", "me"),
        "about" to listOf("the", "it", "you", "that", "a", "this", "what"),
        "after" to listOf("that", "the", "a", "work", "school", "all"),
        "before" to listOf("the", "i", "you", "we", "that", "it"),
        "when" to listOf("you", "i", "we", "the", "is", "it", "they"),
        "why" to listOf("are", "do", "is", "not", "did", "would"),
        "who" to listOf("is", "are", "was", "can", "will"),
        "which" to listOf("is", "one", "of", "was", "means"),
        "because" to listOf("i", "of", "the", "it", "you", "we"),
        "then" to listOf("i", "we", "the", "you", "it", "he"),
        "than" to listOf("the", "i", "a", "that", "you", "it"),
        "too" to listOf("much", "many", "late", "bad", "long", "good"),
        "more" to listOf("than", "and", "of", "about", "time"),
        "some" to listOf("of", "more", "other", "time", "people"),
        "all" to listOf("the", "of", "right", "day", "my", "good"),
        "any" to listOf("of", "other", "time", "more", "way"),
        "new" to listOf("year", "one", "york", "to", "and", "job"),
        "much" to listOf("more", "better", "of", "as", "time"),
        "many" to listOf("thanks", "of", "people", "more", "times", "things"),
        "really" to listOf("good", "want", "like", "need", "sorry", "appreciate"),
        "love" to listOf("you", "it", "to", "the", "this", "that"),
        "miss" to listOf("you", "the", "it", "me", "my"),
        "hope" to listOf("you", "to", "this", "that", "everything", "it"),
        "like" to listOf("to", "a", "the", "it", "you", "that", "this"),
        "need" to listOf("to", "a", "you", "the", "help", "some", "more"),
        "get" to listOf("a", "the", "to", "it", "back", "some", "up"),
        "got" to listOf("a", "it", "the", "to", "you", "my", "home"),
        "make" to listOf("sure", "a", "it", "the", "me", "this", "your"),
        "take" to listOf("a", "care", "the", "it", "me", "your", "this"),
        "come" to listOf("on", "to", "back", "here", "and", "with", "home"),
        "go" to listOf("to", "back", "home", "ahead", "out", "and", "with"),
        "know" to listOf("that", "what", "how", "if", "you", "the", "about"),
        "think" to listOf("that", "so", "about", "it", "i", "we", "of"),
        "feel" to listOf("like", "better", "free", "good", "the", "so"),
        "look" to listOf("at", "like", "for", "forward", "up", "into"),
        "looking" to listOf("forward", "for", "at", "good", "into"),
        "waiting" to listOf("for", "to", "on", "in"),
        "working" to listOf("on", "with", "for", "in", "from", "at"),
        "let's" to listOf("go", "see", "do", "get", "talk", "meet", "try"),
        "sure" to listOf("thing", "i", "that", "you", "if", "about"),
        "no" to listOf("problem", "one", "way", "worries", "i", "need"),
        "yes" to listOf("i", "we", "please", "sure", "it", "of"),
        "ok" to listOf("i", "thanks", "let", "see", "great", "so", "then"),
        "okay" to listOf("i", "thanks", "let", "so", "great", "then"),
        "hello" to listOf("how", "there", "i", "sir", "everyone"),
        "hi" to listOf("there", "how", "i", "thanks", "guys", "everyone"),
        "hey" to listOf("there", "how", "i", "are", "guys", "what"),
        "great" to listOf("job", "to", "thanks", "idea", "day", "news", "work"),
        "nice" to listOf("to", "one", "work", "job", "day", "meeting", "idea"),
        "best" to listOf("of", "wishes", "regards", "way", "friend", "thing"),
        "wish" to listOf("you", "i", "we", "me"),
        "tomorrow" to listOf("morning", "at", "i", "we", "is", "then"),
        "today" to listOf("is", "i", "we", "was", "at", "and"),
        "tonight" to listOf("i", "we", "at", "is", "then"),
        "meeting" to listOf("is", "at", "with", "tomorrow", "today", "on"),
        "free" to listOf("to", "for", "time", "today", "tomorrow", "tonight"),
        "busy" to listOf("with", "right", "today", "now", "at"),
        "right" to listOf("now", "away", "there", "here", "after", "so"),
        "last" to listOf("night", "week", "year", "time", "month", "one"),
        "next" to listOf("week", "time", "year", "month", "day", "to"),
        "one" to listOf("of", "more", "day", "thing", "time", "can"),
        "time" to listOf("to", "for", "i", "is", "and", "you"),
        "day" to listOf("and", "i", "to", "is", "off", "today"),
        "back" to listOf("to", "home", "soon", "and", "in", "i", "from"),
        "again" to listOf("and", "i", "soon", "for", "later"),
        "soon" to listOf("as", "i", "and", "after", "enough"),
        "still" to listOf("have", "not", "waiting", "working", "in", "there"),
        "also" to listOf("have", "the", "a", "i", "we", "need"),
        "other" to listOf("people", "than", "hand", "day", "one", "side"),
        "only" to listOf("one", "a", "the", "if", "have", "for"),
    )

    val bigrams: Map<String, List<String>> =
        (baseBigrams.keys + moreBigrams.keys).associateWith { k ->
            ((baseBigrams[k] ?: emptyList()) + (moreBigrams[k] ?: emptyList())).distinct()
        }

    /** Two-word contexts ("w2 w1") -> likely next words, best first. */
    val trigrams: Map<String, List<String>> = mapOf(
        "thank you" to listOf("so", "very", "for", "all", "again", "much"),
        "thanks for" to listOf("the", "your", "letting", "coming", "helping", "sharing"),
        "see you" to listOf("soon", "later", "tomorrow", "then", "there", "tonight", "next"),
        "how are" to listOf("you", "things", "we", "they", "your"),
        "how about" to listOf("we", "you", "the", "a", "tomorrow", "this"),
        "are you" to listOf("sure", "coming", "ok", "free", "there", "going", "still"),
        "do you" to listOf("have", "want", "know", "think", "need", "like", "mind"),
        "i am" to listOf("not", "going", "so", "very", "a", "sorry", "in", "at"),
        "i will" to listOf("be", "do", "call", "see", "send", "try", "let", "check"),
        "i have" to listOf("a", "to", "been", "no", "the", "just", "got"),
        "i don't" to listOf("know", "think", "have", "want", "like", "understand", "care"),
        "i can't" to listOf("wait", "believe", "find", "do", "make", "get", "say"),
        "i want" to listOf("to", "a", "you", "the", "some", "it"),
        "i need" to listOf("to", "a", "you", "help", "some", "the"),
        "i think" to listOf("it", "we", "you", "that", "i", "so"),
        "i love" to listOf("you", "it", "this", "that", "the", "her", "him"),
        "i'm going" to listOf("to", "home", "out", "back"),
        "going to" to listOf("be", "have", "do", "get", "go", "make", "the", "see"),
        "want to" to listOf("go", "be", "do", "see", "get", "know", "make", "have"),
        "have to" to listOf("go", "be", "do", "get", "make", "say", "see"),
        "need to" to listOf("go", "be", "do", "get", "make", "know", "talk", "see", "take"),
        "let me" to listOf("know", "see", "check", "think", "go", "do", "try"),
        "let us" to listOf("know", "go", "see", "do"),
        "can you" to listOf("please", "send", "help", "call", "tell", "do", "see", "come", "check"),
        "could you" to listOf("please", "send", "help", "tell", "check", "call"),
        "would you" to listOf("like", "please", "mind", "be", "rather"),
        "will you" to listOf("be", "come", "please", "do", "call", "marry"),
        "looking forward" to listOf("to"),
        "as soon" to listOf("as"),
        "soon as" to listOf("possible", "i", "you", "we"),
        "as well" to listOf("as", "i", "thanks"),
        "a lot" to listOf("of", "more", "to", "better"),
        "a few" to listOf("days", "minutes", "more", "things", "times"),
        "a little" to listOf("bit", "more", "late", "while"),
        "a good" to listOf("idea", "time", "one", "day", "night"),
        "in the" to listOf("morning", "evening", "afternoon", "end", "meantime", "same", "world", "future", "middle"),
        "on the" to listOf("way", "other", "phone", "table", "same", "first", "go"),
        "at the" to listOf("end", "moment", "same", "time", "beginning"),
        "for the" to listOf("first", "next", "last", "rest", "same", "best"),
        "to the" to listOf("point", "same", "best", "next", "end", "store", "office"),
        "of the" to listOf("day", "most", "best", "same", "year", "week", "world"),
        "is a" to listOf("good", "great", "very", "little", "lot", "bit"),
        "is the" to listOf("best", "same", "one", "only", "most", "first"),
        "it is" to listOf("a", "not", "the", "so", "very", "good", "my", "time"),
        "it was" to listOf("a", "so", "the", "great", "good", "not", "very", "really"),
        "this is" to listOf("a", "the", "my", "so", "not", "very", "great", "what"),
        "that is" to listOf("a", "the", "so", "not", "what", "why", "true"),
        "what is" to listOf("the", "your", "this", "that", "a", "going", "it"),
        "what are" to listOf("you", "we", "the", "they", "your"),
        "what do" to listOf("you", "we", "they", "i"),
        "where are" to listOf("you", "we", "they", "the"),
        "where is" to listOf("the", "he", "she", "it", "my"),
        "good morning" to listOf("everyone", "to", "sir", "how", "have"),
        "good night" to listOf("and", "to", "everyone", "sleep", "sweet"),
        "good luck" to listOf("with", "to", "on", "for", "and"),
        "happy birthday" to listOf("to", "my", "dear"),
        "happy new" to listOf("year"),
        "new year" to listOf("to", "and", "wishes", "eve"),
        "thanks a" to listOf("lot", "million", "bunch"),
        "no problem" to listOf("at", "i", "thanks", "we"),
        "on my" to listOf("way", "own", "phone", "side", "mind"),
        "by the" to listOf("way", "time", "end", "same"),
        "all the" to listOf("best", "time", "way", "same", "other", "people"),
        "the best" to listOf("way", "of", "thing", "part", "time", "one"),
        "the same" to listOf("time", "as", "thing", "way", "day"),
        "the first" to listOf("time", "one", "day", "thing"),
        "the other" to listOf("day", "hand", "side", "way", "one"),
        "one of" to listOf("the", "my", "those", "them", "these"),
        "some of" to listOf("the", "my", "them", "these", "those"),
        "out of" to listOf("the", "my", "here", "it", "this", "town"),
        "kind of" to listOf("a", "the", "like", "thing"),
        "because of" to listOf("the", "you", "that", "this", "it"),
        "in order" to listOf("to", "for", "of"),
        "order to" to listOf("be", "get", "make", "have", "do"),
        "if you" to listOf("want", "need", "have", "can", "are", "don't", "like"),
        "if i" to listOf("can", "have", "don't", "need", "could", "am"),
        "when i" to listOf("get", "was", "am", "got", "see", "have"),
        "when you" to listOf("get", "are", "have", "can", "see", "want"),
        "so much" to listOf("for", "more", "better", "fun", "to"),
        "too much" to listOf("to", "for", "of", "work"),
        "very much" to listOf("for", "i", "appreciated"),
    )

    val starters = listOf("i", "the", "thanks", "hi", "hey", "yes", "ok")

    val contractions: Map<String, String> = mapOf(
        "im" to "I'm", "dont" to "don't", "cant" to "can't", "wont" to "won't", "ive" to "I've",
        "youre" to "you're", "thats" to "that's", "whats" to "what's", "isnt" to "isn't",
        "doesnt" to "doesn't", "didnt" to "didn't", "wasnt" to "wasn't", "couldnt" to "couldn't",
        "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "hasnt" to "hasn't", "havent" to "haven't",
        "arent" to "aren't", "theyre" to "they're", "lets" to "let's", "id" to "I'd", "theres" to "there's",
        "hes" to "he's", "shes" to "she's", "weve" to "we've", "youve" to "you've", "youll" to "you'll",
        "ill" to "I'll", "werent" to "weren't", "hadnt" to "hadn't",
    )

    val emoji: Map<String, String> = mapOf(
        "love" to "❤️", "happy" to "😊", "sad" to "😢", "lol" to "😂", "haha" to "😂", "funny" to "😂",
        "thanks" to "🙏", "thank" to "🙏", "please" to "🙏", "birthday" to "🎂", "party" to "🎉",
        "congrats" to "🎉", "congratulations" to "🎉", "yes" to "👍", "ok" to "👌", "okay" to "👌",
        "good" to "👍", "great" to "🙌", "cool" to "😎", "fire" to "🔥", "hot" to "🔥", "cold" to "🥶",
        "sleep" to "😴", "tired" to "😴", "food" to "🍕", "pizza" to "🍕", "coffee" to "☕", "tea" to "🍵",
        "beer" to "🍺", "cake" to "🍰", "sun" to "☀️", "sunny" to "☀️", "rain" to "🌧️", "snow" to "❄️",
        "home" to "🏠", "car" to "🚗", "phone" to "📱", "work" to "💼", "money" to "💰", "music" to "🎵",
        "dog" to "🐶", "cat" to "🐱", "kiss" to "😘", "hug" to "🤗", "angry" to "😠", "cry" to "😭",
        "smile" to "😀", "wow" to "😮", "think" to "🤔", "hello" to "👋", "hi" to "👋", "bye" to "👋",
        "star" to "⭐", "heart" to "❤️", "luck" to "🍀", "win" to "🏆", "run" to "🏃", "gym" to "💪",
        "book" to "📚", "idea" to "💡", "time" to "⏰", "gift" to "🎁", "flower" to "🌸", "moon" to "🌙",
    )
}

class PredictionEngine(private val context: Context, private val user: UserData) {

    @Volatile
    private var lex: Lexicon = Lexicon.EMPTY
    private var loadedKey = ""

    val lexicon: Lexicon get() = lex

    private val corrCache = object : LinkedHashMap<String, List<Scored>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Scored>>?) = size > 48
    }

    suspend fun load(langs: List<String>) = withContext(Dispatchers.IO) {
        val key = langs.sorted().joinToString(",")
        if (key == loadedKey) return@withContext
        val seenBase = HashSet<String>()
        val parts = langs.mapNotNull { readLang(it, seenBase) }
        lex = if (parts.isEmpty()) Lexicon.EMPTY else Lexicon.merge(parts)
        lex.topWords
        synchronized(corrCache) { corrCache.clear() }
        loadedKey = key
    }

    fun invalidate() {
        loadedKey = ""
        synchronized(corrCache) { corrCache.clear() }
    }

    /** Regional variants ("en-GB") share the base language dictionary ("en") and may add their own optional dict/en-GB.txt. */
    private fun readLang(code: String, seenBase: MutableSet<String>): Lexicon? {
        val words = ArrayList<String>()
        val base = code.substringBefore('-')
        val assetNames = buildList { if (seenBase.add(base)) add(base); if (code != base) add(code) }
        for (assetName in assetNames) try {
            context.assets.open("dict/$assetName.txt").bufferedReader().useLines { seq ->
                seq.forEach { line -> line.trim().split(Regex("\\s+")).forEach { if (it.isNotEmpty()) words.add(it.lowercase()) } }
            }
        } catch (_: Exception) {
        }
        val imported = File(context.filesDir, "dict_$code.txt")
        if (imported.exists()) {
            try {
                val extra = ArrayList<String>()
                imported.bufferedReader().useLines { seq ->
                    seq.forEach { line -> line.trim().split(Regex("[\\s,;\\t]+"))
                        .firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { extra.add(it) } }
                }
                words.addAll(extra)
            } catch (_: Exception) {
            }
        }
        return if (words.isEmpty()) null else Lexicon.fromRanked(words)
    }

    fun hasBundled(code: String): Boolean = listOf(code.substringBefore('-'), code).distinct().any { name ->
        try {
            context.assets.open("dict/$name.txt").close(); true
        } catch (_: Exception) {
            false
        }
    }

    fun isKnown(word: String): Boolean {
        val w = word.lowercase()
        return lex.contains(w) || user.isLearned(w)
    }

    fun isBlocked(word: String) = user.blocked.contains(word.lowercase())

    fun completions(prefix: String, limit: Int, prev: String? = null, prev2: String? = null): List<Scored> {
        if (prefix.isEmpty()) return emptyList()
        val p = prev?.lowercase()
        val pp = prev2?.lowercase()
        val out = ArrayList<Scored>()
        for (s in lex.completions(prefix, limit * 3 + 2)) {
            if (s.word != prefix && !isBlocked(s.word)) out.add(Scored(s.word, s.score + ctxBoost(pp, p, s.word)))
        }
        for ((w, c) in user.words) {
            if (c >= 2 && w.startsWith(prefix) && w != prefix && !isBlocked(w)) {
                out.add(Scored(w, 5.2f + min(c, 20) * 0.1f + ctxBoost(pp, p, w)))
            }
        }
        return out.distinctBy { it.word }.sortedByDescending { it.score }.take(limit)
    }

    /** How strongly the two previous words point at [cand]: learned phrases beat the built-in seed phrases. */
    private fun ctxBoost(pp: String?, p: String?, cand: String): Float {
        if (p == null) return 0f
        var b = 0f
        user.bigrams[p]?.get(cand)?.let { b = maxOf(b, 0.9f + min(it, 10) * 0.1f) }
        SeedData.bigrams[p]?.indexOf(cand)?.let { if (it >= 0) b = maxOf(b, 1.1f - it * 0.1f) }
        if (pp != null) {
            val key = "$pp $p"
            user.trigrams[key]?.get(cand)?.let { b = maxOf(b, 1.5f + min(it, 10) * 0.12f) }
            SeedData.trigrams[key]?.indexOf(cand)?.let { if (it >= 0) b = maxOf(b, 1.7f - it * 0.12f) }
        }
        return b
    }

    private fun fit(base: Float, cost: Float, typed: String, cand: String): Float {
        var s = base - cost * 2.6f
        if (cand[0] == typed[0]) s += 0.35f
        if (cand.length == typed.length) s += 0.15f
        return s
    }

    fun corrections(typed: String, limit: Int, level: String, prevWord: String? = null, prev2: String? = null): List<Scored> {
        val prev = prevWord?.lowercase()
        val pp = prev2?.lowercase()
        val w = typed.lowercase()
        val n = w.length
        if (n < 2) return emptyList()
        SeedData.contractions[w]?.let { return listOf(Scored(it, 9f, 0f)) }
        val key = "$level|$limit|$w|$prev|$pp|${user.revision.value}|${lex.words.size}"
        synchronized(corrCache) { corrCache[key]?.let { return it } }
        val maxCost = when (level) {
            "low" -> 0.95f
            "high" -> 2.1f
            else -> 1.5f
        }
        val spread = if (level == "high" && n >= 4) 2 else 1
        val out = ArrayList<Scored>()
        for (len in maxOf(1, n - spread)..(n + spread)) {
            val bucket = lex.byLength.getOrNull(len) ?: continue
            for (i in bucket) {
                val cand = lex.words[i]
                if (cand[0] != w[0] && !(n >= 3 && cand.length >= 2 && cand[1] == w[1])) continue
                val cost = editCost(w, cand, maxCost)
                if (cost <= maxCost) out.add(Scored(cand, fit(lex.zipf[i], cost, w, cand) + ctxBoost(pp, prev, cand), cost))
            }
        }
        for ((cand, c) in user.words) {
            if (c < 2 || abs(cand.length - n) > 1) continue
            val cost = editCost(w, cand, maxCost)
            if (cost <= maxCost) out.add(Scored(cand, fit(5.4f + min(c, 20) * 0.03f, cost, w, cand) + ctxBoost(pp, prev, cand), cost))
        }
        val res = out.filter { !isBlocked(it.word) }.distinctBy { it.word }.sortedByDescending { it.score }.take(limit)
        synchronized(corrCache) { corrCache[key] = res }
        return res
    }

    /**
     * Weighted edit distance: neighbouring-key substitutions are cheap, a transposition costs 0.8,
     * doubled letters and a missing apostrophe are cheap to add or drop.
     */
    private fun editCost(a: String, b: String, max: Float): Float {
        val n = a.length
        val m = b.length
        if (abs(n - m) > 2) return Float.MAX_VALUE
        var prev2 = FloatArray(m + 1)
        var prev = FloatArray(m + 1) { it.toFloat() }
        var cur = FloatArray(m + 1)
        for (i in 1..n) {
            cur[0] = i.toFloat()
            var rowMin = cur[0]
            val delCost = if (i > 1 && a[i - 1] == a[i - 2]) 0.6f else 1f
            for (j in 1..m) {
                val sub = if (a[i - 1] == b[j - 1]) 0f else Adjacency.subCost(a[i - 1], b[j - 1])
                val insCost = if (b[j - 1] == '\'') 0.3f else if (j > 1 && b[j - 1] == b[j - 2]) 0.6f else 1f
                var v = min(prev[j - 1] + sub, min(prev[j] + delCost, cur[j - 1] + insCost))
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, prev2[j - 2] + 0.8f)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return Float.MAX_VALUE
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    /** Next-word prediction from the last two words: learned phrases first, then built-in phrases, then common words. */
    fun nextWords(prev: String?, limit: Int, prev2: String? = null): List<String> {
        val p = prev?.lowercase()
        val pp = prev2?.lowercase()
        val sc = LinkedHashMap<String, Float>()
        fun add(w: String, s: Float) {
            if (!isBlocked(w)) sc[w] = (sc[w] ?: 0f) + s
        }
        if (p != null) {
            if (pp != null) {
                val key = "$pp $p"
                user.trigrams[key]?.forEach { (w, c) -> add(w, 3.0f + min(c, 10) * 0.3f) }
                SeedData.trigrams[key]?.forEachIndexed { i, w -> add(w, 2.4f - i * 0.2f) }
            }
            user.bigrams[p]?.forEach { (w, c) -> add(w, 1.6f + min(c, 12) * 0.2f) }
            SeedData.bigrams[p]?.forEachIndexed { i, w -> add(w, 1.5f - i * 0.15f) }
        }
        val ordered = sc.entries.sortedByDescending { it.value }.map { it.key }.toMutableList()
        if (ordered.size < limit) {
            val filler = if (p == null) SeedData.starters + lex.topWords else lex.topWords.ifEmpty { SeedData.starters }
            for (w in filler) {
                if (ordered.size >= limit) break
                if ((w.length >= 2 || w == "i" || w == "a") && w !in ordered && !isBlocked(w)) ordered.add(w)
            }
        }
        return ordered.take(limit)
    }

    fun emojiFor(word: String?): String? = word?.lowercase()?.let { SeedData.emoji[it] }

    fun decodeGesture(path: List<PointF>, centers: Map<Char, PointF>, keyW: Float, limit: Int = 4): List<String> {
        if (path.size < 3 || lex === Lexicon.EMPTY && user.words.isEmpty()) return emptyList()
        val pathLen = length(path)
        if (pathLen < keyW * 1.1f) return emptyList()

        val first = path.first()
        val last = path.last()
        val startKeys = nearKeys(first, centers, keyW)
        val endKeys = nearKeys(last, centers, keyW)
        val userR = resample(path, 40)
        val dense = resample(path, 90)

        val pool = ArrayList<Pair<String, Float>>()
        for (c in startKeys) {
            val idxs = lex.byFirst[c] ?: continue
            for (i in idxs) pool.add(lex.words[i] to lex.zipf[i])
        }
        for ((w, c) in user.words) {
            if (c >= 2 && w.isNotEmpty() && w[0] in startKeys) pool.add(w to 5.4f)
        }

        val scored = ArrayList<Pair<String, Float>>()
        for ((word, z) in pool) {
            if (word.length < 2 || word.last() !in endKeys) continue
            if (isBlocked(word)) continue
            val pts = ArrayList<PointF>()
            var ok = true
            var prevC = ' '
            for (ch in word) {
                if (ch == prevC) continue
                prevC = ch
                val c = centers[ch]
                if (c == null) { ok = false; break }
                pts.add(c)
            }
            if (!ok || pts.size < 2) continue
            val idealLen = length(pts)
            val ratio = pathLen / idealLen
            if (ratio < 0.55f || ratio > 2.0f) continue
            var covered = true
            for (c in pts) {
                var best = Float.MAX_VALUE
                for (p in dense) best = min(best, hypot(p.x - c.x, p.y - c.y))
                if (best > keyW * 1.25f) { covered = false; break }
            }
            if (!covered) continue
            val ideal = resample(pts, 40)
            var d = 0f
            for (i in 0 until 40) d += hypot(userR[i].x - ideal[i].x, userR[i].y - ideal[i].y)
            val loc = d / 40f / keyW
            scored.add(word to (z * 0.35f - loc * 4.0f - abs(1f - ratio) * 0.8f))
        }
        return scored.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    private fun nearKeys(p: PointF, centers: Map<Char, PointF>, keyW: Float): Set<Char> {
        val sorted = centers.entries.sortedBy { hypot(it.value.x - p.x, it.value.y - p.y) }
        val out = HashSet<Char>()
        for ((i, e) in sorted.withIndex()) {
            val d = hypot(e.value.x - p.x, e.value.y - p.y)
            if (i < 2 || d < keyW * 1.05f) out.add(e.key) else break
            if (out.size >= 5) break
        }
        return out
    }

    private fun length(pts: List<PointF>): Float {
        var l = 0f
        for (i in 1 until pts.size) l += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        return l
    }

    private fun resample(pts: List<PointF>, n: Int): List<PointF> {
        val total = length(pts)
        if (total == 0f) return List(n) { pts[0] }
        val step = total / (n - 1)
        val out = ArrayList<PointF>(n)
        out.add(PointF(pts[0].x, pts[0].y))
        var acc = 0f
        var i = 1
        var a = pts[0]
        while (i < pts.size && out.size < n) {
            val b = pts[i]
            val seg = hypot(b.x - a.x, b.y - a.y)
            if (acc + seg >= step && seg > 0f) {
                val t = (step - acc) / seg
                val np = PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                out.add(np)
                a = np
                acc = 0f
            } else {
                acc += seg
                a = b
                i++
            }
        }
        while (out.size < n) out.add(PointF(pts.last().x, pts.last().y))
        return out
    }
}