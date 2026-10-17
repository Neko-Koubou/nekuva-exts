@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package org.nekosukuriputo.nekuva.ioscompat

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.reflect.KClass
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSThread
import platform.Foundation.timeIntervalSince1970

public open class Charset(private val charsetName: String) {
	public fun name(): String = charsetName
	public fun displayName(): String = charsetName
	override fun toString(): String = charsetName
	override fun equals(other: Any?): Boolean =
		other is Charset && charsetName.equals(other.charsetName, ignoreCase = true)
	override fun hashCode(): Int = charsetName.lowercase().hashCode()

	public companion object {
		public fun forName(name: String): Charset = Charset(name)
		public fun defaultCharset(): Charset = Charsets.UTF_8
	}
}

public object Charsets {
	public val UTF_8: Charset = Charset("UTF-8")
	public val ISO_8859_1: Charset = Charset("ISO-8859-1")
	public val US_ASCII: Charset = Charset("US-ASCII")
	public val UTF_16: Charset = Charset("UTF-16")
	public val UTF_16BE: Charset = Charset("UTF-16BE")
	public val UTF_16LE: Charset = Charset("UTF-16LE")
}

public fun String.toByteArray(charset: Charset = Charsets.UTF_8): ByteArray {
	if (charset.name().equals("ISO-8859-1", ignoreCase = true) ||
		charset.name().equals("LATIN1", ignoreCase = true) ||
		charset.name().equals("US-ASCII", ignoreCase = true)
	) {
		return ByteArray(length) { i -> (this[i].code and 0xFF).toByte() }
	}
	return encodeToByteArray()
}

public fun ByteArray.toString(charset: Charset): String {
	if (charset.name().equals("ISO-8859-1", ignoreCase = true) ||
		charset.name().equals("LATIN1", ignoreCase = true)
	) {
		val chars = CharArray(size) { i -> (this[i].toInt() and 0xFF).toChar() }
		return chars.concatToString()
	}
	return decodeToString()
}

public fun String(bytes: ByteArray, charset: Charset = Charsets.UTF_8): String = bytes.toString(charset)

public fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset = Charsets.UTF_8): String =
	bytes.copyOfRange(offset, offset + length).toString(charset)

public fun String(chars: CharArray): String = chars.concatToString()
public fun String(chars: CharArray, offset: Int, count: Int): String = chars.concatToString(offset, offset + count)

public fun String.lowercase(locale: java.util.Locale): String = this.lowercase()
public fun String.uppercase(locale: java.util.Locale): String = this.uppercase()
public fun Char.lowercase(locale: java.util.Locale): String = this.lowercase()
public fun Char.uppercase(locale: java.util.Locale): String = this.uppercase()
public fun Char.titlecase(locale: java.util.Locale): String = this.titlecase()

public fun StringBuilder.delete(start: Int, end: Int): StringBuilder =
	deleteRange(start.coerceAtLeast(0), end.coerceAtMost(length))

public fun StringBuilder.deleteCharAt(index: Int): StringBuilder = deleteAt(index)

public fun StringBuilder.setCharAt(index: Int, value: Char) {
	this[index] = value
}

public fun String.toBigDecimalOrNull(): java.math.BigDecimal? =
	trim().toDoubleOrNull()?.let { java.math.BigDecimal(it) }

public fun String.toBigDecimal(): java.math.BigDecimal =
	java.math.BigDecimal(trim())

public fun String.toBigIntegerOrNull(radix: Int = 10): java.math.BigInteger? = try {
	java.math.BigInteger(trim(), radix)
} catch (_: Throwable) {
	null
}

public fun String.toBigInteger(radix: Int = 10): java.math.BigInteger =
	java.math.BigInteger(trim(), radix)

public fun <K, V> Map<K, V>.getOrDefault(key: K, defaultValue: V): V = this[key] ?: defaultValue

public fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? {
	val existing = get(key)
	if (existing == null) {
		put(key, value)
	}
	return existing
}

