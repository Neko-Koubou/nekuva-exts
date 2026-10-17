package kotlinx.coroutines

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

public suspend fun <T> runInterruptible(
	context: CoroutineContext = EmptyCoroutineContext,
	block: () -> T,
): T = withContext(context) {
	block()
}
