package java.util

import java.io.bufferedReader
import org.nekosukuriputo.nekuva.ioscompat.Class
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitNanosecond
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSCalendarUnitWeekOfMonth
import platform.Foundation.NSCalendarUnitWeekOfYear
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUUID
import platform.Foundation.availableLocaleIdentifiers
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone
import platform.Foundation.localizedStringForCountryCode
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.localizedStringForLocaleIdentifier
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithAbbreviation
import platform.Foundation.timeZoneWithName

public typealias ArrayList<E> = kotlin.collections.ArrayList<E>
public typealias HashMap<K, V> = kotlin.collections.HashMap<K, V>
public typealias LinkedHashMap<K, V> = kotlin.collections.LinkedHashMap<K, V>
public typealias HashSet<E> = kotlin.collections.HashSet<E>
public typealias LinkedHashSet<E> = kotlin.collections.LinkedHashSet<E>
public typealias RandomAccess = kotlin.collections.RandomAccess
public typealias Comparator<T> = kotlin.Comparator<T>
public typealias NoSuchElementException = kotlin.NoSuchElementException
public typealias ConcurrentModificationException = kotlin.ConcurrentModificationException

public class Locale(
	private val lang: String,
	private val rgn: String = "",
	private val vrnt: String = "",
) : java.io.Serializable {
	public val language: String
		get() = lang.lowercase()

	public val country: String
		get() = rgn.uppercase()

	public val variant: String
		get() = vrnt

	public fun getLanguage(): String = language
	public fun getCountry(): String = country
	public fun getVariant(): String = variant

	public fun toLanguageTag(): String {
		if (lang.isEmpty()) return "und"
		return if (rgn.isNotEmpty()) "${lang.lowercase()}-${rgn.uppercase()}" else lang.lowercase()
	}

	public val displayLanguage: String
		get() = getDisplayLanguage(getDefault())

	public fun getDisplayLanguage(): String = displayLanguage

	public fun getDisplayLanguage(inLocale: Locale): String {
		if (lang.isEmpty()) return ""
		val nsLocale = NSLocale(inLocale.toLanguageTag())
		return nsLocale.localizedStringForLanguageCode(lang) ?: lang
	}

	public val displayCountry: String
		get() = getDisplayCountry(getDefault())

	public fun getDisplayCountry(): String = displayCountry

	public fun getDisplayCountry(inLocale: Locale): String {
		if (rgn.isEmpty()) return ""
		val nsLocale = NSLocale(inLocale.toLanguageTag())
		return nsLocale.localizedStringForCountryCode(rgn) ?: rgn
	}

	public val displayName: String
		get() = getDisplayName(getDefault())

	public fun getDisplayName(): String = displayName

	public fun getDisplayName(inLocale: Locale): String {
		if (lang.isEmpty() && rgn.isEmpty()) return ""
		val nsLocale = NSLocale(inLocale.toLanguageTag())
		return nsLocale.localizedStringForLocaleIdentifier(toLanguageTag()) ?: toLanguageTag()
	}

	override fun toString(): String {
		if (lang.isEmpty() && rgn.isEmpty()) return ""
		return if (rgn.isNotEmpty()) "${lang.lowercase()}_${rgn.uppercase()}" else lang.lowercase()
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is Locale) return false
		return language == other.language && country == other.country && variant == other.variant
	}

	override fun hashCode(): Int = (language.hashCode() * 31 + country.hashCode()) * 31 + variant.hashCode()

	public class Builder {
		private var lang = ""
		private var region = ""
		private var variant = ""
		public fun setLanguage(language: String?): Builder {
			lang = language ?: ""
			return this
		}
		public fun setRegion(region: String?): Builder {
			this.region = region ?: ""
			return this
		}
		public fun setVariant(variant: String?): Builder {
			this.variant = variant ?: ""
			return this
		}
		public fun setLanguageTag(languageTag: String?): Builder {
			val loc = forLanguageTag(languageTag ?: "")
			lang = loc.language
			region = loc.country
			variant = loc.variant
			return this
		}
		public fun build(): Locale = Locale(lang, region, variant)
	}

	public companion object {
		public val ROOT: Locale = Locale("", "", "")
		public val ENGLISH: Locale = Locale("en", "")
		public val FRENCH: Locale = Locale("fr", "")
		public val GERMAN: Locale = Locale("de", "")
		public val ITALIAN: Locale = Locale("it", "")
		public val JAPANESE: Locale = Locale("ja", "")
		public val KOREAN: Locale = Locale("ko", "")
		public val CHINESE: Locale = Locale("zh", "")
		public val SIMPLIFIED_CHINESE: Locale = Locale("zh", "CN")
		public val TRADITIONAL_CHINESE: Locale = Locale("zh", "TW")
		public val FRANCE: Locale = Locale("fr", "FR")
		public val GERMANY: Locale = Locale("de", "DE")
		public val ITALY: Locale = Locale("it", "IT")
		public val JAPAN: Locale = Locale("ja", "JP")
		public val KOREA: Locale = Locale("ko", "KR")
		public val CHINA: Locale = SIMPLIFIED_CHINESE
		public val PRC: Locale = SIMPLIFIED_CHINESE
		public val TAIWAN: Locale = TRADITIONAL_CHINESE
		public val UK: Locale = Locale("en", "GB")
		public val US: Locale = Locale("en", "US")
		public val CANADA: Locale = Locale("en", "CA")
		public val CANADA_FRENCH: Locale = Locale("fr", "CA")

		private var defaultLocale: Locale? = null

		public fun getDefault(): Locale {
			defaultLocale?.let { return it }
			val current = NSLocale.currentLocale
			val langCode = (current.objectForKey(NSLocaleLanguageCode) as? String) ?: "en"
			val countryCode = (current.objectForKey(NSLocaleCountryCode) as? String) ?: "US"
			return Locale(langCode, countryCode)
		}

		public fun setDefault(newLocale: Locale) {
			defaultLocale = newLocale
		}

		public fun forLanguageTag(languageTag: String): Locale {
			val cleaned = languageTag.replace('_', '-').trim()
			if (cleaned.isEmpty() || cleaned.equals("und", ignoreCase = true)) return ROOT
			val parts = cleaned.split('-')
			val lang = parts.getOrNull(0) ?: ""
			val region = parts.getOrNull(1)?.takeIf { it.length == 2 || it.length == 3 } ?: ""
			return Locale(lang, region)
		}

		public fun getAvailableLocales(): Array<Locale> {
			return NSLocale.availableLocaleIdentifiers.mapNotNull { id ->
				(id as? String)?.let { forLanguageTag(it) }
			}.toTypedArray()
		}
	}
}

