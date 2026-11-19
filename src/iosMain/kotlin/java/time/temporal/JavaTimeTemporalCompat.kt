package java.time.temporal

public interface TemporalAccessor

public enum class ChronoUnit(public val millis: Long) {
	NANOS(1L),
	MICROS(1L),
	MILLIS(1L),
	SECONDS(1_000L),
	MINUTES(60_000L),
	HOURS(3_600_000L),
	HALF_DAYS(43_200_000L),
	DAYS(86_400_000L),
	WEEKS(7L * 86_400_000L),
	MONTHS(30L * 86_400_000L),
	YEARS(365L * 86_400_000L),
	;

	public fun between(startInclusive: java.time.Instant, endExclusive: java.time.Instant): Long =
		(endExclusive.toEpochMilli() - startInclusive.toEpochMilli()) / millis
}
