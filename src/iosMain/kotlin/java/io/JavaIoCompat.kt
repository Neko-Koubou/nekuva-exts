@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package java.io

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import okio.Path
import okio.Path.Companion.toPath
import org.nekosukuriputo.nekuva.ioscompat.Charset
import org.nekosukuriputo.nekuva.ioscompat.Charsets
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.FILE
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.feof
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.memcpy
import platform.posix.rename

public typealias IOException = okio.IOException
public typealias EOFException = okio.EOFException
public typealias FileNotFoundException = okio.FileNotFoundException
public typealias Closeable = kotlin.AutoCloseable

public open class InterruptedIOException(message: String? = null) : IOException(message)

public interface Flushable {
	public fun flush()
}

public interface Serializable

public fun interface FileFilter {
	public fun accept(pathname: File): Boolean
}

public fun interface FilenameFilter {
	public fun accept(dir: File, name: String): Boolean
}

public abstract class InputStream : Closeable {
	public abstract fun read(): Int

	public open fun read(b: ByteArray): Int = read(b, 0, b.size)

	public open fun read(b: ByteArray, off: Int, len: Int): Int {
		if (len == 0) return 0
		val first = read()
		if (first == -1) return -1
		b[off] = first.toByte()
		var count = 1
		while (count < len) {
			val next = read()
			if (next == -1) break
			b[off + count] = next.toByte()
			count++
		}
		return count
	}

	public open fun readBytes(): ByteArray {
		val out = ByteArrayOutputStream()
		copyTo(out)
		return out.toByteArray()
	}

	public open fun available(): Int = 0

	public open fun skip(n: Long): Long {
		var remaining = n
		val buf = ByteArray(2048)
		while (remaining > 0) {
			val toRead = minOf(remaining, buf.size.toLong()).toInt()
			val r = read(buf, 0, toRead)
			if (r < 0) break
			remaining -= r
		}
		return n - remaining
	}

	override fun close() {}
}

public fun InputStream.copyTo(out: OutputStream, bufferSize: Int = 8 * 1024): Long {
	var bytesCopied: Long = 0
	val buffer = ByteArray(bufferSize)
	var bytes = read(buffer)
	while (bytes >= 0) {
		out.write(buffer, 0, bytes)
		bytesCopied += bytes
		bytes = read(buffer)
	}
	return bytesCopied
}

public fun InputStream.readBytes(): ByteArray {
	val out = ByteArrayOutputStream()
	copyTo(out)
	return out.toByteArray()
}

public fun InputStream.bufferedReader(charset: Charset = Charsets.UTF_8): BufferedReader =
	BufferedReader(InputStreamReader(this, charset))

public fun InputStream.reader(charset: Charset = Charsets.UTF_8): InputStreamReader =
	InputStreamReader(this, charset)

public open class ByteArrayInputStream(
	protected val buf: ByteArray,
	 offset: Int = 0,
	length: Int = buf.size - offset,
) : InputStream() {
	protected var pos: Int = offset
	protected var count: Int = minOf(offset + length, buf.size)

	override fun read(): Int {
		return if (pos < count) (buf[pos++].toInt() and 0xFF) else -1
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		if (pos >= count) return -1
		val avail = count - pos
		val toCopy = minOf(len, avail)
		if (toCopy <= 0) return 0
		buf.copyInto(b, destinationOffset = off, startIndex = pos, endIndex = pos + toCopy)
		pos += toCopy
		return toCopy
	}

	override fun available(): Int = maxOf(0, count - pos)

	override fun readBytes(): ByteArray {
		val avail = available()
		if (avail <= 0) return ByteArray(0)
		val copy = buf.copyOfRange(pos, count)
		pos = count
		return copy
	}
}

public abstract class OutputStream : Closeable, Flushable {
	public abstract fun write(b: Int)

	public open fun write(b: ByteArray) {
		write(b, 0, b.size)
	}

	public open fun write(b: ByteArray, off: Int, len: Int) {
		for (i in 0 until len) {
			write(b[off + i].toInt() and 0xFF)
		}
	}

	override fun flush() {}

	override fun close() {}
}

public open class ByteArrayOutputStream(size: Int = 32) : OutputStream() {
	protected var buf: ByteArray = ByteArray(maxOf(32, size))
	protected var count: Int = 0

	private fun ensureCapacity(minCapacity: Int) {
		if (minCapacity > buf.size) {
			var newSize = buf.size * 2
			if (newSize < minCapacity) newSize = minCapacity
			buf = buf.copyOf(newSize)
		}
	}

