package java.math

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

public enum class RoundingMode {
	UP,
	DOWN,
	CEILING,
	FLOOR,
	HALF_UP,
	HALF_DOWN,
	HALF_EVEN,
	UNNECESSARY,
}

/**
 * arbitrary-precision unsigned magnitude in base 10^9 (or signed wrapper) for BigInteger
 * used by manga parsers (e.g. hex/radix conversions, modular arithmetic, IDs).
 */
public class BigInteger : Number, Comparable<BigInteger>, java.io.Serializable {
	public val signum: Int
	// Little-endian limbs in base 2^32
	private val mag: UIntArray

	private constructor(signum: Int, mag: UIntArray) {
		val trimmed = trimLeadingZeros(mag)
		if (trimmed.isEmpty() || signum == 0) {
			this.signum = 0
			this.mag = UIntArray(0)
		} else {
			this.signum = if (signum < 0) -1 else 1
			this.mag = trimmed
		}
	}

	public constructor(valStr: String, radix: Int = 10) {
		val s = valStr.trim()
		if (s.isEmpty()) throw NumberFormatException("Zero length BigInteger")
		var sign = 1
		var idx = 0
		if (s[0] == '-') {
			sign = -1
			idx = 1
		} else if (s[0] == '+') {
			idx = 1
		}
		if (idx >= s.length) throw NumberFormatException("Illegal BigInteger: $valStr")
		var acc = ZERO
		val base = valueOf(radix.toLong())
		while (idx < s.length) {
			val d = s[idx].digitToIntOrNull(radix)
				?: throw NumberFormatException("Illegal digit '${s[idx]}' in $valStr")
			acc = acc.multiply(base).add(valueOf(d.toLong()))
			idx++
		}
		this.signum = if (acc.mag.isEmpty()) 0 else sign
		this.mag = acc.mag
	}

	public constructor(signum: Int, magnitude: ByteArray) {
		if (signum == 0 || magnitude.all { it.toInt() == 0 }) {
			this.signum = 0
			this.mag = UIntArray(0)
		} else {
			this.signum = if (signum < 0) -1 else 1
			this.mag = bytesToLimbs(magnitude)
		}
	}

	public constructor(bytes: ByteArray) {
		if (bytes.isEmpty()) throw NumberFormatException("Zero length BigInteger")
		if (bytes[0] < 0) {
			// Two's complement negative
			val inverted = ByteArray(bytes.size) { (bytes[it].toInt() xor 0xFF).toByte() }
			val pos = BigInteger(1, inverted).add(ONE)
			this.signum = -1
			this.mag = pos.mag
		} else {
			val pos = BigInteger(1, bytes)
			this.signum = pos.signum
			this.mag = pos.mag
		}
	}

	public fun signum(): Int = signum

	public fun abs(): BigInteger = if (signum < 0) negate() else this

	public fun negate(): BigInteger = if (signum == 0) this else BigInteger(-signum, mag)

	public fun add(other: BigInteger): BigInteger {
		if (this.signum == 0) return other
		if (other.signum == 0) return this
		if (this.signum == other.signum) {
			return BigInteger(this.signum, addMag(this.mag, other.mag))
		}
		val cmp = compareMag(this.mag, other.mag)
		if (cmp == 0) return ZERO
		return if (cmp > 0) {
			BigInteger(this.signum, subMag(this.mag, other.mag))
		} else {
			BigInteger(other.signum, subMag(other.mag, this.mag))
		}
	}

	public fun subtract(other: BigInteger): BigInteger = add(other.negate())

	public fun multiply(other: BigInteger): BigInteger {
		if (this.signum == 0 || other.signum == 0) return ZERO
		return BigInteger(this.signum * other.signum, mulMag(this.mag, other.mag))
	}

	public fun divide(other: BigInteger): BigInteger = divideAndRemainder(other)[0]

	public fun remainder(other: BigInteger): BigInteger = divideAndRemainder(other)[1]

	public fun mod(m: BigInteger): BigInteger {
		val r = remainder(m)
		return if (r.signum < 0) r.add(m.abs()) else r
	}

	public fun divideAndRemainder(other: BigInteger): Array<BigInteger> {
		if (other.signum == 0) throw ArithmeticException("BigInteger divide by zero")
		if (this.signum == 0) return arrayOf(ZERO, ZERO)
		val cmp = compareMag(this.mag, other.mag)
		if (cmp < 0) return arrayOf(ZERO, this)
		if (cmp == 0) return arrayOf(if (this.signum == other.signum) ONE else ONE.negate(), ZERO)

		val (qMag, rMag) = divRemMag(this.mag, other.mag)
		val q = BigInteger(this.signum * other.signum, qMag)
		val r = BigInteger(this.signum, rMag)
		return arrayOf(q, r)
	}

