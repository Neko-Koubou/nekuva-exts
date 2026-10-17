package org.jsoup.internal

public object StringUtil {
	public class StringJoiner(private val separator: String) {
		private val sb: StringBuilder = StringBuilder()
		private var first: Boolean = true

		public fun add(stringy: Any?): StringJoiner {
			if (!first) {
				sb.append(separator)
			}
			sb.append(stringy)
			first = false
			return this
		}

		public fun append(stringy: Any?): StringJoiner {
			sb.append(stringy)
			return this
		}

		public fun complete(): String = sb.toString()
	}

	public fun join(strings: Collection<*>, sep: String): String =
		strings.joinToString(sep)

	public fun join(strings: Iterator<*>, sep: String): String =
		strings.asSequence().joinToString(sep)

	public fun isBlank(string: String?): Boolean =
		com.fleeksoft.ksoup.internal.StringUtil.isBlank(string)

	public fun isNumeric(string: String?): Boolean =
		com.fleeksoft.ksoup.internal.StringUtil.isNumeric(string)

	public fun isWhitespace(c: Int): Boolean =
		com.fleeksoft.ksoup.internal.StringUtil.isWhitespace(c)

	public fun normaliseWhitespace(string: String): String =
		com.fleeksoft.ksoup.internal.StringUtil.normaliseWhitespace(string)
}