public open class Date(private var millis: Long = org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis()) :
	Comparable<Date>, java.io.Serializable {

	public var time: Long
		get() = millis
		set(value) {
			millis = value
		}

	public fun getTime(): Long = millis

	public fun setTime(time: Long) {
		millis = time
	}

	public fun before(whenDate: Date): Boolean = millis < whenDate.millis
	public fun after(whenDate: Date): Boolean = millis > whenDate.millis

	override fun compareTo(other: Date): Int = millis.compareTo(other.millis)
	override fun equals(other: Any?): Boolean = other is Date && millis == other.millis
	override fun hashCode(): Int = millis.hashCode()
	override fun toString(): String = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0).toString()
}

public open class TimeZone(
	internal val nsTimeZone: NSTimeZone = NSTimeZone.defaultTimeZone,
) : java.io.Serializable {
	public val id: String
		get() = nsTimeZone.name

	public fun getID(): String = id

	public val rawOffset: Int
		get() = (nsTimeZone.secondsFromGMTForDate(NSDate()) * 1000L).toInt()

	public fun getRawOffset(): Int = rawOffset

	public fun getOffset(date: Long): Int {
		val nsDate = NSDate.dateWithTimeIntervalSince1970(date / 1000.0)
		return (nsTimeZone.secondsFromGMTForDate(nsDate) * 1000L).toInt()
	}

	public companion object {
		public fun getDefault(): TimeZone = TimeZone(NSTimeZone.defaultTimeZone)

		public fun getTimeZone(id: String): TimeZone {
			val tz = NSTimeZone.timeZoneWithName(id)
				?: NSTimeZone.timeZoneWithAbbreviation(id)
				?: NSTimeZone.timeZoneWithName("GMT")
				?: NSTimeZone.defaultTimeZone
			return TimeZone(tz)
		}
	}
}

