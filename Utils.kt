package com.example.shoppinglist

import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale

/** تبدیل ارقام فارسی و عربی به لاتین. */
fun latinDigits(s: String): String = buildString {
    for (c in s) {
        append(
            when (c) {
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                else -> c
            }
        )
    }
}

/** تعداد: ارقام فارسی و جداکننده‌های اعشار (٫ / , .) پذیرفته می‌شود. null یعنی نامعتبر. */
fun parseQty(s: String): Double? =
    latinDigits(s).trim()
        .replace('\u066B', '.')
        .replace('/', '.')
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it > 0 && it.isFinite() }

/** قیمت: فقط ارقام (جداکننده‌های هزارگان نادیده گرفته می‌شود). */
fun parsePrice(s: String): Long =
    latinDigits(s).filter { it in '0'..'9' }.toLongOrNull() ?: 0L

/** 2.0 -> "2" ، 1.50 -> "1.5" ، بدون نمایش علمی (E7). */
fun fmtQty(q: Double): String = BigDecimal.valueOf(q).stripTrailingZeros().toPlainString()

fun lineTotal(x: Item): Long = Math.round(x.qty * x.price)

/** فیلد CSV طبق RFC 4180. */
fun csv(s: String): String =
    if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"" + s.replace("\"", "\"\"") + "\""
    else s

/** تاریخ شمسی (۱۴۰۵/۰۷/۱۱) از میلی‌ثانیه؛ ارقام همیشه لاتین‌اند. */
fun jalaliDate(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val gy = c.get(Calendar.YEAR)
    val gm = c.get(Calendar.MONTH) + 1
    val gd = c.get(Calendar.DAY_OF_MONTH)
    val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31
        jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30
        jd = 1 + (days - 186) % 30
    }
    return String.format(Locale.US, "%d/%02d/%02d", jy, jm, jd)
}