	override fun write(b: Int) {
		ensureCapacity(count + 1)
		buf[count++] = b.toByte()
	}

	override fun write(b: ByteArray, off: Int, len: Int) {
		if (len <= 0) return
		ensureCapacity(count + len)
		b.copyInto(buf, destinationOffset = count, startIndex = off, endIndex = off + len)
		count += len
	}

	public fun toByteArray(): ByteArray = buf.copyOf(count)

	public fun size(): Int = count

	public fun reset() {
		count = 0
	}

	override fun toString(): String = buf.decodeToString(0, count)

	public fun toString(charsetName: String): String = buf.decodeToString(0, count)

	public fun toString(charset: Charset): String = buf.decodeToString(0, count)
}

public abstract class Reader : Closeable {
	public abstract fun read(cbuf: CharArray, off: Int, len: Int): Int

	public open fun read(): Int {
		val ch = CharArray(1)
		return if (read(ch, 0, 1) == -1) -1 else ch[0].code
	}

	public open fun readText(): String {
		val sb = StringBuilder()
		val buf = CharArray(4096)
		while (true) {
			val r = read(buf, 0, buf.size)
			if (r < 0) break
			sb.appendRange(buf, 0, r)
		}
		return sb.toString()
	}

	override fun close() {}
}

public open class InputStreamReader(
	private val input: InputStream,
	private val charset: Charset = Charsets.UTF_8,
) : Reader() {
	public constructor(input: InputStream, charsetName: String) : this(input, Charset.forName(charsetName))

	private val text: String by lazy {
		val bytes = input.readBytes()
		org.nekosukuriputo.nekuva.ioscompat.String(bytes, charset)
	}
	private var pos: Int = 0

	override fun read(cbuf: CharArray, off: Int, len: Int): Int {
		if (pos >= text.length) return -1
		val count = minOf(len, text.length - pos)
		for (i in 0 until count) {
			cbuf[off + i] = text[pos + i]
		}
		pos += count
		return count
	}

	override fun readText(): String {
		val remaining = text.substring(pos)
		pos = text.length
		return remaining
	}

	override fun close() {
		input.close()
	}
}

public open class BufferedReader(
	private val reader: Reader,
	bufferSize: Int = 8192,
) : Reader() {
	private val linesList: List<String> by lazy {
		reader.readText().lines()
	}
	private var lineIndex: Int = 0

	override fun read(cbuf: CharArray, off: Int, len: Int): Int = reader.read(cbuf, off, len)

	override fun readText(): String = reader.readText()

	public fun readLine(): String? {
		if (lineIndex >= linesList.size) return null
		return linesList[lineIndex++]
	}

	public fun lines(): Sequence<String> = linesList.asSequence()

	public fun <T> useLines(block: (Sequence<String>) -> T): T {
		try {
			return block(lines())
		} finally {
			close()
		}
	}

	override fun close() {
		reader.close()
	}
}

public open class StringReader(private val str: String) : Reader() {
	private var pos = 0
	override fun read(cbuf: CharArray, off: Int, len: Int): Int {
		if (pos >= str.length) return -1
		val n = minOf(len, str.length - pos)
		for (i in 0 until n) cbuf[off + i] = str[pos + i]
		pos += n
		return n
	}
	override fun readText(): String {
		val s = str.substring(pos)
		pos = str.length
		return s
	}
}

public abstract class Writer : Closeable, Flushable, Appendable {
	public abstract fun write(cbuf: CharArray, off: Int, len: Int)

	public open fun write(str: String) {
		val chars = str.toCharArray()
		write(chars, 0, chars.size)
	}

	public open fun write(c: Int) {
		write(charArrayOf(c.toChar()), 0, 1)
	}

	override fun append(value: Char): Appendable {
		write(value.code)
		return this
	}

	override fun append(value: CharSequence?): Appendable {
		write((value ?: "null").toString())
		return this
	}

	override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
		val seq = value ?: "null"
		write(seq.subSequence(startIndex, endIndex).toString())
		return this
	}

	override fun flush() {}
	override fun close() {}
}

public open class StringWriter : Writer() {
	private val sb = StringBuilder()
	override fun write(cbuf: CharArray, off: Int, len: Int) {
		sb.appendRange(cbuf, off, off + len)
	}
	override fun write(str: String) {
		sb.append(str)
	}
	override fun toString(): String = sb.toString()
}

