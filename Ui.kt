package com.example.shoppinglist

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/**
 * هویت بصری: سبز شبانه (زمینه) + طلای شامپاین (تنها رنگ تأکید) + عاج (متن).
 * طلا فقط برای مبلغ، دکمه اصلی و انتخاب فعال به کار می‌رود تا خاص بماند.
 */
object Lux {
    val BG_TOP = 0xFF0F1D19.toInt()
    val BG_BOTTOM = 0xFF070E0C.toInt()
    val SURFACE = 0xFF13241F.toInt()
    val SURFACE_HI = 0xFF193029.toInt()
    val BORDER = 0xFF244039.toInt()
    val GOLD_BORDER = 0x55D6B56A
    val GOLD_LIGHT = 0xFFEFDCA4.toInt()
    val GOLD = 0xFFD6B56A.toInt()
    val GOLD_DARK = 0xFFA8843E.toInt()
    val ON_GOLD = 0xFF1B1608.toInt()
    val IVORY = 0xFFF2EBDA.toInt()
    val MUTED = 0xFF8FA39B.toInt()
    val DANGER = 0xFFD98A82.toInt()
    val GOOD = 0xFF8CCDA6.toInt()
    val TRACK = 0xFF21382F.toInt()
}

class Ui(private val ctx: Context) {
    val regular: Typeface = ResourcesCompat.getFont(ctx, R.font.vazirmatn_regular) ?: Typeface.DEFAULT
    val bold: Typeface = ResourcesCompat.getFont(ctx, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD

    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun round(fill: Int, radius: Int, stroke: Int = 0, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (strokeDp > 0) setStroke(maxOf(1, dp(strokeDp)), stroke)
        }

    fun oval(fill: Int, stroke: Int = 0, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (strokeDp > 0) setStroke(maxOf(1, dp(strokeDp)), stroke)
        }

    fun gold(radius: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Lux.GOLD_LIGHT, Lux.GOLD, Lux.GOLD_DARK)
        ).apply { cornerRadius = dp(radius).toFloat() }

    /** خط باریک طلایی که در دو سر محو می‌شود. */
    fun hairline(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x00D6B56A, 0xAAD6B56A.toInt(), 0x00D6B56A)
        )

    fun background(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Lux.BG_TOP, Lux.BG_BOTTOM)
        )

    fun ripple(content: Drawable, radius: Int, color: Int = 0x33D6B56A): Drawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(color), content, mask)
    }

    fun text(t: String, sp: Float, color: Int, isBold: Boolean = false): TextView =
        TextView(ctx).apply {
            this.text = t
            textSize = sp
            setTextColor(color)
            typeface = if (isBold) bold else regular
        }

    /** دکمه اصلی طلایی. */
    fun goldButton(label: String, onClick: () -> Unit): TextView =
        text(label, 16f, Lux.ON_GOLD, true).apply {
            gravity = Gravity.CENTER
            background = ripple(gold(16), 16, 0x33000000)
            setOnClickListener { onClick() }
        }

    /** دکمه ثانویه با حاشیه نازک. */
    fun ghostButton(label: String, onClick: () -> Unit): TextView =
        text(label, 13f, Lux.IVORY).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(6), 0)
            background = ripple(round(Color.TRANSPARENT, 14, Lux.BORDER, 1), 14)
            setOnClickListener { onClick() }
        }

    fun input(hint: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(ctx).apply {
            this.hint = hint
            setText(value)
            inputType = type
            textSize = 15f
            typeface = regular
            setTextColor(Lux.IVORY)
            setHintTextColor(Lux.MUTED)
            background = round(Lux.SURFACE_HI, 14, Lux.BORDER, 1)
            setPadding(dp(14), dp(11), dp(14), dp(11))
        }
}
