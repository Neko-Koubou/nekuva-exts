package java.net

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.nekosukuriputo.nekuva.ioscompat.Charset
import org.nekosukuriputo.nekuva.ioscompat.Charsets

public open class SocketTimeoutException(message: String? = null) : java.io.InterruptedIOException(message)
public open class UnknownHostException(message: String? = null) : IOException(message)
public open class ConnectException(message: String? = null) : IOException(message)
public open class MalformedURLException(message: String? = null) : IOException(message)
public open class URISyntaxException(
	public val input: String,
	public val reason: String,
	public val index: Int = -1,
) : Exception("$reason at index $index: $input")

public object URLEncoder {
	public fun encode(s: String, enc: String = "UTF-8"): String = encode(s, Charset.forName(enc))

	public fun encode(s: String, charset: Charset): String {
		val bytes = s.encodeToByteArray()
		val sb = StringBuilder(bytes.size * 2)
		for (b in bytes) {
			val c = b.toInt() and 0xFF
			val ch = c.toChar()
			when {
				ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '*' -> {
					sb.append(ch)
				}
				ch == ' ' -> sb.append('+')
				else -> {
					sb.append('%')
					sb.append("0123456789ABCDEF"[c ushr 4])
					sb.append("0123456789ABCDEF"[c and 0x0F])
				}
			}
		}
		return sb.toString()
	}
}

public object URLDecoder {
	public fun decode(s: String?, enc: String = "UTF-8"): String =
		if (s == null) "" else decode(s, Charset.forName(enc))

	public fun decode(s: String, charset: Charset): String {
		val out = java.io.ByteArrayOutputStream(s.length)
		var i = 0
		while (i < s.length) {
			val ch = s[i]
			when (ch) {
				'+' -> {
					out.write(' '.code)
					i++
				}
				'%' -> {
					if (i + 2 < s.length) {
						val hex = s.substring(i + 1, i + 3)
						val v = hex.toIntOrNull(16)
						if (v != null) {
							out.write(v)
							i += 3
							continue
						}
					}
					out.write('%'.code)
					i++
				}
				else -> {
					val bytes = ch.toString().encodeToByteArray()
					out.write(bytes, 0, bytes.size)
					i++
				}
			}
		}
		return out.toByteArray().decodeToString()
	}
}

public class URI(private val raw: String) {
	public val scheme: String?
		get() {
			val colonIdx = raw.indexOf(':')
			if (colonIdx <= 0) return null
			val s = raw.substring(0, colonIdx)
			if (s.first().isLetter() && s.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) {
				return s
			}
			return null
		}

	public val schemeSpecificPart: String
		get() {
			val s = if (scheme != null) raw.substring(scheme!!.length + 1) else raw
			return s.substringBefore('#')
		}

	public val authority: String?
		get() {
			val afterScheme = if (scheme != null) raw.substring(scheme!!.length + 1) else raw
			if (!afterScheme.startsWith("//")) return null
			val authorityPart = afterScheme.removePrefix("//").substringBefore('/').substringBefore('?').substringBefore('#')
			return authorityPart.ifEmpty { null }
		}

	public val host: String?
		get() {
			val auth = authority ?: return null
			return auth.substringAfterLast('@').substringBefore(':')
		}

	public val port: Int
		get() {
			val auth = authority ?: return -1
			val portStr = auth.substringAfterLast('@').substringAfterLast(':', "")
			return portStr.toIntOrNull() ?: -1
		}

	public val path: String?
		get() {
			val afterScheme = if (scheme != null) raw.substring(scheme!!.length + 1) else raw
			val withoutFragQuery = afterScheme.substringBefore('?').substringBefore('#')
			val rawPath = if (withoutFragQuery.startsWith("//")) {
				val slashIdx = withoutFragQuery.indexOf('/', startIndex = 2)
				if (slashIdx >= 0) withoutFragQuery.substring(slashIdx) else ""
			} else {
				withoutFragQuery
			}
			return URLDecoder.decode(rawPath)
		}

	public val rawPath: String?
		get() {
			val afterScheme = if (scheme != null) raw.substring(scheme!!.length + 1) else raw
			val withoutFragQuery = afterScheme.substringBefore('?').substringBefore('#')
			return if (withoutFragQuery.startsWith("//")) {
				val slashIdx = withoutFragQuery.indexOf('/', startIndex = 2)
				if (slashIdx >= 0) withoutFragQuery.substring(slashIdx) else ""
			} else {
				withoutFragQuery
			}
		}

	public val query: String?
		get() {
			val q = raw.substringAfter('?', "").substringBefore('#')
			return q.ifEmpty { null }
		}

	public val rawQuery: String?
		get() = query

	public val fragment: String?
		get() {
			val f = raw.substringAfter('#', "")
			return f.ifEmpty { null }
		}

	public fun resolve(str: String): URI {
		if (str.contains("://")) return URI(str)
		if (str.startsWith("//")) return URI("${scheme ?: "https"}:$str")
		val baseHost = "${scheme ?: "https"}://${authority ?: ""}"
		if (str.startsWith("/")) return URI("$baseHost$str")
		val basePath = (path ?: "/").substringBeforeLast('/', "")
		return URI("$baseHost$basePath/$str")
	}

	public fun toURL(): URL = URL(raw)

	public fun toASCIIString(): String = raw

	override fun toString(): String = raw

