package okhttp3

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

public class Headers internal constructor(
	internal val namesAndValues: List<Pair<String, String>>,
) : Iterable<Pair<String, String>> {

	public val size: Int
		get() = namesAndValues.size

	public fun size(): Int = namesAndValues.size

	public fun name(index: Int): String = namesAndValues[index].first

	public fun value(index: Int): String = namesAndValues[index].second

	public operator fun get(name: String): String? =
		namesAndValues.lastOrNull { it.first.equals(name, ignoreCase = true) }?.second

	public fun values(name: String): List<String> =
		namesAndValues.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }

	public fun names(): Set<String> {
		val result = LinkedHashSet<String>()
		for ((k, _) in namesAndValues) {
			if (result.none { it.equals(k, ignoreCase = true) }) {
				result.add(k)
			}
		}
		return result
	}

	public fun toMultimap(): Map<String, List<String>> {
		val result = LinkedHashMap<String, MutableList<String>>()
		for ((k, v) in namesAndValues) {
			val key = k.lowercase()
			result.getOrPut(key) { ArrayList() }.add(v)
		}
		return result
	}

	public fun newBuilder(): Builder = Builder().also { it.namesAndValues.addAll(namesAndValues) }

	override fun iterator(): Iterator<Pair<String, String>> = namesAndValues.iterator()

	override fun equals(other: Any?): Boolean = other is Headers && namesAndValues == other.namesAndValues

	override fun hashCode(): Int = namesAndValues.hashCode()

	override fun toString(): String = namesAndValues.joinToString("\n") { "${it.first}: ${it.second}" }

	public class Builder {
		internal val namesAndValues: MutableList<Pair<String, String>> = ArrayList()

		public fun add(name: String, value: String): Builder {
			namesAndValues.add(name.trim() to value.trim())
			return this
		}

		public fun addUnsafeNonAscii(name: String, value: String): Builder = add(name, value)

		public fun add(line: String): Builder {
			val idx = line.indexOf(':')
			require(idx != -1) { "Unexpected header: $line" }
			return add(line.substring(0, idx), line.substring(idx + 1))
		}

		public fun addAll(headers: Headers): Builder {
			namesAndValues.addAll(headers.namesAndValues)
			return this
		}

		public operator fun set(name: String, value: String): Builder {
			removeAll(name)
			add(name, value)
			return this
		}

		public fun removeAll(name: String): Builder {
			namesAndValues.removeAll { it.first.equals(name, ignoreCase = true) }
			return this
		}

		public operator fun get(name: String): String? =
			namesAndValues.lastOrNull { it.first.equals(name, ignoreCase = true) }?.second

		public fun build(): Headers = Headers(namesAndValues.toList())
	}

	public companion object {
		public val EMPTY: Headers = Headers(emptyList())

		public fun headersOf(vararg namesAndValues: String): Headers {
			require(namesAndValues.size % 2 == 0) { "Expected alternating header names and values" }
			val b = Builder()
			var i = 0
			while (i < namesAndValues.size) {
				b.add(namesAndValues[i], namesAndValues[i + 1])
				i += 2
			}
			return b.build()
		}

		public fun Map<String, String>.toHeaders(): Headers {
			val b = Builder()
			for ((k, v) in this) b.add(k, v)
			return b.build()
		}

		public fun of(vararg namesAndValues: String): Headers = headersOf(*namesAndValues)
		public fun of(headers: Map<String, String>): Headers = headers.toHeaders()
	}
}

