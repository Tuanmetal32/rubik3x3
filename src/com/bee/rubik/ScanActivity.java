package com.bee.rubik;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/** Màn hình quét: nửa trên camera + lưới 3x3, nửa dưới rubik 3D. */
@SuppressWarnings("deprecation")
public class ScanActivity extends Activity implements TextureView.SurfaceTextureListener, Camera.PreviewCallback {
    /** Thứ tự quét: F, R, B, L, U, D (chỉ số mặt Kociemba). */
    static final int[] SCAN_FACE = {2, 1, 5, 4, 0, 3};
    static final int[][] FRONT = {{0, 0, 1}, {1, 0, 0}, {0, 0, -1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
    static final int[][] UP = {{0, 1, 0}, {0, 1, 0}, {0, 1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    static final float[] TILT = Quat.tilt(-24, 20);
    static final int GREY = 0xFF4A463D;
    // Xoay cả khối sang trái (mặt phải ra trước): quay âm quanh trục y, vòng cung phía trước khối.
    static final CubeView.Arrow ARROW_LEFT = CubeView.Arrow.arc(new float[]{0, 1, 0}, new float[]{0, -0.2f, 0},
            new float[]{1, 0, 0}, 2.25f, -25, -130, null);
    static final CubeView.Arrow ARROW_LEFT_1 = CubeView.Arrow.arc(new float[]{0, 1, 0}, new float[]{0, -0.6f, 0},
            new float[]{1, 0, 0}, 2.25f, -25, -130, "1");
    // Lật mặt trên xuống phía camera: quay dương quanh trục x, vòng cung bên phải khối.
    static final CubeView.Arrow ARROW_TILT_2 = CubeView.Arrow.arc(new float[]{1, 0, 0}, new float[]{1.95f, 0, 0},
            new float[]{0, 1, 0}, 1.7f, -15, 110, "2");
    static final CubeView.Arrow ARROW_TILT_X2 = CubeView.Arrow.arc(new float[]{1, 0, 0}, new float[]{1.95f, 0, 0},
            new float[]{0, 1, 0}, 1.7f, -60, 200, "×2");

    private PreviewFrame frame;
    private TextureView texture;
    private GridOverlay overlay;
    private CubeView cube;
    private TextView info;
    private Button btnBack, btnCap, btnNext, btnTorch;

    private Camera camera;
    private int camOrient = 90, prevW, prevH;
    private SurfaceTexture surface;
    private boolean torchOn;
    private long lastProc;

    private int step = 0;
    private final boolean[] captured = new boolean[6];
    private final int[][] samples = new int[6][9];
    private final int[] live = new int[9];
    private boolean liveValid;
    private final int[] override = new int[9];
    private String firstFrontName;

    static float[] orientFor(int step) { return Quat.mul(TILT, Quat.orient(FRONT[step], UP[step])); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        Arrays.fill(override, -1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        frame = new PreviewFrame(this);
        texture = new TextureView(this);
        texture.setSurfaceTextureListener(this);
        overlay = new GridOverlay(this);
        frame.addView(texture);
        frame.addView(overlay);
        btnTorch = Ui.button(this, "💡", false);
        btnTorch.setOnClickListener(v -> toggleTorch());
        frame.addView(btnTorch);
        root.addView(frame, Ui.lp(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(Ui.PANEL);
        int p = Ui.dp(this, 12);
        bottom.setPadding(p, Ui.dp(this, 8), p, p);
        info = Ui.text(this, "", 15, Ui.TEXT, false);
        info.setMinLines(2);
        bottom.addView(info, Ui.lp(-1, -2, 0));
        cube = new CubeView(this);
        bottom.addView(cube, Ui.lp(-1, 0, 1));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        btnBack = Ui.button(this, "◀ Quay lại", false);
        btnCap = Ui.button(this, "📷 Chụp", true);
        btnNext = Ui.button(this, "Next ▶", false);
        LinearLayout.LayoutParams l1 = Ui.lp(0, -2, 1), l2 = Ui.lp(0, -2, 1.2f), l3 = Ui.lp(0, -2, 1);
        l2.leftMargin = l2.rightMargin = Ui.dp(this, 8);
        row.addView(btnBack, l1);
        row.addView(btnCap, l2);
        row.addView(btnNext, l3);
        bottom.addView(row, Ui.lp(-1, -2, 0));
        root.addView(bottom, Ui.lp(-1, 0, 1));
        setContentView(root);

        btnBack.setOnClickListener(v -> onBackStep());
        btnCap.setOnClickListener(v -> capture());
        btnNext.setOnClickListener(v -> onNext());

        cube.setView(orientFor(0));
        refreshCube();
        updateTexts(-1);
        Ui.enable(btnNext, false);
    }

    // ---------------- Luồng quét ----------------
    private void updateTexts(int fromStep) {
        int f = SCAN_FACE[step];
        overlay.banner = "Mặt " + (step + 1) + "/6 · " + Texts.FACE_VN[f] + "  —  cầm cách ~25-30cm, vừa khung";
        String s;
        if (captured[step]) {
            s = "✔ Đã chụp mặt " + Texts.FACE_VN[f] + ".";
        } else if (fromStep < 0 && step == 0) {
            s = "Mặt 1/6 – MẶT TRƯỚC: hướng mặt có tâm XANH LÁ vào camera, tâm TRẮNG ở trên "
                    + "(hoặc chọn mặt bất kỳ, nhưng giữ cố định mặt trên). Bấm Chụp.";
        } else if (fromStep == step - 1 && step >= 1 && step <= 3) {
            s = "↻ Xoay cả khối rubik sang TRÁI 90° (như hình 3D), giữ nguyên mặt trên (theo mũi tên). Chụp mặt "
                    + Texts.FACE_VN[f] + ".";
        } else if (fromStep == 3 && step == 4) {
            s = "↻ Xoay sang TRÁI thêm 1 lần về mặt đầu tiên" + (firstFrontName != null ? " (tâm " + firstFrontName + ")" : "")
                    + ", rồi lật mặt TRÊN xuống hướng về camera (theo mũi tên 1 → 2). Chụp mặt TRÊN.";
        } else if (fromStep == 4 && step == 5) {
            s = "↻ Lật tiếp khối rubik 180° theo chiều dọc (như hình 3D) để mặt DƯỚI hướng về camera. Chụp mặt DƯỚI.";
        } else {
            s = "Xoay rubik để mặt " + Texts.FACE_VN[f] + " hướng về camera đúng như hình 3D, rồi bấm Chụp.";
        }
        info.setText(s);
        overlay.invalidate();
    }

    private void refreshCube() {
        int[] c = new int[54];
        for (int k = 0; k < 6; k++) {
            int f = SCAN_FACE[k];
            for (int i = 0; i < 9; i++) {
                int col = GREY;
                if (captured[k]) col = ColorUtil.PALETTE[ColorUtil.classify(samples[k][i])];
                else if (k == step && liveValid) col = ColorUtil.PALETTE[liveClass(i)];
                c[f * 9 + i] = col;
            }
        }
        cube.setColors(c);
    }

    private int liveClass(int i) { return override[i] >= 0 ? override[i] : ColorUtil.classify(live[i]); }

    private void capture() {
        if (!liveValid) { toast("Chưa nhận được hình từ camera"); return; }
        for (int i = 0; i < 9; i++)
            samples[step][i] = override[i] >= 0 ? ColorUtil.PALETTE[override[i]] : live[i];
        captured[step] = true;
        if (step == 0) firstFrontName = ColorUtil.NAMES[ColorUtil.classify(samples[0][4])];
        Arrays.fill(override, -1);
        cube.setArrows(null, null);
        refreshCube();
        updateTexts(step);
        // tự động chuyển sang mặt tiếp theo sau khi chụp
        Ui.enable(btnCap, false);
        btnCap.postDelayed(() -> { Ui.enable(btnCap, true); advance(); }, 450);
    }

    private void advance() {
        if (!captured[step]) return;
        int next = -1;
        for (int k = 1; k <= 6; k++) {
            int s = (step + k) % 6;
            if (!captured[s]) { next = s; break; }
        }
        if (next < 0) { processAll(); return; }
        goTo(next);
    }

    private void onNext() {
        if (cube.isAnimating()) return;
        if (!captured[step]) { toast("Hãy bấm Chụp trước"); return; }
        advance();
    }

    private void goTo(int next) {
        int from = step;
        if (from == 3 && next == 4) {
            cube.queueView(orientFor(0), 700);
            cube.queueView(orientFor(4), 700);
        } else if (from == 4 && next == 5) {
            cube.queueView(Quat.mul(TILT, Quat.orient(new int[]{0, 0, -1}, new int[]{0, -1, 0})), 650);
            cube.queueView(orientFor(5), 650);
        } else cube.queueView(orientFor(next), 800);
        // mũi tên hướng dẫn lật rubik (vẽ trong không gian nhìn, không xoay theo khối)
        CubeView.Arrow[] arrows = null;
        if (next == from + 1 && next >= 1 && next <= 3) arrows = new CubeView.Arrow[]{ARROW_LEFT};
        else if (from == 3 && next == 4) arrows = new CubeView.Arrow[]{ARROW_LEFT_1, ARROW_TILT_2};
        else if (from == 4 && next == 5) arrows = new CubeView.Arrow[]{ARROW_TILT_X2};
        cube.setArrows(arrows, TILT);
        step = next;
        Ui.enable(btnNext, captured[step]);
        cube.setHighlight(null);
        refreshCube();
        updateTexts(from);
    }

    private void onBackStep() {
        if (cube.isAnimating()) return;
        if (step == 0) {
            if (!captured[0]) { finish(); return; }
            captured[0] = false;
            refreshCube(); updateTexts(-1); Ui.enable(btnNext, false);
            return;
        }
        captured[step] = false;
        captured[step - 1] = false;
        goTo(step - 1);
    }

    private void processAll() {
        int[] all = new int[54];
        for (int k = 0; k < 6; k++)
            for (int i = 0; i < 9; i++) all[SCAN_FACE[k] * 9 + i] = samples[k][i];
        int[] labels = ColorUtil.cluster(all);
        int[] means = ColorUtil.clusterMeans(all, labels);
        int[] cls = new int[6];
        boolean[] seen = new boolean[6];
        boolean unique = true;
        for (int k = 0; k < 6; k++) {
            cls[k] = ColorUtil.classify(means[k]);
            if (seen[cls[k]]) unique = false;
            seen[cls[k]] = true;
        }
        final int[] faceColor = new int[6];
        final String[] faceName = new String[6];
        for (int k = 0; k < 6; k++) {
            faceColor[k] = unique ? ColorUtil.PALETTE[cls[k]] : means[k];
            faceName[k] = unique ? ColorUtil.NAMES[cls[k]] : ColorUtil.NAMES[cls[k]] + " " + (k + 1);
        }
        int[] c = new int[54];
        for (int i = 0; i < 54; i++) c[i] = faceColor[labels[i]];
        cube.setColors(c);
        try {
            Cubie.fromFacelets(labels);
        } catch (CubeError e) {
            cube.setHighlight(Texts.errorFacelets(e));
            showError(e, labels, faceName);
            return;
        }
        Intent it = new Intent(this, SolveActivity.class);
        it.putExtra("labels", labels);
        it.putExtra("faceColor", faceColor);
        it.putExtra("faceName", faceName);
        startActivity(it);
        finish();
    }

    private void showError(CubeError e, int[] labels, String[] names) {
        info.setText(Texts.title(e) + " – xem hướng dẫn. Các ô lỗi đang nhấp nháy trên hình 3D.");
        new AlertDialog.Builder(this)
                .setTitle(Texts.title(e))
                .setMessage(Texts.error(e, labels, names))
                .setPositiveButton("Quét lại từ đầu", (d, w) -> restart())
                .setNeutralButton("Quét lại 1 mặt", (d, w) -> chooseFace(names))
                .setNegativeButton("Xem hình 3D", null)
                .show();
        Ui.enable(btnNext, true);
    }

    private void chooseFace(String[] names) {
        String[] items = new String[6];
        for (int k = 0; k < 6; k++) items[k] = "Mặt " + Texts.FACE_VN[SCAN_FACE[k]] + " (tâm " + names[SCAN_FACE[k]] + ")";
        new AlertDialog.Builder(this).setTitle("Chọn mặt cần quét lại")
                .setItems(items, (d, which) -> {
                    captured[which] = false;
                    goTo(which);
                }).show();
    }

    private void restart() {
        cube.setArrows(null, null);
        Arrays.fill(captured, false);
        Arrays.fill(override, -1);
        cube.setHighlight(null);
        int from = step;
        step = 0;
        cube.queueView(orientFor(0), 900);
        Ui.enable(btnNext, false);
        refreshCube();
        updateTexts(-1);
    }

    void onCellTap(int i) {
        override[i] = override[i] + 1;
        if (override[i] >= 6) override[i] = -1;
        if (override[i] >= 0) toast("Ô " + (i + 1) + ": " + ColorUtil.NAMES[override[i]] + " (chạm tiếp để đổi)");
        else toast("Ô " + (i + 1) + ": tự động nhận diện");
        overlay.invalidate();
        refreshCube();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------------- Camera ----------------
    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        else openCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) openCamera();
        else {
            overlay.banner = "Cần quyền Camera để quét rubik";
            overlay.invalidate();
            toast("Ứng dụng cần quyền Camera để quét rubik");
        }
    }

    private void openCamera() {
        if (camera != null) return;
        try {
            int id = 0;
            Camera.CameraInfo ci = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, ci);
                if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { id = i; break; }
            }
            Camera.getCameraInfo(id, ci);
            camera = Camera.open(id);
            camOrient = ci.orientation;
            Camera.Parameters p = camera.getParameters();
            Camera.Size best = null;
            for (Camera.Size s : p.getSupportedPreviewSizes()) {
                if (s.width > 1280 || s.height > 960) continue;
                if (best == null || s.width * s.height > best.width * best.height) best = s;
            }
            if (best == null) best = p.getPreviewSize();
            p.setPreviewSize(best.width, best.height);
            List<String> fm = p.getSupportedFocusModes();
            if (fm != null && fm.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            else if (fm != null && fm.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            else if (fm != null && fm.contains(Camera.Parameters.FOCUS_MODE_AUTO))
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            camera.setParameters(p);
            prevW = best.width;
            prevH = best.height;
            camera.setDisplayOrientation(camOrient);
            boolean rot = camOrient % 180 != 0;
            frame.setPreview(rot ? prevH : prevW, rot ? prevW : prevH);
            List<String> fl = p.getSupportedFlashModes();
            btnTorch.setVisibility(fl != null && fl.contains(Camera.Parameters.FLASH_MODE_TORCH) ? View.VISIBLE : View.GONE);
            torchOn = false;
            int size = prevW * prevH * 3 / 2;
            camera.addCallbackBuffer(new byte[size]);
            camera.addCallbackBuffer(new byte[size]);
            camera.setPreviewCallbackWithBuffer(this);
            if (surface != null) startPreview();
        } catch (Exception e) {
            releaseCamera();
            overlay.banner = "Không mở được camera";
            overlay.invalidate();
        }
    }

    private void startPreview() {
        try {
            camera.setPreviewTexture(surface);
            camera.startPreview();
        } catch (Exception e) {
            toast("Lỗi camera: " + e.getMessage());
        }
    }

    private void releaseCamera() {
        if (camera == null) return;
        camera.setPreviewCallbackWithBuffer(null);
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    /** Chạm ngoài lưới để lấy nét lại. */
    private void refocus() {
        if (camera == null) return;
        try {
            camera.cancelAutoFocus();
            camera.autoFocus((ok, c) -> {
                try {
                    Camera.Parameters p = c.getParameters();
                    if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        c.cancelAutoFocus();
                        p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        c.setParameters(p);
                    }
                } catch (Exception ignored) {}
            });
            toast("Đang lấy nét…");
        } catch (Exception ignored) {}
    }

    private void toggleTorch() {
        if (camera == null) return;
        try {
            Camera.Parameters p = camera.getParameters();
            torchOn = !torchOn;
            p.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(p);
        } catch (Exception ignored) {}
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { surface = s; if (camera != null) startPreview(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { surface = null; releaseCamera(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}

    @Override
    public void onPreviewFrame(byte[] data, Camera cam) {
        long now = SystemClock.uptimeMillis();
        if (now - lastProc > 120 && frame.sw > 0 && overlay.cell > 0) {
            lastProc = now;
            float dispW = camOrient % 180 != 0 ? prevH : prevW;
            int r = Math.max(2, (int) (overlay.cell * 0.17f / frame.sw * dispW));
            for (int i = 0; i < 9; i++) {
                float ox = overlay.gx + (i % 3 + 0.5f) * overlay.cell, oy = overlay.gy + (i / 3 + 0.5f) * overlay.cell;
                float nx = (ox - frame.offX) / frame.sw, ny = (oy - frame.offY) / frame.sh;
                float fx, fy;
                switch (camOrient) {
                    case 90: fx = ny * prevW; fy = (1 - nx) * prevH; break;
                    case 180: fx = (1 - nx) * prevW; fy = (1 - ny) * prevH; break;
                    case 270: fx = (1 - ny) * prevW; fy = nx * prevH; break;
                    default: fx = nx * prevW; fy = ny * prevH; break;
                }
                live[i] = sample(data, (int) fx, (int) fy, r);
            }
            liveValid = true;
            overlay.invalidate();
            if (!captured[step]) refreshCube();
        }
        cam.addCallbackBuffer(data);
    }

    private int sample(byte[] d, int cx, int cy, int r) {
        int n = 0;
        int[] rs = new int[49], gs = new int[49], bs = new int[49];
        int stepPx = Math.max(1, r / 3);
        for (int dy = -3; dy <= 3; dy++)
            for (int dx = -3; dx <= 3; dx++) {
                int x = Math.max(0, Math.min(prevW - 1, cx + dx * stepPx));
                int y = Math.max(0, Math.min(prevH - 1, cy + dy * stepPx));
                int Y = d[y * prevW + x] & 255;
                int uv = prevW * prevH + (y >> 1) * prevW + (x & ~1);
                int V = (d[uv] & 255) - 128, Uc = (d[uv + 1] & 255) - 128;
                rs[n] = clamp(Y + 1.402f * V);
                gs[n] = clamp(Y - 0.344f * Uc - 0.714f * V);
                bs[n] = clamp(Y + 1.772f * Uc);
                n++;
            }
        Arrays.sort(rs); Arrays.sort(gs); Arrays.sort(bs);
        return 0xFF000000 | rs[24] << 16 | gs[24] << 8 | bs[24];
    }

    private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : (int) v; }

    // ---------------- Khung xem trước ----------------
    static class PreviewFrame extends ViewGroup {
        int pw, ph;
        float offX, offY, sw, sh;

        PreviewFrame(Context c) { super(c); setBackgroundColor(0xFF000000); }

        void setPreview(int w, int h) { pw = w; ph = h; requestLayout(); }

        @Override
        protected void onMeasure(int wm, int hm) {
            int w = MeasureSpec.getSize(wm), h = MeasureSpec.getSize(hm);
            setMeasuredDimension(w, h);
            int tb = Ui.dp(getContext(), 48);
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v instanceof Button) v.measure(MeasureSpec.makeMeasureSpec(tb, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(tb, MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, h = b - t;
            float s = (pw > 0 && ph > 0) ? Math.max(w / (float) pw, h / (float) ph) : 1;
            sw = pw > 0 ? pw * s : w;
            sh = ph > 0 ? ph * s : h;
            offX = (w - sw) / 2;
            offY = (h - sh) / 2;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v instanceof TextureView) {
                    v.measure(MeasureSpec.makeMeasureSpec((int) sw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) sh, MeasureSpec.EXACTLY));
                    v.layout((int) offX, (int) offY, (int) (offX + sw), (int) (offY + sh));
                } else if (v instanceof Button) {
                    int m = Ui.dp(getContext(), 10), sz = v.getMeasuredWidth();
                    v.layout(w - m - sz, h - m - sz, w - m, h - m);
                } else {
                    v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                    v.layout(0, 0, w, h);
                }
            }
        }
    }

    /** Lớp phủ: lưới 3x3 và màu nhận diện trực tiếp; chạm vào ô để sửa màu thủ công. */
    class GridOverlay extends View {
        String banner = "";
        float gx, gy, cell;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rf = new RectF();

        GridOverlay(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float bannerH = Ui.dp(getContext(), 34);
            float size = Math.min(w * 0.50f, (h - bannerH) * 0.58f);
            cell = size / 3;
            gx = (w - size) / 2;
            gy = bannerH + (h - bannerH - size) / 2;
            // làm tối vùng ngoài lưới
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x88000000);
            c.drawRect(0, 0, w, gy, p);
            c.drawRect(0, gy + size, w, h, p);
            c.drawRect(0, gy, gx, gy + size, p);
            c.drawRect(gx + size, gy, w, gy + size, p);
            // lưới
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(), 2));
            p.setColor(Ui.ACCENT);
            c.drawRoundRect(new RectF(gx, gy, gx + size, gy + size), 18, 18, p);
            p.setColor(0xCCFFFFFF);
            p.setStrokeWidth(Ui.dp(getContext(), 1.2f));
            for (int k = 1; k < 3; k++) {
                c.drawLine(gx + k * cell, gy, gx + k * cell, gy + size, p);
                c.drawLine(gx, gy + k * cell, gx + size, gy + k * cell, p);
            }
            float rad = cell * 0.17f;
            for (int i = 0; i < 9; i++) {
                float cx = gx + (i % 3 + 0.5f) * cell, cy = gy + (i / 3 + 0.5f) * cell;
                // ô lấy mẫu
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xAAFFFFFF);
                c.drawRect(cx - rad, cy - rad, cx + rad, cy + rad, p);
                if (!liveValid) continue;
                int cls = liveClass(i);
                rf.set(cx + cell * 0.18f, cy + cell * 0.18f, cx + cell * 0.44f, cy + cell * 0.44f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(ColorUtil.PALETTE[cls]);
                c.drawRoundRect(rf, 8, 8, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(override[i] >= 0 ? Ui.ACCENT : 0xFF000000);
                p.setStrokeWidth(Ui.dp(getContext(), override[i] >= 0 ? 3 : 1.5f));
                c.drawRoundRect(rf, 8, 8, p);
                p.setStrokeWidth(Ui.dp(getContext(), 1.2f));
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCC000000);
            c.drawRect(0, 0, w, bannerH, p);
            p.setColor(Ui.ACCENT);
            p.setTextSize(Ui.dp(getContext(), 14));
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            c.drawText(banner, w / 2, bannerH * 0.66f, p);
            p.setFakeBoldText(false);
            p.setTextSize(Ui.dp(getContext(), 11));
            p.setColor(0xDDFFFFFF);
            c.drawText("Chạm ô để sửa màu · chạm ngoài lưới để lấy nét", w / 2, Math.min(h - 6, gy + size + Ui.dp(getContext(), 16)), p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (e.getAction() == MotionEvent.ACTION_UP && cell > 0) {
                int col = (int) ((e.getX() - gx) / cell), row = (int) ((e.getY() - gy) / cell);
                if (e.getX() >= gx && e.getY() >= gy && col < 3 && row < 3) onCellTap(row * 3 + col);
                else refocus();
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

}