public open class Calendar(
	private var timeZoneVal: TimeZone = TimeZone.getDefault(),
	private val locale: Locale = Locale.getDefault(),
) : java.io.Serializable {
	private var timeMillis: Long = org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis()
	private val fields: IntArray = IntArray(FIELD_COUNT)

	init {
		computeFields()
	}

	private fun nsCalendar(): NSCalendar {
		val cal = NSCalendar(NSCalendarIdentifierGregorian)
		cal.timeZone = timeZoneVal.nsTimeZone
		return cal
	}

	private fun computeFields() {
		val cal = nsCalendar()
		val date = NSDate.dateWithTimeIntervalSince1970(timeMillis / 1000.0)
		val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
			NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or
			NSCalendarUnitWeekday or NSCalendarUnitWeekOfYear or NSCalendarUnitWeekOfMonth
		val comps = cal.components(units, fromDate = date)
		fields[YEAR] = comps.year.toInt()
		fields[MONTH] = (comps.month.toInt() - 1).coerceAtLeast(0)
		fields[DAY_OF_MONTH] = comps.day.toInt()
		fields[HOUR_OF_DAY] = comps.hour.toInt()
		fields[HOUR] = comps.hour.toInt() % 12
		fields[AM_PM] = if (comps.hour.toInt() >= 12) PM else AM
		fields[MINUTE] = comps.minute.toInt()
		fields[SECOND] = comps.second.toInt()
		fields[MILLISECOND] = ((timeMillis % 1000L + 1000L) % 1000L).toInt()
		fields[DAY_OF_WEEK] = comps.weekday.toInt()
		fields[WEEK_OF_YEAR] = comps.weekOfYear.toInt()
		fields[WEEK_OF_MONTH] = comps.weekOfMonth.toInt()
		fields[DAY_OF_YEAR] = cal.ordinalityOfUnit(NSCalendarUnitDay, inUnit = NSCalendarUnitYear, forDate = date).toInt()
	}

	private fun recomputeTime() {
		val cal = nsCalendar()
		val comps = NSDateComponents()
		comps.year = fields[YEAR].toLong()
		comps.month = (fields[MONTH] + 1).toLong()
		comps.day = fields[DAY_OF_MONTH].toLong()
		comps.hour = fields[HOUR_OF_DAY].toLong()
		comps.minute = fields[MINUTE].toLong()
		comps.second = fields[SECOND].toLong()
		val nsDate = cal.dateFromComponents(comps)
		if (nsDate != null) {
			timeMillis = (nsDate.timeIntervalSince1970 * 1000.0).toLong() + fields[MILLISECOND]
		}
		computeFields()
	}

	public var time: Date
		get() = Date(timeMillis)
		set(value) {
			timeMillis = value.time
			computeFields()
		}

	public fun getTime(): Date = Date(timeMillis)

	public fun setTime(date: Date) {
		timeMillis = date.time
		computeFields()
	}

	public var timeInMillis: Long
		get() = timeMillis
		set(value) {
			timeMillis = value
			computeFields()
		}

	public fun getTimeInMillis(): Long = timeMillis

	public fun setTimeInMillis(millis: Long) {
		timeMillis = millis
		computeFields()
	}

	public var timeZone: TimeZone
		get() = timeZoneVal
		set(value) {
			timeZoneVal = value
			computeFields()
		}

	public operator fun get(field: Int): Int = fields.getOrElse(field) { 0 }

	public operator fun set(field: Int, value: Int) {
		if (field in fields.indices) {
			fields[field] = value
			if (field == HOUR) {
				fields[HOUR_OF_DAY] = if (fields[AM_PM] == PM) (value % 12) + 12 else value % 12
			}
			recomputeTime()
		}
	}

	public fun set(year: Int, month: Int, date: Int) {
		fields[YEAR] = year
		fields[MONTH] = month
		fields[DAY_OF_MONTH] = date
		recomputeTime()
	}

	public fun set(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int) {
		fields[YEAR] = year
		fields[MONTH] = month
		fields[DAY_OF_MONTH] = date
		fields[HOUR_OF_DAY] = hourOfDay
		fields[MINUTE] = minute
		recomputeTime()
	}

	public fun set(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int, second: Int) {
		fields[YEAR] = year
		fields[MONTH] = month
		fields[DAY_OF_MONTH] = date
		fields[HOUR_OF_DAY] = hourOfDay
		fields[MINUTE] = minute
		fields[SECOND] = second
		recomputeTime()
	}

	public fun add(field: Int, amount: Int) {
		when (field) {
			MILLISECOND -> timeMillis += amount
			SECOND -> timeMillis += amount * 1000L
			MINUTE -> timeMillis += amount * 60_000L
			HOUR, HOUR_OF_DAY -> timeMillis += amount * 3_600_000L
			DAY_OF_MONTH, DAY_OF_YEAR, DAY_OF_WEEK -> timeMillis += amount * 86_400_000L
			WEEK_OF_YEAR, WEEK_OF_MONTH -> timeMillis += amount * 7L * 86_400_000L
			MONTH -> {
				val cal = nsCalendar()
				val comps = NSDateComponents()
				comps.month = amount.toLong()
				val current = NSDate.dateWithTimeIntervalSince1970(timeMillis / 1000.0)
				val next = cal.dateByAddingComponents(comps, toDate = current, options = 0u)
				if (next != null) timeMillis = (next.timeIntervalSince1970 * 1000.0).toLong()
			}
			YEAR -> {
				val cal = nsCalendar()
				val comps = NSDateComponents()
				comps.year = amount.toLong()
				val current = NSDate.dateWithTimeIntervalSince1970(timeMillis / 1000.0)
				val next = cal.dateByAddingComponents(comps, toDate = current, options = 0u)
				if (next != null) timeMillis = (next.timeIntervalSince1970 * 1000.0).toLong()
			}
		}
		computeFields()
	}

	public companion object {
		public const val ERA: Int = 0
		public const val YEAR: Int = 1
		public const val MONTH: Int = 2
		public const val WEEK_OF_YEAR: Int = 3
		public const val WEEK_OF_MONTH: Int = 4
		public const val DATE: Int = 5
		public const val DAY_OF_MONTH: Int = 5
		public const val DAY_OF_YEAR: Int = 6
		public const val DAY_OF_WEEK: Int = 7
		public const val DAY_OF_WEEK_IN_MONTH: Int = 8
		public const val AM_PM: Int = 9
		public const val HOUR: Int = 10
		public const val HOUR_OF_DAY: Int = 11
		public const val MINUTE: Int = 12
		public const val SECOND: Int = 13
		public const val MILLISECOND: Int = 14
		public const val ZONE_OFFSET: Int = 15
		public const val DST_OFFSET: Int = 16
		public const val FIELD_COUNT: Int = 17

		public const val SUNDAY: Int = 1
		public const val MONDAY: Int = 2
		public const val TUESDAY: Int = 3
		public const val WEDNESDAY: Int = 4
		public const val THURSDAY: Int = 5
		public const val FRIDAY: Int = 6
		public const val SATURDAY: Int = 7

		public const val JANUARY: Int = 0
		public const val FEBRUARY: Int = 1
		public const val MARCH: Int = 2
		public const val APRIL: Int = 3
		public const val MAY: Int = 4
		public const val JUNE: Int = 5
		public const val JULY: Int = 6
		public const val AUGUST: Int = 7
		public const val SEPTEMBER: Int = 8
		public const val OCTOBER: Int = 9
		public const val NOVEMBER: Int = 10
		public const val DECEMBER: Int = 11

		public const val AM: Int = 0
		public const val PM: Int = 1

		public fun getInstance(): Calendar = Calendar()
		public fun getInstance(zone: TimeZone): Calendar = Calendar(zone)
		public fun getInstance(locale: Locale): Calendar = Calendar(TimeZone.getDefault(), locale)
		public fun getInstance(zone: TimeZone, locale: Locale): Calendar = Calendar(zone, locale)
	}
}

