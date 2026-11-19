package java.nio

public class ByteOrder private constructor(private val name: String) {
	override fun toString(): String = name

	public companion object {
		public val BIG_ENDIAN: ByteOrder = ByteOrder("BIG_ENDIAN")
		public val LITTLE_ENDIAN: ByteOrder = ByteOrder("LITTLE_ENDIAN")
		public fun nativeOrder(): ByteOrder = LITTLE_ENDIAN
	}
}

public class ByteBuffer private constructor(
	private val bytes: ByteArray,
	private var pos: Int = 0,
	private var lim: Int = bytes.size,
) {
	private var orderVal: ByteOrder = ByteOrder.BIG_ENDIAN

	public fun order(bo: ByteOrder): ByteBuffer {
		orderVal = bo
		return this
	}

	public fun order(): ByteOrder = orderVal

	public fun position(): Int = pos

	public fun position(newPosition: Int): ByteBuffer {
		pos = newPosition
		return this
	}

	public fun limit(): Int = lim

	public fun remaining(): Int = lim - pos

	public fun hasRemaining(): Boolean = pos < lim

	public fun rewind(): ByteBuffer {
		pos = 0
		return this
	}

	public fun flip(): ByteBuffer {
		lim = pos
		pos = 0
		return this
	}

	public fun clear(): ByteBuffer {
		pos = 0
		lim = bytes.size
		return this
	}

	public fun array(): ByteArray = bytes

	public fun get(): Byte = bytes[pos++]

	public fun get(dst: ByteArray, offset: Int = 0, length: Int = dst.size): ByteBuffer {
		bytes.copyInto(dst, destinationOffset = offset, startIndex = pos, endIndex = pos + length)
		pos += length
		return this
	}

	public fun put(b: Byte): ByteBuffer {
		bytes[pos++] = b
		return this
	}

	public fun put(src: ByteArray, offset: Int = 0, length: Int = src.size): ByteBuffer {
		src.copyInto(bytes, destinationOffset = pos, startIndex = offset, endIndex = offset + length)
		pos += length
		return this
	}

	public fun put(src: ByteBuffer): ByteBuffer {
		val rem = src.remaining()
		src.get(bytes, pos, rem)
		pos += rem
		return this
	}

	public fun getShort(): Short {
		val b0 = bytes[pos++].toInt() and 0xFF
		val b1 = bytes[pos++].toInt() and 0xFF
		return if (orderVal === ByteOrder.BIG_ENDIAN) {
			((b0 shl 8) or b1).toShort()
		} else {
			((b1 shl 8) or b0).toShort()
		}
	}

	public fun putShort(value: Short): ByteBuffer {
		val v = value.toInt()
		if (orderVal === ByteOrder.BIG_ENDIAN) {
			bytes[pos++] = ((v ushr 8) and 0xFF).toByte()
			bytes[pos++] = (v and 0xFF).toByte()
		} else {
			bytes[pos++] = (v and 0xFF).toByte()
			bytes[pos++] = ((v ushr 8) and 0xFF).toByte()
		}
		return this
	}

	public fun getInt(): Int {
		val b0 = bytes[pos++].toInt() and 0xFF
		val b1 = bytes[pos++].toInt() and 0xFF
		val b2 = bytes[pos++].toInt() and 0xFF
		val b3 = bytes[pos++].toInt() and 0xFF
		return if (orderVal === ByteOrder.BIG_ENDIAN) {
			(b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
		} else {
			(b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
		}
	}

	public fun getInt(index: Int): Int {
		val saved = pos
		pos = index
		val res = getInt()
		pos = saved
		return res
	}

	public fun putInt(value: Int): ByteBuffer {
		if (orderVal === ByteOrder.BIG_ENDIAN) {
			bytes[pos++] = ((value ushr 24) and 0xFF).toByte()
			bytes[pos++] = ((value ushr 16) and 0xFF).toByte()
			bytes[pos++] = ((value ushr 8) and 0xFF).toByte()
			bytes[pos++] = (value and 0xFF).toByte()
		} else {
			bytes[pos++] = (value and 0xFF).toByte()
			bytes[pos++] = ((value ushr 8) and 0xFF).toByte()
			bytes[pos++] = ((value ushr 16) and 0xFF).toByte()
			bytes[pos++] = ((value ushr 24) and 0xFF).toByte()
		}
		return this
	}

	public fun getLong(): Long {
		var result = 0L
		if (orderVal === ByteOrder.BIG_ENDIAN) {
			for (i in 0 until 8) {
				result = (result shl 8) or (bytes[pos++].toLong() and 0xFFL)
			}
		} else {
			for (i in 0 until 8) {
				result = result or ((bytes[pos++].toLong() and 0xFFL) shl (i * 8))
			}
		}
		return result
	}

	public fun putLong(value: Long): ByteBuffer {
		if (orderVal === ByteOrder.BIG_ENDIAN) {
			for (i in 7 downTo 0) {
				bytes[pos++] = ((value ushr (i * 8)) and 0xFFL).toByte()
			}
		} else {
			for (i in 0 until 8) {
				bytes[pos++] = ((value ushr (i * 8)) and 0xFFL).toByte()
			}
		}
		return this
	}

	public val short: Short
		get() = getShort()

	public val int: Int
		get() = getInt()

	public val long: Long
		get() = getLong()

	public companion object {
		public fun allocate(capacity: Int): ByteBuffer = ByteBuffer(ByteArray(capacity))
		public fun allocateDirect(capacity: Int): ByteBuffer = ByteBuffer(ByteArray(capacity))
		public fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(array)
		public fun wrap(array: ByteArray, offset: Int, length: Int): ByteBuffer =
			ByteBuffer(array, offset, offset + length)
	}
}