public open class OutputStreamWriter(
	private val out: OutputStream,
	private val charset: Charset = Charsets.UTF_8,
) : Writer() {
	public constructor(out: OutputStream, charsetName: String) : this(out, Charset.forName(charsetName))

	override fun write(cbuf: CharArray, off: Int, len: Int) {
		val s = cbuf.concatToString(off, off + len)
		out.write(s.encodeToByteArray())
	}

	override fun write(str: String) {
		out.write(str.encodeToByteArray())
	}

	override fun flush() = out.flush()
	override fun close() = out.close()
}

public open class File : Comparable<File> {
	public val path: String

	public constructor(pathname: String) {
		this.path = normalizePath(pathname)
	}

	public constructor(parent: String?, child: String) {
		this.path = if (parent.isNullOrEmpty()) {
			normalizePath(child)
		} else {
			normalizePath("$parent/$child")
		}
	}

	public constructor(parent: File?, child: String) : this(parent?.path, child)

	public constructor(uri: java.net.URI) : this(uri.path ?: "")

	public val name: String
		get() = path.substringAfterLast('/')

	public val nameWithoutExtension: String
		get() = name.substringBeforeLast('.')

	public val extension: String
		get() = name.substringAfterLast('.', "")

	public fun getName(): String = name

	public fun getPath(): String = path

	public val absolutePath: String
		get() = if (path.startsWith("/")) path else normalizePath("${NSFileManager.defaultManager.currentDirectoryPath}/$path")

	public fun getAbsolutePath(): String = absolutePath

	public val canonicalPath: String
		get() = absolutePath

	public fun getCanonicalPath(): String = canonicalPath

	public val canonicalFile: File
		get() = File(canonicalPath)

	public fun getCanonicalFile(): File = canonicalFile

	public val absoluteFile: File
		get() = File(absolutePath)

	public fun getAbsoluteFile(): File = absoluteFile

	public val parent: String?
		get() {
			val idx = path.lastIndexOf('/')
			if (idx <= 0) return if (idx == 0 && path.length > 1) "/" else null
			return path.substring(0, idx)
		}

	public fun getParent(): String? = parent

	public val parentFile: File?
		get() = parent?.let { File(it) }

	public fun getParentFile(): File? = parentFile

	public fun exists(): Boolean = NSFileManager.defaultManager.fileExistsAtPath(absolutePath)

	public fun isDirectory(): Boolean {
		val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(absolutePath, null) ?: return false
		return attrs[NSFileType] == NSFileTypeDirectory
	}

	public fun isFile(): Boolean = exists() && !isDirectory()

	public fun canRead(): Boolean = NSFileManager.defaultManager.isReadableFileAtPath(absolutePath)

	public fun canWrite(): Boolean = NSFileManager.defaultManager.isWritableFileAtPath(absolutePath)