public class GregorianCalendar : Calendar {
	public constructor() : super()
	public constructor(zone: TimeZone) : super(zone)
	public constructor(locale: Locale) : super(TimeZone.getDefault(), locale)
	public constructor(zone: TimeZone, locale: Locale) : super(zone, locale)
	public constructor(year: Int, month: Int, dayOfMonth: Int) : super() {
		set(year, month, dayOfMonth, 0, 0, 0)
	}
	public constructor(year: Int, month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int) : super() {
		set(year, month, dayOfMonth, hourOfDay, minute, 0)
	}
	public constructor(year: Int, month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int, second: Int) : super() {
		set(year, month, dayOfMonth, hourOfDay, minute, second)
	}
}

public class EnumSet<E : Enum<E>> @PublishedApi internal constructor(
	internal val allUniverseValues: List<E>,
	private val backing: MutableSet<E> = LinkedHashSet(),
) : AbstractMutableSet<E>(), MutableSet<E>, java.io.Serializable {

	override val size: Int
		get() = backing.size

	override fun add(element: E): Boolean = backing.add(element)
	override fun remove(element: E): Boolean = backing.remove(element)
	override fun contains(element: E): Boolean = backing.contains(element)
	override fun iterator(): MutableIterator<E> = backing.iterator()
	override fun clear() = backing.clear()

	public fun clone(): EnumSet<E> = EnumSet(allUniverseValues, LinkedHashSet(backing))

	public companion object {
		@PublishedApi
		internal val enumUniverseRegistry: MutableMap<kotlin.reflect.KClass<*>, List<Enum<*>>> = HashMap()

		public fun <E : Enum<E>> registerUniverse(kClass: kotlin.reflect.KClass<E>, entries: List<E>) {
			enumUniverseRegistry[kClass] = entries
		}

		@Suppress("UNCHECKED_CAST")
		public fun <E : Enum<E>> noneOf(elementType: Class<E>): EnumSet<E> {
			val universe = (enumUniverseRegistry[elementType.kClass] as? List<E>) ?: emptyList()
			return EnumSet(universe)
		}

		public inline fun <reified E : Enum<E>> noneOf(): EnumSet<E> {
			val entries = enumValues<E>().toList()
			registerUniverse(E::class, entries)
			return EnumSet(entries)
		}

		@Suppress("UNCHECKED_CAST")
		public fun <E : Enum<E>> allOf(elementType: Class<E>): EnumSet<E> {
			val universe = (enumUniverseRegistry[elementType.kClass] as? List<E>) ?: emptyList()
			return EnumSet(universe, LinkedHashSet(universe))
		}

		public inline fun <reified E : Enum<E>> allOf(): EnumSet<E> {
			val entries = enumValues<E>().toList()
			registerUniverse(E::class, entries)
			return EnumSet(entries, LinkedHashSet(entries))
		}

		public fun <E : Enum<E>> of(e: E): EnumSet<E> =
			EnumSet(emptyList(), linkedSetOf(e))

		public fun <E : Enum<E>> of(e1: E, e2: E): EnumSet<E> =
			EnumSet(emptyList(), linkedSetOf(e1, e2))

		public fun <E : Enum<E>> of(e1: E, e2: E, e3: E): EnumSet<E> =
			EnumSet(emptyList(), linkedSetOf(e1, e2, e3))

		public fun <E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E): EnumSet<E> =
			EnumSet(emptyList(), linkedSetOf(e1, e2, e3, e4))

		public fun <E : Enum<E>> of(e1: E, e2: E, e3: E, e4: E, e5: E): EnumSet<E> =
			EnumSet(emptyList(), linkedSetOf(e1, e2, e3, e4, e5))

		public fun <E : Enum<E>> of(first: E, vararg rest: E): EnumSet<E> {
			val set = LinkedHashSet<E>(rest.size + 1)
			set.add(first)
			set.addAll(rest)
			return EnumSet(emptyList(), set)
		}

		public fun <E : Enum<E>> copyOf(s: EnumSet<E>): EnumSet<E> = s.clone()

		public fun <E : Enum<E>> copyOf(c: Collection<E>): EnumSet<E> =
			if (c is EnumSet<E>) c.clone() else EnumSet(emptyList(), LinkedHashSet(c))

		public fun <E : Enum<E>> complementOf(s: EnumSet<E>): EnumSet<E> =
			EnumSet(s.allUniverseValues, LinkedHashSet(s.allUniverseValues - s))
	}
}