	public fun pow(exponent: Int): BigInteger {
		if (exponent < 0) throw ArithmeticException("Negative exponent")
		if (exponent == 0) return ONE
		var result = ONE
		var base = this
		var exp = exponent
		while (exp > 0) {
			if ((exp and 1) == 1) result = result.multiply(base)
			base = base.multiply(base)
			exp = exp ushr 1
		}
		return result
	}

	public fun modPow(exponent: BigInteger, m: BigInteger): BigInteger {
		if (m.signum <= 0) throw ArithmeticException("BigInteger: modulus not positive")
		var result = ONE
		var base = this.mod(m)
		var exp = exponent.abs()
		while (exp.signum > 0) {
			if (exp.testBit(0)) {
				result = result.multiply(base).mod(m)
			}
			exp = exp.shiftRight(1)
			base = base.multiply(base).mod(m)
		}
		return result
	}

	public fun shiftLeft(n: Int): BigInteger {
		if (signum == 0 || n == 0) return this
		if (n < 0) return shiftRight(-n)
		val wordShift = n ushr 5
		val bitShift = n and 31
		val out = UIntArray(mag.size + wordShift + 1)
		var carry = 0u
		for (i in mag.indices) {
			val v = mag[i]
			out[i + wordShift] = (v shl bitShift) or carry
			carry = if (bitShift == 0) 0u else (v shr (32 - bitShift))
		}
		out[mag.size + wordShift] = carry
		return BigInteger(signum, out)
	}

	public fun shiftRight(n: Int): BigInteger {
		if (signum == 0 || n == 0) return this
		if (n < 0) return shiftLeft(-n)
		val wordShift = n ushr 5
		val bitShift = n and 31
		if (wordShift >= mag.size) return if (signum < 0) valueOf(-1L) else ZERO
		val out = UIntArray(mag.size - wordShift)
		for (i in out.indices) {
			val lo = mag[i + wordShift] shr bitShift
			val hi = if (bitShift != 0 && i + wordShift + 1 < mag.size) {
				mag[i + wordShift + 1] shl (32 - bitShift)
			} else {
				0u
			}
			out[i] = lo or hi
		}
		return BigInteger(signum, out)
	}

	public fun testBit(n: Int): Boolean {
		val wordIdx = n ushr 5
		if (wordIdx >= mag.size) return signum < 0
		return ((mag[wordIdx] shr (n and 31)) and 1u) != 0u
	}

	public fun bitLength(): Int {
		if (signum == 0) return 0
		val top = mag.last()
		return (mag.size - 1) * 32 + (32 - top.countLeadingZeroBits())
	}

	public fun and(other: BigInteger): BigInteger = BigInteger.valueOf(this.toLong() and other.toLong())
	public fun or(other: BigInteger): BigInteger = BigInteger.valueOf(this.toLong() or other.toLong())
	public fun xor(other: BigInteger): BigInteger = BigInteger.valueOf(this.toLong() xor other.toLong())

	public fun toByteArray(): ByteArray {
		if (signum == 0) return byteArrayOf(0)
		val raw = ArrayList<Byte>()
		for (i in mag.indices.reversed()) {
			val w = mag[i]
			raw.add(((w shr 24) and 0xFFu).toByte())
			raw.add(((w shr 16) and 0xFFu).toByte())
			raw.add(((w shr 8) and 0xFFu).toByte())
			raw.add((w and 0xFFu).toByte())
		}
		while (raw.size > 1 && raw[0].toInt() == 0) {
			raw.removeAt(0)
		}
		if (signum > 0 && (raw[0].toInt() and 0x80) != 0) {
			raw.add(0, 0)
		}
		return raw.toByteArray()
	}

	public fun toString(radix: Int): String {
		if (signum == 0) return "0"
		val r = radix.coerceIn(2, 36)
		val base = valueOf(r.toLong())
		var cur = this.abs()
		val sb = StringBuilder()
		val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
		while (cur.signum > 0) {
			val dr = cur.divideAndRemainder(base)
			val rem = dr[1].toInt()
			sb.append(digits[rem])
			cur = dr[0]
		}
		if (signum < 0) sb.append('-')
		return sb.reverse().toString()
	}

	override fun toString(): String = toString(10)

	override fun toByte(): Byte = toInt().toByte()
	override fun toChar(): Char = toInt().toChar()
	override fun toShort(): Short = toInt().toShort()