public inline fun <K, V> MutableMap<K, V>.computeIfAbsent(key: K, mappingFunction: (K) -> V): V {
	val existing = get(key)
	if (existing != null) return existing
	val created = mappingFunction(key)
	put(key, created)
	return created
}

public fun <K : Comparable<K>, V> Map<out K, V>.toSortedMap(): MutableMap<K, V> {
	val sorted = entries.sortedBy { it.key }
	val out = LinkedHashMap<K, V>(size)
	for ((k, v) in sorted) out[k] = v
	return out
}

public fun <K, V> Map<out K, V>.toSortedMap(comparator: Comparator<in K>): MutableMap<K, V> {
	val sorted = entries.sortedWith { a, b -> comparator.compare(a.key, b.key) }
	val out = LinkedHashMap<K, V>(size)
	for ((k, v) in sorted) out[k] = v
	return out
}

public class JavaStream<T>(@PublishedApi internal val items: Iterable<T>) {
	public inline fun forEach(action: (T) -> Unit) {
		for (item in items) action(item)
	}
	public inline fun <R> map(transform: (T) -> R): JavaStream<R> =
		JavaStream(items.map(transform))
	public inline fun filter(predicate: (T) -> Boolean): JavaStream<T> =
		JavaStream(items.filter(predicate))
	public fun toList(): List<T> = items.toList()
}

public fun <T> Collection<T>.stream(): JavaStream<T> = JavaStream(this)

public fun com.fleeksoft.ksoup.nodes.Element.`val`(): String = attr("value")
public fun com.fleeksoft.ksoup.nodes.Element.`val`(value: String): com.fleeksoft.ksoup.nodes.Element =
	attr("value", value)

public inline fun <T : okio.Closeable?, R> T.use(block: (T) -> R): R {
	var exception: Throwable? = null
	try {
		return block(this)
	} catch (e: Throwable) {
		exception = e
		throw e
	} finally {
		try {
			this?.close()
		} catch (closeException: Throwable) {
			exception?.addSuppressed(closeException)
		}
	}
}

public open class LruLinkedHashMap<K, V>(
	initialCapacity: Int = 16,
	loadFactor: Float = 0.75f,
	private val accessOrder: Boolean = false,
) : AbstractMutableMap<K, V>(), MutableMap<K, V> {
	private val map = LinkedHashMap<K, V>(initialCapacity.coerceAtLeast(1), loadFactor)

	protected open fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = false

	override val size: Int
		get() = map.size

	override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
		get() = map.entries

	override val keys: MutableSet<K>
		get() = map.keys

	override val values: MutableCollection<V>
		get() = map.values

	override fun get(key: K): V? {
		val v = map[key]
		if (v != null && accessOrder) {
			map.remove(key)
			map[key] = v
		}
		return v
	}

	override fun put(key: K, value: V): V? {
		if (accessOrder && map.containsKey(key)) {
			map.remove(key)
		}
		val prev = map.put(key, value)
		val eldest = map.entries.firstOrNull()
		if (eldest != null && removeEldestEntry(eldest)) {
			map.remove(eldest.key)
		}
		return prev
	}

	override fun remove(key: K): V? = map.remove(key)
	override fun clear() = map.clear()
	override fun containsKey(key: K): Boolean = map.containsKey(key)
	override fun containsValue(value: V): Boolean = map.containsValue(value)
}

public open class JavaThreadLocal<T>(private val initializer: (() -> T)? = null) {
	private var value: T? = null
	private var initialized: Boolean = false

	protected open fun initialValue(): T? = initializer?.invoke()

	@Suppress("UNCHECKED_CAST")
	public fun get(): T {
		if (!initialized) {
			value = initialValue()
			initialized = true
		}
		return value as T
	}

	public fun set(newValue: T) {
		value = newValue
		initialized = true
	}

	public fun remove() {
		value = null
		initialized = false
	}

	public companion object {
		public fun <S> withInitial(supplier: () -> S): JavaThreadLocal<S> = JavaThreadLocal(supplier)
	}
}

