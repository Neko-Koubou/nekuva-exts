@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package java.util.zip

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.zlib.MAX_WBITS
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

public class CRC32 {
	private var crc: ULong = 0uL

	public fun update(b: Int) {
		update(byteArrayOf(b.toByte()), 0, 1)
	}

	public fun update(b: ByteArray, off: Int = 0, len: Int = b.size - off) {
		if (len <= 0) return
		b.usePinned { pinned ->
			crc = crc32(crc, pinned.addressOf(off).reinterpret(), len.toUInt())
		}
	}

	public val value: Long
		get() = crc.toLong() and 0xFFFFFFFFL

	public fun getValue(): Long = value

	public fun reset() {
		crc = 0uL
	}
}

public open class Deflater(
	private val level: Int = DEFAULT_COMPRESSION,
	private val nowrap: Boolean = false,
) {
	private var inputBuf: ByteArray = ByteArray(0)
	private var finishedFlag: Boolean = false

	public fun setInput(b: ByteArray, off: Int = 0, len: Int = b.size - off) {
		inputBuf = b.copyOfRange(off, off + len)
	}

	public fun finish() {}

	public fun finished(): Boolean = finishedFlag

	public fun deflate(output: ByteArray, off: Int = 0, len: Int = output.size - off): Int {
		if (finishedFlag) return 0
		val compressed = zlibCompress(inputBuf, level = level, nowrap = nowrap, gzip = false)
		val toCopy = minOf(len, compressed.size)
		compressed.copyInto(output, destinationOffset = off, startIndex = 0, endIndex = toCopy)
		finishedFlag = true
		return toCopy
	}

	public fun end() {}

	public companion object {
		public const val DEFAULT_COMPRESSION: Int = -1
		public const val NO_COMPRESSION: Int = 0
		public const val BEST_SPEED: Int = 1
		public const val BEST_COMPRESSION: Int = 9
		public const val DEFLATED: Int = 8
	}
}

public open class Inflater(private val nowrap: Boolean = false) {
	private var inputBuf: ByteArray = ByteArray(0)
	private var finishedFlag: Boolean = false

	public fun setInput(b: ByteArray, off: Int = 0, len: Int = b.size - off) {
		inputBuf = b.copyOfRange(off, off + len)
	}

	public fun finished(): Boolean = finishedFlag

	public fun inflate(output: ByteArray, off: Int = 0, len: Int = output.size - off): Int {
		if (finishedFlag) return 0
		val decompressed = zlibDecompress(inputBuf, windowBits = if (nowrap) -MAX_WBITS else MAX_WBITS)
		val toCopy = minOf(len, decompressed.size)
		decompressed.copyInto(output, destinationOffset = off, startIndex = 0, endIndex = toCopy)
		finishedFlag = true
		return toCopy
	}

	public fun end() {}
}

internal fun zlibCompress(
	input: ByteArray,
	level: Int = Z_DEFAULT_COMPRESSION,
	nowrap: Boolean = false,
	gzip: Boolean = false,
): ByteArray {
	val windowBits = when {
		gzip -> MAX_WBITS + 16
		nowrap -> -MAX_WBITS
		else -> MAX_WBITS
	}
	memScoped {
		val stream = alloc<z_stream>()
		val initRes = deflateInit2_(
			stream.ptr,
			level,
			Z_DEFLATED,
			windowBits,
			8,
			0,
			zlibVersion()?.toKString() ?: "1.2.11",
			kotlinx.cinterop.sizeOf<z_stream>().toInt(),
		)
		if (initRes != Z_OK) {
			throw IOException("deflateInit2 failed: $initRes")
		}
		try {
			val out = ByteArrayOutputStream(maxOf(64, input.size))
			val chunk = ByteArray(16 * 1024)
			chunk.usePinned { pinnedChunk ->
				if (input.isNotEmpty()) {
					input.usePinned { pinnedIn ->
						stream.next_in = pinnedIn.addressOf(0).reinterpret()
						stream.avail_in = input.size.toUInt()
						do {
							stream.next_out = pinnedChunk.addressOf(0).reinterpret()
							stream.avail_out = chunk.size.toUInt()
							deflate(stream.ptr, Z_FINISH)
							val have = chunk.size - stream.avail_out.toInt()
							if (have > 0) out.write(chunk, 0, have)
						} while (stream.avail_out == 0u)
					}
				} else {
					stream.next_in = null
					stream.avail_in = 0u
					do {
						stream.next_out = pinnedChunk.addressOf(0).reinterpret()
						stream.avail_out = chunk.size.toUInt()
						deflate(stream.ptr, Z_FINISH)
						val have = chunk.size - stream.avail_out.toInt()
						if (have > 0) out.write(chunk, 0, have)
					} while (stream.avail_out == 0u)
				}
			}
			return out.toByteArray()
		} finally {
			deflateEnd(stream.ptr)
		}
	}
}

