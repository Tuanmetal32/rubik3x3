package com.bee.rubik;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiện ích giao diện (chủ đề vàng-đen của chú ong). */
final class Ui {
    static final int BG = 0xFF14120E, PANEL = 0xFF221F18, ACCENT = 0xFFFFC107, TEXT = 0xFFFFFFFF, SUB = 0xFFC9C1AA;

    private Ui() {}

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Button button(Context c, String text, boolean primary) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? 0xFF15120A : TEXT);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(c, primary ? 0xFFD99E00 : 0xFF57503F, 14));
        s.addState(new int[]{}, round(c, primary ? ACCENT : 0xFF3B362A, 14));
        b.setBackground(s);
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        b.setMinHeight(dp(c, 50));
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(110)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
                    break;
            }
            return false; // vẫn để Button xử lý click
        });
        return b;
    }

    static void enable(Button b, boolean on) {
        b.setEnabled(on);
        b.animate().alpha(on ? 1f : 0.38f).setDuration(220).start();
    }

    /** Hiệu ứng "bật" nhẹ khi nội dung thay đổi. */
    static void pop(android.view.View v) {
        v.animate().cancel();
        v.setScaleX(0.6f); v.setScaleY(0.6f); v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }
}
