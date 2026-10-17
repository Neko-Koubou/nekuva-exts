package okhttp3

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.URLDecoder
import java.net.URLEncoder
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.use
import org.nekosukuriputo.nekuva.ioscompat.Charset
import org.nekosukuriputo.nekuva.ioscompat.Charsets

public class MediaType internal constructor(
	private val mediaType: String,
	public val type: String,
	public val subtype: String,
	private val charsetParam: String? = null,
) {
	public fun charset(defaultValue: Charset? = Charsets.UTF_8): Charset? =
		if (charsetParam != null) Charset.forName(charsetParam) else defaultValue

	public fun parameter(name: String): String? =
		if (name.equals("charset", ignoreCase = true)) charsetParam else null

	override fun toString(): String = mediaType

	override fun equals(other: Any?): Boolean =
		other is MediaType && other.mediaType.equals(mediaType, ignoreCase = true)

	override fun hashCode(): Int = mediaType.lowercase().hashCode()

	public companion object {
		public fun String.toMediaType(): MediaType =
			toMediaTypeOrNull() ?: throw IllegalArgumentException("No subtype found for: \"$this\"")

		public fun String.toMediaTypeOrNull(): MediaType? {
			val trimmed = trim()
			val mainPart = trimmed.substringBefore(';').trim()
			val slashIdx = mainPart.indexOf('/')
			if (slashIdx <= 0 || slashIdx == mainPart.length - 1) return null
			val type = mainPart.substring(0, slashIdx).trim().lowercase()
			val subtype = mainPart.substring(slashIdx + 1).trim().lowercase()
			var charsetStr: String? = null
			val params = trimmed.substringAfter(';', "")
			if (params.isNotEmpty()) {
				for (p in params.split(';')) {
					val kv = p.trim()
					if (kv.startsWith("charset=", ignoreCase = true)) {
						charsetStr = kv.substringAfter('=').trim().trim('"', '\'')
					}
				}
			}
			return MediaType(trimmed, type, subtype, charsetStr)
		}

		public fun get(mediaType: String): MediaType = mediaType.toMediaType()
		public fun parse(mediaType: String): MediaType? = mediaType.toMediaTypeOrNull()
	}
}

public abstract class RequestBody {
	public abstract fun contentType(): MediaType?

	public open fun contentLength(): Long = -1L

	public abstract fun writeTo(sink: BufferedSink)

	public open fun isDuplex(): Boolean = false

	public open fun isOneShot(): Boolean = false

	public companion object {
		public val EMPTY: RequestBody = ByteArray(0).toRequestBody(null, 0, 0)

		public fun String.toRequestBody(contentType: MediaType? = null): RequestBody {
			val bytes = encodeToByteArray()
			val finalType = if (contentType != null && contentType.charset(null) == null) {
				"${contentType}; charset=utf-8".let {
					with(MediaType) { it.toMediaTypeOrNull() } ?: contentType
				}
			} else {
				contentType
			}
			return bytes.toRequestBody(finalType, 0, bytes.size)
		}

		public fun ByteString.toRequestBody(contentType: MediaType? = null): RequestBody =
			object : RequestBody() {
				override fun contentType(): MediaType? = contentType
				override fun contentLength(): Long = size.toLong()
				override fun writeTo(sink: BufferedSink) {
					sink.write(this@toRequestBody)
				}
			}

		public fun ByteArray.toRequestBody(
			contentType: MediaType? = null,
			offset: Int = 0,
			byteCount: Int = size - offset,
		): RequestBody = object : RequestBody() {
			override fun contentType(): MediaType? = contentType
			override fun contentLength(): Long = byteCount.toLong()
			override fun writeTo(sink: BufferedSink) {
				sink.write(this@toRequestBody, offset, byteCount)
			}
		}

		public fun File.asRequestBody(contentType: MediaType? = null): RequestBody =
			object : RequestBody() {
				override fun contentType(): MediaType? = contentType
				override fun contentLength(): Long = length()
				override fun writeTo(sink: BufferedSink) {
					sink.write(readBytes())
				}
			}

		public fun create(contentType: MediaType?, content: String): RequestBody =
			content.toRequestBody(contentType)

		public fun create(contentType: MediaType?, content: ByteString): RequestBody =
			content.toRequestBody(contentType)

		public fun create(
			contentType: MediaType?,
			content: ByteArray,
			offset: Int = 0,
			byteCount: Int = content.size - offset,
		): RequestBody = content.toRequestBody(contentType, offset, byteCount)

		public fun create(contentType: MediaType?, file: File): RequestBody =
			file.asRequestBody(contentType)
	}
}