public object System {
	public fun currentTimeMillis(): Long =
		(NSDate().timeIntervalSince1970 * 1000.0).toLong()

	public fun nanoTime(): Long =
		(NSProcessInfo.processInfo.systemUptime * 1_000_000_000.0).toLong()

	public fun getProperty(key: String): String? = when (key) {
		"user.home" -> platform.Foundation.NSHomeDirectory()
		"java.io.tmpdir" -> platform.Foundation.NSTemporaryDirectory()
		"os.name" -> "iOS"
		"file.separator" -> "/"
		"line.separator" -> "\n"
		"path.separator" -> ":"
		else -> null
	}

	public fun getProperty(key: String, def: String): String = getProperty(key) ?: def

	public fun setProperty(key: String, value: String): String? = value

	public fun getenv(name: String): String? =
		NSProcessInfo.processInfo.environment[name]?.toString()

	public fun arraycopy(src: Any, srcPos: Int, dest: Any, destPos: Int, length: Int) {
		when {
			src is ByteArray && dest is ByteArray -> src.copyInto(dest, destPos, srcPos, srcPos + length)
			src is IntArray && dest is IntArray -> src.copyInto(dest, destPos, srcPos, srcPos + length)
			src is LongArray && dest is LongArray -> src.copyInto(dest, destPos, srcPos, srcPos + length)
			src is CharArray && dest is CharArray -> src.copyInto(dest, destPos, srcPos, srcPos + length)
			src is Array<*> && dest is Array<*> -> {
				@Suppress("UNCHECKED_CAST")
				(src as Array<Any?>).copyInto(dest as Array<Any?>, destPos, srcPos, srcPos + length)
			}
		}
	}
}

@PublishedApi
internal val globalSyncLock: NSRecursiveLock = NSRecursiveLock()

public inline fun <R> synchronized(lock: Any, block: () -> R): R {
	globalSyncLock.lock()
	try {
		return block()
	} finally {
		globalSyncLock.unlock()
	}
}

public class Class<T : Any>(
	public val kClass: KClass<T>,
	public val enumConstantsProvider: (() -> Array<T>)? = null,
) {
	public val simpleName: String
		get() = kClass.simpleName ?: "Unknown"

	public val name: String
		get() = kClass.qualifiedName ?: kClass.simpleName ?: "Unknown"

	public val canonicalName: String?
		get() = kClass.qualifiedName

	@Suppress("UNCHECKED_CAST")
	public val enumConstants: Array<T>?
		get() {
			enumConstantsProvider?.invoke()?.let { return it }
			val list = java.util.EnumSet.enumUniverseRegistry[kClass] ?: return null
			return (list as List<Any>).toTypedArray() as Array<T>
		}

	public fun desiredAssertionStatus(): Boolean = false

	public fun isInstance(obj: Any?): Boolean = kClass.isInstance(obj)

	public fun isAssignableFrom(cls: Class<*>): Boolean = kClass == cls.kClass

	@Suppress("UNCHECKED_CAST")
	public fun cast(obj: Any?): T {
		if (obj == null) throw ClassCastException("Cannot cast null to $name")
		if (kClass.isInstance(obj)) return obj as T
		return when (kClass) {
			String::class -> obj.toString() as T
			Int::class -> ((obj as? Number)?.toInt() ?: obj.toString().toInt()) as T
			Long::class -> ((obj as? Number)?.toLong() ?: obj.toString().toLong()) as T
			Double::class -> ((obj as? Number)?.toDouble() ?: obj.toString().toDouble()) as T
			Float::class -> ((obj as? Number)?.toFloat() ?: obj.toString().toFloat()) as T
			Boolean::class -> ((obj as? Boolean) ?: obj.toString().toBoolean()) as T
			else -> obj as T
		}
	}

	override fun equals(other: Any?): Boolean = other is Class<*> && kClass == other.kClass
	override fun hashCode(): Int = kClass.hashCode()
	override fun toString(): String = "class $name"
}

public val <T : Any> KClass<T>.java: Class<T>
	get() = Class(this)