public class EnumMap<K : Enum<K>, V>(
	private val backing: MutableMap<K, V> = LinkedHashMap(),
) : AbstractMutableMap<K, V>(), MutableMap<K, V>, java.io.Serializable {
	public constructor(keyType: Class<K>) : this(LinkedHashMap())
	public constructor(m: Map<K, out V>) : this(LinkedHashMap(m))

	override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
		get() = backing.entries

	override fun put(key: K, value: V): V? = backing.put(key, value)
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
public object Base64 {
	public class Encoder internal constructor(
		private val urlSafe: Boolean,
		private val withPadding: Boolean = true,
	) {
		private fun base64Instance(): kotlin.io.encoding.Base64 {
			val base = if (urlSafe) kotlin.io.encoding.Base64.UrlSafe else kotlin.io.encoding.Base64.Default
			return if (withPadding) {
				base
			} else {
				base.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
			}
		}

		public fun encode(src: ByteArray): ByteArray = encodeToString(src).encodeToByteArray()

		public fun encodeToString(src: ByteArray): String = base64Instance().encode(src)

		public fun withoutPadding(): Encoder = Encoder(urlSafe, withPadding = false)
	}

	public class Decoder internal constructor(private val urlSafe: Boolean) {
		public fun decode(src: ByteArray): ByteArray = decode(src.decodeToString())

		public fun decode(src: String): ByteArray {
			val cleaned = src.trim().replace("\n", "").replace("\r", "").replace(" ", "")
			if (cleaned.isEmpty()) return ByteArray(0)
			val padded = if (cleaned.length % 4 == 0) {
				cleaned
			} else {
				cleaned.padEnd(cleaned.length + (4 - cleaned.length % 4), '=')
			}
			val hasUrlChars = padded.contains('-') || padded.contains('_')
			val codec = if (urlSafe || hasUrlChars) {
				kotlin.io.encoding.Base64.UrlSafe
			} else {
				kotlin.io.encoding.Base64.Mime
			}
			return codec.decode(padded)
		}
	}

	private val stdEncoder = Encoder(urlSafe = false)
	private val urlEncoder = Encoder(urlSafe = true)
	private val stdDecoder = Decoder(urlSafe = false)
	private val urlDecoder = Decoder(urlSafe = true)

	public fun getEncoder(): Encoder = stdEncoder
	public fun getUrlEncoder(): Encoder = urlEncoder
	public fun getMimeEncoder(): Encoder = stdEncoder
	public fun getDecoder(): Decoder = stdDecoder
	public fun getUrlDecoder(): Decoder = urlDecoder
	public fun getMimeDecoder(): Decoder = stdDecoder
}

public class UUID(
	public val mostSignificantBits: Long,
	public val leastSignificantBits: Long,
) : Comparable<UUID>, java.io.Serializable {

	override fun toString(): String {
		fun digits(v: Long, digits: Int): String {
			val hi = 1L shl (digits * 4)
			return (hi or (v and (hi - 1))).toULong().toString(16).substring(1)
		}
		return "${digits(mostSignificantBits shr 32, 8)}-" +
			"${digits(mostSignificantBits shr 16, 4)}-" +
			"${digits(mostSignificantBits, 4)}-" +
			"${digits(leastSignificantBits shr 48, 4)}-" +
			digits(leastSignificantBits, 12)
	}

	override fun compareTo(other: UUID): Int {
		val cmp = mostSignificantBits.compareTo(other.mostSignificantBits)
		return if (cmp != 0) cmp else leastSignificantBits.compareTo(other.leastSignificantBits)
	}

	override fun equals(other: Any?): Boolean =
		other is UUID && mostSignificantBits == other.mostSignificantBits && leastSignificantBits == other.leastSignificantBits

	override fun hashCode(): Int {
		val hilo = mostSignificantBits xor leastSignificantBits
		return (hilo shr 32).toInt() xor hilo.toInt()
	}

	public companion object {
		public fun randomUUID(): UUID = fromString(NSUUID().UUIDString.lowercase())

		public fun fromString(name: String): UUID {
			val components = name.trim().split('-')
			if (components.size != 5) throw IllegalArgumentException("Invalid UUID string: $name")
			var msb = components[0].toULong(16).toLong()
			msb = (msb shl 16) or components[1].toULong(16).toLong()
			msb = (msb shl 16) or components[2].toULong(16).toLong()
			var lsb = components[3].toULong(16).toLong()
			lsb = (lsb shl 48) or components[4].toULong(16).toLong()
			return UUID(msb, lsb)
		}

		public fun nameUUIDFromBytes(name: ByteArray): UUID {
			val md5 = java.security.MessageDigest.getInstance("MD5").digest(name)
			md5[6] = ((md5[6].toInt() and 0x0f) or 0x30).toByte()
			md5[8] = ((md5[8].toInt() and 0x3f) or 0x80).toByte()
			var msb = 0L
			var lsb = 0L
			for (i in 0..7) msb = (msb shl 8) or (md5[i].toLong() and 0xffL)
			for (i in 8..15) lsb = (lsb shl 8) or (md5[i].toLong() and 0xffL)
			return UUID(msb, lsb)
		}
	}
}

public interface Queue<E> : MutableCollection<E> {
	public fun offer(e: E): Boolean
	public fun poll(): E?
	public fun element(): E
	public fun peek(): E?
}

public interface Deque<E> : Queue<E> {
	public fun addFirst(e: E)
	public fun addLast(e: E)
	public fun pollFirst(): E?
	public fun pollLast(): E?
	public fun peekFirst(): E?
	public fun peekLast(): E?
	public fun removeFirst(): E
	public fun removeLast(): E
	public fun push(e: E)
	public fun pop(): E
}

public open class LinkedList<E>() : AbstractMutableList<E>(), Deque<E>, java.io.Serializable {
	private val delegate = ArrayDeque<E>()

	public constructor(c: Collection<E>) : this() {
		delegate.addAll(c)
	}

	override val size: Int
		get() = delegate.size

	override fun get(index: Int): E = delegate[index]
	override fun set(index: Int, element: E): E = delegate.set(index, element)
	override fun add(index: Int, element: E) = delegate.add(index, element)
	override fun removeAt(index: Int): E = delegate.removeAt(index)

	override fun addFirst(e: E) = delegate.addFirst(e)
	override fun addLast(e: E) = delegate.addLast(e)
	override fun pollFirst(): E? = delegate.removeFirstOrNull()
	override fun pollLast(): E? = delegate.removeLastOrNull()
	override fun peekFirst(): E? = delegate.firstOrNull()
	override fun peekLast(): E? = delegate.lastOrNull()
	override fun removeFirst(): E = delegate.removeFirst()
	override fun removeLast(): E = delegate.removeLast()
	override fun offer(e: E): Boolean = delegate.add(e)
	override fun poll(): E? = delegate.removeFirstOrNull()
	override fun element(): E = delegate.first()
	override fun peek(): E? = delegate.firstOrNull()
	override fun push(e: E) = delegate.addFirst(e)
	override fun pop(): E = delegate.removeFirst()
}

public open class Stack<E>(
	private val backing: ArrayList<E> = ArrayList(),
) : AbstractMutableList<E>(), MutableList<E> {
	override val size: Int
		get() = backing.size

	override fun get(index: Int): E = backing[index]
	override fun set(index: Int, element: E): E = backing.set(index, element)
	override fun add(index: Int, element: E) = backing.add(index, element)
	override fun removeAt(index: Int): E = backing.removeAt(index)

	public fun push(item: E): E {
		backing.add(item)
		return item
	}
	public fun pop(): E = backing.removeAt(backing.size - 1)
	public fun peek(): E = backing[backing.size - 1]
	public fun empty(): Boolean = backing.isEmpty()
}

public class BitSet(nbits: Int = 64) {
	private var words = LongArray(maxOf(1, (nbits + 63) / 64))

	private fun ensureWordIndex(wordIndex: Int) {
		if (wordIndex >= words.size) {
			words = words.copyOf(maxOf(words.size * 2, wordIndex + 1))
		}
	}

	public operator fun get(bitIndex: Int): Boolean {
		val wordIndex = bitIndex shr 6
		if (wordIndex >= words.size) return false
		return (words[wordIndex] and (1L shl (bitIndex and 63))) != 0L
	}

	public fun set(bitIndex: Int) {
		val wordIndex = bitIndex shr 6
		ensureWordIndex(wordIndex)
		words[wordIndex] = words[wordIndex] or (1L shl (bitIndex and 63))
	}

	public fun set(bitIndex: Int, value: Boolean) {
		if (value) set(bitIndex) else clear(bitIndex)
	}

	public fun set(fromIndex: Int, toIndex: Int) {
		for (i in fromIndex until toIndex) set(i)
	}

	public fun set(fromIndex: Int, toIndex: Int, value: Boolean) {
		for (i in fromIndex until toIndex) set(i, value)
	}

	public fun clear(bitIndex: Int) {
		val wordIndex = bitIndex shr 6
		if (wordIndex >= words.size) return
		words[wordIndex] = words[wordIndex] and (1L shl (bitIndex and 63)).inv()
	}

	public fun clear() {
		words.fill(0L)
	}

	public fun cardinality(): Int = words.sumOf { it.countOneBits() }

	public fun length(): Int {
		for (i in words.indices.reversed()) {
			val w = words[i]
			if (w != 0L) {
				return (i * 64) + (64 - w.countLeadingZeroBits())
			}
		}
		return 0
	}

	public fun isEmpty(): Boolean = words.all { it == 0L }
}

public object Objects {
	public fun equals(a: Any?, b: Any?): Boolean = a == b
	public fun hashCode(o: Any?): Int = o?.hashCode() ?: 0
	public fun hash(vararg values: Any?): Int = values.contentHashCode()
	public fun toString(o: Any?): String = o.toString()
	public fun toString(o: Any?, nullDefault: String): String = o?.toString() ?: nullDefault
	public fun <T : Any> requireNonNull(obj: T?): T = obj ?: throw NullPointerException()
	public fun <T : Any> requireNonNull(obj: T?, message: String): T = obj ?: throw NullPointerException(message)
	public fun checkFromIndexSize(fromIndex: Int, size: Int, length: Int): Int {
		if ((length or fromIndex or size) < 0 || size > length - fromIndex) {
			throw IndexOutOfBoundsException("Range [$fromIndex, ${fromIndex + size}) out of bounds for length $length")
		}
		return fromIndex
	}
}

public object Collections {
	public fun <T> emptyList(): List<T> = kotlin.collections.emptyList()
	public fun <T> emptySet(): Set<T> = kotlin.collections.emptySet()
	public fun <K, V> emptyMap(): Map<K, V> = kotlin.collections.emptyMap()
	public fun <T> singleton(o: T): Set<T> = setOf(o)
	public fun <T> singletonList(o: T): List<T> = listOf(o)
	public fun <K, V> singletonMap(key: K, value: V): Map<K, V> = mapOf(key to value)
	public fun <T> unmodifiableList(list: List<T>): List<T> = list
	public fun <T> unmodifiableSet(s: Set<T>): Set<T> = s
	public fun <K, V> unmodifiableMap(m: Map<K, V>): Map<K, V> = m
	public fun <T> synchronizedList(list: MutableList<T>): MutableList<T> = list
	public fun <T> synchronizedSet(s: MutableSet<T>): MutableSet<T> = s
	public fun <K, V> synchronizedMap(m: MutableMap<K, V>): MutableMap<K, V> = m
	public fun <K : Any, V : Any> newSetFromMap(map: MutableMap<K, Boolean>): MutableSet<K> = map.keys
	public fun <T> reverse(list: MutableList<T>): Unit = list.reverse()
	public fun <T> shuffle(list: MutableList<T>): Unit = list.shuffle()
	public fun <T : Comparable<T>> sort(list: MutableList<T>): Unit = list.sort()
	public fun <T> sort(list: MutableList<T>, c: Comparator<in T>?): Unit {
		if (c != null) list.sortWith(c)
	}
	public fun <T> addAll(c: MutableCollection<in T>, vararg elements: T): Boolean = c.addAll(elements)
	public fun <T> nCopies(n: Int, o: T): List<T> = List(n) { o }
	public fun <T> rotate(list: MutableList<T>, distance: Int) {
		val size = list.size
		if (size == 0) return
		val d = ((distance % size) + size) % size
		if (d == 0) return
		val copy = list.toList()
		for (i in 0 until size) {
			list[(i + d) % size] = copy[i]
		}
	}
}

public object Arrays {
	public fun <T> asList(vararg a: T): MutableList<T> = a.toMutableList()
	public fun equals(a: ByteArray?, a2: ByteArray?): Boolean = a.contentEquals(a2)
	public fun equals(a: IntArray?, a2: IntArray?): Boolean = a.contentEquals(a2)
	public fun fill(a: ByteArray, `val`: Byte) = a.fill(`val`)
	public fun fill(a: IntArray, `val`: Int) = a.fill(`val`)
	public fun copyOf(original: ByteArray, newLength: Int): ByteArray = original.copyOf(newLength)
	public fun copyOfRange(original: ByteArray, from: Int, to: Int): ByteArray = original.copyOfRange(from, to)
}

public class Optional<T : Any> private constructor(private val value: T?) {
	public val isPresent: Boolean
		get() = value != null

	public val isEmpty: Boolean
		get() = value == null

	public fun isPresent(): Boolean = value != null
	public fun isEmpty(): Boolean = value == null

	public fun get(): T = value ?: throw NoSuchElementException("No value present")
	public fun orElse(other: T?): T? = value ?: other
	public fun orElseGet(supplier: () -> T?): T? = value ?: supplier()
	public fun <U : Any> map(mapper: (T) -> U?): Optional<U> =
		if (value == null) empty() else ofNullable(mapper(value))

	public companion object {
		private val EMPTY = Optional<Any>(null)
		@Suppress("UNCHECKED_CAST")
		public fun <T : Any> empty(): Optional<T> = EMPTY as Optional<T>
		public fun <T : Any> of(value: T): Optional<T> = Optional(value)
		public fun <T : Any> ofNullable(value: T?): Optional<T> = if (value == null) empty() else Optional(value)
	}
}

public open class Properties(
	private val backing: LinkedHashMap<Any, Any> = LinkedHashMap(),
) : AbstractMutableMap<Any, Any>(), MutableMap<Any, Any> {
	override val entries: MutableSet<MutableMap.MutableEntry<Any, Any>>
		get() = backing.entries

	override fun put(key: Any, value: Any): Any? = backing.put(key, value)

	public fun getProperty(key: String): String? = backing[key] as? String
	public fun getProperty(key: String, defaultValue: String): String = (backing[key] as? String) ?: defaultValue
	public fun setProperty(key: String, value: String): Any? = backing.put(key, value)
	public fun stringPropertyNames(): Set<String> = backing.keys.mapNotNull { it as? String }.toSet()

	public fun load(inStream: java.io.InputStream) {
		val text = inStream.bufferedReader().readText()
		loadFromText(text)
	}

	public fun load(reader: java.io.Reader) {
		loadFromText(reader.readText())
	}

	private fun loadFromText(text: String) {
		for (line in text.lines()) {
			val trimmed = line.trim()
			if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith('!')) continue
			val sepIdx = trimmed.indexOfFirst { it == '=' || it == ':' }
			if (sepIdx > 0) {
				val k = trimmed.substring(0, sepIdx).trim()
				val v = trimmed.substring(sepIdx + 1).trim()
				backing[k] = v
			}
		}
	}
}