	public fun length(): Long {
		val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(absolutePath, null) ?: return 0L
		return (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
	}

	public fun lastModified(): Long {
		val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(absolutePath, null) ?: return 0L
		val date = attrs[NSFileModificationDate] as? NSDate ?: return 0L
		return (date.timeIntervalSince1970 * 1000.0).toLong()
	}

	public fun mkdir(): Boolean =
		NSFileManager.defaultManager.createDirectoryAtPath(absolutePath, withIntermediateDirectories = false, attributes = null, error = null)

	public fun mkdirs(): Boolean =
		NSFileManager.defaultManager.createDirectoryAtPath(absolutePath, withIntermediateDirectories = true, attributes = null, error = null)

	public fun createNewFile(): Boolean {
		if (exists()) return false
		parentFile?.mkdirs()
		return NSFileManager.defaultManager.createFileAtPath(absolutePath, contents = null, attributes = null)
	}

	public fun delete(): Boolean {
		if (!exists()) return false
		return NSFileManager.defaultManager.removeItemAtPath(absolutePath, null)
	}

	public fun deleteRecursively(): Boolean {
		if (!exists()) return true
		return NSFileManager.defaultManager.removeItemAtPath(absolutePath, null)
	}

	public fun renameTo(dest: File): Boolean {
		dest.parentFile?.mkdirs()
		if (rename(absolutePath, dest.absolutePath) == 0) {
			return true
		}
		if (dest.exists()) dest.delete()
		return NSFileManager.defaultManager.moveItemAtPath(absolutePath, toPath = dest.absolutePath, error = null)
	}

	public fun list(): Array<String>? {
		if (!isDirectory()) return null
		val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(absolutePath, null) ?: return null
		return contents.mapNotNull { it as? String }.toTypedArray()
	}

	public fun listFiles(): Array<File>? {
		val names = list() ?: return null
		return names.map { File(this, it) }.toTypedArray()
	}

	public fun listFiles(filter: FileFilter?): Array<File>? {
		val all = listFiles() ?: return null
		return if (filter == null) all else all.filter { filter.accept(it) }.toTypedArray()
	}

	public fun listFiles(filter: FilenameFilter?): Array<File>? {
		val names = list() ?: return null
		return names
			.filter { filter == null || filter.accept(this, it) }
			.map { File(this, it) }
			.toTypedArray()
	}

	public fun resolve(relative: String): File = File(this, relative)

	public fun resolve(relative: File): File =
		if (relative.path.startsWith("/")) relative else File(this, relative.path)

	public fun readBytes(): ByteArray {
		val fp = fopen(absolutePath, "rb")
			?: throw FileNotFoundException("File not found: $absolutePath")
		try {
			fseek(fp, 0, SEEK_END)
			val size = ftell(fp)
			fseek(fp, 0, SEEK_SET)
			if (size <= 0) return ByteArray(0)
			val bytes = ByteArray(size.toInt())
			val n = bytes.usePinned { pinned ->
				fread(pinned.addressOf(0), 1u, size.toULong(), fp).toLong()
			}
			return if (n < size) bytes.copyOf(n.toInt()) else bytes
		} finally {
			fclose(fp)
		}
	}

	public fun writeBytes(array: ByteArray) {
		parentFile?.mkdirs()
		val data = if (array.isEmpty()) {
			NSData()
		} else {
			array.usePinned { pinned ->
				NSData.dataWithBytes(pinned.addressOf(0), array.size.toULong())
			}
		}
		val ok = data.writeToFile(absolutePath, atomically = false)
		if (!ok) {
			throw IOException("Failed to write to $absolutePath")
		}
	}

	public fun readText(charset: Charset = Charsets.UTF_8): String =
		org.nekosukuriputo.nekuva.ioscompat.String(readBytes(), charset)

	public fun writeText(text: String, charset: Charset = Charsets.UTF_8) {
		writeBytes(text.encodeToByteArray())
	}

	public fun appendText(text: String, charset: Charset = Charsets.UTF_8) {
		val existing = if (exists()) readBytes() else ByteArray(0)
		val added = text.encodeToByteArray()
		writeBytes(existing + added)
	}

	public fun copyTo(target: File, overwrite: Boolean = false, bufferSize: Int = 8192): File {
		if (!exists()) throw FileNotFoundException("Source file does not exist: $this")
		if (target.exists()) {
			if (!overwrite) throw IOException("Target file already exists: $target")
			target.delete()
		}
		target.parentFile?.mkdirs()
		target.writeBytes(readBytes())
		return target
	}

	public val usableSpace: Long
		get() {
			val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(absolutePath, null) ?: return 10L * 1024L * 1024L * 1024L
			return (attrs[platform.Foundation.NSFileSystemFreeSize] as? NSNumber)?.longLongValue ?: (10L * 1024L * 1024L * 1024L)
		}

	public fun copyRecursively(target: File, overwrite: Boolean = false): Boolean {
		if (!exists()) return false
		if (isDirectory()) {
			target.mkdirs()
			listFiles()?.forEach { child ->
				child.copyRecursively(File(target, child.name), overwrite)
			}
			return true
		} else {
			copyTo(target, overwrite)
			return true
		}
	}

	public fun inputStream(): FileInputStream = FileInputStream(this)

	public fun outputStream(): FileOutputStream = FileOutputStream(this)

	public fun bufferedReader(charset: Charset = Charsets.UTF_8): BufferedReader =
		inputStream().bufferedReader(charset)

	public fun <T> useLines(charset: Charset = Charsets.UTF_8, block: (Sequence<String>) -> T): T =
		bufferedReader(charset).useLines(block)

	public fun toURI(): java.net.URI = java.net.URI("file://${absolutePath}")

	public fun toOkioPath(): Path = absolutePath.toPath()

	override fun compareTo(other: File): Int = this.path.compareTo(other.path)

	override fun equals(other: Any?): Boolean = other is File && this.path == other.path

	override fun hashCode(): Int = path.hashCode()

	override fun toString(): String = path

	public companion object {
		public const val separator: String = "/"
		public const val separatorChar: Char = '/'
		public const val pathSeparator: String = ":"
		public const val pathSeparatorChar: Char = ':'

		public fun createTempFile(prefix: String, suffix: String? = ".tmp", directory: File? = null): File {
			val dir = directory ?: File(NSTemporaryDirectory())
			dir.mkdirs()
			val ext = suffix ?: ".tmp"
			val rand = kotlin.random.Random.nextLong().toULong().toString(16)
			val file = File(dir, "$prefix$rand$ext")
			file.writeBytes(ByteArray(0))
			return file
		}

		private fun normalizePath(raw: String): String {
			if (raw.isEmpty()) return ""
			val trimmed = raw.replace("//", "/")
			return if (trimmed.length > 1 && trimmed.endsWith('/')) {
				trimmed.trimEnd('/')
			} else {
				trimmed
			}
		}
	}
}

public open class FileInputStream(private val file: File) : InputStream() {
	public constructor(path: String) : this(File(path))

