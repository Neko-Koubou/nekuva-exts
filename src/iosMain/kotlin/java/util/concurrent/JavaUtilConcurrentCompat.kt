package java.util.concurrent

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public enum class TimeUnit(internal val nanosPerUnit: Long) {
	NANOSECONDS(1L),
	MICROSECONDS(1_000L),
	MILLISECONDS(1_000_000L),
	SECONDS(1_000_000_000L),
	MINUTES(60L * 1_000_000_000L),
	HOURS(3600L * 1_000_000_000L),
	DAYS(86400L * 1_000_000_000L),
	;

	public fun toNanos(duration: Long): Long = duration * nanosPerUnit
	public fun toMicros(duration: Long): Long = (duration * nanosPerUnit) / MICROSECONDS.nanosPerUnit
	public fun toMillis(duration: Long): Long = (duration * nanosPerUnit) / MILLISECONDS.nanosPerUnit
	public fun toSeconds(duration: Long): Long = (duration * nanosPerUnit) / SECONDS.nanosPerUnit
	public fun toMinutes(duration: Long): Long = (duration * nanosPerUnit) / MINUTES.nanosPerUnit
	public fun toHours(duration: Long): Long = (duration * nanosPerUnit) / HOURS.nanosPerUnit
	public fun toDays(duration: Long): Long = (duration * nanosPerUnit) / DAYS.nanosPerUnit

	public fun convert(sourceDuration: Long, sourceUnit: TimeUnit): Long =
		(sourceDuration * sourceUnit.nanosPerUnit) / nanosPerUnit

	public fun sleep(timeout: Long) {
		val ms = toMillis(timeout)
		if (ms > 0) {
			org.nekosukuriputo.nekuva.ioscompat.Thread.sleep(ms)
		}
	}
}

public open class ConcurrentHashMap<K, V>(
	private val backing: LinkedHashMap<K, V> = LinkedHashMap(),
) : AbstractMutableMap<K, V>(), MutableMap<K, V> {
	public constructor() : this(LinkedHashMap())
	public constructor(initialCapacity: Int) : this(LinkedHashMap(initialCapacity))
	public constructor(initialCapacity: Int, loadFactor: Float) : this(LinkedHashMap(initialCapacity, loadFactor))
	public constructor(initialCapacity: Int, loadFactor: Float, concurrencyLevel: Int) : this(LinkedHashMap(initialCapacity, loadFactor))
	public constructor(m: Map<out K, V>) : this(LinkedHashMap(m))

	override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
		get() = backing.entries

	override fun put(key: K, value: V): V? = backing.put(key, value)

	public fun putIfAbsent(key: K, value: V): V? {
		val existing = backing[key]
		if (existing == null) {
			backing[key] = value
		}
		return existing
	}

	public fun computeIfAbsent(key: K, mappingFunction: (K) -> V): V {
		val existing = backing[key]
		if (existing != null) return existing
		val created = mappingFunction(key)
		backing[key] = created
		return created
	}

	public companion object {
		public fun <K> newKeySet(): MutableSet<K> = LinkedHashSet()
		public fun <K> newKeySet(initialCapacity: Int): MutableSet<K> = LinkedHashSet(initialCapacity)
	}
}

public open class CopyOnWriteArrayList<E>(
	private val backing: ArrayList<E> = ArrayList(),
) : AbstractMutableList<E>(), MutableList<E> {
	public constructor() : this(ArrayList())
	public constructor(c: Collection<E>) : this(ArrayList(c))

	override val size: Int
		get() = backing.size

	override fun get(index: Int): E = backing[index]
	override fun set(index: Int, element: E): E = backing.set(index, element)
	override fun add(index: Int, element: E) = backing.add(index, element)
	override fun removeAt(index: Int): E = backing.removeAt(index)
}

public open class CopyOnWriteArraySet<E>(
	private val backing: LinkedHashSet<E> = LinkedHashSet(),
) : AbstractMutableSet<E>(), MutableSet<E> {
	public constructor() : this(LinkedHashSet())
	public constructor(c: Collection<E>) : this(LinkedHashSet(c))

	override val size: Int
		get() = backing.size

	override fun add(element: E): Boolean = backing.add(element)
	override fun iterator(): MutableIterator<E> = backing.iterator()
}

public fun interface Callable<V> {
	public fun call(): V
}

public class CountDownLatch(count: Int) {
	private var remaining = count
	public fun countDown() {
		if (remaining > 0) remaining--
	}
	public fun await() {}
	public fun await(timeout: Long, unit: TimeUnit): Boolean = true
	public fun getCount(): Long = remaining.toLong()
}

public open class TimeoutException(message: String? = null) : Exception(message)
public open class ExecutionException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
public typealias CancellationException = kotlin.coroutines.cancellation.CancellationException
