package java.time

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

public open class ZoneId internal constructor(public val id: String) {
	public companion object {
		public fun of(zoneId: String): ZoneId = ZoneId(zoneId)
		public fun systemDefault(): ZoneId = ZoneId(TimeZone.getDefault().id)
	}
}

public class ZoneOffset(id: String) : ZoneId(id) {
	public companion object {
		public val UTC: ZoneOffset = ZoneOffset("UTC")
	}
}

public class Instant private constructor(private val epochMillis: Long) : Comparable<Instant> {
	public val epochSecond: Long
		get() = epochMillis / 1000L

	public fun toEpochMilli(): Long = epochMillis

	public fun plusSeconds(secondsToAdd: Long): Instant = Instant(epochMillis + secondsToAdd * 1000L)
	public fun minusSeconds(secondsToSubtract: Long): Instant = Instant(epochMillis - secondsToSubtract * 1000L)
	public fun plusMillis(millisToAdd: Long): Instant = Instant(epochMillis + millisToAdd)
	public fun minusMillis(millisToSubtract: Long): Instant = Instant(epochMillis - millisToSubtract)

	public fun plus(amountToAdd: Long, unit: ChronoUnit): Instant =
		Instant(epochMillis + amountToAdd * unit.millis)

	public fun minus(amountToSubtract: Long, unit: ChronoUnit): Instant =
		Instant(epochMillis - amountToSubtract * unit.millis)

	public fun until(endExclusive: Instant, unit: ChronoUnit): Long =
		unit.between(this, endExclusive)

	public fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime(epochMillis, zone)
	public fun atOffset(offset: ZoneOffset): OffsetDateTime = OffsetDateTime(epochMillis, offset)

	override fun compareTo(other: Instant): Int = epochMillis.compareTo(other.epochMillis)
	override fun equals(other: Any?): Boolean = other is Instant && epochMillis == other.epochMillis
	override fun hashCode(): Int = epochMillis.hashCode()
	override fun toString(): String = DateTimeFormatter.ISO_INSTANT.format(this)

	public companion object {
		public val EPOCH: Instant = Instant(0L)
		public fun now(): Instant = Instant(org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis())
		public fun ofEpochMilli(epochMilli: Long): Instant = Instant(epochMilli)
		public fun ofEpochSecond(epochSecond: Long, nanoAdjustment: Long = 0L): Instant =
			Instant(epochSecond * 1000L + nanoAdjustment / 1_000_000L)
		public fun parse(text: CharSequence): Instant = DateTimeFormatter.ISO_INSTANT.parseInstant(text.toString())
	}
}

