package java.text

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSString
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Foundation.timeIntervalSince1970

public open class ParseException(
	message: String,
	public val errorOffset: Int = 0,
) : Exception(message)

public class ParsePosition(public var index: Int) {
	public var errorIndex: Int = -1
}

public class FieldPosition(public val field: Int)

public abstract class DateFormat {
	protected val nsFormatter: NSDateFormatter = NSDateFormatter().apply {
		locale = NSLocale("en_US_POSIX")
	}

	public var timeZone: TimeZone
		get() = TimeZone(nsFormatter.timeZone)
		set(value) {
			nsFormatter.timeZone = value.nsTimeZone
		}

	public var isLenient: Boolean = true

	public var calendar: Calendar = Calendar.getInstance()

	public open fun format(date: Date): String {
		val nsDate = NSDate.dateWithTimeIntervalSince1970(date.time / 1000.0)
		return nsFormatter.stringFromDate(nsDate)
	}

	public open fun format(obj: Any): String = when (obj) {
		is Date -> format(obj)
		is Number -> format(Date(obj.toLong()))
		else -> throw IllegalArgumentException("Cannot format given Object as a Date")
	}

	public open fun parse(source: String?): Date {
		val cleaned = source?.trim() ?: throw ParseException("Unparseable date: null", 0)
		if (cleaned.isEmpty()) throw ParseException("Unparseable date: \"\"", 0)
		val nsDate = nsFormatter.dateFromString(cleaned)
		if (nsDate != null) {
			return Date((nsDate.timeIntervalSince1970 * 1000.0).toLong())
		}
		throw ParseException("Unparseable date: \"$source\"", 0)
	}

	public open fun parse(source: String, pos: ParsePosition): Date? {
		return try {
			val d = parse(source.substring(pos.index))
			pos.index = source.length
			d
		} catch (_: Throwable) {
			pos.errorIndex = pos.index
			null
		}
	}

	public companion object {
		public const val FULL: Int = 0
		public const val LONG: Int = 1
		public const val MEDIUM: Int = 2
		public const val SHORT: Int = 3
		public const val DEFAULT: Int = MEDIUM

		private fun toNsStyle(style: Int): ULong = when (style) {
			FULL -> NSDateFormatterFullStyle
			LONG -> NSDateFormatterLongStyle
			MEDIUM -> NSDateFormatterMediumStyle
			SHORT -> NSDateFormatterShortStyle
			else -> NSDateFormatterMediumStyle
		}

		public fun getDateInstance(style: Int = DEFAULT, aLocale: Locale = Locale.getDefault()): DateFormat {
			val df = SimpleDateFormat()
			df.nsFormatter.locale = NSLocale(aLocale.toLanguageTag())
			df.nsFormatter.dateStyle = toNsStyle(style)
			df.nsFormatter.timeStyle = NSDateFormatterNoStyle
			return df
		}

		public fun getDateTimeInstance(
			dateStyle: Int = DEFAULT,
			timeStyle: Int = DEFAULT,
			aLocale: Locale = Locale.getDefault(),
		): DateFormat {
			val df = SimpleDateFormat()
			df.nsFormatter.locale = NSLocale(aLocale.toLanguageTag())
			df.nsFormatter.dateStyle = toNsStyle(dateStyle)
			df.nsFormatter.timeStyle = toNsStyle(timeStyle)
			return df
		}

		public fun getInstance(): DateFormat = getDateTimeInstance(SHORT, SHORT)
	}
}

public open class SimpleDateFormat(
	private var pattern: String = "yyyy-MM-dd'T'HH:mm:ss",
	private val locale: Locale = Locale.getDefault(),
) : DateFormat() {

	public constructor(pattern: String) : this(pattern, Locale.getDefault())

	init {
		val localeId = if (locale.language.isEmpty()) "en_US_POSIX" else locale.toLanguageTag()
		nsFormatter.locale = NSLocale(localeId)
		nsFormatter.dateFormat = normalizePattern(pattern)
	}

	public fun toPattern(): String = pattern

	public fun applyPattern(newPattern: String) {
		pattern = newPattern
		nsFormatter.dateFormat = normalizePattern(newPattern)
	}

	private fun normalizePattern(p: String): String {
		// Java SimpleDateFormat 'u' (day number of week) vs ICU 'e', otherwise compatible
		return p
	}

	public companion object {
		public const val FULL: Int = DateFormat.FULL
		public const val LONG: Int = DateFormat.LONG
		public const val MEDIUM: Int = DateFormat.MEDIUM
		public const val SHORT: Int = DateFormat.SHORT
		public const val DEFAULT: Int = DateFormat.DEFAULT

		public fun getDateInstance(style: Int = DEFAULT, aLocale: Locale = Locale.getDefault()): DateFormat =
			DateFormat.getDateInstance(style, aLocale)

		public fun getDateTimeInstance(
			dateStyle: Int = DEFAULT,
			timeStyle: Int = DEFAULT,
			aLocale: Locale = Locale.getDefault(),
		): DateFormat = DateFormat.getDateTimeInstance(dateStyle, timeStyle, aLocale)

		public fun getInstance(): DateFormat = DateFormat.getInstance()
	}
}