internal fun zlibDecompress(
	input: ByteArray,
	windowBits: Int = MAX_WBITS + 32,
): ByteArray {
	if (input.isEmpty()) return ByteArray(0)
	memScoped {
		val stream = alloc<z_stream>()
		val initRes = inflateInit2_(
			stream.ptr,
			windowBits,
			zlibVersion()?.toKString() ?: "1.2.11",
			kotlinx.cinterop.sizeOf<z_stream>().toInt(),
		)
		if (initRes != Z_OK) {
			throw IOException("inflateInit2 failed: $initRes")
		}
		try {
			val out = ByteArrayOutputStream(input.size * 2)
			val chunk = ByteArray(16 * 1024)
			input.usePinned { pinnedIn ->
				chunk.usePinned { pinnedChunk ->
					stream.next_in = pinnedIn.addressOf(0).reinterpret()
					stream.avail_in = input.size.toUInt()
					var status: Int
					do {
						stream.next_out = pinnedChunk.addressOf(0).reinterpret()
						stream.avail_out = chunk.size.toUInt()
						status = inflate(stream.ptr, Z_NO_FLUSH)
						if (status != Z_OK && status != Z_STREAM_END) {
							break
						}
						val have = chunk.size - stream.avail_out.toInt()
						if (have > 0) out.write(chunk, 0, have)
					} while (status != Z_STREAM_END && stream.avail_in > 0u)
				}
			}
			return out.toByteArray()
		} finally {
			inflateEnd(stream.ptr)
		}
	}
}

public open class GZIPInputStream(inStream: InputStream) : ByteArrayInputStream(
	zlibDecompress(inStream.readBytes(), windowBits = MAX_WBITS + 32),
)

public open class GZIPOutputStream(private val outStream: OutputStream) : OutputStream() {
	private val buffer = ByteArrayOutputStream()
	private var closed = false

	override fun write(b: Int) = buffer.write(b)
	override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)

	override fun close() {
		if (!closed) {
			closed = true
			val gz = zlibCompress(buffer.toByteArray(), gzip = true)
			outStream.write(gz)
			outStream.close()
		}
	}
}

public open class InflaterInputStream(
	inStream: InputStream,
	inflater: Inflater = Inflater(),
) : ByteArrayInputStream(
	try {
		val raw = inStream.readBytes()
		try {
			zlibDecompress(raw, windowBits = MAX_WBITS + 32)
		} catch (_: Throwable) {
			zlibDecompress(raw, windowBits = -MAX_WBITS)
		}
	} catch (_: Throwable) {
		ByteArray(0)
	},
)

public open class DeflaterOutputStream(
	private val outStream: OutputStream,
	private val deflater: Deflater = Deflater(),
) : OutputStream() {
	private val buffer = ByteArrayOutputStream()
	private var closed = false

	override fun write(b: Int) = buffer.write(b)
	override fun write(b: ByteArray, off: Int, len: Int) = buffer.write(b, off, len)

	override fun close() {
		if (!closed) {
			closed = true
			val compressed = zlibCompress(buffer.toByteArray(), nowrap = false)
			outStream.write(compressed)
			outStream.close()
		}
	}
}

public open class ZipEntry(public val name: String) {
	public var size: Long = -1L
	public var compressedSize: Long = -1L
	public var crc: Long = -1L
	public var method: Int = DEFLATED
	public var time: Long = org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis()
	public var comment: String? = null
	public var extra: ByteArray? = null

	public val isDirectory: Boolean
		get() = name.endsWith('/')

