package androidx.collection

public class ArrayMap<K, V> : AbstractMutableMap<K, V>, MutableMap<K, V> {
	private val backing: LinkedHashMap<K, V>

	public constructor() : super() {
		backing = LinkedHashMap()
	}

	public constructor(capacity: Int) : super() {
		backing = if (capacity > 0) LinkedHashMap(capacity) else LinkedHashMap()
	}

	public constructor(map: Map<out K, V>?) : super() {
		backing = LinkedHashMap()
		if (map != null) {
			backing.putAll(map)
		}
	}

	override val size: Int
		get() = backing.size

	override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
		get() = backing.entries

	override val keys: MutableSet<K>
		get() = backing.keys

	override val values: MutableCollection<V>
		get() = backing.values

	override fun put(key: K, value: V): V? = backing.put(key, value)

	override fun get(key: K): V? = backing[key]

	public fun getOrDefault(key: K, defaultValue: V): V = backing[key] ?: defaultValue

	override fun remove(key: K): V? = backing.remove(key)

	override fun containsKey(key: K): Boolean = backing.containsKey(key)

	override fun containsValue(value: V): Boolean = backing.containsValue(value)

	override fun clear() = backing.clear()

	public fun ensureCapacity(minimumCapacity: Int) {}
}
