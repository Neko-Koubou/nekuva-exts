package java.util.concurrent.locks

import platform.Foundation.NSRecursiveLock

public interface Lock {
	public fun lock()
	public fun unlock()
	public fun tryLock(): Boolean
}

public open class ReentrantLock(fair: Boolean = false) : Lock {
	private val nsLock = NSRecursiveLock()

	override fun lock() {
		nsLock.lock()
	}

	override fun unlock() {
		nsLock.unlock()
	}

	override fun tryLock(): Boolean = nsLock.tryLock()

	public inline fun <T> withLock(action: () -> T): T {
		lock()
		try {
			return action()
		} finally {
			unlock()
		}
	}
}

public inline fun <T> Lock.withLock(action: () -> T): T {
	lock()
	try {
		return action()
	} finally {
		unlock()
	}
}