	public fun isDirectory(): Boolean = name.endsWith('/')

	override fun toString(): String = name

	public companion object {
		public const val STORED: Int = 0
		public const val DEFLATED: Int = 8
	}
}

public open class ZipInputStream(inStream: InputStream) : InputStream() {
	private val parsedEntries: List<Pair<ZipEntry, ByteArray>> = parseZipBytes(inStream.readBytes())
	private var entryIndex = -1
	private var currentStream: ByteArrayInputStream? = null

	public val nextEntry: ZipEntry?
		get() = getNextEntry()

	public fun getNextEntry(): ZipEntry? {
		entryIndex++
		if (entryIndex >= parsedEntries.size) {
			currentStream = null
			return null
		}
		val (entry, data) = parsedEntries[entryIndex]
		currentStream = ByteArrayInputStream(data)
		return entry
	}

	public fun closeEntry() {
		currentStream = null
	}

	override fun read(): Int = currentStream?.read() ?: -1

	override fun read(b: ByteArray, off: Int, len: Int): Int =
		currentStream?.read(b, off, len) ?: -1

	override fun readBytes(): ByteArray =
		currentStream?.readBytes() ?: ByteArray(0)
}

public open class ZipFile(private val file: File) : Closeable {
	public constructor(name: String) : this(File(name))

	private val entryMap: LinkedHashMap<String, Pair<ZipEntry, ByteArray>> by lazy {
		val list = parseZipBytes(file.readBytes())
		val map = LinkedHashMap<String, Pair<ZipEntry, ByteArray>>(list.size)
		for (pair in list) {
			map[pair.first.name] = pair
		}
		map
	}

	public fun entries(): Iterator<ZipEntry> = entryMap.values.map { it.first }.iterator()

	public fun getEntry(name: String): ZipEntry? = entryMap[name]?.first

	public fun getInputStream(entry: ZipEntry): InputStream {
		val data = entryMap[entry.name]?.second
			?: throw IOException("ZipEntry not found: ${entry.name}")
		return ByteArrayInputStream(data)
	}

	public fun size(): Int = entryMap.size

	override fun close() {}
}

public open class ZipOutputStream(private val out: OutputStream) : OutputStream() {
	private data class WrittenEntry(
		val entry: ZipEntry,
		val rawBytes: ByteArray,
		val compressedBytes: ByteArray,
		val crc: Long,
		val method: Int,
		val offset: Int,
	)

	private val writtenEntries = ArrayList<WrittenEntry>()
	private var currentEntry: ZipEntry? = null
	private var currentBuffer: ByteArrayOutputStream? = null
	private var defaultMethod: Int = DEFLATED
	private var defaultLevel: Int = Deflater.DEFAULT_COMPRESSION
	private var offset = 0
	private var closed = false
	private var finished = false
	private var archiveComment: ByteArray = ByteArray(0)

	public fun setMethod(method: Int) {
		defaultMethod = method
	}

	public fun setLevel(level: Int) {
		defaultLevel = level
	}

	public fun setComment(comment: String?) {
		archiveComment = (comment ?: "").encodeToByteArray()
	}

	public fun putNextEntry(e: ZipEntry) {
		if (currentEntry != null) {
			closeEntry()
		}
		currentEntry = e
		currentBuffer = ByteArrayOutputStream()
	}

	public fun closeEntry() {
		val entry = currentEntry ?: return
		val raw = currentBuffer?.toByteArray() ?: ByteArray(0)
		val crc32 = CRC32().apply { update(raw) }.value
		val method = if (entry.method == STORED || raw.isEmpty()) STORED else defaultMethod
		val comp = if (method == STORED) {
			raw
		} else {
			zlibCompress(raw, level = defaultLevel, nowrap = true)
		}
		val nameBytes = entry.name.encodeToByteArray()
		val extraBytes = entry.extra ?: ByteArray(0)
		val localHeaderOffset = offset

		// Local file header signature (0x04034b50)
		val header = ByteArrayOutputStream(30 + nameBytes.size + extraBytes.size)
		header.writeIntLE(0x04034b50)
		header.writeShortLE(20) // version needed
		header.writeShortLE(0x0800) // UTF-8 flag
		header.writeShortLE(method)
		header.writeIntLE(0) // mod time/date
		header.writeIntLE(crc32.toInt())
		header.writeIntLE(comp.size)
		header.writeIntLE(raw.size)
		header.writeShortLE(nameBytes.size)
		header.writeShortLE(extraBytes.size)
		header.write(nameBytes)
		header.write(extraBytes)

		val headerBytes = header.toByteArray()
		out.write(headerBytes)
		out.write(comp)
		offset += headerBytes.size + comp.size

		writtenEntries.add(
			WrittenEntry(
				entry = entry,
				rawBytes = raw,
				compressedBytes = comp,
				crc = crc32,
				method = method,
				offset = localHeaderOffset,
			),
		)
		currentEntry = null
		currentBuffer = null
	}