	override fun toInt(): Int {
		if (signum == 0) return 0
		val v = mag[0].toInt()
		return if (signum < 0) -v else v
	}

	override fun toLong(): Long {
		if (signum == 0) return 0L
		val lo = mag[0].toLong() and 0xFFFFFFFFL
		val hi = if (mag.size > 1) (mag[1].toLong() and 0xFFFFFFFFL) shl 32 else 0L
		val u = hi or lo
		return if (signum < 0) -u else u
	}

	override fun toFloat(): Float = toDouble().toFloat()

	override fun toDouble(): Double {
		var res = 0.0
		for (i in mag.indices.reversed()) {
			res = res * 4294967296.0 + mag[i].toDouble()
		}
		return if (signum < 0) -res else res
	}

	override fun compareTo(other: BigInteger): Int {
		if (this.signum != other.signum) return this.signum.compareTo(other.signum)
		if (this.signum == 0) return 0
		val cmp = compareMag(this.mag, other.mag)
		return if (this.signum > 0) cmp else -cmp
	}

	override fun equals(other: Any?): Boolean =
		other is BigInteger && signum == other.signum && mag.contentEquals(other.mag)

	override fun hashCode(): Int = signum * 31 + mag.contentHashCode()

	public companion object {
		public val ZERO: BigInteger = BigInteger(0, UIntArray(0))
		public val ONE: BigInteger = valueOf(1L)
		public val TWO: BigInteger = valueOf(2L)
		public val TEN: BigInteger = valueOf(10L)

		public fun valueOf(v: Long): BigInteger {
			if (v == 0L) return ZERO
			val sign = if (v < 0) -1 else 1
			val absVal = if (v == Long.MIN_VALUE) v.toULong() else abs(v).toULong()
			val lo = (absVal and 0xFFFFFFFFuL).toUInt()
			val hi = (absVal shr 32).toUInt()
			val arr = if (hi == 0u) uintArrayOf(lo) else uintArrayOf(lo, hi)
			return BigInteger(sign, arr)
		}

		private fun trimLeadingZeros(a: UIntArray): UIntArray {
			var lastNonZero = a.size - 1
			while (lastNonZero >= 0 && a[lastNonZero] == 0u) {
				lastNonZero--
			}
			if (lastNonZero < 0) return UIntArray(0)
			return if (lastNonZero == a.size - 1) a else a.copyOf(lastNonZero + 1)
		}

		private fun bytesToLimbs(bytes: ByteArray): UIntArray {
			val numWords = (bytes.size + 3) / 4
			val out = UIntArray(numWords)
			var byteIdx = bytes.size - 1
			for (w in 0 until numWords) {
				var word = 0u
				for (b in 0 until 4) {
					if (byteIdx >= 0) {
						word = word or (((bytes[byteIdx].toInt() and 0xFF).toUInt()) shl (b * 8))
						byteIdx--
					}
				}
				out[w] = word
			}
			return trimLeadingZeros(out)
		}

		private fun compareMag(a: UIntArray, b: UIntArray): Int {
			if (a.size != b.size) return a.size.compareTo(b.size)
			for (i in a.indices.reversed()) {
				if (a[i] != b[i]) return a[i].compareTo(b[i])
			}
			return 0
		}

		private fun addMag(a: UIntArray, b: UIntArray): UIntArray {
			val maxLen = maxOf(a.size, b.size)
			val out = UIntArray(maxLen + 1)
			var carry = 0uL
			for (i in 0 until maxLen) {
				val av = if (i < a.size) a[i].toULong() else 0uL
				val bv = if (i < b.size) b[i].toULong() else 0uL
				val sum = av + bv + carry
				out[i] = (sum and 0xFFFFFFFFuL).toUInt()
				carry = sum shr 32
			}
			out[maxLen] = carry.toUInt()
			return trimLeadingZeros(out)
		}

		private fun subMag(a: UIntArray, b: UIntArray): UIntArray {
			val out = UIntArray(a.size)
			var borrow = 0L
			for (i in a.indices) {
				val av = a[i].toLong() and 0xFFFFFFFFL
				val bv = if (i < b.size) (b[i].toLong() and 0xFFFFFFFFL) else 0L
				val diff = av - bv - borrow
				if (diff < 0) {
					out[i] = (diff + 0x100000000L).toUInt()
					borrow = 1L
				} else {
					out[i] = diff.toUInt()
					borrow = 0L
				}
			}
			return trimLeadingZeros(out)
		}

		private fun mulMag(a: UIntArray, b: UIntArray): UIntArray {
			val out = UIntArray(a.size + b.size)
			for (i in a.indices) {
				val av = a[i].toULong()
				var carry = 0uL
				for (j in b.indices) {
					val prod = av * b[j].toULong() + out[i + j].toULong() + carry
					out[i + j] = (prod and 0xFFFFFFFFuL).toUInt()
					carry = prod shr 32
				}
				out[i + b.size] = (out[i + b.size].toULong() + carry).toUInt()
			}
			return trimLeadingZeros(out)
		}

		private fun divRemMag(u: UIntArray, v: UIntArray): Pair<UIntArray, UIntArray> {
			if (v.size == 1) {
				val d = v[0].toULong()
				val q = UIntArray(u.size)
				var rem = 0uL
				for (i in u.indices.reversed()) {
					val cur = (rem shl 32) or u[i].toULong()
					q[i] = (cur / d).toUInt()
					rem = cur % d
				}
				return trimLeadingZeros(q) to if (rem == 0uL) UIntArray(0) else uintArrayOf(rem.toUInt())
			}
			// Binary long division for multi-limb divisors
			var rem = ZERO
			var quot = ZERO
			val divisor = BigInteger(1, v)
			val totalBits = BigInteger(1, u).bitLength()
			val dividend = BigInteger(1, u)
			for (bit in (totalBits - 1) downTo 0) {
				rem = rem.shiftLeft(1)
				if (dividend.testBit(bit)) {
					rem = rem.add(ONE)
				}
				if (rem >= divisor) {
					rem = rem.subtract(divisor)
					quot = quot.add(ONE.shiftLeft(bit))
				}
			}
			return quot.mag to rem.mag
		}
	}
}

