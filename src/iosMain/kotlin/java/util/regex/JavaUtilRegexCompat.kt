package java.util.regex

public class Pattern internal constructor(
	private val patternStr: String,
	private val flagsVal: Int,
) : java.io.Serializable {
	internal val kotlinRegex: Regex by lazy {
		val opts = mutableSetOf<RegexOption>()
		if ((flagsVal and CASE_INSENSITIVE) != 0) opts.add(RegexOption.IGNORE_CASE)
		if ((flagsVal and MULTILINE) != 0) opts.add(RegexOption.MULTILINE)
		if ((flagsVal and DOTALL) != 0) opts.add(RegexOption.DOT_MATCHES_ALL)
		Regex(patternStr, opts)
	}

	public fun pattern(): String = patternStr

	public fun flags(): Int = flagsVal

	public fun matcher(input: CharSequence): Matcher = Matcher(this, input.toString())

	public fun split(input: CharSequence): Array<String> = kotlinRegex.split(input).toTypedArray()

	public fun split(input: CharSequence, limit: Int): Array<String> =
		kotlinRegex.split(input, limit.coerceAtLeast(0)).toTypedArray()

	public fun toRegex(): Regex = kotlinRegex

	override fun toString(): String = patternStr

	public companion object {
		public const val UNIX_LINES: Int = 0x01
		public const val CASE_INSENSITIVE: Int = 0x02
		public const val COMMENTS: Int = 0x04
		public const val MULTILINE: Int = 0x08
		public const val LITERAL: Int = 0x10
		public const val DOTALL: Int = 0x20
		public const val UNICODE_CASE: Int = 0x40
		public const val CANON_EQ: Int = 0x80
		public const val UNICODE_CHARACTER_CLASS: Int = 0x100

		public fun compile(regex: String): Pattern = Pattern(regex, 0)

		public fun compile(regex: String, flags: Int): Pattern = Pattern(regex, flags)

		public fun matches(regex: String, input: CharSequence): Boolean =
			compile(regex).matcher(input).matches()

		public fun quote(s: String): String = Regex.escape(s)
	}
}

public class Matcher internal constructor(
	private val patternObj: Pattern,
	private val inputStr: String,
) {
	private val allMatches: List<MatchResult> by lazy {
		patternObj.kotlinRegex.findAll(inputStr).toList()
	}
	private var matchIdx: Int = -1
	private var currentMatch: MatchResult? = null
	private var lastAppendPos: Int = 0

	public fun pattern(): Pattern = patternObj

	public fun matches(): Boolean {
		currentMatch = patternObj.kotlinRegex.matchEntire(inputStr)
		return currentMatch != null
	}

	public fun find(): Boolean {
		matchIdx++
		return if (matchIdx < allMatches.size) {
			currentMatch = allMatches[matchIdx]
			true
		} else {
			currentMatch = null
			false
		}
	}

	public fun find(start: Int): Boolean {
		currentMatch = patternObj.kotlinRegex.find(inputStr, start)
		return currentMatch != null
	}

	public fun lookingAt(): Boolean {
		val m = patternObj.kotlinRegex.find(inputStr, 0)
		return if (m != null && m.range.first == 0) {
			currentMatch = m
			true
		} else {
			currentMatch = null
			false
		}
	}

	public fun group(): String = group(0) ?: ""

	public fun group(group: Int): String? {
		val m = currentMatch ?: throw IllegalStateException("No match found")
		return m.groups[group]?.value
	}

	public fun group(name: String): String? {
		val m = currentMatch ?: throw IllegalStateException("No match found")
		return try {
			m.groups[name]?.value
		} catch (_: Throwable) {
			null
		}
	}

	public fun groupCount(): Int {
		val m = currentMatch ?: return 0
		return (m.groups.size - 1).coerceAtLeast(0)
	}

	public fun start(): Int = start(0)

	public fun start(group: Int): Int {
		val m = currentMatch ?: throw IllegalStateException("No match found")
		return m.groups[group]?.range?.first ?: -1
	}

	public fun end(): Int = end(0)

	public fun end(group: Int): Int {
		val m = currentMatch ?: throw IllegalStateException("No match found")
		val r = m.groups[group]?.range ?: return -1
		return r.last + 1
	}

	public fun replaceAll(replacement: String): String =
		patternObj.kotlinRegex.replace(inputStr, replacement)

	public fun replaceFirst(replacement: String): String =
		patternObj.kotlinRegex.replaceFirst(inputStr, replacement)

	public fun appendReplacement(sb: StringBuilder, replacement: String): Matcher {
		val m = currentMatch ?: throw IllegalStateException("No match available")
		sb.append(inputStr.substring(lastAppendPos, m.range.first))
		sb.append(replacement)
		lastAppendPos = m.range.last + 1
		return this
	}

	public fun appendTail(sb: StringBuilder): StringBuilder {
		sb.append(inputStr.substring(lastAppendPos))
		return sb
	}

	public fun reset(): Matcher {
		matchIdx = -1
		currentMatch = null
		lastAppendPos = 0
		return this
	}

	public fun reset(input: CharSequence): Matcher = Matcher(patternObj, input.toString())

	public companion object {
		public fun quoteReplacement(s: String): String = Regex.escapeReplacement(s)
	}
}
