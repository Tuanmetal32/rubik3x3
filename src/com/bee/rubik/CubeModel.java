package com.bee.rubik;

/**
 * Hình học 3D của 54 ô (facelet) theo thứ tự Kociemba: U1..U9 R1..R9 F1..F9 D1..D9 L1..L9 B1..B9.
 * Hệ trục: x sang phải (R), y lên trên (U), z hướng về người xem (F).
 * Hoán vị của 18 bước xoay được suy ra trực tiếp từ hình học để 3D và bộ giải luôn khớp nhau.
 */
public final class CubeModel {
    public static final int U = 0, R = 1, F = 2, D = 3, L = 4, B = 5;
    public static final int[][] POS = new int[54][];
    public static final int[][] NRM = new int[54][];
    /** PERM[m][i] = vị trí mới của ô đang ở vị trí i sau bước xoay m. */
    public static final int[][] PERM = new int[18][54];
    public static final String[] MOVE_NAMES = {
            "U", "U2", "U'", "R", "R2", "R'", "F", "F2", "F'",
            "D", "D2", "D'", "L", "L2", "L'", "B", "B2", "B'"};
    public static final int[][] FACE_NORMAL = {
            {0, 1, 0}, {1, 0, 0}, {0, 0, 1}, {0, -1, 0}, {-1, 0, 0}, {0, 0, -1}};

    static {
        for (int f = 0; f < 6; f++)
            for (int r = 0; r < 3; r++)
                for (int c = 0; c < 3; c++) {
                    int x = 0, y = 0, z = 0;
                    switch (f) {
                        case U: x = c - 1; y = 1; z = r - 1; break;
                        case R: x = 1; y = 1 - r; z = 1 - c; break;
                        case F: x = c - 1; y = 1 - r; z = 1; break;
                        case D: x = c - 1; y = -1; z = 1 - r; break;
                        case L: x = -1; y = 1 - r; z = c - 1; break;
                        default: x = 1 - c; y = 1 - r; z = -1; break; // B
                    }
                    int i = f * 9 + r * 3 + c;
                    POS[i] = new int[]{x, y, z};
                    NRM[i] = FACE_NORMAL[f].clone();
                }
        for (int m = 0; m < 18; m++) {
            int f = m / 3, k = m % 3;
            int[] axis = FACE_NORMAL[f];
            double deg = -90.0 * (k + 1);
            for (int i = 0; i < 54; i++) {
                if (dot(POS[i], axis) == 1) {
                    int j = find(rot(POS[i], axis, deg), rot(NRM[i], axis, deg));
                    PERM[m][i] = j;
                } else PERM[m][i] = i;
            }
        }
    }

    private CubeModel() {}

    static int dot(int[] a, int[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    /** Ma trận quay Rodrigues (hàng-trước) quanh trục đơn vị a, góc tính bằng độ. */
    public static double[] rotMatrix(double ax, double ay, double az, double deg) {
        double t = Math.toRadians(deg), c = Math.cos(t), s = Math.sin(t), v = 1 - c;
        return new double[]{
                v * ax * ax + c, v * ax * ay - s * az, v * ax * az + s * ay,
                v * ax * ay + s * az, v * ay * ay + c, v * ay * az - s * ax,
                v * ax * az - s * ay, v * ay * az + s * ax, v * az * az + c};
    }

    static int[] rot(int[] p, int[] a, double deg) {
        double[] m = rotMatrix(a[0], a[1], a[2], deg);
        return new int[]{
                (int) Math.round(m[0] * p[0] + m[1] * p[1] + m[2] * p[2]),
                (int) Math.round(m[3] * p[0] + m[4] * p[1] + m[5] * p[2]),
                (int) Math.round(m[6] * p[0] + m[7] * p[1] + m[8] * p[2])};
    }

    public static int find(int[] p, int[] n) {
        for (int i = 0; i < 54; i++)
            if (POS[i][0] == p[0] && POS[i][1] == p[1] && POS[i][2] == p[2]
                    && NRM[i][0] == n[0] && NRM[i][1] == n[1] && NRM[i][2] == n[2]) return i;
        return -1;
    }

    public static int[] apply(int[] state, int m) {
        int[] out = new int[54];
        for (int i = 0; i < 54; i++) out[PERM[m][i]] = state[i];
        return out;
    }

    public static int inverse(int m) { return m / 3 * 3 + (2 - m % 3); }

    public static int[] solvedLabels() {
        int[] s = new int[54];
        for (int i = 0; i < 54; i++) s[i] = i / 9;
        return s;
    }
}