public class DecimalFormatSymbols(locale: Locale = Locale.getDefault()) {
	public var decimalSeparator: Char = '.'
	public var groupingSeparator: Char = ','
	public var minusSign: Char = '-'
	public var percent: Char = '%'
	public var zeroDigit: Char = '0'

	public companion object {
		public fun getInstance(locale: Locale = Locale.getDefault()): DecimalFormatSymbols =
			DecimalFormatSymbols(locale)
	}
}

public open class NumberFormat {
	protected val nsNumberFormatter = NSNumberFormatter().apply {
		numberStyle = NSNumberFormatterDecimalStyle
	}

	public var isGroupingUsed: Boolean
		get() = nsNumberFormatter.usesGroupingSeparator
		set(value) {
			nsNumberFormatter.usesGroupingSeparator = value
		}

	public var maximumFractionDigits: Int
		get() = nsNumberFormatter.maximumFractionDigits.toInt()
		set(value) {
			nsNumberFormatter.maximumFractionDigits = value.toULong()
		}

	public var minimumFractionDigits: Int
		get() = nsNumberFormatter.minimumFractionDigits.toInt()
		set(value) {
			nsNumberFormatter.minimumFractionDigits = value.toULong()
		}

	public open fun format(number: Long): String =
		nsNumberFormatter.stringFromNumber(NSNumber(longLong = number)) ?: number.toString()

	public open fun format(number: Double): String =
		nsNumberFormatter.stringFromNumber(NSNumber(double = number)) ?: number.toString()

	public open fun format(number: Any): String = when (number) {
		is Double, is Float -> format((number as Number).toDouble())
		is Number -> format(number.toLong())
		else -> number.toString()
	}

	public open fun parse(source: String): Number? {
		val trimmed = source.trim()
		return trimmed.toLongOrNull() ?: trimmed.toDoubleOrNull()
	}

	public companion object {
		public fun getInstance(locale: Locale = Locale.getDefault()): NumberFormat = DecimalFormat()
		public fun getNumberInstance(locale: Locale = Locale.getDefault()): NumberFormat = DecimalFormat()
		public fun getIntegerInstance(locale: Locale = Locale.getDefault()): NumberFormat = DecimalFormat("#")
	}
}

public open class DecimalFormat(
	private val pattern: String = "#,##0.###",
	private var symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(),
) : NumberFormat() {

	public var decimalFormatSymbols: DecimalFormatSymbols
		get() = symbols
		set(value) {
			symbols = value
			nsNumberFormatter.decimalSeparator = value.decimalSeparator.toString()
			nsNumberFormatter.groupingSeparator = value.groupingSeparator.toString()
		}

	init {
		nsNumberFormatter.decimalSeparator = symbols.decimalSeparator.toString()
		nsNumberFormatter.groupingSeparator = symbols.groupingSeparator.toString()
		if (!pattern.contains(',')) {
			nsNumberFormatter.usesGroupingSeparator = false
		}
		val dotIdx = pattern.indexOf('.')
		if (dotIdx >= 0) {
			val frac = pattern.substring(dotIdx + 1)
			nsNumberFormatter.minimumFractionDigits = frac.count { it == '0' }.toULong()
			nsNumberFormatter.maximumFractionDigits = frac.count { it == '0' || it == '#' }.toULong()
		} else {
			nsNumberFormatter.minimumFractionDigits = 0u
			nsNumberFormatter.maximumFractionDigits = 0u
		}
	}
}

public object Normalizer {
	public enum class Form {
		NFD,
		NFC,
		NFKD,
		NFKC,
	}

	@Suppress("CAST_NEVER_SUCCEEDS")
	public fun normalize(src: CharSequence, form: Form): String {
		val nsStr = src.toString() as NSString
		return when (form) {
			Form.NFD -> nsStr.decomposedStringWithCanonicalMapping
			Form.NFC -> nsStr.precomposedStringWithCanonicalMapping
			Form.NFKD -> nsStr.decomposedStringWithCompatibilityMapping
			Form.NFKC -> nsStr.precomposedStringWithCompatibilityMapping
		}
	}

	public fun isNormalized(src: CharSequence, form: Form): Boolean =
		normalize(src, form) == src.toString()
}