public class StringJoiner(
	private val delimiter: CharSequence,
	private val prefix: CharSequence = "",
	private val suffix: CharSequence = "",
) {
	private val elements = ArrayList<String>()
	private var emptyValue: String? = null

	public fun setEmptyValue(emptyValue: CharSequence): StringJoiner {
		this.emptyValue = emptyValue.toString()
		return this
	}

	public fun add(newElement: CharSequence?): StringJoiner {
		elements.add(newElement?.toString() ?: "null")
		return this
	}

	public fun merge(other: StringJoiner): StringJoiner {
		if (other.elements.isNotEmpty()) {
			elements.add(other.elements.joinToString(other.delimiter))
		}
		return this
	}

	public fun length(): Int = toString().length

	override fun toString(): String {
		if (elements.isEmpty() && emptyValue != null) {
			return emptyValue!!
		}
		return elements.joinToString(delimiter, prefix, suffix)
	}
}

public class Scanner(source: String) : Iterator<String>, java.io.Closeable {
	public constructor(source: java.io.InputStream) : this(source.bufferedReader().readText())
	public constructor(source: java.io.File) : this(source.readText())

	private val lines = source.lines()
	private var lineIdx = 0
	private val tokens = source.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
	private var tokenIdx = 0

	public fun hasNextLine(): Boolean = lineIdx < lines.size
	public fun nextLine(): String = lines[lineIdx++]
	override fun hasNext(): Boolean = tokenIdx < tokens.size
	override fun next(): String = tokens[tokenIdx++]
	public fun nextInt(): Int = next().toInt()
	public fun nextLong(): Long = next().toLong()
	public fun nextDouble(): Double = next().toDouble()
	public fun useDelimiter(pattern: String): Scanner = this
	override fun close() {}
}

public open class Random(seed: Long = org.nekosukuriputo.nekuva.ioscompat.System.nanoTime()) : java.io.Serializable {
	private var rng = kotlin.random.Random(seed)

	public fun setSeed(seed: Long) {
		rng = kotlin.random.Random(seed)
	}

	public fun nextInt(): Int = rng.nextInt()
	public fun nextInt(bound: Int): Int = rng.nextInt(bound)
	public fun nextLong(): Long = rng.nextLong()
	public fun nextBoolean(): Boolean = rng.nextBoolean()
	public fun nextFloat(): Float = rng.nextFloat()
	public fun nextDouble(): Double = rng.nextDouble()
	public fun nextBytes(bytes: ByteArray) {
		rng.nextBytes(bytes)
	}
}

public class ServiceLoader<S : Any> private constructor(private val providers: List<S>) : Iterable<S> {
	override fun iterator(): Iterator<S> = providers.iterator()

	public companion object {
		public fun <S : Any> load(service: Class<S>): ServiceLoader<S> = ServiceLoader(emptyList())
		public fun <S : Any> load(service: Class<S>, loader: Any?): ServiceLoader<S> = ServiceLoader(emptyList())
	}
}