public class FormBody internal constructor(
	internal val encodedNames: List<String>,
	internal val encodedValues: List<String>,
) : RequestBody() {

	public val size: Int
		get() = encodedNames.size

	public fun size(): Int = encodedNames.size

	public fun encodedName(index: Int): String = encodedNames[index]

	public fun name(index: Int): String = URLDecoder.decode(encodedNames[index], "UTF-8")

	public fun encodedValue(index: Int): String = encodedValues[index]

	public fun value(index: Int): String = URLDecoder.decode(encodedValues[index], "UTF-8")

	override fun contentType(): MediaType = CONTENT_TYPE

	override fun contentLength(): Long = toByteArray().size.toLong()

	override fun writeTo(sink: BufferedSink) {
		sink.write(toByteArray())
	}

	private fun toByteArray(): ByteArray {
		val sb = StringBuilder()
		for (i in encodedNames.indices) {
			if (i > 0) sb.append('&')
			sb.append(encodedNames[i]).append('=').append(encodedValues[i])
		}
		return sb.toString().encodeToByteArray()
	}

	public class Builder(private val charset: Charset? = null) {
		private val names = ArrayList<String>()
		private val values = ArrayList<String>()

		public fun add(name: String, value: String): Builder {
			names.add(URLEncoder.encode(name, "UTF-8"))
			values.add(URLEncoder.encode(value, "UTF-8"))
			return this
		}

		public fun addEncoded(name: String, value: String): Builder {
			names.add(name)
			values.add(value)
			return this
		}

		public fun build(): FormBody = FormBody(names.toList(), values.toList())
	}

	public companion object {
		private val CONTENT_TYPE: MediaType =
			with(MediaType) { "application/x-www-form-urlencoded".toMediaType() }
	}
}

public class MultipartBody internal constructor(
	public val boundaryByteString: ByteString,
	public val type: MediaType,
	public val parts: List<Part>,
) : RequestBody() {

	private val contentTypeVal: MediaType =
		with(MediaType) { "$type; boundary=${boundaryByteString.utf8()}".toMediaType() }

	public val boundary: String
		get() = boundaryByteString.utf8()

	public val size: Int
		get() = parts.size

	public fun part(index: Int): Part = parts[index]

	override fun contentType(): MediaType = contentTypeVal

	override fun contentLength(): Long {
		val buf = Buffer()
		writeTo(buf)
		return buf.size
	}

	override fun writeTo(sink: BufferedSink) {
		val boundaryStr = boundaryByteString.utf8()
		for (part in parts) {
			sink.writeUtf8("--$boundaryStr\r\n")
			val headers = part.headers
			if (headers != null) {
				for ((name, value) in headers) {
					sink.writeUtf8("$name: $value\r\n")
				}
			}
			val contentType = part.body.contentType()
			if (contentType != null) {
				sink.writeUtf8("Content-Type: $contentType\r\n")
			}
			val len = part.body.contentLength()
			if (len != -1L) {
				sink.writeUtf8("Content-Length: $len\r\n")
			}
			sink.writeUtf8("\r\n")
			part.body.writeTo(sink)
			sink.writeUtf8("\r\n")
		}
		sink.writeUtf8("--$boundaryStr--\r\n")
	}

	public class Part private constructor(
		public val headers: Headers?,
		public val body: RequestBody,
	) {
		public companion object {
			public fun create(body: RequestBody): Part = create(null, body)

			public fun create(headers: Headers?, body: RequestBody): Part = Part(headers, body)

			public fun createFormData(name: String, value: String): Part =
				createFormData(name, null, with(RequestBody) { value.toRequestBody(null) })

			public fun createFormData(name: String, filename: String?, body: RequestBody): Part {
				val disposition = buildString {
					append("form-data; name=\"").append(name).append('"')
					if (filename != null) {
						append("; filename=\"").append(filename).append('"')
					}
				}
				val headers = Headers.Builder()
					.addUnsafeNonAscii("Content-Disposition", disposition)
					.build()
				return create(headers, body)
			}
		}
	}

	public class Builder(boundary: String = java.util.UUID.randomUUID().toString()) {
		private val boundaryByteString: ByteString = boundary.encodeUtf8()
		private var type: MediaType = MIXED
		private val parts: MutableList<Part> = ArrayList()

		public fun setType(type: MediaType): Builder {
			this.type = type
			return this
		}

		public fun addPart(body: RequestBody): Builder {
			addPart(Part.create(body))
			return this
		}

		public fun addPart(headers: Headers?, body: RequestBody): Builder {
			addPart(Part.create(headers, body))
			return this
		}

		public fun addFormDataPart(name: String, value: String): Builder {
			addPart(Part.createFormData(name, value))
			return this
		}

		public fun addFormDataPart(name: String, filename: String?, body: RequestBody): Builder {
			addPart(Part.createFormData(name, filename, body))
			return this
		}

		public fun addPart(part: Part): Builder {
			parts.add(part)
			return this
		}

		public fun build(): MultipartBody = MultipartBody(boundaryByteString, type, parts.toList())
	}

	public companion object {
		public val MIXED: MediaType = with(MediaType) { "multipart/mixed".toMediaType() }
		public val ALTERNATIVE: MediaType = with(MediaType) { "multipart/alternative".toMediaType() }
		public val DIGEST: MediaType = with(MediaType) { "multipart/digest".toMediaType() }
		public val PARALLEL: MediaType = with(MediaType) { "multipart/parallel".toMediaType() }
		public val FORM: MediaType = with(MediaType) { "multipart/form-data".toMediaType() }
	}
}

