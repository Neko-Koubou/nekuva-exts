@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package java.lang.ref

import kotlin.native.ref.WeakReference as NativeWeakReference

public open class Reference<T : Any> internal constructor(target: T?) {
	private val weak = target?.let { NativeWeakReference(it) }

	public open fun get(): T? = weak?.get()

	public open fun clear() {
		weak?.clear()
	}
}

public class WeakReference<T : Any>(referent: T?) : Reference<T>(referent)

public class SoftReference<T : Any>(referent: T?) : Reference<T>(referent)
