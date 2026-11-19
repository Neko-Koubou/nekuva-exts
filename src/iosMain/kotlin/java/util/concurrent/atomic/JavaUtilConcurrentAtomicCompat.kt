package java.util.concurrent.atomic

import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong as NativeAtomicLong
import kotlin.concurrent.AtomicReference as NativeAtomicReference

public class AtomicBoolean(initialValue: Boolean = false) : java.io.Serializable {
	private val atom = AtomicInt(if (initialValue) 1 else 0)

	public fun get(): Boolean = atom.value != 0

	public fun set(newValue: Boolean) {
		atom.value = if (newValue) 1 else 0
	}

	public fun getAndSet(newValue: Boolean): Boolean =
		atom.getAndSet(if (newValue) 1 else 0) != 0

	public fun compareAndSet(expect: Boolean, update: Boolean): Boolean =
		atom.compareAndSet(if (expect) 1 else 0, if (update) 1 else 0)

	override fun toString(): String = get().toString()
}

public class AtomicInteger(initialValue: Int = 0) : Number(), java.io.Serializable {
	private val atom = AtomicInt(initialValue)

	public fun get(): Int = atom.value

	public fun set(newValue: Int) {
		atom.value = newValue
	}

	public fun getAndSet(newValue: Int): Int = atom.getAndSet(newValue)

	public fun compareAndSet(expect: Int, update: Int): Boolean = atom.compareAndSet(expect, update)

	public fun getAndIncrement(): Int = atom.getAndIncrement()

	public fun getAndDecrement(): Int = atom.getAndDecrement()

	public fun getAndAdd(delta: Int): Int = atom.getAndAdd(delta)

	public fun incrementAndGet(): Int = atom.incrementAndGet()

	public fun decrementAndGet(): Int = atom.decrementAndGet()

	public fun addAndGet(delta: Int): Int = atom.addAndGet(delta)

	override fun toByte(): Byte = get().toByte()
	override fun toChar(): Char = get().toChar()
	override fun toDouble(): Double = get().toDouble()
	override fun toFloat(): Float = get().toFloat()
	override fun toInt(): Int = get()
	override fun toLong(): Long = get().toLong()
	override fun toShort(): Short = get().toShort()
	override fun toString(): String = get().toString()
}

public class AtomicLong(initialValue: Long = 0L) : Number(), java.io.Serializable {
	private val atom = NativeAtomicLong(initialValue)

	public fun get(): Long = atom.value

	public fun set(newValue: Long) {
		atom.value = newValue
	}

	public fun getAndSet(newValue: Long): Long = atom.getAndSet(newValue)

	public fun compareAndSet(expect: Long, update: Long): Boolean = atom.compareAndSet(expect, update)

	public fun getAndIncrement(): Long = atom.getAndIncrement()

	public fun getAndDecrement(): Long = atom.getAndDecrement()

	public fun getAndAdd(delta: Long): Long = atom.getAndAdd(delta)

	public fun incrementAndGet(): Long = atom.incrementAndGet()

	public fun decrementAndGet(): Long = atom.decrementAndGet()

	public fun addAndGet(delta: Long): Long = atom.addAndGet(delta)

	override fun toByte(): Byte = get().toByte()
	override fun toChar(): Char = get().toChar()
	override fun toDouble(): Double = get().toDouble()
	override fun toFloat(): Float = get().toFloat()
	override fun toInt(): Int = get().toInt()
	override fun toLong(): Long = get()
	override fun toShort(): Short = get().toShort()
	override fun toString(): String = get().toString()
}

public class AtomicReference<V>(initialValue: V? = null) : java.io.Serializable {
	private val atom = NativeAtomicReference<V?>(initialValue)

	public fun get(): V? = atom.value

	public fun set(newValue: V?) {
		atom.value = newValue
	}

	public fun getAndSet(newValue: V?): V? = atom.getAndSet(newValue)

	public fun compareAndSet(expect: V?, update: V?): Boolean = atom.compareAndSet(expect, update)

	override fun toString(): String = get().toString()
}
