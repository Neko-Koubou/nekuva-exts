package java.time.format

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

public class DateTimeFormatter internal constructor(
	private val pattern: String,
	private val locale: Locale = Locale.US,
	private val zone: ZoneId = ZoneOffset.UTC,
) {
	public fun withLocale(locale: Locale): DateTimeFormatter = DateTimeFormatter(pattern, locale, zone)
	public fun withZone(zone: ZoneId): DateTimeFormatter = DateTimeFormatter(pattern, locale, zone)

	public fun format(instant: Instant): String = format(Date(instant.toEpochMilli()))

	public fun format(dateTime: LocalDateTime): String =
		format(Date(dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()))

	public fun format(zdt: java.time.ZonedDateTime): String =
		format(Date(zdt.toInstant().toEpochMilli()))

	public fun format(odt: java.time.OffsetDateTime): String =
		format(Date(odt.toInstant().toEpochMilli()))

	public fun format(date: Date): String {
		val sdf = SimpleDateFormat(pattern, locale).apply {
			timeZone = TimeZone.getTimeZone(zone.id)
		}
		return sdf.format(date)
	}

	internal fun parseInstant(text: String): Instant {
		val candidates = listOf(
			pattern,
			"yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
			"yyyy-MM-dd'T'HH:mm:ssXXX",
			"yyyy-MM-dd'T'HH:mm:ss.SSSZ",
			"yyyy-MM-dd'T'HH:mm:ss'Z'",
			"yyyy-MM-dd'T'HH:mm:ss",
			"yyyy-MM-dd HH:mm:ss",
			"yyyy-MM-dd",
		)
		for (pat in candidates) {
			try {
				val sdf = SimpleDateFormat(pat, locale).apply {
					timeZone = TimeZone.getTimeZone(zone.id)
				}
				val d = sdf.parse(text)
				if (d != null) return Instant.ofEpochMilli(d.time)
			} catch (_: Throwable) {
			}
		}
		return Instant.EPOCH
	}

	internal fun parseLocalDate(text: String): LocalDate {
		val inst = parseInstant(text)
		val cal = Calendar.getInstance(TimeZone.getTimeZone(zone.id)).apply {
			timeInMillis = inst.toEpochMilli()
		}
		return LocalDate(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
	}

	internal fun parseLocalDateTime(text: String): LocalDateTime {
		val inst = parseInstant(text)
		val cal = Calendar.getInstance(TimeZone.getTimeZone(zone.id)).apply {
			timeInMillis = inst.toEpochMilli()
		}
		return LocalDateTime(
			cal[Calendar.YEAR],
			cal[Calendar.MONTH] + 1,
			cal[Calendar.DAY_OF_MONTH],
			cal[Calendar.HOUR_OF_DAY],
			cal[Calendar.MINUTE],
			cal[Calendar.SECOND],
		)
	}

	public companion object {
		public val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter("yyyy-MM-dd'T'HH:mm:ss'Z'")
		public val ISO_LOCAL_DATE: DateTimeFormatter = DateTimeFormatter("yyyy-MM-dd")
		public val ISO_LOCAL_DATE_TIME: DateTimeFormatter = DateTimeFormatter("yyyy-MM-dd'T'HH:mm:ss")
		public val ISO_OFFSET_DATE_TIME: DateTimeFormatter = DateTimeFormatter("yyyy-MM-dd'T'HH:mm:ssXXX")
		public val ISO_ZONED_DATE_TIME: DateTimeFormatter = DateTimeFormatter("yyyy-MM-dd'T'HH:mm:ssXXX")
		public val ISO_DATE_TIME: DateTimeFormatter = DateTimeFormatter("yyyy-MM-dd'T'HH:mm:ssXXX")
		public val RFC_1123_DATE_TIME: DateTimeFormatter = DateTimeFormatter("EEE, dd MMM yyyy HH:mm:ss zzz")

		public fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern)
		public fun ofPattern(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatter(pattern, locale)
	}
}
