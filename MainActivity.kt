package com.example.shoppinglist

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class Form(
    val view: View,
    val name: EditText,
    val qty: EditText,
    val unit: EditText,
    val price: EditText,
    val category: EditText,
    val note: EditText
)

class MainActivity : AppCompatActivity() {

    private lateinit var ui: Ui
    private lateinit var store: Store
    private lateinit var rootView: LinearLayout
    private lateinit var box: LinearLayout
    private lateinit var chips: LinearLayout
    private lateinit var search: EditText
    private lateinit var sumAmount: TextView
    private lateinit var sumLeft: TextView
    private lateinit var sumDone: TextView
    private lateinit var barFill: View
    private lateinit var barRest: View

    private val nf = NumberFormat.getNumberInstance(Locale.US)
    private var pendingCsv = ""

    private val csvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(pendingCsv.toByteArray(Charsets.UTF_8))
                    }
                    toast("فایل ذخیره شد")
                } catch (e: Exception) {
                    toast("ذخیره فایل ناموفق بود")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        store = Store(this)
        buildUi()
        renderChips()
        render()
    }

    // ---------- کمکی‌ها ----------

    private fun dp(v: Int) = ui.dp(v)

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun currentItems() = store.items.filter { it.list == store.current }

    private fun dialog() = AlertDialog.Builder(this, R.style.LuxDialog)

    // ---------- رابط کاربری ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = ui.background()
        }
        rootView = root
        val base = dp(18)
        root.setPadding(base, dp(10), base, dp(10))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(base + bars.left, dp(10) + bars.top, base + bars.right, dp(10) + bars.bottom)
            insets
        }

        // عنوان و خط طلایی
        root.addView(ui.text("لیست خرید من", 26f, Lux.IVORY, true).apply {
            setPadding(dp(2), dp(6), 0, dp(8))
        })
        root.addView(
            View(this).apply { background = ui.hairline() },
            lp(MATCH, dp(1)).apply { bottomMargin = dp(12) }
        )

        // انتخاب لیست
        chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(chips)
        }
        val menuBtn = ui.text("⋮", 20f, Lux.IVORY, true).apply {
            gravity = Gravity.CENTER
            background = ui.ripple(ui.round(Color.TRANSPARENT, 22), 22)
            setOnClickListener { showListMenu(it) }
        }
        val listRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listRow.addView(chipScroll, lp(0, dp(44), 1f))
        listRow.addView(menuBtn, lp(dp(44), dp(44)))
        root.addView(listRow, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })

        // کارت جمع کل
        sumAmount = ui.text("0", 30f, Lux.GOLD, true)
        sumLeft = ui.text("", 13f, Lux.IVORY)
        sumDone = ui.text("", 13f, Lux.MUTED)
        barFill = View(this).apply { background = ui.gold(3) }
        barRest = View(this)

        val amountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(sumAmount)
            addView(ui.text("تومان", 13f, Lux.MUTED).apply { setPadding(dp(8), 0, 0, dp(6)) })
        }
        val amountCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text("مجموع برآورد", 12f, Lux.MUTED))
            addView(amountRow)
        }
        val counts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(sumLeft)
            addView(sumDone)
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(amountCol, lp(0, WRAP, 1f))
            addView(counts, lp(WRAP, WRAP))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = ui.round(Lux.TRACK, 3)
            addView(barFill, lp(0, MATCH, 0f))
            addView(barRest, lp(0, MATCH, 0f))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.round(Lux.SURFACE, 22, Lux.GOLD_BORDER, 1)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            addView(topRow)
            addView(bar, lp(MATCH, dp(6)).apply { topMargin = dp(14) })
        }
        root.addView(card, lp(MATCH, WRAP).apply { bottomMargin = dp(12) })

        // جستجو
        search = ui.input("جستجو در کالاها…", "").apply {
            maxLines = 1
            doAfterTextChanged { render() }
        }
        root.addView(search, lp(MATCH, WRAP).apply { bottomMargin = dp(12) })

        // فهرست کالاها
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(box)
            },
            lp(MATCH, 0, 1f)
        )

        // دکمه‌ها
        root.addView(
            ui.goldButton("افزودن کالا") { addItem() },
            lp(MATCH, dp(54)).apply { topMargin = dp(10) }
        )
        val ghosts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun ghost(label: String, onClick: () -> Unit) {
            ghosts.addView(
                ui.ghostButton(label, onClick),
                lp(0, dp(44), 1f).apply { marginStart = dp(4); marginEnd = dp(4) }
            )
        }
        ghost("پاک‌سازی خریدها") { clearDone() }
        ghost("خروجی CSV") { exportCsv() }
        ghost("پرتکرارها") { frequent() }
        root.addView(ghosts, lp(MATCH, WRAP).apply { topMargin = dp(10) })

        setContentView(root)
    }

    private fun renderChips() {
        chips.removeAllViews()
        var selected: View? = null
        store.lists.forEach { name ->
            val on = name == store.current
            val chip = ui.text(name, 14f, if (on) Lux.ON_GOLD else Lux.IVORY, on).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), 0, dp(18), 0)
                background = if (on) ui.ripple(ui.gold(20), 20, 0x33000000)
                else ui.ripple(ui.round(Lux.SURFACE, 20, Lux.BORDER, 1), 20)
                setOnClickListener {
                    if (!on) {
                        store.current = name
                        store.save()
                        renderChips()
                        render()
                    }
                }
            }
            if (on) selected = chip
            chips.addView(chip, lp(WRAP, dp(38)).apply { marginEnd = dp(8) })
        }
        chips.addView(
            ui.text("لیست جدید", 13f, Lux.GOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                background = ui.ripple(ui.round(Color.TRANSPARENT, 20, Lux.GOLD_BORDER, 1), 20)
                setOnClickListener { newList() }
            },
            lp(WRAP, dp(38))
        )
        selected?.let { c -> c.post { c.requestRectangleOnScreen(Rect(0, 0, c.width, c.height), true) } }
    }

    private fun visible(): List<Item> {
        val q = search.text.toString().trim()
        return currentItems()
            .filter { q.isEmpty() || it.name.contains(q, true) || it.category.contains(q, true) }
            .sortedBy { it.done }
    }

    private fun render() {
        if (!::box.isInitialized || !::sumAmount.isInitialized) return
        box.removeAllViews()
        val shown = visible()
        if (shown.isEmpty()) {
            box.addView(emptyState())
        } else {
            fun card(x: Item) = box.addView(itemCard(x), lp(MATCH, WRAP).apply { bottomMargin = dp(10) })
            // کالاهای باقی‌مانده بر اساس دسته (مثل راهروهای فروشگاه)، خریدشده‌ها در انتها
            shown.filter { !it.done }.groupBy { it.category }.forEach { (cat, group) ->
                box.addView(sectionHeader(cat, group.size, Lux.GOLD))
                group.forEach { card(it) }
            }
            val bought = shown.filter { it.done }
            if (bought.isNotEmpty()) {
                box.addView(sectionHeader("خریداری‌شده", bought.size, Lux.MUTED))
                bought.forEach { card(it) }
            }
        }

        // جمع‌ها روی کل لیست فعلی محاسبه می‌شود، نه فقط نتیجه جستجو
        val all = currentItems()
        val left = all.count { !it.done }
        val done = all.size - left
        val total = all.filter { !it.done }.sumOf { lineTotal(it) }
        sumAmount.text = nf.format(total)
        sumLeft.text = "باقی‌مانده: $left"
        sumDone.text = "خریداری‌شده: $done"
        (barFill.layoutParams as LinearLayout.LayoutParams).weight = done.toFloat()
        (barRest.layoutParams as LinearLayout.LayoutParams).weight = left.toFloat()
        barFill.requestLayout()
        barRest.requestLayout()
    }

    private fun sectionHeader(title: String, n: Int, color: Int): View =
        ui.text("$title  ($n)", 12f, color, true).apply { setPadding(dp(4), dp(10), dp(4), dp(8)) }

    private fun emptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(48), 0, dp(24))
        addView(ui.text("◆", 20f, Lux.GOLD).apply { gravity = Gravity.CENTER })
        val msg = if (search.text.isNullOrBlank())
            "لیست خالی است.\nبا دکمه «افزودن کالا» شروع کنید."
        else
            "کالایی با این نام پیدا نشد."
        addView(ui.text(msg, 14f, Lux.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
    }

    private fun itemCard(x: Item): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(10), dp(6), dp(10))
            background = ui.ripple(
                ui.round(Lux.SURFACE, 18, if (x.done) Lux.BORDER else Lux.GOLD_BORDER, 1), 18
            )
            alpha = if (x.done) 0.6f else 1f
            setOnClickListener { editItem(x) }
        }

        // دایره انتخاب
        val dot = ui.text(if (x.done) "✓" else "", 14f, Lux.ON_GOLD, true).apply {
            gravity = Gravity.CENTER
            background = if (x.done) ui.gold(0).apply { shape = GradientDrawable.OVAL }
            else ui.oval(Color.TRANSPARENT, Lux.GOLD, 2)
        }
        val check = FrameLayout(this).apply {
            addView(dot, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                x.done = !x.done
                store.save()
                render()
            }
        }
        card.addView(check, lp(dp(46), dp(46)))

        // نام، تعداد، دسته
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(ui.text(x.name, 16f, if (x.done) Lux.MUTED else Lux.IVORY, true).apply {
            if (x.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        })
        col.addView(ui.text("${fmtQty(x.qty)} ${x.unit} × ${nf.format(x.price)}", 12.5f, Lux.MUTED).apply {
            setPadding(0, dp(2), 0, 0)
        })
        if (!x.done) {
            store.lastChange(x.name)?.let { (prev, now) ->
                val pct = Math.round((now - prev) * 100.0 / prev)
                if (pct != 0L) {
                    val up = pct > 0
                    col.addView(
                        ui.text(
                            if (up) "▲ ${pct}% گران‌تر از ثبت قبلی" else "▼ ${-pct}% ارزان‌تر از ثبت قبلی",
                            11.5f, if (up) Lux.DANGER else Lux.GOOD
                        ),
                        lp(WRAP, WRAP).apply { topMargin = dp(4) }
                    )
                }
            }
        }
        if (x.done) { // برای کالاهای فعال، دسته در عنوان بخش نشان داده می‌شود
            col.addView(
                ui.text(x.category, 11f, Lux.MUTED).apply {
                    setPadding(dp(10), dp(2), dp(10), dp(3))
                    background = ui.round(Color.TRANSPARENT, 10, Lux.BORDER, 1)
                },
                lp(WRAP, WRAP).apply { topMargin = dp(8) }
            )
        }
        if (x.note.isNotBlank()) {
            col.addView(ui.text(x.note, 12f, Lux.MUTED), lp(WRAP, WRAP).apply { topMargin = dp(6) })
        }
        card.addView(col, lp(0, WRAP, 1f))

        // مبلغ ردیف
        val amt = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(dp(8), 0, dp(4), 0)
            addView(ui.text(nf.format(lineTotal(x)), 16f, if (x.done) Lux.MUTED else Lux.GOLD, true))
            addView(ui.text("تومان", 10.5f, Lux.MUTED))
        }
        card.addView(amt, lp(WRAP, WRAP))

        // حذف
        card.addView(
            ui.text("✕", 15f, Lux.DANGER).apply {
                gravity = Gravity.CENTER
                background = ui.ripple(ui.round(Color.TRANSPARENT, 20), 20, 0x33D98A82)
                setOnClickListener { deleteItem(x) }
            },
            lp(dp(40), dp(40))
        )
        return card
    }

    private fun deleteItem(x: Item) {
        val idx = store.items.indexOfFirst { it === x }
        if (idx < 0) return
        store.items.removeAt(idx)
        store.save()
        render()
        Snackbar.make(rootView, "«${x.name}» حذف شد", Snackbar.LENGTH_LONG)
            .setBackgroundTint(Lux.SURFACE_HI)
            .setTextColor(Lux.IVORY)
            .setActionTextColor(Lux.GOLD)
            .setAction("بازگردانی") {
                store.items.add(idx.coerceAtMost(store.items.size), x)
                store.save()
                render()
            }
            .show()
    }

    // ---------- فرم کالا ----------

    private fun form(old: Item?): Form {
        val name = ui.input("نام کالا", old?.name ?: "")
        val qty = ui.input(
            "تعداد", old?.let { fmtQty(it.qty) } ?: "1",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val unit = ui.input("واحد", old?.unit ?: "عدد")
        val price = ui.input("قیمت واحد (تومان)", old?.price?.toString() ?: "0", InputType.TYPE_CLASS_NUMBER)
        val category = ui.input("دسته‌بندی", old?.category ?: "خوراکی")
        val note = ui.input("یادداشت", old?.note ?: "")

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(22), dp(4), dp(22), dp(4))
        }
        fun field(label: String, input: EditText) {
            col.addView(ui.text(label, 12f, Lux.MUTED), lp(WRAP, WRAP).apply { topMargin = dp(12) })
            col.addView(input, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        }
        field("نام کالا", name)
        field("تعداد", qty)
        field("واحد", unit)
        field("قیمت واحد (تومان)", price)
        old?.let { o ->
            val h = store.prices[o.name]?.takeLast(5)?.reversed()
            if (!h.isNullOrEmpty()) {
                val lines = h.joinToString("\n") { "${jalaliDate(it.time)}   ${nf.format(it.price)}" }
                col.addView(
                    ui.text("قیمت‌های ثبت‌شده (جدید به قدیم)\n$lines", 11.5f, Lux.MUTED),
                    lp(WRAP, WRAP).apply { topMargin = dp(6) }
                )
            }
        }
        field("دسته‌بندی", category)
        field("یادداشت", note)

        return Form(ScrollView(this).apply { addView(col) }, name, qty, unit, price, category, note)
    }

    private fun itemDialog(title: String, ok: String, old: Item?, onOk: (Item) -> Unit) {
        val f = form(old)
        val d = dialog()
            .setTitle(title)
            .setView(f.view)
            .setNegativeButton("انصراف", null)
            .setPositiveButton(ok, null)
            .create()
        d.setOnShowListener {
            // با ورودی نامعتبر، دیالوگ بسته نمی‌شود
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = f.name.text.toString().trim()
                if (name.isEmpty()) {
                    f.name.error = "نام کالا را وارد کنید"
                    return@setOnClickListener
                }
                val qtyText = f.qty.text.toString().trim()
                val qty = if (qtyText.isEmpty()) 1.0 else parseQty(qtyText)
                if (qty == null) {
                    f.qty.error = "عدد معتبر وارد کنید"
                    return@setOnClickListener
                }
                onOk(
                    Item(
                        name,
                        qty,
                        f.unit.text.toString().trim().ifBlank { "عدد" },
                        parsePrice(f.price.text.toString()),
                        f.category.text.toString().trim().ifBlank { "سایر" },
                        f.note.text.toString().trim(),
                        old?.done ?: false,
                        store.current
                    )
                )
                d.dismiss()
            }
        }
        d.show()
    }

    private fun addItem() = itemDialog("افزودن کالا", "افزودن", null) { n ->
        store.items.add(n)
        store.bump(n.name)
        store.recordPrice(n.name, n.price)
        store.save()
        render()
    }

    private fun editItem(x: Item) = itemDialog("ویرایش کالا", "ذخیره", x) { n ->
        x.name = n.name
        x.qty = n.qty
        x.unit = n.unit
        x.price = n.price
        x.category = n.category
        x.note = n.note
        store.recordPrice(n.name, n.price)
        store.save()
        render()
    }

    private fun clearDone() {
        val n = currentItems().count { it.done }
        if (n == 0) {
            toast("کالای خریداری‌شده‌ای وجود ندارد")
            return
        }
        store.items.removeAll { it.list == store.current && it.done }
        store.save()
        render()
        toast("$n کالا حذف شد")
    }

    // ---------- لیست‌ها ----------

    private fun askName(title: String, ok: String, initial: String, onOk: (String) -> Unit) {
        val e = ui.input("نام لیست", initial).apply { setSelection(text.length) }
        val wrap = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(e)
        }
        dialog()
            .setTitle(title)
            .setView(wrap)
            .setNegativeButton("انصراف", null)
            .setPositiveButton(ok) { _, _ -> onOk(e.text.toString().trim()) }
            .show()
    }

    private fun newList() = askName("ساخت لیست جدید", "ساخت", "") { n ->
        when {
            n.isEmpty() -> {}
            n in store.lists -> toast("این نام قبلاً وجود دارد")
            else -> {
                store.lists.add(n)
                store.current = n
                store.save()
                renderChips()
                render()
            }
        }
    }

    private fun showListMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 3, 0, "اشتراک‌گذاری لیست")
            menu.add(0, 4, 1, "بازنشانی خریدها")
            menu.add(0, 1, 2, "تغییر نام لیست")
            menu.add(0, 2, 3, "حذف لیست")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> renameList()
                    2 -> deleteList()
                    3 -> shareList()
                    4 -> resetDone()
                }
                true
            }
        }.show()
    }

    /** متن لیست را برای ارسال در پیام‌رسان‌ها (تلگرام، واتس‌اپ، ایتا و...) می‌فرستد. */
    private fun shareList() {
        val all = currentItems()
        if (all.isEmpty()) {
            toast("این لیست خالی است")
            return
        }
        val sb = StringBuilder("🛒 ${store.current}\n\n")
        all.sortedBy { it.done }.forEach { x ->
            sb.append(if (x.done) "✅ " else "⬜ ")
                .append(x.name).append(" — ").append(fmtQty(x.qty)).append(' ').append(x.unit)
            if (x.note.isNotBlank()) sb.append(" (").append(x.note).append(')')
            sb.append('\n')
        }
        val total = all.filter { !it.done }.sumOf { lineTotal(it) }
        if (total > 0) sb.append("\nمجموع برآورد: ${nf.format(total)} تومان")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
        }
        startActivity(Intent.createChooser(send, "اشتراک‌گذاری لیست"))
    }

    /** برای لیست‌های تکراری (مثلاً خرید هفتگی): همه کالاها دوباره «خریداری‌نشده» می‌شوند. */
    private fun resetDone() {
        val doneItems = currentItems().filter { it.done }
        if (doneItems.isEmpty()) {
            toast("کالای خریداری‌شده‌ای وجود ندارد")
            return
        }
        doneItems.forEach { it.done = false }
        store.save()
        render()
        toast("${doneItems.size} کالا دوباره به لیست برگشت")
    }

    private fun renameList() {
        val old = store.current
        askName("تغییر نام لیست", "ذخیره", old) { n ->
            when {
                n.isEmpty() || n == old -> {}
                n in store.lists -> toast("این نام قبلاً وجود دارد")
                else -> {
                    store.lists[store.lists.indexOf(old)] = n
                    store.items.filter { it.list == old }.forEach { it.list = n }
                    store.current = n
                    store.save()
                    renderChips()
                    render()
                }
            }
        }
    }

    private fun deleteList() {
        if (store.lists.size <= 1) {
            toast("حداقل یک لیست باید باقی بماند")
            return
        }
        val name = store.current
        val count = currentItems().size
        dialog()
            .setTitle("حذف لیست")
            .setMessage("لیست «$name» و $count کالای آن حذف شود؟")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("حذف") { _, _ ->
                store.items.removeAll { it.list == name }
                store.lists.remove(name)
                store.current = store.lists.first()
                store.save()
                renderChips()
                render()
            }
            .show()
    }

    // ---------- کالاهای پرتکرار ----------

    private fun frequent() {
        val top = store.freq.entries.sortedByDescending { it.value }.take(15)
        if (top.isEmpty()) {
            toast("هنوز کالایی ثبت نشده")
            return
        }
        dialog()
            .setTitle("کالاهای پرتکرار")
            .setItems(top.map { "${it.key}  (${it.value} بار)" }.toTypedArray()) { _, which ->
                addFrequent(top[which].key)
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun addFrequent(name: String) {
        if (currentItems().any { it.name == name && !it.done }) {
            toast("«$name» از قبل در این لیست هست")
            return
        }
        // واحد، قیمت و دسته را از آخرین ثبت همین کالا برمی‌دارد
        val last = store.items.lastOrNull { it.name == name }
        store.items.add(
            Item(
                name, 1.0,
                last?.unit ?: "عدد",
                last?.price ?: 0L,
                last?.category ?: "سایر",
                "", false, store.current
            )
        )
        store.bump(name)
        store.save()
        render()
    }

    // ---------- خروجی CSV ----------

    private fun exportCsv() {
        val list = currentItems() // کل لیست، مستقل از جستجو
        if (list.isEmpty()) {
            toast("این لیست خالی است")
            return
        }
        val sb = StringBuilder("\uFEFF")
        sb.append("نام کالا,تعداد,واحد,قیمت واحد,مبلغ,دسته بندی,یادداشت,خریداری شده\r\n")
        list.forEach { x ->
            sb.append(
                listOf(
                    x.name, fmtQty(x.qty), x.unit, x.price.toString(),
                    lineTotal(x).toString(), x.category, x.note, if (x.done) "بله" else "خیر"
                ).joinToString(",") { csv(it) }
            ).append("\r\n")
        }
        pendingCsv = sb.toString()
        val safe = store.current.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        csvLauncher.launch("shopping_$safe.csv")
    }
}
