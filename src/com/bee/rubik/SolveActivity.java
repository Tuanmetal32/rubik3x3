package com.bee.rubik;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Màn hình hướng dẫn giải từng bước. */
public class SolveActivity extends Activity {
    private int[] labels, faceColor, solution;
    private String[] faceName;
    private int step; // số bước đã thực hiện
    private CubeView cube;
    private TextView tvStep, tvMove, tvDesc, tvSeq, tvHint;
    private Button prev, next;
    private ProgressBar progress;
    private long solveMs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        Intent it = getIntent();
        labels = it.getIntArrayExtra("labels");
        faceColor = it.getIntArrayExtra("faceColor");
        faceName = it.getStringArrayExtra("faceName");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int p = Ui.dp(this, 14);
        root.setPadding(p, p, p, p);

        tvStep = Ui.text(this, "Đang tính toán…", 18, Ui.ACCENT, true);
        root.addView(tvStep, Ui.lp(-1, -2, 0));
        tvMove = Ui.text(this, "", 54, Ui.TEXT, true);
        tvMove.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(tvMove, Ui.lp(-1, -2, 0));
        tvDesc = Ui.text(this, "", 16, Ui.TEXT, false);
        tvDesc.setMinLines(2);
        root.addView(tvDesc, Ui.lp(-1, -2, 0));
        progress = new ProgressBar(this);
        root.addView(progress, Ui.lp(-1, -2, 0));

        cube = new CubeView(this);
        int[] c = new int[54];
        for (int i = 0; i < 54; i++) c[i] = faceColor[labels[i]];
        cube.setColors(c);
        cube.setView(Quat.tilt(-30, 25));
        cube.setTapListener(this::replay);
        root.addView(cube, Ui.lp(-1, 0, 1));

        tvSeq = Ui.text(this, "", 15, Ui.SUB, false);
        tvSeq.setTypeface(Typeface.MONOSPACE);
        tvSeq.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        root.addView(tvSeq, Ui.lp(-1, -2, 0));
        tvHint = Ui.text(this, "Luôn giữ rubik: tâm " + faceName[2] + " hướng về bạn, tâm " + faceName[0]
                + " ở trên.  (Chạm hình 3D để xem lại, kéo để xoay)", 13, Ui.SUB, false);
        tvHint.setPadding(0, 0, 0, Ui.dp(this, 8));
        root.addView(tvHint, Ui.lp(-1, -2, 0));

        LinearLayout row = new LinearLayout(this);
        prev = Ui.button(this, "◀ Return", false);
        next = Ui.button(this, "Next step ▶", true);
        LinearLayout.LayoutParams l1 = Ui.lp(0, -2, 1), l2 = Ui.lp(0, -2, 1.4f);
        l2.leftMargin = Ui.dp(this, 10);
        row.addView(prev, l1);
        row.addView(next, l2);
        root.addView(row, Ui.lp(-1, -2, 0));
        setContentView(root);

        prev.setOnClickListener(v -> back());
        next.setOnClickListener(v -> forward());
        Ui.enable(prev, false);
        Ui.enable(next, false);
        tvDesc.setText("Đang tính toán lời giải ngắn nhất…");

        new Thread(() -> {
            int[] sol = null;
            long t0 = System.currentTimeMillis();
            try {
                sol = Solver.solve(Cubie.fromFacelets(labels), 4000);
            } catch (CubeError ignored) {}
            final int[] s = sol;
            solveMs = System.currentTimeMillis() - t0;
            runOnUiThread(() -> onSolved(s));
        }, "solver").start();
    }

    private void onSolved(int[] s) {
        progress.setVisibility(View.GONE);
        if (s == null) {
            tvStep.setText("Không tìm được lời giải");
            tvDesc.setText("Hãy quét lại rubik.");
            next.setText("Quét lại");
            Ui.enable(next, true);
            solution = new int[0];
            step = -1;
            return;
        }
        solution = s;
        step = 0;
        update();
    }

    private void forward() {
        if (step < 0) { startActivity(new Intent(this, ScanActivity.class)); finish(); return; }
        if (step >= solution.length) { startActivity(new Intent(this, ScanActivity.class)); finish(); return; }
        cube.finishMove();
        int m = solution[step];
        step++;
        cube.setArrows(new CubeView.Arrow[]{CubeView.Arrow.forMove(m)}, null);
        cube.animateMove(m, null);
        update();
    }

    private void back() {
        if (step <= 0) return;
        cube.finishMove();
        step--;
        int m = solution[step];
        cube.setArrows(step > 0 ? new CubeView.Arrow[]{CubeView.Arrow.forMove(solution[step - 1])} : null, null);
        cube.animateMove(CubeModel.inverse(m), null);
        update();
    }

    private void replay() {
        if (solution == null || step <= 0 || cube.isAnimating()) return;
        int m = solution[step - 1];
        cube.applyMove(CubeModel.inverse(m));
        cube.animateMove(m, null);
    }

    private void update() {
        int n = solution.length;
        Ui.enable(prev, step > 0);
        Ui.enable(next, true);
        if (n == 0) {
            tvStep.setText("🎉 Rubik đã được giải sẵn!");
            tvMove.setText("✔");
            tvDesc.setText("Không cần xoay bước nào.");
            next.setText("Quét rubik khác");
            return;
        }
        if (step == 0) {
            tvStep.setText("Lời giải: " + n + " bước  ·  tính trong " + String.format("%.1f", solveMs / 1000.0) + " giây");
            tvMove.setText("▶");
            Ui.pop(tvMove);
            cube.setArrows(null, null);
            tvDesc.setText("Cầm rubik: tâm " + faceName[2] + " hướng về bạn, tâm " + faceName[0]
                    + " ở trên (giống lúc chụp mặt đầu tiên). Bấm Next step để bắt đầu.");
            next.setText("Next step ▶");
        } else {
            int m = solution[step - 1];
            tvStep.setText("Bước " + step + " / " + n);
            tvMove.setText(CubeModel.MOVE_NAMES[m]);
            Ui.pop(tvMove);
            String d = Texts.moveDesc(m, faceName);
            if (step == n) d += "\n🎉 Hoàn thành! Rubik đã được giải.";
            tvDesc.setText(d);
            next.setText(step == n ? "Quét rubik khác" : "Next step ▶");
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < n; i++) {
            int st = sb.length();
            sb.append(CubeModel.MOVE_NAMES[solution[i]]);
            int color = i < step - 1 ? 0xFF6E6858 : (i == step - 1 ? Ui.ACCENT : Ui.TEXT);
            sb.setSpan(new ForegroundColorSpan(color), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (i == step - 1) sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append("  ");
        }
        tvSeq.setText(sb);
    }
}