public val <T : Any> KClass<T>.javaObjectType: Class<T>
	get() = Class(this)

public val <T : Any> T.javaClass: Class<T>
	get() {
		@Suppress("UNCHECKED_CAST")
		val kc = this::class as KClass<T>
		return Class(kc)
	}

public object Character {
	public const val MIN_RADIX: Int = 2
	public const val MAX_RADIX: Int = 36

	public fun isDigit(c: Char): Boolean = c.isDigit()
	public fun isWhitespace(c: Char): Boolean = c.isWhitespace()
	public fun isSpaceChar(c: Char): Boolean = c.isWhitespace()
	public fun isLetter(c: Char): Boolean = c.isLetter()
	public fun isLetterOrDigit(c: Char): Boolean = c.isLetterOrDigit()
	public fun isUpperCase(c: Char): Boolean = c.isUpperCase()
	public fun isLowerCase(c: Char): Boolean = c.isLowerCase()
	public fun toLowerCase(c: Char): Char = c.lowercaseChar()
	public fun toUpperCase(c: Char): Char = c.uppercaseChar()
	public fun isHighSurrogate(ch: Char): Boolean = ch.isHighSurrogate()
	public fun isLowSurrogate(ch: Char): Boolean = ch.isLowSurrogate()
	public fun isSurrogatePair(high: Char, low: Char): Boolean = high.isHighSurrogate() && low.isLowSurrogate()
	public fun toCodePoint(high: Char, low: Char): Int =
		((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
	public fun isSupplementaryCodePoint(codePoint: Int): Boolean = codePoint in 0x10000..0x10FFFF
	public fun digit(ch: Char, radix: Int): Int = ch.digitToIntOrNull(radix) ?: -1
	public fun forDigit(digit: Int, radix: Int): Char =
		if (digit in 0 until radix && radix in MIN_RADIX..MAX_RADIX) {
			if (digit < 10) ('0'.code + digit).toChar() else ('a'.code + digit - 10).toChar()
		} else '\u0000'

	public fun toChars(codePoint: Int): CharArray {
		return if (codePoint in 0..0xFFFF) {
			charArrayOf(codePoint.toChar())
		} else {
			val cp = codePoint - 0x10000
			val high = ((cp ushr 10) + 0xD800).toChar()
			val low = ((cp and 0x3FF) + 0xDC00).toChar()
			charArrayOf(high, low)
		}
	}
}

public object Integer {
	public const val MAX_VALUE: Int = Int.MAX_VALUE
	public const val MIN_VALUE: Int = Int.MIN_VALUE

	public fun parseInt(s: String, radix: Int = 10): Int = s.trim().toInt(radix)
	public fun toHexString(i: Int): String = i.toUInt().toString(16)
	public fun toBinaryString(i: Int): String = i.toUInt().toString(2)
	public fun toOctalString(i: Int): String = i.toUInt().toString(8)
	public fun valueOf(s: String, radix: Int = 10): Int = s.trim().toInt(radix)
	public fun valueOf(i: Int): Int = i
	public fun reverseBytes(i: Int): Int =
		((i ushr 24) and 0xFF) or
			((i ushr 8) and 0xFF00) or
			((i shl 8) and 0xFF0000) or
			(i shl 24)
}

public object Math {
	public const val PI: Double = kotlin.math.PI
	public const val E: Double = kotlin.math.E

	public fun abs(a: Int): Int = kotlin.math.abs(a)
	public fun abs(a: Long): Long = kotlin.math.abs(a)
	public fun abs(a: Float): Float = kotlin.math.abs(a)
	public fun abs(a: Double): Double = kotlin.math.abs(a)
	public fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
	public fun max(a: Long, b: Long): Long = kotlin.math.max(a, b)
	public fun max(a: Float, b: Float): Float = kotlin.math.max(a, b)
	public fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
	public fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
	public fun min(a: Long, b: Long): Long = kotlin.math.min(a, b)
	public fun min(a: Float, b: Float): Float = kotlin.math.min(a, b)
	public fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
	public fun ceil(a: Double): Double = kotlin.math.ceil(a)
	public fun floor(a: Double): Double = kotlin.math.floor(a)
	public fun round(a: Double): Long = a.roundToLong()
	public fun round(a: Float): Int = kotlin.math.round(a).toInt()
	public fun sqrt(a: Double): Double = kotlin.math.sqrt(a)
	public fun pow(a: Double, b: Double): Double = a.pow(b)
	public fun sin(a: Double): Double = kotlin.math.sin(a)
	public fun cos(a: Double): Double = kotlin.math.cos(a)
	public fun tan(a: Double): Double = kotlin.math.tan(a)
	public fun asin(a: Double): Double = kotlin.math.asin(a)
	public fun acos(a: Double): Double = kotlin.math.acos(a)
	public fun atan(a: Double): Double = kotlin.math.atan(a)
	public fun atan2(y: Double, x: Double): Double = kotlin.math.atan2(y, x)
	public fun exp(a: Double): Double = kotlin.math.exp(a)
	public fun log(a: Double): Double = kotlin.math.ln(a)
	public fun log10(a: Double): Double = kotlin.math.log10(a)
	public fun scalb(d: Double, scaleFactor: Int): Double = d * 2.0.pow(scaleFactor)
	public fun scalb(f: Float, scaleFactor: Int): Float = (f * 2.0.pow(scaleFactor)).toFloat()
	public fun random(): Double = kotlin.random.Random.Default.nextDouble()
	public fun floorMod(x: Int, y: Int): Int = x.mod(y)
	public fun floorMod(x: Long, y: Long): Long = x.mod(y)
	public fun floorDiv(x: Int, y: Int): Int = x.floorDiv(y)
	public fun floorDiv(x: Long, y: Long): Long = x.floorDiv(y)
}

public class Thread {
	public val name: String = "ios-thread"
	public val id: Long = 1L
	public fun isInterrupted(): Boolean = false
	public fun interrupt() {}

	public companion object {
		private val mainInstance = Thread()
		public fun currentThread(): Thread = mainInstance
		public fun sleep(millis: Long) {
			if (millis > 0) {
				NSThread.sleepForTimeInterval(millis.toDouble() / 1000.0)
			}
		}
		public fun interrupted(): Boolean = false
	}
}

public open class InterruptedException(message: String? = null) : Exception(message)

public open class ReflectiveOperationException : Exception {
	public constructor() : super()
	public constructor(message: String?) : super(message)
	public constructor(message: String?, cause: Throwable?) : super(message, cause)
	public constructor(cause: Throwable?) : super(cause)
}

public fun String.format(vararg args: Any?): String = formatStringImpl(this, args)
public fun String.Companion.format(fmt: String, vararg args: Any?): String = formatStringImpl(fmt, args)
public fun String.Companion.format(locale: Any?, fmt: String, vararg args: Any?): String = formatStringImpl(fmt, args)

internal fun formatStringImpl(fmt: String, args: Array<out Any?>): String {
	if (args.isEmpty()) return fmt
	val sb = StringBuilder(fmt.length + 16)
	var i = 0
	var argIdx = 0
	while (i < fmt.length) {
		val c = fmt[i]
		if (c != '%' || i + 1 >= fmt.length) {
			sb.append(c)
			i++
			continue
		}
		if (fmt[i + 1] == '%') {
			sb.append('%')
			i += 2
			continue
		}
		if (fmt[i + 1] == 'n') {
			sb.append('\n')
			i += 2
			continue
		}
		var j = i + 1
		var explicitIndex: Int? = null
		val dollarPos = fmt.indexOf('$', j)
		if (dollarPos != -1 && dollarPos - j in 1..2 && fmt.substring(j, dollarPos).all { it.isDigit() }) {
			explicitIndex = fmt.substring(j, dollarPos).toInt() - 1
			j = dollarPos + 1
		}
		var zeroPad = false
		var leftAlign = false
		while (j < fmt.length && (fmt[j] == '0' || fmt[j] == '-' || fmt[j] == '+' || fmt[j] == ' ' || fmt[j] == ',')) {
			if (fmt[j] == '0') zeroPad = true
			if (fmt[j] == '-') leftAlign = true
			j++
		}
		var width = 0
		while (j < fmt.length && fmt[j].isDigit()) {
			width = width * 10 + (fmt[j] - '0')
			j++
		}
		var precision = -1
		if (j < fmt.length && fmt[j] == '.') {
			j++
			precision = 0
			while (j < fmt.length && fmt[j].isDigit()) {
				precision = precision * 10 + (fmt[j] - '0')
				j++
			}
		}
		if (j >= fmt.length) {
			sb.append(fmt.substring(i))
			break
		}
		val spec = fmt[j]
		val currentArgIdx = explicitIndex ?: argIdx++
		val arg = args.getOrNull(currentArgIdx)
		val formatted = when (spec) {
			'd', 'i', 'u' -> (arg as? Number)?.toLong()?.toString() ?: arg?.toString() ?: "0"
			'x' -> ((arg as? Number)?.toLong() ?: 0L).toULong().toString(16)
			'X' -> ((arg as? Number)?.toLong() ?: 0L).toULong().toString(16).uppercase()
			'o' -> ((arg as? Number)?.toLong() ?: 0L).toULong().toString(8)
			'f', 'e', 'g' -> {
				val d = (arg as? Number)?.toDouble() ?: 0.0
				if (precision >= 0) {
					val factor = 10.0.pow(precision)
					val rounded = kotlin.math.round(d * factor) / factor
					val parts = rounded.toString().split('.')
					val intPart = parts[0]
					val fracPart = (parts.getOrNull(1) ?: "").padEnd(precision, '0').take(precision)
					if (precision == 0) intPart else "$intPart.$fracPart"
				} else {
					d.toString()
				}
			}
			's', 'S' -> {
				val s = arg?.toString() ?: "null"
				val s2 = if (precision >= 0 && s.length > precision) s.take(precision) else s
				if (spec == 'S') s2.uppercase() else s2
			}
			'c', 'C' -> {
				val ch = when (arg) {
					is Char -> arg.toString()
					is Number -> arg.toInt().toChar().toString()
					else -> arg?.toString()?.take(1) ?: ""
				}
				if (spec == 'C') ch.uppercase() else ch
			}
			'b', 'B' -> {
				val b = (arg as? Boolean ?: (arg != null)).toString()
				if (spec == 'B') b.uppercase() else b
			}
			else -> arg?.toString() ?: ""
		}
		val padChar = if (zeroPad && !leftAlign) '0' else ' '
		val padded = if (width > formatted.length) {
			if (leftAlign) formatted.padEnd(width, ' ')
			else formatted.padStart(width, padChar)
		} else {
			formatted
		}
		sb.append(padded)
		i = j + 1
	}
	return sb.toString()
}

public fun ByteArray.inputStream(): java.io.ByteArrayInputStream = java.io.ByteArrayInputStream(this)
public fun ByteArray.inputStream(offset: Int, length: Int): java.io.ByteArrayInputStream =
	java.io.ByteArrayInputStream(this, offset, length)

public inline fun <T> org.json.JSONArray?.mapJSON(block: (org.json.JSONObject) -> T): List<T> {
	if (this == null) return emptyList()
	val len = length()
	val result = ArrayList<T>(len)
	for (i in 0 until len) {
		val jo = getJSONObject(i)
		result.add(block(jo))
	}
	return result
}

public inline fun <T> org.json.JSONArray?.mapJSONToSet(mapper: (org.json.JSONObject) -> T): Set<T> {
	if (this == null) return emptySet()
	val len = length()
	val result = LinkedHashSet<T>(len)
	for (i in 0 until len) {
		val jo = getJSONObject(i)
		result.add(mapper(jo))
	}
	return result
}

public fun Throwable.printStackTrace(pw: java.io.PrintWriter) {
	pw.println(this.stackTraceToString())
}