public class LocalDate(
	public val year: Int,
	public val monthValue: Int,
	public val dayOfMonth: Int,
) : Comparable<LocalDate> {
	public fun getYear(): Int = year
	public fun getMonthValue(): Int = monthValue
	public fun getDayOfMonth(): Int = dayOfMonth

	private fun toCalendar(zone: ZoneId = ZoneOffset.UTC): Calendar {
		return Calendar.getInstance(TimeZone.getTimeZone(zone.id)).apply {
			set(year, monthValue - 1, dayOfMonth, 0, 0, 0)
			set(Calendar.MILLISECOND, 0)
		}
	}

	public fun atStartOfDay(): LocalDateTime = LocalDateTime(year, monthValue, dayOfMonth, 0, 0, 0)
	public fun atStartOfDay(zone: ZoneId): ZonedDateTime = ZonedDateTime(toCalendar(zone).timeInMillis, zone)

	public fun plusDays(daysToAdd: Long): LocalDate {
		val cal = toCalendar()
		cal.add(Calendar.DAY_OF_MONTH, daysToAdd.toInt())
		return LocalDate(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
	}

	public fun minusDays(daysToSubtract: Long): LocalDate = plusDays(-daysToSubtract)

	public fun plusMonths(monthsToAdd: Long): LocalDate {
		val cal = toCalendar()
		cal.add(Calendar.MONTH, monthsToAdd.toInt())
		return LocalDate(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
	}

	public fun minusMonths(monthsToSubtract: Long): LocalDate = plusMonths(-monthsToSubtract)

	public fun plusYears(yearsToAdd: Long): LocalDate {
		val cal = toCalendar()
		cal.add(Calendar.YEAR, yearsToAdd.toInt())
		return LocalDate(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
	}

	public fun minusYears(yearsToSubtract: Long): LocalDate = plusYears(-yearsToSubtract)

	public fun toEpochDay(): Long = toCalendar(ZoneOffset.UTC).timeInMillis / 86_400_000L

	public fun until(endExclusive: LocalDate, unit: ChronoUnit): Long {
		val diffDays = endExclusive.toEpochDay() - this.toEpochDay()
		return when (unit) {
			ChronoUnit.DAYS -> diffDays
			ChronoUnit.WEEKS -> diffDays / 7L
			ChronoUnit.MONTHS -> diffDays / 30L
			ChronoUnit.YEARS -> diffDays / 365L
			else -> (diffDays * 86_400_000L) / unit.millis
		}
	}

	public fun format(formatter: DateTimeFormatter): String = formatter.format(toCalendar().time)

	override fun compareTo(other: LocalDate): Int {
		val y = year.compareTo(other.year)
		if (y != 0) return y
		val m = monthValue.compareTo(other.monthValue)
		return if (m != 0) m else dayOfMonth.compareTo(other.dayOfMonth)
	}

	override fun toString(): String =
		"${year.toString().padStart(4, '0')}-${monthValue.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"

	public companion object {
		public fun now(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
			val cal = Calendar.getInstance(TimeZone.getTimeZone(zone.id))
			return LocalDate(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
		}

		public fun of(year: Int, month: Int, dayOfMonth: Int): LocalDate =
			LocalDate(year, month, dayOfMonth)

		public fun parse(text: CharSequence, formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE): LocalDate =
			formatter.parseLocalDate(text.toString())
	}
}

public class LocalDateTime(
	public val year: Int,
	public val monthValue: Int,
	public val dayOfMonth: Int,
	public val hour: Int = 0,
	public val minute: Int = 0,
	public val second: Int = 0,
) {
	public fun toLocalDate(): LocalDate = LocalDate(year, monthValue, dayOfMonth)

	public fun atZone(zone: ZoneId): ZonedDateTime {
		val cal = Calendar.getInstance(TimeZone.getTimeZone(zone.id)).apply {
			set(year, monthValue - 1, dayOfMonth, hour, minute, second)
			set(Calendar.MILLISECOND, 0)
		}
		return ZonedDateTime(cal.timeInMillis, zone)
	}

	public fun toInstant(offset: ZoneOffset): Instant = atZone(offset).toInstant()

	public fun toEpochSecond(offset: ZoneOffset): Long = toInstant(offset).epochSecond

	public fun format(formatter: DateTimeFormatter): String = formatter.format(this)

	public companion object {
		public fun now(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime {
			val cal = Calendar.getInstance(TimeZone.getTimeZone(zone.id))
			return LocalDateTime(
				cal[Calendar.YEAR],
				cal[Calendar.MONTH] + 1,
				cal[Calendar.DAY_OF_MONTH],
				cal[Calendar.HOUR_OF_DAY],
				cal[Calendar.MINUTE],
				cal[Calendar.SECOND],
			)
		}

		public fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int = 0): LocalDateTime =
			LocalDateTime(year, month, dayOfMonth, hour, minute, second)

		public fun parse(text: CharSequence, formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME): LocalDateTime =
			formatter.parseLocalDateTime(text.toString())
	}
}

public class ZonedDateTime(
	internal val epochMillis: Long,
	public val zone: ZoneId = ZoneId.systemDefault(),
) {
	public fun toInstant(): Instant = Instant.ofEpochMilli(epochMillis)
	public fun toEpochSecond(): Long = epochMillis / 1000L
	public fun toLocalDate(): LocalDate {
		val cal = Calendar.getInstance(TimeZone.getTimeZone(zone.id)).apply { timeInMillis = epochMillis }
		return LocalDate(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
	}
	public fun toLocalDateTime(): LocalDateTime {
		val cal = Calendar.getInstance(TimeZone.getTimeZone(zone.id)).apply { timeInMillis = epochMillis }
		return LocalDateTime(
			cal[Calendar.YEAR],
			cal[Calendar.MONTH] + 1,
			cal[Calendar.DAY_OF_MONTH],
			cal[Calendar.HOUR_OF_DAY],
			cal[Calendar.MINUTE],
			cal[Calendar.SECOND],
		)
	}
	public fun format(formatter: DateTimeFormatter): String = formatter.format(this)

	public companion object {
		public fun now(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
			ZonedDateTime(org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis(), zone)

		public fun parse(text: CharSequence, formatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME): ZonedDateTime =
			ZonedDateTime(formatter.parseInstant(text.toString()).toEpochMilli(), ZoneOffset.UTC)
	}
}

public class OffsetDateTime(
	internal val epochMillis: Long,
	public val offset: ZoneOffset = ZoneOffset.UTC,
) {
	public fun toInstant(): Instant = Instant.ofEpochMilli(epochMillis)
	public fun toEpochSecond(): Long = epochMillis / 1000L
	public fun toZonedDateTime(): ZonedDateTime = ZonedDateTime(epochMillis, offset)
	public fun toLocalDate(): LocalDate = toZonedDateTime().toLocalDate()
	public fun toLocalDateTime(): LocalDateTime = toZonedDateTime().toLocalDateTime()
	public fun format(formatter: DateTimeFormatter): String = formatter.format(this)

	public companion object {
		public fun now(zone: ZoneId = ZoneId.systemDefault()): OffsetDateTime =
			OffsetDateTime(org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis(), ZoneOffset.UTC)

		public fun parse(text: CharSequence, formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME): OffsetDateTime =
			OffsetDateTime(formatter.parseInstant(text.toString()).toEpochMilli(), ZoneOffset.UTC)
	}
}