	override fun write(b: Int) {
		currentBuffer?.write(b) ?: throw IOException("No active ZipEntry")
	}

	override fun write(b: ByteArray, off: Int, len: Int) {
		currentBuffer?.write(b, off, len) ?: throw IOException("No active ZipEntry")
	}

	public fun finish() {
		if (finished) return
		finished = true
		if (currentEntry != null) {
			closeEntry()
		}
		val centralDirOffset = offset
		val centralDir = ByteArrayOutputStream()
		for (item in writtenEntries) {
			val nameBytes = item.entry.name.encodeToByteArray()
			val extraBytes = item.entry.extra ?: ByteArray(0)
			val commentBytes = (item.entry.comment ?: "").encodeToByteArray()
			centralDir.writeIntLE(0x02014b50)
			centralDir.writeShortLE(20) // version made by
			centralDir.writeShortLE(20) // version needed
			centralDir.writeShortLE(0x0800) // UTF-8
			centralDir.writeShortLE(item.method)
			centralDir.writeIntLE(0) // mod time/date
			centralDir.writeIntLE(item.crc.toInt())
			centralDir.writeIntLE(item.compressedBytes.size)
			centralDir.writeIntLE(item.rawBytes.size)
			centralDir.writeShortLE(nameBytes.size)
			centralDir.writeShortLE(extraBytes.size)
			centralDir.writeShortLE(commentBytes.size)
			centralDir.writeShortLE(0) // disk number
			centralDir.writeShortLE(0) // internal attrs
			centralDir.writeIntLE(0) // external attrs
			centralDir.writeIntLE(item.offset)
			centralDir.write(nameBytes)
			centralDir.write(extraBytes)
			centralDir.write(commentBytes)
		}
		val centralBytes = centralDir.toByteArray()
		out.write(centralBytes)
		offset += centralBytes.size

		// End of Central Directory (0x06054b50)
		val eocd = ByteArrayOutputStream(22 + archiveComment.size)
		eocd.writeIntLE(0x06054b50)
		eocd.writeShortLE(0)
		eocd.writeShortLE(0)
		eocd.writeShortLE(writtenEntries.size)
		eocd.writeShortLE(writtenEntries.size)
		eocd.writeIntLE(centralBytes.size)
		eocd.writeIntLE(centralDirOffset)
		eocd.writeShortLE(archiveComment.size)
		eocd.write(archiveComment)
		out.write(eocd.toByteArray())
		out.flush()
	}

	override fun flush() {
		out.flush()
	}

	override fun close() {
		if (!closed) {
			closed = true
			if (!finished) {
				finish()
			}
			out.close()
		}
	}

	public companion object {
		public const val STORED: Int = 0
		public const val DEFLATED: Int = 8
	}
}

private fun ByteArrayOutputStream.writeShortLE(v: Int) {
	write(v and 0xFF)
	write((v ushr 8) and 0xFF)
}

private fun ByteArrayOutputStream.writeIntLE(v: Int) {
	write(v and 0xFF)
	write((v ushr 8) and 0xFF)
	write((v ushr 16) and 0xFF)
	write((v ushr 24) and 0xFF)
}

