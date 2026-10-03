package com.example.shoppinglist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_LIST = "خرید امروز"

data class Item(
    var name: String,
    var qty: Double,
    var unit: String,
    var price: Long,
    var category: String,
    var note: String = "",
    var done: Boolean = false,
    var list: String = ""
)

class PricePoint(val time: Long, val price: Long)

/**
 * ذخیره‌سازی در همان SharedPreferences نسخه ۳ تا داده‌های قبلی حفظ شوند.
 * در نسخه ۳ نام لیست به‌صورت پیشوند "[نام لیست] " در note بود؛ اینجا هنگام بارگذاری
 * به فیلد جدید `list` منتقل می‌شود.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("shopping_v3", Context.MODE_PRIVATE)

    val lists = mutableListOf<String>()
    val items = mutableListOf<Item>()

    /** تعداد دفعات افزودن هر کالا؛ مستقل از پاک‌سازی خریدهای انجام‌شده. */
    val freq = LinkedHashMap<String, Int>()
    var current: String = DEFAULT_LIST

    /** تاریخچه قیمت هر کالا (حداکثر ۱۲ ثبت آخر)، از قدیم به جدید. */
    val prices = LinkedHashMap<String, MutableList<PricePoint>>()

    init {
        load()
    }

    fun bump(name: String) {
        if (name.isNotBlank()) freq[name] = (freq[name] ?: 0) + 1
    }

    /** فقط وقتی قیمت نسبت به آخرین ثبت عوض شده باشد ثبت می‌کند. */
    fun recordPrice(name: String, price: Long) {
        if (name.isBlank() || price <= 0) return
        val list = prices.getOrPut(name) { mutableListOf() }
        if (list.lastOrNull()?.price == price) return
        list.add(PricePoint(System.currentTimeMillis(), price))
        while (list.size > 12) list.removeAt(0)
    }

    /** (قیمت قبلی، قیمت فعلی) یا null اگر هنوز دو ثبت مختلف نداریم. */
    fun lastChange(name: String): Pair<Long, Long>? {
        val l = prices[name] ?: return null
        return if (l.size >= 2) l[l.size - 2].price to l[l.size - 1].price else null
    }

    fun save() {
        val itemsJson = JSONArray()
        items.forEach { x ->
            itemsJson.put(JSONObject().apply {
                put("name", x.name)
                put("qty", x.qty)
                put("unit", x.unit)
                put("price", x.price)
                put("category", x.category)
                put("note", x.note)
                put("done", x.done)
                put("list", x.list)
            })
        }
        val freqJson = JSONObject()
        freq.forEach { (k, v) -> freqJson.put(k, v) }

        val pricesJson = JSONObject()
        prices.forEach { (k, l) ->
            val arr = JSONArray()
            l.forEach { arr.put(JSONArray().put(it.time).put(it.price)) }
            pricesJson.put(k, arr)
        }

        prefs.edit()
            .putString("prices", pricesJson.toString())
            .putString("lists", JSONArray(lists).toString())
            .putString("current", current)
            .putString("items", itemsJson.toString())
            .putString("freq", freqJson.toString())
            .apply()
    }

    private fun load() {
        try {
            prefs.getString("lists", null)?.let {
                val a = JSONArray(it)
                for (i in 0 until a.length()) lists.add(a.getString(i))
            }
        } catch (e: Exception) {
        }
        if (lists.isEmpty()) lists.add(DEFAULT_LIST)

        try {
            prefs.getString("items", null)?.let { s ->
                val a = JSONArray(s)
                for (i in 0 until a.length()) {
                    try {
                        val o = a.getJSONObject(i)
                        var note = o.optString("note")
                        var list = o.optString("list", "")
                        if (list.isEmpty()) {
                            // مهاجرت از قالب نسخه ۳
                            val owner = lists.firstOrNull { note.startsWith("[$it]") }
                            if (owner != null) {
                                list = owner
                                note = note.removePrefix("[$owner]").trim()
                            } else {
                                list = lists.first()
                            }
                        }
                        if (list !in lists) lists.add(list)
                        items.add(
                            Item(
                                o.getString("name"),
                                o.optDouble("qty", 1.0),
                                o.optString("unit", "عدد"),
                                o.optLong("price", 0),
                                o.optString("category", "سایر"),
                                note,
                                o.optBoolean("done"),
                                list
                            )
                        )
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
        }

        current = prefs.getString("current", null)?.takeIf { it in lists } ?: lists.first()

        val fs = prefs.getString("freq", null)
        if (fs != null) {
            try {
                val o = JSONObject(fs)
                o.keys().forEach { k -> freq[k] = o.getInt(k) }
            } catch (e: Exception) {
            }
        } else {
            items.forEach { bump(it.name) }
        }

        val ps = prefs.getString("prices", null)
        if (ps != null) {
            try {
                val o = JSONObject(ps)
                o.keys().forEach { k ->
                    val arr = o.getJSONArray(k)
                    val l = mutableListOf<PricePoint>()
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONArray(i)
                        l.add(PricePoint(p.getLong(0), p.getLong(1)))
                    }
                    prices[k] = l
                }
            } catch (e: Exception) {
            }
        } else {
            // اولین اجرا پس از به‌روزرسانی: قیمت فعلی کالاها نقطه شروع تاریخچه می‌شود
            items.forEach { recordPrice(it.name, it.price) }
        }
    }
}
