package com.contoh.doorbell

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button

fun Context.dp(nilai: Int): Int = (nilai * resources.displayMetrics.density).toInt()

/** Animasi kecil saat ditekan: mengecil sedikit lalu memantul kembali. */
fun efekTekan(view: View) {
    view.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate()
                .scaleX(0.94f).scaleY(0.94f)
                .setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(260)
                .setInterpolator(OvershootInterpolator(2f)).start()
        }
        false
    }
}

/** Tombol gradien dengan ikon, efek ripple, dan animasi tekan. */
fun gayaTombol(tombol: Button, warnaA: Int, warnaB: Int, ikonRes: Int) {
    val latar = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(warnaA, warnaB)
    ).apply {
        cornerRadius = tombol.context.dp(28).toFloat()
    }

    tombol.background = RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), latar, null)
    tombol.stateListAnimator = null
    tombol.setTextColor(Color.WHITE)
    tombol.isAllCaps = false
    tombol.setTypeface(null, Typeface.BOLD)

    val ikon = tombol.context.getDrawable(ikonRes)?.mutate()
    ikon?.setTint(Color.WHITE)
    tombol.setCompoundDrawablesRelativeWithIntrinsicBounds(ikon, null, null, null)

    efekTekan(tombol)
}