public abstract class ResponseBody : Closeable {
	public abstract fun contentType(): MediaType?

	public abstract fun contentLength(): Long

	public abstract fun source(): BufferedSource

	public fun bytes(): ByteArray = source().use { it.readByteArray() }

	public fun byteString(): ByteString = source().use { it.readByteString() }

	public fun byteStream(): InputStream = ByteArrayInputStream(bytes())

	public fun charStream(): Reader = InputStreamReader(byteStream(), charset())

	public fun string(): String = source().use { src ->
		val bytes = src.readByteArray()
		org.nekosukuriputo.nekuva.ioscompat.String(bytes, charset())
	}

	private fun charset(): Charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8

	override fun close() {
		try {
			source().close()
		} catch (_: Throwable) {
		}
	}

	public companion object {
		public val EMPTY: ResponseBody = ByteArray(0).toResponseBody(null)

		public fun String.toResponseBody(contentType: MediaType? = null): ResponseBody =
			encodeToByteArray().toResponseBody(contentType)

		public fun ByteArray.toResponseBody(contentType: MediaType? = null): ResponseBody {
			val buffer = Buffer().write(this)
			return buffer.asResponseBody(contentType, size.toLong())
		}

		public fun ByteString.toResponseBody(contentType: MediaType? = null): ResponseBody {
			val buffer = Buffer().write(this)
			return buffer.asResponseBody(contentType, size.toLong())
		}

		public fun BufferedSource.asResponseBody(
			contentType: MediaType? = null,
			contentLength: Long = -1L,
		): ResponseBody = object : ResponseBody() {
			override fun contentType(): MediaType? = contentType
			override fun contentLength(): Long = contentLength
			override fun source(): BufferedSource = this@asResponseBody
		}

		public fun create(contentType: MediaType?, content: String): ResponseBody =
			content.toResponseBody(contentType)

		public fun create(contentType: MediaType?, content: ByteArray): ResponseBody =
			content.toResponseBody(contentType)

		public fun create(contentType: MediaType?, content: ByteString): ResponseBody =
			content.toResponseBody(contentType)

		public fun create(
			contentType: MediaType?,
			contentLength: Long,
			content: BufferedSource,
		): ResponseBody = content.asResponseBody(contentType, contentLength)
	}
}
