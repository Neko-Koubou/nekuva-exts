package kotlin.concurrent

import java.util.concurrent.locks.Lock

public inline fun <T> Lock.withLock(action: () -> T): T {
	lock()
	try {
		return action()
	} finally {
		unlock()
	}
}