	override fun equals(other: Any?): Boolean = other is URI && other.raw == raw

	override fun hashCode(): Int = raw.hashCode()

	public companion object {
		public fun create(str: String): URI = URI(str)
	}
}

public fun URI.toFile(): File {
	val p = this.path?.ifEmpty { null } ?: this.schemeSpecificPart
	val clean = p.removePrefix("file://").removePrefix("file:")
	return File(clean)
}

public class URL {
	private val raw: String

	public constructor(spec: String) {
		this.raw = spec
	}

	public constructor(context: URL?, spec: String) {
		this.raw = if (context == null || spec.contains("://")) {
			spec
		} else {
			context.toURI().resolve(spec).toString()
		}
	}

	public val protocol: String
		get() = URI(raw).scheme ?: "https"

	public val host: String
		get() = URI(raw).host ?: ""

	public val authority: String
		get() = URI(raw).authority ?: ""

	public val port: Int
		get() = URI(raw).port

	public val defaultPort: Int
		get() = if (protocol == "https") 443 else 80

	public val path: String
		get() = URI(raw).path ?: ""

	public val query: String?
		get() = URI(raw).query

	public val file: String
		get() {
			val p = path
			val q = query
			return if (q != null) "$p?$q" else p
		}

	public val ref: String?
		get() = URI(raw).fragment

	public fun toURI(): URI = URI(raw)

	public fun toExternalForm(): String = raw

	override fun toString(): String = raw

	override fun equals(other: Any?): Boolean = other is URL && other.raw == raw

	override fun hashCode(): Int = raw.hashCode()
}

public object HttpURLConnection {
	public const val HTTP_OK: Int = 200
	public const val HTTP_CREATED: Int = 201
	public const val HTTP_ACCEPTED: Int = 202
	public const val HTTP_NO_CONTENT: Int = 204
	public const val HTTP_PARTIAL: Int = 206
	public const val HTTP_MOVED_PERM: Int = 301
	public const val HTTP_MOVED_TEMP: Int = 302
	public const val HTTP_SEE_OTHER: Int = 303
	public const val HTTP_NOT_MODIFIED: Int = 304
	public const val HTTP_BAD_REQUEST: Int = 400
	public const val HTTP_UNAUTHORIZED: Int = 401
	public const val HTTP_FORBIDDEN: Int = 403
	public const val HTTP_NOT_FOUND: Int = 404
	public const val HTTP_CLIENT_TIMEOUT: Int = 408
	public const val HTTP_CONFLICT: Int = 409
	public const val HTTP_GONE: Int = 410
	public const val HTTP_INTERNAL_ERROR: Int = 500
	public const val HTTP_BAD_GATEWAY: Int = 502
	public const val HTTP_UNAVAILABLE: Int = 503
	public const val HTTP_GATEWAY_TIMEOUT: Int = 504
}

public class InetAddress private constructor(
	public val hostName: String,
	public val address: ByteArray,
) {
	public fun getHostAddress(): String =
		if (address.size == 4) {
			address.joinToString(".") { (it.toInt() and 0xFF).toString() }
		} else {
			hostName
		}

	override fun toString(): String = "$hostName/${getHostAddress()}"

	public companion object {
		public fun getByName(host: String): InetAddress =
			InetAddress(host, byteArrayOf(127, 0, 0, 1))

		public fun getAllByName(host: String): Array<InetAddress> =
			arrayOf(getByName(host))

		public fun getByAddress(host: String?, addr: ByteArray): InetAddress =
			InetAddress(host ?: "", addr)
	}
}

public open class SocketAddress

public class InetSocketAddress(
	public val hostName: String,
	public val port: Int,
) : SocketAddress() {
	public constructor(addr: InetAddress, port: Int) : this(addr.hostName, port)

	public val hostString: String
		get() = hostName

	public val address: InetAddress
		get() = InetAddress.getByName(hostName)

	override fun toString(): String = "$hostName:$port"

	public companion object {
		public fun createUnresolved(host: String, port: Int): InetSocketAddress =
			InetSocketAddress(host, port)
	}
}

public class Proxy(
	private val proxyType: Type,
	private val sa: SocketAddress?,
) {
	public enum class Type {
		DIRECT,
		HTTP,
		SOCKS,
	}

	public fun type(): Type = proxyType
	public fun address(): SocketAddress? = sa

	public companion object {
		public val NO_PROXY: Proxy = Proxy(Type.DIRECT, null)
	}
}

public abstract class ProxySelector {
	public abstract fun select(uri: URI?): List<Proxy>
	public abstract fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?)

	public companion object {
		private var defaultSelector: ProxySelector? = null
		public fun getDefault(): ProxySelector? = defaultSelector
		public fun setDefault(ps: ProxySelector?) {
			defaultSelector = ps
		}
	}
}

public open class ProtocolException(message: String? = null) : IOException(message)

public object IDN {
	public fun toASCII(input: String): String = input
	public fun toUnicode(input: String): String = input
}

public abstract class Authenticator {
	protected open fun getPasswordAuthentication(): PasswordAuthentication? = null

	public companion object {
		private var defaultAuthenticator: Authenticator? = null
		public fun setDefault(a: Authenticator?) {
			defaultAuthenticator = a
		}
	}
}

public class PasswordAuthentication(
	public val userName: String,
	public val password: CharArray,
)
