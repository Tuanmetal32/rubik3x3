package com.bee.rubik;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Màn hình khởi động. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        new Thread(Solver::init, "solver-init").start(); // chuẩn bị bảng tra trước

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, "🐝 Bee_Rubik", 38, Ui.ACCENT, true);
        root.addView(title, Ui.lp(-1, -2, 0));
        TextView sub = Ui.text(this, "Giải rubik 3x3x3", 16, Ui.SUB, false);
        root.addView(sub, Ui.lp(-1, -2, 0));

        CubeView cube = new CubeView(this);
        int[] c = new int[54];
        int[] faceCol = {ColorUtil.WHITE, ColorUtil.RED, ColorUtil.GREEN, ColorUtil.YELLOW, ColorUtil.ORANGE, ColorUtil.BLUE};
        for (int i = 0; i < 54; i++) c[i] = ColorUtil.PALETTE[faceCol[i / 9]];
        cube.setColors(c);
        cube.setAutoSpin(true);
        root.addView(cube, Ui.lp(-1, 0, 1));

        TextView welcome = Ui.text(this, "Chào mừng đến với ứng dụng Bee_Rubik!", 22, Ui.TEXT, true);
        root.addView(welcome, Ui.lp(-1, -2, 0));
        TextView hint = Ui.text(this, "Bấm nút Chụp để scan rubik của bạn", 16, Ui.SUB, false);
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 20));
        root.addView(hint, Ui.lp(-1, -2, 0));

        Button shot = Ui.button(this, "📷  Chụp", true);
        shot.setTextSize(20);
        shot.setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
        root.addView(shot, Ui.lp(-1, Ui.dp(this, 60), 0));
        setContentView(root);
    }
}