public class Cookie internal constructor(
	public val name: String,
	public val value: String,
	public val expiresAt: Long,
	public val domain: String,
	public val path: String,
	public val secure: Boolean,
	public val httpOnly: Boolean,
	public val persistent: Boolean,
	public val hostOnly: Boolean,
) {
	public fun matches(url: HttpUrl): Boolean {
		val domainMatch = if (hostOnly) {
			url.host.equals(domain, ignoreCase = true)
		} else {
			url.host.equals(domain, ignoreCase = true) ||
				url.host.endsWith(".$domain", ignoreCase = true)
		}
		if (!domainMatch) return false
		if (!url.encodedPath.startsWith(path)) return false
		if (secure && !url.isHttps) return false
		return true
	}

	public fun newBuilder(): Builder = Builder(this)

	override fun toString(): String = "$name=$value; domain=$domain; path=$path"

	override fun equals(other: Any?): Boolean =
		other is Cookie &&
			other.name == name &&
			other.value == value &&
			other.domain == domain &&
			other.path == path

	override fun hashCode(): Int {
		var result = name.hashCode()
		result = 31 * result + value.hashCode()
		result = 31 * result + domain.hashCode()
		result = 31 * result + path.hashCode()
		return result
	}

	public class Builder() {
		private var name: String? = null
		private var value: String? = null
		private var expiresAt: Long = 253402300799999L
		private var domain: String? = null
		private var path: String = "/"
		private var secure: Boolean = false
		private var httpOnly: Boolean = false
		private var persistent: Boolean = false
		private var hostOnly: Boolean = false

		internal constructor(cookie: Cookie) : this() {
			this.name = cookie.name
			this.value = cookie.value
			this.expiresAt = cookie.expiresAt
			this.domain = cookie.domain
			this.path = cookie.path
			this.secure = cookie.secure
			this.httpOnly = cookie.httpOnly
			this.persistent = cookie.persistent
			this.hostOnly = cookie.hostOnly
		}

		public fun name(name: String): Builder {
			this.name = name.trim()
			return this
		}

		public fun value(value: String): Builder {
			this.value = value.trim()
			return this
		}

		public fun expiresAt(expiresAt: Long): Builder {
			this.expiresAt = expiresAt
			this.persistent = true
			return this
		}

		public fun domain(domain: String): Builder {
			this.domain = domain.trim().removePrefix(".").lowercase()
			this.hostOnly = false
			return this
		}

		public fun hostOnlyDomain(domain: String): Builder {
			this.domain = domain.trim().removePrefix(".").lowercase()
			this.hostOnly = true
			return this
		}

		public fun path(path: String): Builder {
			this.path = if (path.startsWith("/")) path else "/$path"
			return this
		}

		public fun secure(): Builder {
			this.secure = true
			return this
		}

		public fun httpOnly(): Builder {
			this.httpOnly = true
			return this
		}

		public fun build(): Cookie = Cookie(
			name = requireNotNull(name) { "builder.name == null" },
			value = requireNotNull(value) { "builder.value == null" },
			expiresAt = expiresAt,
			domain = requireNotNull(domain) { "builder.domain == null" },
			path = path,
			secure = secure,
			httpOnly = httpOnly,
			persistent = persistent,
			hostOnly = hostOnly,
		)
	}

	public companion object {
		public fun parse(url: HttpUrl, setCookie: String): Cookie? {
			val parts = setCookie.split(';')
			if (parts.isEmpty()) return null
			val nameValue = parts[0].trim()
			val eqIdx = nameValue.indexOf('=')
			if (eqIdx <= 0) return null
			val name = nameValue.substring(0, eqIdx).trim()
			val value = nameValue.substring(eqIdx + 1).trim()
			if (name.isEmpty()) return null

			val b = Builder()
				.name(name)
				.value(value)
				.hostOnlyDomain(url.host)
				.path("/")

			for (i in 1 until parts.size) {
				val attr = parts[i].trim()
				if (attr.isEmpty()) continue
				val attrEq = attr.indexOf('=')
				val attrName = (if (attrEq >= 0) attr.substring(0, attrEq) else attr).trim().lowercase()
				val attrVal = (if (attrEq >= 0) attr.substring(attrEq + 1) else "").trim()
				when (attrName) {
					"domain" -> if (attrVal.isNotEmpty()) b.domain(attrVal)
					"path" -> if (attrVal.startsWith("/")) b.path(attrVal)
					"secure" -> b.secure()
					"httponly" -> b.httpOnly()
					"max-age" -> {
						val sec = attrVal.toLongOrNull()
						if (sec != null) {
							b.expiresAt(
								if (sec <= 0) Long.MIN_VALUE
								else org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis() + sec * 1000L,
							)
						}
					}
				}
			}
			return b.build()
		}

		public fun parseAll(url: HttpUrl, headers: Headers): List<Cookie> =
			headers.values("Set-Cookie").mapNotNull { parse(url, it) }
	}
}

public interface CookieJar {
	public fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>)
	public fun loadForRequest(url: HttpUrl): List<Cookie>

	public companion object {
		public val NO_COOKIES: CookieJar = object : CookieJar {
			override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
			override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
		}
	}
}

public class CacheControl private constructor(
	public val noCache: Boolean = false,
	public val noStore: Boolean = false,
	public val maxAgeSeconds: Int = -1,
	public val maxStaleSeconds: Int = -1,
	public val minFreshSeconds: Int = -1,
	public val onlyIfCached: Boolean = false,
) {
	override fun toString(): String {
		val parts = ArrayList<String>()
		if (noCache) parts.add("no-cache")
		if (noStore) parts.add("no-store")
		if (maxAgeSeconds != -1) parts.add("max-age=$maxAgeSeconds")
		if (maxStaleSeconds != -1) parts.add("max-stale=$maxStaleSeconds")
		if (minFreshSeconds != -1) parts.add("min-fresh=$minFreshSeconds")
		if (onlyIfCached) parts.add("only-if-cached")
		return parts.joinToString(", ")
	}

	public class Builder {
		private var noCache = false
		private var noStore = false
		private var maxAgeSeconds = -1
		private var maxStaleSeconds = -1
		private var minFreshSeconds = -1
		private var onlyIfCached = false

		public fun noCache(): Builder {
			this.noCache = true
			return this
		}

		public fun noStore(): Builder {
			this.noStore = true
			return this
		}

		public fun maxAge(maxAge: Int, timeUnit: TimeUnit): Builder {
			this.maxAgeSeconds = timeUnit.toSeconds(maxAge.toLong()).toInt()
			return this
		}

		public fun maxStale(maxStale: Int, timeUnit: TimeUnit): Builder {
			this.maxStaleSeconds = timeUnit.toSeconds(maxStale.toLong()).toInt()
			return this
		}

		public fun minFresh(minFresh: Int, timeUnit: TimeUnit): Builder {
			this.minFreshSeconds = timeUnit.toSeconds(minFresh.toLong()).toInt()
			return this
		}

		public fun onlyIfCached(): Builder {
			this.onlyIfCached = true
			return this
		}

		public fun build(): CacheControl = CacheControl(
			noCache = noCache,
			noStore = noStore,
			maxAgeSeconds = maxAgeSeconds,
			maxStaleSeconds = maxStaleSeconds,
			minFreshSeconds = minFreshSeconds,
			onlyIfCached = onlyIfCached,
		)
	}

	public companion object {
		public val FORCE_NETWORK: CacheControl = Builder().noCache().build()
		public val FORCE_CACHE: CacheControl = Builder().onlyIfCached().maxStale(Int.MAX_VALUE, TimeUnit.SECONDS).build()
	}
}

public class Cache(
	public val directory: File,
	public val maxSize: Long,
) : java.io.Closeable, java.io.Flushable {
	public fun evictAll() {}
	public fun size(): Long = 0L
	override fun flush() {}
	override fun close() {}
}
