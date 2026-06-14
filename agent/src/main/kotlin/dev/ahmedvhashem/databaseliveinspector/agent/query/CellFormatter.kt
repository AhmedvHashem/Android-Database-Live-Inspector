package dev.ahmedvhashem.databaseliveinspector.agent.query

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Cell stringification matching SQLite's text representation: integers/reals as their SQLite
 * `CAST(x AS TEXT)` form, BLOBs as `blob(<n> bytes) 0x<first 16 bytes hex>`.
 */
internal object CellFormatter {

    private val REAL_MATH_CONTEXT = MathContext(15, RoundingMode.HALF_EVEN)

    /**
     * SQLite's text representation of a REAL — the equivalent of
     * `sqlite3_snprintf("%!.15g", v)` used by `CAST(x AS TEXT)`:
     *
     * - 15 significant digits, trailing zeros stripped;
     * - fixed notation while the decimal exponent is in `[-4, 14]`, otherwise scientific
     *   with a signed two-digit-minimum exponent (`1.0e+20`, `1.0e-05`);
     * - the `!` flag always keeps a radix point (`1.0`, `100000000000000.0`);
     * - infinities render as `Inf` / `-Inf`; the sign of negative zero is dropped (`0.0`).
     */
    fun formatReal(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value == Double.POSITIVE_INFINITY) return "Inf"
        if (value == Double.NEGATIVE_INFINITY) return "-Inf"
        if (value == 0.0) return "0.0" // covers -0.0: SQLite renders it unsigned

        val sign = if (value < 0) "-" else ""
        // BigDecimal(double) is the exact binary value, so HALF_EVEN to 15 significant digits
        // reproduces C's %.15g rounding (including carry, e.g. 999999999999999.9 -> 1.0e+15).
        val rounded = BigDecimal(Math.abs(value)).round(REAL_MATH_CONTEXT)
        val unscaled = rounded.unscaledValue().toString()
        val exponent = unscaled.length - 1 - rounded.scale()
        val digits = unscaled.trimEnd('0') // first digit is non-zero, so never empty

        return sign + if (exponent < -4 || exponent >= 15) {
            val mantissa =
                if (digits.length > 1) "${digits[0]}.${digits.substring(1)}" else "${digits[0]}.0"
            val expSign = if (exponent < 0) "-" else "+"
            val expDigits = Math.abs(exponent).toString().padStart(2, '0')
            "${mantissa}e$expSign$expDigits"
        } else if (exponent < 0) {
            "0." + "0".repeat(-exponent - 1) + digits
        } else {
            val intDigitCount = exponent + 1
            if (digits.length <= intDigitCount) {
                digits.padEnd(intDigitCount, '0') + ".0"
            } else {
                digits.take(intDigitCount) + "." + digits.substring(intDigitCount)
            }
        }
    }

    fun formatBlob(bytes: ByteArray): String {
        val builder = StringBuilder("blob(${bytes.size} bytes) 0x")
        for (i in 0 until minOf(bytes.size, 16)) {
            builder.append("%02x".format(bytes[i]))
        }
        return builder.toString()
    }
}