private fun ByteArray.readUShortLE(offset: Int): Int =
	(this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.readIntLE(offset: Int): Int =
	(this[offset].toInt() and 0xFF) or
		((this[offset + 1].toInt() and 0xFF) shl 8) or
		((this[offset + 2].toInt() and 0xFF) shl 16) or
		((this[offset + 3].toInt() and 0xFF) shl 24)

internal fun parseZipBytes(bytes: ByteArray): List<Pair<ZipEntry, ByteArray>> {
	if (bytes.size < 22) return emptyList()
	val result = ArrayList<Pair<ZipEntry, ByteArray>>()

	// Try Central Directory first (accurate even when data descriptors are used)
	var eocdPos = -1
	val minEocdSearch = maxOf(0, bytes.size - 65557)
	for (i in (bytes.size - 22) downTo minEocdSearch) {
		if (bytes.readIntLE(i) == 0x06054b50) {
			eocdPos = i
			break
		}
	}

	if (eocdPos >= 0) {
		val totalEntries = bytes.readUShortLE(eocdPos + 10)
		var cdPos = bytes.readIntLE(eocdPos + 16)
		for (idx in 0 until totalEntries) {
			if (cdPos + 46 > bytes.size || bytes.readIntLE(cdPos) != 0x02014b50) break
			val method = bytes.readUShortLE(cdPos + 10)
			val crc = bytes.readIntLE(cdPos + 16).toLong() and 0xFFFFFFFFL
			val compSize = bytes.readIntLE(cdPos + 20)
			val uncompSize = bytes.readIntLE(cdPos + 24)
			val nameLen = bytes.readUShortLE(cdPos + 28)
			val extraLen = bytes.readUShortLE(cdPos + 30)
			val commentLen = bytes.readUShortLE(cdPos + 32)
			val localOffset = bytes.readIntLE(cdPos + 42)
			val name = bytes.decodeToString(cdPos + 46, cdPos + 46 + nameLen)

			val entry = ZipEntry(name).apply {
				this.method = method
				this.crc = crc
				this.compressedSize = compSize.toLong()
				this.size = uncompSize.toLong()
			}

			if (localOffset + 30 <= bytes.size && bytes.readIntLE(localOffset) == 0x04034b50) {
				val localNameLen = bytes.readUShortLE(localOffset + 26)
				val localExtraLen = bytes.readUShortLE(localOffset + 28)
				val dataStart = localOffset + 30 + localNameLen + localExtraLen
				val dataEnd = (dataStart + compSize).coerceAtMost(bytes.size)
				val compData = if (dataStart < dataEnd) bytes.copyOfRange(dataStart, dataEnd) else ByteArray(0)
				val rawData = when {
					entry.isDirectory || compData.isEmpty() -> ByteArray(0)
					method == ZipEntry.STORED -> compData
					else -> try {
						zlibDecompress(compData, windowBits = -MAX_WBITS)
					} catch (_: Throwable) {
						compData
					}
				}
				result.add(entry to rawData)
			}
			cdPos += 46 + nameLen + extraLen + commentLen
		}
		if (result.isNotEmpty()) return result
	}

	// Fallback: sequential local file headers
	var pos = 0
	while (pos + 30 <= bytes.size && bytes.readIntLE(pos) == 0x04034b50) {
		val method = bytes.readUShortLE(pos + 8)
		val crc = bytes.readIntLE(pos + 14).toLong() and 0xFFFFFFFFL
		val compSize = bytes.readIntLE(pos + 18)
		val uncompSize = bytes.readIntLE(pos + 22)
		val nameLen = bytes.readUShortLE(pos + 26)
		val extraLen = bytes.readUShortLE(pos + 28)
		val name = bytes.decodeToString(pos + 30, pos + 30 + nameLen)
		val dataStart = pos + 30 + nameLen + extraLen
		val dataEnd = (dataStart + compSize).coerceAtMost(bytes.size)
		val compData = if (dataStart < dataEnd) bytes.copyOfRange(dataStart, dataEnd) else ByteArray(0)
		val entry = ZipEntry(name).apply {
			this.method = method
			this.crc = crc
			this.compressedSize = compSize.toLong()
			this.size = uncompSize.toLong()
		}
		val rawData = when {
			entry.isDirectory || compData.isEmpty() -> ByteArray(0)
			method == ZipEntry.STORED -> compData
			else -> try {
				zlibDecompress(compData, windowBits = -MAX_WBITS)
			} catch (_: Throwable) {
				compData
			}
		}
		result.add(entry to rawData)
		pos = dataEnd
	}
	return result
}