	private val fp: CPointer<FILE>?
	private var closed = false

	init {
		fp = fopen(file.absolutePath, "rb")
			?: throw FileNotFoundException("File not found: ${file.absolutePath}")
	}

	override fun read(): Int {
		if (closed || fp == null) throw IOException("Stream closed")
		val buf = ByteArray(1)
		val n = buf.usePinned { pinned ->
			fread(pinned.addressOf(0), 1u, 1u, fp)
		}
		return if (n.toLong() <= 0L) -1 else (buf[0].toInt() and 0xFF)
	}

	override fun read(b: ByteArray, off: Int, len: Int): Int {
		if (closed || fp == null) throw IOException("Stream closed")
		if (len <= 0) return 0
		val bytesRead = b.usePinned { pinned ->
			fread(pinned.addressOf(off), 1u, len.toULong(), fp)
		}
		val n = bytesRead.toInt()
		return if (n <= 0) {
			if (feof(fp) != 0) -1 else throw IOException("Error reading from ${file.absolutePath}")
		} else {
			n
		}
	}

	override fun available(): Int = 0

	override fun close() {
		if (!closed) {
			closed = true
			if (fp != null) fclose(fp)
		}
	}
}

public open class FileOutputStream(
	private val file: File,
	private val append: Boolean = false,
) : OutputStream() {
	public constructor(path: String, append: Boolean = false) : this(File(path), append)

	private val fp: CPointer<FILE>?
	private var closed = false

	init {
		file.parentFile?.mkdirs()
		fp = fopen(file.absolutePath, if (append) "ab" else "wb")
			?: throw FileNotFoundException("Cannot open file for writing: ${file.absolutePath}")
	}

	override fun write(b: Int) {
		write(byteArrayOf(b.toByte()), 0, 1)
	}

	override fun write(b: ByteArray, off: Int, len: Int) {
		if (closed || fp == null) throw IOException("Stream closed")
		if (len <= 0) return
		b.usePinned { pinned ->
			fwrite(pinned.addressOf(off), 1u, len.toULong(), fp)
		}
	}

	override fun flush() {
		if (!closed && fp != null) {
			fflush(fp)
		}
	}

	override fun close() {
		if (!closed) {
			closed = true
			if (fp != null) {
				fclose(fp)
			}
		}
	}
}

public open class PrintWriter(
	private val out: Writer,
	private val autoFlush: Boolean = false,
) : Writer() {
	public constructor(out: OutputStream, autoFlush: Boolean = false) : this(OutputStreamWriter(out), autoFlush)

	override fun write(cbuf: CharArray, off: Int, len: Int) {
		out.write(cbuf, off, len)
	}

	override fun write(str: String) {
		out.write(str)
	}

	public fun print(s: String?) {
		write(s ?: "null")
	}

	public fun print(obj: Any?) {
		write(obj?.toString() ?: "null")
	}

	public fun println(s: String? = "") {
		print(s)
		write("\n")
		if (autoFlush) flush()
	}

	public fun println(obj: Any?) {
		println(obj?.toString() ?: "null")
	}

	override fun flush() = out.flush()
	override fun close() = out.close()
}

public open class ObjectOutputStream(
	private val out: OutputStream,
) : OutputStream() {
	override fun write(b: Int) = out.write(b)
	override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
	public fun writeObject(obj: Any?) {
		if (obj != null && obj !is Serializable && obj !is Number && obj !is String && obj !is Boolean && obj !is Enum<*>) {
			throw IOException("Not serializable: ${obj::class}")
		}
	}
	override fun flush() = out.flush()
	override fun close() = out.close()
}