public class BigDecimal(private val value: Double) : Number(), Comparable<BigDecimal>, java.io.Serializable {
	public constructor(valStr: String) : this(valStr.trim().toDoubleOrNull() ?: 0.0)
	public constructor(valInt: Int) : this(valInt.toDouble())
	public constructor(valLong: Long) : this(valLong.toDouble())
	public constructor(valBigInt: BigInteger) : this(valBigInt.toDouble())

	public fun add(augend: BigDecimal): BigDecimal = BigDecimal(value + augend.value)
	public fun subtract(subtrahend: BigDecimal): BigDecimal = BigDecimal(value - subtrahend.value)
	public fun multiply(multiplicand: BigDecimal): BigDecimal = BigDecimal(value * multiplicand.value)
	public fun divide(divisor: BigDecimal): BigDecimal = BigDecimal(value / divisor.value)
	public fun divide(divisor: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal =
		BigDecimal(value / divisor.value).setScale(scale, roundingMode)

	public fun setScale(newScale: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): BigDecimal {
		val factor = 10.0.pow(newScale.toDouble())
		val scaled = value * factor
		val rounded = when (roundingMode) {
			RoundingMode.CEILING -> ceil(scaled)
			RoundingMode.FLOOR -> floor(scaled)
			RoundingMode.DOWN -> if (scaled >= 0) floor(scaled) else ceil(scaled)
			RoundingMode.UP -> if (scaled >= 0) ceil(scaled) else floor(scaled)
			else -> round(scaled)
		}
		return BigDecimal(rounded / factor)
	}

	public fun stripTrailingZeros(): BigDecimal = this

	public fun toPlainString(): String {
		val l = value.toLong()
		return if (value == l.toDouble()) l.toString() else value.toString()
	}

	override fun toByte(): Byte = value.toInt().toByte()
	override fun toChar(): Char = value.toInt().toChar()
	override fun toDouble(): Double = value
	override fun toFloat(): Float = value.toFloat()
	override fun toInt(): Int = value.toInt()
	override fun toLong(): Long = value.toLong()
	override fun toShort(): Short = value.toInt().toShort()
	public fun toBigInteger(): BigInteger = BigInteger.valueOf(value.toLong())

	override fun compareTo(other: BigDecimal): Int = value.compareTo(other.value)
	override fun equals(other: Any?): Boolean = other is BigDecimal && value == other.value
	override fun hashCode(): Int = value.hashCode()
	override fun toString(): String = toPlainString()

	public companion object {
		public val ZERO: BigDecimal = BigDecimal(0.0)
		public val ONE: BigDecimal = BigDecimal(1.0)
		public val TEN: BigDecimal = BigDecimal(10.0)

		public fun valueOf(v: Long): BigDecimal = BigDecimal(v.toDouble())
		public fun valueOf(v: Double): BigDecimal = BigDecimal(v)
	}
}
