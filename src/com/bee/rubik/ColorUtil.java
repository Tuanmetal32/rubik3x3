package com.bee.rubik;

import android.graphics.Color;

import java.util.Arrays;

/** Nhận diện màu: phân loại nhanh theo HSV khi xem trực tiếp, phân cụm cân bằng (Lab) khi xử lý cuối. */
final class ColorUtil {
    static final int WHITE = 0, YELLOW = 1, RED = 2, ORANGE = 3, BLUE = 4, GREEN = 5;
    static final int[] PALETTE = {0xFFF5F5F5, 0xFFFFD600, 0xFFD32F2F, 0xFFFF7A00, 0xFF1E5BD8, 0xFF19A94B};
    static final String[] NAMES = {"Trắng", "Vàng", "Đỏ", "Cam", "Xanh dương", "Xanh lá"};

    private ColorUtil() {}

    static int classify(int rgb) {
        float[] hsv = new float[3];
        Color.colorToHSV(rgb, hsv);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        if (s < 0.22f || (s < 0.32f && v > 0.55f)) return WHITE;
        if (h < 12 || h >= 330) return RED;
        if (h < 38) return ORANGE;
        if (h < 72) return YELLOW;
        if (h < 165) return GREEN;
        if (h < 265) return BLUE;
        return RED;
    }

    static double[] lab(int rgb) {
        double r = lin(((rgb >> 16) & 255) / 255.0), g = lin(((rgb >> 8) & 255) / 255.0), b = lin((rgb & 255) / 255.0);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883;
        x = f(x); y = f(y); z = f(z);
        return new double[]{116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double lin(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }

    private static double f(double t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116; }

    private static double dist(double[] a, double[] b) {
        double dl = a[0] - b[0], da = a[1] - b[1], db = a[2] - b[2];
        return 0.55 * dl * dl + da * da + db * db;
    }

    /**
     * Gán 54 mẫu màu (RGB) vào 6 nhóm, mỗi nhóm đúng 9 ô; ô tâm k (chỉ số k*9+4) xác định nhóm k.
     * Trả về nhãn mặt 0..5 cho từng ô.
     */
    static int[] cluster(int[] rgb) {
        double[][] lab = new double[54][];
        for (int i = 0; i < 54; i++) lab[i] = lab(rgb[i]);
        double[][] mean = new double[6][];
        for (int k = 0; k < 6; k++) mean[k] = lab[k * 9 + 4].clone();
        int[] label = new int[54];
        for (int iter = 0; iter < 6; iter++) {
            Arrays.fill(label, -1);
            int[] cnt = new int[6];
            for (int k = 0; k < 6; k++) { label[k * 9 + 4] = k; cnt[k] = 1; }
            long[] pairs = new long[48 * 6];
            double[] d = new double[48 * 6];
            int n = 0;
            for (int i = 0; i < 54; i++) {
                if (i % 9 == 4) continue;
                for (int k = 0; k < 6; k++) { d[n] = dist(lab[i], mean[k]); pairs[n] = i * 6L + k; n++; }
            }
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            final double[] dd = d;
            Arrays.sort(order, (x, y) -> Double.compare(dd[x], dd[y]));
            for (Integer o : order) {
                int i = (int) (pairs[o] / 6), k = (int) (pairs[o] % 6);
                if (label[i] >= 0 || cnt[k] >= 9) continue;
                label[i] = k;
                cnt[k]++;
            }
            for (int k = 0; k < 6; k++) {
                double[] s = new double[3];
                int c = 0;
                for (int i = 0; i < 54; i++)
                    if (label[i] == k) { s[0] += lab[i][0]; s[1] += lab[i][1]; s[2] += lab[i][2]; c++; }
                mean[k] = new double[]{s[0] / c, s[1] / c, s[2] / c};
            }
        }
        return label;
    }

    /** Màu trung bình RGB của từng nhóm. */
    static int[] clusterMeans(int[] rgb, int[] label) {
        int[] out = new int[6];
        for (int k = 0; k < 6; k++) {
            long r = 0, g = 0, b = 0;
            int c = 0;
            for (int i = 0; i < 54; i++)
                if (label[i] == k) { r += (rgb[i] >> 16) & 255; g += (rgb[i] >> 8) & 255; b += rgb[i] & 255; c++; }
            out[k] = 0xFF000000 | (int) (r / c) << 16 | (int) (g / c) << 8 | (int) (b / c);
        }
        return out;
    }
}
