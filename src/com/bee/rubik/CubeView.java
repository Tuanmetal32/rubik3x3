package com.bee.rubik;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Vẽ rubik 3D bằng Canvas (thuật toán họa sĩ), có hoạt ảnh xoay tầng và xoay cả khối. */
public class CubeView extends View {
    static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    static final int[][] CUBIE_POS = new int[26][];
    static final int[][] CUBIE_FACELET = new int[26][6];

    static {
        int n = 0;
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    int[] p = {x, y, z};
                    CUBIE_POS[n] = p;
                    for (int d = 0; d < 6; d++) CUBIE_FACELET[n][d] = CubeModel.find(p, DIRS[d]);
                    n++;
                }
    }

    public interface TapListener { void onTap(); }

    /** Mũi tên cong hình cung: tâm c, bán kính r, quay quanh trục a từ góc start, quét sweep độ (dương = ngược kim đồng hồ nhìn từ đầu trục). */
    public static final class Arrow {
        final float[] c, u, v;
        final float r, start, sweep;
        final String label;

        private Arrow(float[] c, float[] u, float[] v, float r, float start, float sweep, String label) {
            this.c = c; this.u = u; this.v = v; this.r = r; this.start = start; this.sweep = sweep; this.label = label;
        }

        public static Arrow arc(float[] a, float[] c, float[] u, float r, float start, float sweep, String label) {
            float[] v = {a[1] * u[2] - a[2] * u[1], a[2] * u[0] - a[0] * u[2], a[0] * u[1] - a[1] * u[0]};
            return new Arrow(c, u, v, r, start, sweep, label);
        }

        /** Mũi tên trên mặt đang xoay cho bước m (toạ độ thế giới). */
        public static Arrow forMove(int m) {
            int f = m / 3, k = m % 3;
            int[] n = CubeModel.FACE_NORMAL[f];
            float[] a = {n[0], n[1], n[2]};
            float[] c = {n[0] * 1.58f, n[1] * 1.58f, n[2] * 1.58f};
            float[] u = n[1] != 0 ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
            float sweep = k == 0 ? -120 : k == 2 ? 120 : -250;
            return arc(a, c, u, 1.0f, 60 - sweep / 2, sweep, k == 1 ? "×2" : null);
        }
    }

    private Arrow[] arrows;
    private float[] arrowSpace; // null = toạ độ thế giới, ngược lại = quaternion không gian nhìn
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();

    private int[] colors = new int[54];
    private boolean[] hl = new boolean[54];
    private boolean anyHl;
    private float[] quat = Quat.tilt(-30, 25);
    private final ArrayDeque<float[]> queue = new ArrayDeque<>();
    private float[] animFrom, animTo;
    private long animStart;
    private float animDur;
    private int moveAnim = -1;
    private long moveStart;
    private int moveDur = 450;
    private Runnable moveDone;
    private boolean autoSpin;
    private float spin;
    private long lastFrame;
    private float dragYaw, dragPitch, downX, downY, lastX, lastY;
    private boolean dragging;
    private TapListener tapListener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] tmp = new float[3];
    private final float[] sx = new float[4], sy = new float[4];

    public CubeView(Context c) {
        super(c);
        Arrays.fill(colors, 0xFF4A463D);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColors(int[] c) { colors = c.clone(); invalidate(); }
    public int[] getColors() { return colors.clone(); }
    public void setColor(int facelet, int c) { colors[facelet] = c; invalidate(); }

    public void setHighlight(int[] facelets) {
        Arrays.fill(hl, false);
        anyHl = false;
        if (facelets != null) for (int f : facelets) { hl[f] = true; anyHl = true; }
        invalidate();
    }

    public void setArrows(Arrow[] a, float[] spaceQuat) {
        arrows = a;
        arrowSpace = spaceQuat == null ? null : spaceQuat.clone();
        invalidate();
    }

    public void setAutoSpin(boolean on) { autoSpin = on; lastFrame = SystemClock.uptimeMillis(); invalidate(); }
    public void setTapListener(TapListener l) { tapListener = l; }

    public void setView(float[] q) { queue.clear(); animTo = null; quat = q.clone(); invalidate(); }

    /** Thêm một đoạn hoạt ảnh xoay cả khối tới hướng q. */
    public void queueView(float[] q, int durMs) {
        queue.add(new float[]{q[0], q[1], q[2], q[3], durMs});
        invalidate();
    }

    public void resetDrag() { dragYaw = 0; dragPitch = 0; invalidate(); }

    public boolean isAnimating() { return moveAnim >= 0 || animTo != null || !queue.isEmpty(); }

    public void animateMove(int m, Runnable done) {
        finishMove();
        moveAnim = m;
        moveStart = SystemClock.uptimeMillis();
        moveDur = (m % 3 == 1) ? 650 : 450;
        moveDone = done;
        invalidate();
    }

    /** Áp dụng ngay bước xoay (không hoạt ảnh). */
    public void applyMove(int m) {
        finishMove();
        colors = permute(colors, m);
        hl = permuteB(hl, m);
        invalidate();
    }

    public void finishMove() {
        if (moveAnim < 0) return;
        int m = moveAnim;
        moveAnim = -1;
        colors = permute(colors, m);
        hl = permuteB(hl, m);
        Runnable r = moveDone;
        moveDone = null;
        if (r != null) r.run();
    }

    private static int[] permute(int[] c, int m) {
        int[] o = new int[54];
        for (int i = 0; i < 54; i++) o[CubeModel.PERM[m][i]] = c[i];
        return o;
    }

    private static boolean[] permuteB(boolean[] c, int m) {
        boolean[] o = new boolean[54];
        for (int i = 0; i < 54; i++) o[CubeModel.PERM[m][i]] = c[i];
        return o;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX(); downY = lastY = e.getY(); dragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - lastX, dy = e.getY() - lastY;
                if (Math.abs(e.getX() - downX) + Math.abs(e.getY() - downY) > 20) dragging = true;
                if (dragging) {
                    dragYaw += dx * 0.4f;
                    dragPitch = Math.max(-80, Math.min(80, dragPitch + dy * 0.4f));
                    invalidate();
                }
                lastX = e.getX(); lastY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && tapListener != null) tapListener.onTap();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private static float ease(float t) { return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2; }

    private void startNextView(long now) {
        float[] n = queue.poll();
        animFrom = quat.clone();
        animTo = new float[]{n[0], n[1], n[2], n[3]};
        animDur = n[4];
        animStart = now;
    }

    @Override
    protected void onDraw(Canvas cv) {
        long now = SystemClock.uptimeMillis();
        boolean more = false;
        if (animTo == null && !queue.isEmpty()) startNextView(now);
        if (animTo != null) {
            float t = (now - animStart) / animDur;
            if (t >= 1) {
                quat = animTo;
                animTo = null;
                if (!queue.isEmpty()) startNextView(now);
            } else quat = Quat.slerp(animFrom, animTo, ease(t));
            more = true;
        }
        if (autoSpin) { spin += (now - lastFrame) * 0.035f; more = true; }
        lastFrame = now;
        float[] q = quat;
        if (autoSpin) q = Quat.mul(Quat.mul(Quat.axisAngle(1, 0, 0, 22), Quat.axisAngle(0, 1, 0, spin)), Quat.axisAngle(1, 0, 0, -10));
        if (dragYaw != 0 || dragPitch != 0) q = Quat.mul(Quat.tilt(dragYaw, dragPitch), q);
        float[] M = Quat.toMatrix(q);

        double[] A = null;
        int[] axis = null;
        if (moveAnim >= 0) {
            float t = (now - moveStart) / (float) moveDur;
            if (t >= 1) finishMove();
            else {
                int f = moveAnim / 3, k = moveAnim % 3;
                axis = CubeModel.FACE_NORMAL[f];
                double target = k == 2 ? 90 : -90.0 * (k + 1);
                A = CubeModel.rotMatrix(axis[0], axis[1], axis[2], target * ease(t));
                more = true;
            }
        }

        float w = getWidth(), h = getHeight();
        float cx = w / 2, cy = h / 2, scale = Math.min(w, h) / 6.6f, camD = 11f;
        // độ sâu từng khối nhỏ
        float[] depth = new float[26];
        Integer[] order = new Integer[26];
        for (int i = 0; i < 26; i++) {
            order[i] = i;
            boolean in = A != null && CubeModel.dot(CUBIE_POS[i], axis) == 1;
            xf(CUBIE_POS[i][0], CUBIE_POS[i][1], CUBIE_POS[i][2], in ? A : null, M);
            depth[i] = tmp[2];
        }
        final float[] dd = depth;
        Arrays.sort(order, (a, b) -> Float.compare(dd[a], dd[b]));

        boolean blinkOn = (now / 350) % 2 == 0;
        for (int oi = 0; oi < 26; oi++) {
            int ci = order[oi];
            int[] p = CUBIE_POS[ci];
            boolean in = A != null && CubeModel.dot(p, axis) == 1;
            double[] RA = in ? A : null;
            for (int d = 0; d < 6; d++) {
                int[] n = DIRS[d];
                // pháp tuyến sau biến đổi
                xfv(n[0], n[1], n[2], RA, M);
                float nx = tmp[0], ny = tmp[1], nz = tmp[2];
                float fcx = p[0] + 0.5f * n[0], fcy = p[1] + 0.5f * n[1], fcz = p[2] + 0.5f * n[2];
                xf(fcx, fcy, fcz, RA, M);
                float vx = -tmp[0], vy = -tmp[1], vz = camD - tmp[2];
                if (vx * nx + vy * ny + vz * nz <= 0) continue;
                int fi = CUBIE_FACELET[ci][d];
                int[] t1, t2;
                if (n[0] != 0) { t1 = DIRS[2]; t2 = DIRS[4]; }
                else if (n[1] != 0) { t1 = DIRS[0]; t2 = DIRS[4]; }
                else { t1 = DIRS[0]; t2 = DIRS[2]; }
                quad(fcx, fcy, fcz, t1, t2, 0.5f, RA, M, cx, cy, scale, camD);
                paint.setColor(0xFF0E0E0E);
                cv.drawPath(path, paint);
                if (fi < 0) continue;
                quad(fcx, fcy, fcz, t1, t2, 0.43f, RA, M, cx, cy, scale, camD);
                float light = 0.68f + 0.32f * Math.max(0, nz);
                paint.setColor(shade(colors[fi], light));
                cv.drawPath(path, paint);
                if (hl[fi] && blinkOn) {
                    stroke.setColor(0xFFFF1744);
                    stroke.setStrokeWidth(scale * 0.12f);
                    cv.drawPath(path, stroke);
                }
            }
        }
        if (arrows != null) {
            float[] AM = M;
            if (arrowSpace != null) {
                float[] aq = arrowSpace;
                if (dragYaw != 0 || dragPitch != 0) aq = Quat.mul(Quat.tilt(dragYaw, dragPitch), aq);
                AM = Quat.toMatrix(aq);
            }
            float pulse = 0.75f + 0.25f * (float) Math.sin(now / 180.0);
            for (Arrow a : arrows) drawArrow(cv, a, AM, cx, cy, scale, camD, pulse, now);
            more = true;
        }
        if (more || anyHl) postInvalidateOnAnimation();
    }

    private void drawArrow(Canvas cv, Arrow a, float[] AM, float cx, float cy, float scale, float camD, float pulse, long now) {
        int N = 28;
        // chuyển động "chảy" theo hướng xoay: cung trượt về phía trước rồi lặp lại, mờ ở hai đầu chu kỳ
        float ph = (now % 1500) / 1500f;
        float flow = (float) (0.5 - 0.5 * Math.cos(Math.PI * ph)); // ease in-out 0..1
        float shift = Math.signum(a.sweep) * 28f * flow;
        float fade = (float) Math.sin(Math.PI * Math.min(1f, ph * 1.15f));
        int alpha = (int) (255 * (0.35f + 0.65f * fade));
        float[] px = new float[N + 1], py = new float[N + 1];
        for (int i = 0; i <= N; i++) {
            double t = Math.toRadians(a.start + shift + a.sweep * i / N);
            float ct = (float) Math.cos(t), st = (float) Math.sin(t);
            xf(a.c[0] + a.r * (ct * a.u[0] + st * a.v[0]), a.c[1] + a.r * (ct * a.u[1] + st * a.v[1]),
                    a.c[2] + a.r * (ct * a.u[2] + st * a.v[2]), null, AM);
            float pf = camD / (camD - tmp[2]);
            px[i] = cx + tmp[0] * pf * scale;
            py[i] = cy - tmp[1] * pf * scale;
        }
        float hx = px[N], hy = py[N];
        float dx = hx - px[N - 3], dy = hy - py[N - 3];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) return;
        dx /= len; dy /= len;
        float hs = scale * 0.42f;
        // thân mũi tên dừng trước đầu nhọn
        arrowPath.reset();
        arrowPath.moveTo(px[0], py[0]);
        for (int i = 1; i < N - 1; i++) arrowPath.lineTo(px[i], py[i]);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setColor(((alpha * 13 / 15) << 24));
        arrowPaint.setStrokeWidth(scale * 0.24f);
        cv.drawPath(arrowPath, arrowPaint);
        int col = (alpha << 24) | ((int) (255 * pulse) << 16) | ((int) (193 * pulse + 40 * (1 - pulse)) << 8) | 20;
        arrowPaint.setColor(col);
        arrowPaint.setStrokeWidth(scale * 0.13f);
        cv.drawPath(arrowPath, arrowPaint);
        // đầu mũi tên
        float tx = hx + dx * hs * 0.5f, ty = hy + dy * hs * 0.5f;
        float bx = hx - dx * hs * 0.6f, by = hy - dy * hs * 0.6f;
        arrowPath.reset();
        arrowPath.moveTo(tx, ty);
        arrowPath.lineTo(bx - dy * hs * 0.6f, by + dx * hs * 0.6f);
        arrowPath.lineTo(bx + dy * hs * 0.6f, by - dx * hs * 0.6f);
        arrowPath.close();
        arrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        arrowPaint.setStrokeWidth(scale * 0.06f);
        arrowPaint.setColor(((alpha * 13 / 15) << 24));
        cv.drawPath(arrowPath, arrowPaint);
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setColor(col);
        cv.drawPath(arrowPath, arrowPaint);
        if (a.label != null) {
            arrowPaint.setTextSize(scale * 0.55f);
            arrowPaint.setTextAlign(Paint.Align.CENTER);
            arrowPaint.setFakeBoldText(true);
            float lx = px[0] - dx * 0 + 0, ly = py[0];
            arrowPaint.setColor(((alpha * 13 / 15) << 24));
            cv.drawCircle(lx, ly, scale * 0.36f, arrowPaint);
            arrowPaint.setColor(col);
            cv.drawText(a.label, lx, ly + scale * 0.19f, arrowPaint);
        }
    }

    private void quad(float fx, float fy, float fz, int[] t1, int[] t2, float s, double[] RA, float[] M,
                      float cx, float cy, float scale, float camD) {
        float[][] sg = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        for (int k = 0; k < 4; k++) {
            float a = sg[k][0] * s, b = sg[k][1] * s;
            xf(fx + a * t1[0] + b * t2[0], fy + a * t1[1] + b * t2[1], fz + a * t1[2] + b * t2[2], RA, M);
            float pf = camD / (camD - tmp[2]);
            sx[k] = cx + tmp[0] * pf * scale;
            sy[k] = cy - tmp[1] * pf * scale;
        }
        path.reset();
        path.moveTo(sx[0], sy[0]);
        for (int k = 1; k < 4; k++) path.lineTo(sx[k], sy[k]);
        path.close();
    }

    private void xf(float x, float y, float z, double[] RA, float[] M) {
        if (RA != null) {
            float a = (float) (RA[0] * x + RA[1] * y + RA[2] * z);
            float b = (float) (RA[3] * x + RA[4] * y + RA[5] * z);
            float c = (float) (RA[6] * x + RA[7] * y + RA[8] * z);
            x = a; y = b; z = c;
        }
        tmp[0] = M[0] * x + M[1] * y + M[2] * z;
        tmp[1] = M[3] * x + M[4] * y + M[5] * z;
        tmp[2] = M[6] * x + M[7] * y + M[8] * z;
    }

    private void xfv(float x, float y, float z, double[] RA, float[] M) { xf(x, y, z, RA, M); }

    private static int shade(int c, float k) {
        int r = Math.min(255, (int) (Color.red(c) * k)), g = Math.min(255, (int) (Color.green(c) * k)), b = Math.min(255, (int) (Color.blue(c) * k));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
