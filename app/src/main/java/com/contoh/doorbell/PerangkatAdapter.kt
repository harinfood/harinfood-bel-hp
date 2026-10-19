package com.contoh.doorbell

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class PerangkatAdapter(
    private val context: Context,
    private val onPilih: (Perangkat) -> Unit
) : BaseAdapter() {

    private var data: List<Perangkat> = emptyList()

    fun setData(baru: List<Perangkat>) {
        data = baru
        notifyDataSetChanged()
    }

    override fun getCount(): Int = data.size

    override fun getItem(position: Int): Any = data[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val ctx = parent?.context ?: context
        val p = data[position]

        val baris = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(12))
            background = RippleDrawable(ColorStateList.valueOf(0x33EC407A), null, null)
        }

        val ikon = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_bluetooth)
            imageTintList = ColorStateList.valueOf(
                if (p.adalahItag) Color.parseColor("#EC407A") else Color.parseColor("#78909C")
            )
            layoutParams = LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(36))
        }

        val kolom = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ctx.dp(12)
            }
        }
        kolom.addView(TextView(ctx).apply {
            text = p.nama
            textSize = 16f
            setTextColor(Color.parseColor("#263238"))
            setTypeface(null, if (p.adalahItag) Typeface.BOLD else Typeface.NORMAL)
        })
        kolom.addView(TextView(ctx).apply {
            text = "${p.mac} • sinyal ${p.sinyal} dBm"
            textSize = 12f
            setTextColor(Color.parseColor("#78909C"))
        })

        baris.addView(ikon)
        baris.addView(kolom)

        if (p.adalahItag) {
            baris.addView(TextView(ctx).apply {
                text = "iTag"
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = ctx.dp(12).toFloat()
                    setColor(Color.parseColor("#EC407A"))
                }
            })
        }

        baris.setOnClickListener { onPilih(p) }
        efekTekan(baris)
        return baris
    }
}
