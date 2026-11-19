package okhttp3

import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

public class HttpUrl internal constructor(
	public val scheme: String,
	public val username: String,
	public val password: String,
	public val host: String,
	public val port: Int,
	public val pathSegments: List<String>,
	internal val queryPairs: List<Pair<String, String?>>,
	public val fragment: String?,
	private val rawUrlString: String,
) {
	public val isHttps: Boolean
		get() = scheme.equals("https", ignoreCase = true)

	public val encodedPath: String
		get() {
			if (pathSegments.isEmpty() || (pathSegments.size == 1 && pathSegments[0].isEmpty())) {
				return "/"
			}
			return "/" + pathSegments.joinToString("/") { encodePathSegment(it) }
		}

	public val encodedPathSegments: List<String>
		get() = pathSegments.map { encodePathSegment(it) }

	public val pathSize: Int
		get() = pathSegments.count { it.isNotEmpty() }.coerceAtLeast(if (pathSegments.isNotEmpty()) pathSegments.size else 1)

	public val encodedQuery: String?
		get() {
			if (queryPairs.isEmpty()) return null
			return queryPairs.joinToString("&") { (k, v) ->
				val ek = encodeQueryComponent(k)
				if (v == null) ek else "$ek=${encodeQueryComponent(v)}"
			}
		}

	public val query: String?
		get() {
			if (queryPairs.isEmpty()) return null
			return queryPairs.joinToString("&") { (k, v) ->
				if (v == null) k else "$k=$v"
			}
		}

	public val querySize: Int
		get() = queryPairs.size

	public val queryParameterNames: Set<String>
		get() = queryPairs.mapTo(LinkedHashSet()) { it.first }

	public val encodedFragment: String?
		get() = fragment

	public fun queryParameter(name: String): String? =
		queryPairs.firstOrNull { it.first == name }?.second

	public fun queryParameterValues(name: String): List<String?> =
		queryPairs.filter { it.first == name }.map { it.second }

	public fun queryParameterName(index: Int): String = queryPairs[index].first

	public fun queryParameterValue(index: Int): String? = queryPairs[index].second

	public fun topPrivateDomain(): String? {
		val parts = host.split('.')
		return if (parts.size >= 2) {
			"${parts[parts.size - 2]}.${parts.last()}"
		} else {
			host
		}
	}

	public fun redact(): String = newBuilder().username("").password("").query(null).fragment(null).toString()

	public fun resolve(link: String): HttpUrl? = newBuilder(link)?.build()

	public fun newBuilder(): Builder {
		val b = Builder()
		b.scheme = scheme
		b.encodedUsername = username
		b.encodedPassword = password
		b.host = host
		b.port = port
		b.encodedPathSegments.clear()
		if (pathSegments.isEmpty()) {
			b.encodedPathSegments.add("")
		} else {
			b.encodedPathSegments.addAll(pathSegments)
		}
		b.queryPairs.addAll(queryPairs)
		b.encodedFragment = fragment
		return b
	}

	public fun newBuilder(link: String): Builder? = try {
		val trimmed = link.trim()
		when {
			trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) ->
				trimmed.toHttpUrl().newBuilder()
			trimmed.startsWith("//") ->
				"$scheme:$trimmed".toHttpUrl().newBuilder()
			trimmed.startsWith("/") -> {
				val base = "$scheme://$host${if (port != defaultPort(scheme)) ":$port" else ""}$trimmed"
				base.toHttpUrl().newBuilder()
			}
			trimmed.startsWith("?") -> {
				val base = "$scheme://$host${if (port != defaultPort(scheme)) ":$port" else ""}$encodedPath$trimmed"
				base.toHttpUrl().newBuilder()
			}
			else -> {
				val parentPath = encodedPath.substringBeforeLast('/', "")
				val base = "$scheme://$host${if (port != defaultPort(scheme)) ":$port" else ""}$parentPath/$trimmed"
				base.toHttpUrl().newBuilder()
			}
		}
	} catch (_: Throwable) {
		null
	}

	public fun toUrl(): URL = URL(toString())

	public fun toUri(): URI = URI(toString())

	override fun toString(): String = rawUrlString

	override fun equals(other: Any?): Boolean = other is HttpUrl && other.rawUrlString == rawUrlString

	override fun hashCode(): Int = rawUrlString.hashCode()

	public class Builder {
		internal var scheme: String = "https"
		internal var encodedUsername: String = ""
		internal var encodedPassword: String = ""
		internal var host: String = ""
		internal var port: Int = -1
		internal val encodedPathSegments: MutableList<String> = mutableListOf("")
		internal val queryPairs: MutableList<Pair<String, String?>> = mutableListOf()
		internal var encodedFragment: String? = null

		public fun scheme(scheme: String): Builder {
			this.scheme = scheme.lowercase()
			return this
		}

		public fun username(username: String): Builder {
			this.encodedUsername = username
			return this
		}

		public fun encodedUsername(encodedUsername: String): Builder {
			this.encodedUsername = decodePercent(encodedUsername)
			return this
		}

		public fun password(password: String): Builder {
			this.encodedPassword = password
			return this
		}

		public fun encodedPassword(encodedPassword: String): Builder {
			this.encodedPassword = decodePercent(encodedPassword)
			return this
		}

		public fun host(host: String): Builder {
			val h = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
			if (h.isEmpty()) throw IllegalArgumentException("Empty host")
			this.host = h
			return this
		}

		public fun port(port: Int): Builder {
			this.port = port
			return this
		}

		public fun addPathSegment(pathSegment: String): Builder {
			pushSegment(pathSegment, alreadyEncoded = false)
			return this
		}

		public fun addPathSegments(pathSegments: String): Builder {
			for (seg in pathSegments.split('/')) {
				if (seg.isNotEmpty()) pushSegment(seg, alreadyEncoded = false)
			}
			return this
		}

		public fun addEncodedPathSegment(encodedPathSegment: String): Builder {
			pushSegment(encodedPathSegment, alreadyEncoded = true)
			return this
		}

		public fun addEncodedPathSegments(encodedPathSegments: String): Builder {
			for (seg in encodedPathSegments.split('/')) {
				if (seg.isNotEmpty()) pushSegment(seg, alreadyEncoded = true)
			}
			return this
		}

		public fun setPathSegment(index: Int, pathSegment: String): Builder {
			while (encodedPathSegments.size <= index) encodedPathSegments.add("")
			encodedPathSegments[index] = pathSegment
			return this
		}

		public fun setEncodedPathSegment(index: Int, encodedPathSegment: String): Builder {
			while (encodedPathSegments.size <= index) encodedPathSegments.add("")
			encodedPathSegments[index] = decodePercent(encodedPathSegment)
			return this
		}

		public fun removePathSegment(index: Int): Builder {
			if (index in encodedPathSegments.indices) {
				encodedPathSegments.removeAt(index)
				if (encodedPathSegments.isEmpty()) encodedPathSegments.add("")
			}
			return this
		}

		public fun encodedPath(encodedPath: String): Builder {
			if (!encodedPath.startsWith("/")) {
				throw IllegalArgumentException("unexpected encodedPath: $encodedPath")
			}
			encodedPathSegments.clear()
			val segments = encodedPath.removePrefix("/").split('/')
			for (s in segments) {
				encodedPathSegments.add(decodePercent(s))
			}
			return this
		}

		private fun pushSegment(segment: String, alreadyEncoded: Boolean) {
			val clean = if (alreadyEncoded) decodePercent(segment) else segment
			if (clean == "." || clean.equals("%2e", ignoreCase = true)) return
			if (clean == ".." || clean.equals("%2e%2e", ignoreCase = true)) {
				if (encodedPathSegments.size > 1) {
					encodedPathSegments.removeAt(encodedPathSegments.lastIndex)
				} else if (encodedPathSegments.isNotEmpty()) {
					encodedPathSegments[0] = ""
				}
				return
			}
			if (encodedPathSegments.size == 1 && encodedPathSegments[0].isEmpty()) {
				encodedPathSegments[0] = clean
			} else {
				encodedPathSegments.add(clean)
			}
		}

		public fun query(query: String?): Builder {
			queryPairs.clear()
			if (!query.isNullOrEmpty()) {
				for (part in query.split('&')) {
					if (part.isEmpty()) continue
					val eq = part.indexOf('=')
					if (eq == -1) {
						queryPairs.add(part to null)
					} else {
						queryPairs.add(part.substring(0, eq) to part.substring(eq + 1))
					}
				}
			}
			return this
		}

		public fun encodedQuery(encodedQuery: String?): Builder {
			queryPairs.clear()
			if (!encodedQuery.isNullOrEmpty()) {
				for (part in encodedQuery.split('&')) {
					if (part.isEmpty()) continue
					val eq = part.indexOf('=')
					if (eq == -1) {
						queryPairs.add(decodeQueryComponent(part) to null)
					} else {
						queryPairs.add(
							decodeQueryComponent(part.substring(0, eq)) to decodeQueryComponent(part.substring(eq + 1)),
						)
					}
				}
			}
			return this
		}

		public fun addQueryParameter(name: String, value: String?): Builder {
			queryPairs.add(name to value)
			return this
		}

		public fun addEncodedQueryParameter(encodedName: String, encodedValue: String?): Builder {
			queryPairs.add(decodeQueryComponent(encodedName) to encodedValue?.let { decodeQueryComponent(it) })
			return this
		}

		public fun setQueryParameter(name: String, value: String?): Builder {
			removeAllQueryParameters(name)
			addQueryParameter(name, value)
			return this
		}

		public fun setEncodedQueryParameter(encodedName: String, encodedValue: String?): Builder {
			val decodedName = decodeQueryComponent(encodedName)
			removeAllQueryParameters(decodedName)
			addEncodedQueryParameter(encodedName, encodedValue)
			return this
		}

		public fun removeAllQueryParameters(name: String): Builder {
			queryPairs.removeAll { it.first == name }
			return this
		}

		public fun removeAllEncodedQueryParameters(encodedName: String): Builder {
			val name = decodeQueryComponent(encodedName)
			queryPairs.removeAll { it.first == name }
			return this
		}

		public fun fragment(fragment: String?): Builder {
			this.encodedFragment = fragment
			return this
		}

		public fun encodedFragment(encodedFragment: String?): Builder {
			this.encodedFragment = encodedFragment?.let { decodePercent(it) }
			return this
		}

		public fun build(): HttpUrl {
			if (host.isEmpty()) throw IllegalStateException("host == null")
			val effectivePort = if (port > 0) port else defaultPort(scheme)
			val sb = StringBuilder()
			sb.append(scheme).append("://")
			if (encodedUsername.isNotEmpty() || encodedPassword.isNotEmpty()) {
				sb.append(encodeComponent(encodedUsername))
				if (encodedPassword.isNotEmpty()) {
					sb.append(':').append(encodeComponent(encodedPassword))
				}
				sb.append('@')
			}
			sb.append(host)
			if (effectivePort != defaultPort(scheme)) {
				sb.append(':').append(effectivePort)
			}
			val segs = if (encodedPathSegments.isEmpty()) listOf("") else encodedPathSegments.toList()
			if (segs.size == 1 && segs[0].isEmpty()) {
				sb.append('/')
			} else {
				for (seg in segs) {
					sb.append('/').append(encodePathSegment(seg))
				}
			}
			if (queryPairs.isNotEmpty()) {
				sb.append('?')
				queryPairs.joinTo(sb, "&") { (k, v) ->
					val ek = encodeQueryComponent(k)
					if (v == null) ek else "$ek=${encodeQueryComponent(v)}"
				}
			}
			if (encodedFragment != null) {
				sb.append('#').append(encodedFragment)
			}
			return HttpUrl(
				scheme = scheme,
				username = encodedUsername,
				password = encodedPassword,
				host = host,
				port = effectivePort,
				pathSegments = segs,
				queryPairs = queryPairs.toList(),
				fragment = encodedFragment,
				rawUrlString = sb.toString(),
			)
		}

		override fun toString(): String = build().toString()
	}

	public companion object {
		public fun defaultPort(scheme: String): Int = when (scheme.lowercase()) {
			"http" -> 80
			"https" -> 443
			else -> -1
		}

		public fun String.toHttpUrl(): HttpUrl = parseUrl(this)
			?: throw IllegalArgumentException("Invalid URL: $this")

		public fun String.toHttpUrlOrNull(): HttpUrl? = parseUrl(this)

		public fun URL.toHttpUrl(): HttpUrl = toString().toHttpUrl()
		public fun URL.toHttpUrlOrNull(): HttpUrl? = toString().toHttpUrlOrNull()
		public fun URI.toHttpUrl(): HttpUrl = toString().toHttpUrl()
		public fun URI.toHttpUrlOrNull(): HttpUrl? = toString().toHttpUrlOrNull()

		public fun get(url: String): HttpUrl = url.toHttpUrl()
		public fun parse(url: String): HttpUrl? = url.toHttpUrlOrNull()

		internal fun parseUrl(rawInput: String): HttpUrl? {
			val input = rawInput.trim()
			val schemeSep = input.indexOf("://")
			if (schemeSep <= 0) return null
			val scheme = input.substring(0, schemeSep).lowercase()
			if (scheme != "http" && scheme != "https") return null

			var rest = input.substring(schemeSep + 3)
			var fragment: String? = null
			val hashIdx = rest.indexOf('#')
			if (hashIdx >= 0) {
				fragment = decodePercent(rest.substring(hashIdx + 1))
				rest = rest.substring(0, hashIdx)
			}

			var queryStr: String? = null
			val qIdx = rest.indexOf('?')
			if (qIdx >= 0) {
				queryStr = rest.substring(qIdx + 1)
				rest = rest.substring(0, qIdx)
			}

			val slashIdx = rest.indexOf('/')
			val authority = if (slashIdx >= 0) rest.substring(0, slashIdx) else rest
			val rawPath = if (slashIdx >= 0) rest.substring(slashIdx) else "/"

			var username = ""
			var password = ""
			val atIdx = authority.lastIndexOf('@')
			val hostPort = if (atIdx >= 0) {
				val userInfo = authority.substring(0, atIdx)
				val colonIdx = userInfo.indexOf(':')
				if (colonIdx >= 0) {
					username = decodePercent(userInfo.substring(0, colonIdx))
					password = decodePercent(userInfo.substring(colonIdx + 1))
				} else {
					username = decodePercent(userInfo)
				}
				authority.substring(atIdx + 1)
			} else {
				authority
			}

			val colonIdx = hostPort.lastIndexOf(':')
			val host: String
			val port: Int
			if (colonIdx > 0 && !hostPort.endsWith(']')) {
				host = hostPort.substring(0, colonIdx)
				port = hostPort.substring(colonIdx + 1).toIntOrNull() ?: defaultPort(scheme)
			} else {
				host = hostPort
				port = defaultPort(scheme)
			}
			if (host.isEmpty()) return null

			val builder = Builder()
				.scheme(scheme)
				.username(username)
				.password(password)
				.host(host)
				.port(port)
				.encodedPath(if (rawPath.startsWith("/")) rawPath else "/$rawPath")
				.encodedQuery(queryStr)
				.fragment(fragment)
			return builder.build()
		}

		internal fun decodePercent(s: String): String {
			if (!s.contains('%')) return s
			return try {
				URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
			} catch (_: Throwable) {
				s
			}
		}

		internal fun decodeQueryComponent(s: String): String {
			if (!s.contains('%') && !s.contains('+')) return s
			return try {
				URLDecoder.decode(s, "UTF-8")
			} catch (_: Throwable) {
				s
			}
		}

		internal fun encodePathSegment(s: String): String {
			if (s.isEmpty()) return ""
			val bytes = s.encodeToByteArray()
			val sb = StringBuilder(bytes.size)
			for (b in bytes) {
				val c = b.toInt() and 0xFF
				val ch = c.toChar()
				if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' ||
					ch == '-' || ch == '_' || ch == '.' || ch == '~' ||
					ch == ':' || ch == '@' || ch == '!' || ch == '$' ||
					ch == '&' || ch == '\'' || ch == '(' || ch == ')' ||
					ch == '*' || ch == '+' || ch == ',' || ch == ';' || ch == '='
				) {
					sb.append(ch)
				} else {
					sb.append('%')
					sb.append("0123456789ABCDEF"[c ushr 4])
					sb.append("0123456789ABCDEF"[c and 0x0F])
				}
			}
			return sb.toString()
		}

		internal fun encodeQueryComponent(s: String): String =
			URLEncoder.encode(s, "UTF-8").replace("*", "%2A")

		internal fun encodeComponent(s: String): String =
			URLEncoder.encode(s, "UTF-8").replace("+", "%20")
	}
}
