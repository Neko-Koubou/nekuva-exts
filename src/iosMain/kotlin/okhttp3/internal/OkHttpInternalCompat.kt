package okhttp3.internal

import java.io.Closeable

public fun Closeable.closeQuietly() {
	try {
		close()
	} catch (rethrown: RuntimeException) {
		throw rethrown
	} catch (_: Exception) {
	}
}

public fun String.toLongOrDefault(defaultValue: Long): Long =
	trim().toLongOrNull() ?: defaultValue

public fun String.canParseAsIpAddress(): Boolean {
	val s = trim()
	if (s.contains(':')) return true
	val parts = s.split('.')
	return parts.size == 4 && parts.all { p -> p.toIntOrNull() in 0..255 }
}
