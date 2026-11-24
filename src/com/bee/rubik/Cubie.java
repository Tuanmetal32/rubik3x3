package com.bee.rubik;

/**
 * Biểu diễn rubik ở mức khối nhỏ (cubie): hoán vị và độ xoay của 8 góc, 12 cạnh.
 * Góc: URF UFL ULB UBR DFR DLF DBL DRB. Cạnh: UR UF UL UB DR DF DL DB FR FL BL BR.
 */
public final class Cubie {
    public static final int[][] CORNER_FACELET = {
            {8, 9, 20}, {6, 18, 38}, {0, 36, 47}, {2, 45, 11},
            {29, 26, 15}, {27, 44, 24}, {33, 53, 42}, {35, 17, 51}};
    public static final int[][] EDGE_FACELET = {
            {5, 10}, {7, 19}, {3, 37}, {1, 46}, {32, 16}, {28, 25},
            {30, 43}, {34, 52}, {23, 12}, {21, 41}, {50, 39}, {48, 14}};
    static final int U = 0, R = 1, F = 2, D = 3, L = 4, B = 5;
    public static final int[][] CORNER_COLOR = {
            {U, R, F}, {U, F, L}, {U, L, B}, {U, B, R},
            {D, F, R}, {D, L, F}, {D, B, L}, {D, R, B}};
    public static final int[][] EDGE_COLOR = {
            {U, R}, {U, F}, {U, L}, {U, B}, {D, R}, {D, F},
            {D, L}, {D, B}, {F, R}, {F, L}, {B, L}, {B, R}};

    public final byte[] cp = new byte[8], co = new byte[8], ep = new byte[12], eo = new byte[12];

    public Cubie() {
        for (int i = 0; i < 8; i++) cp[i] = (byte) i;
        for (int i = 0; i < 12; i++) ep[i] = (byte) i;
    }

    public Cubie copy() {
        Cubie c = new Cubie();
        System.arraycopy(cp, 0, c.cp, 0, 8);
        System.arraycopy(co, 0, c.co, 0, 8);
        System.arraycopy(ep, 0, c.ep, 0, 12);
        System.arraycopy(eo, 0, c.eo, 0, 12);
        return c;
    }

    /** this * b (áp dụng b sau this). */
    public Cubie mul(Cubie b) {
        Cubie r = new Cubie();
        for (int i = 0; i < 8; i++) {
            r.cp[i] = cp[b.cp[i]];
            r.co[i] = (byte) ((co[b.cp[i]] + b.co[i]) % 3);
        }
        for (int i = 0; i < 12; i++) {
            r.ep[i] = ep[b.ep[i]];
            r.eo[i] = (byte) ((eo[b.ep[i]] + b.eo[i]) % 2);
        }
        return r;
    }

    public int[] toFacelets() {
        int[] f = new int[54];
        for (int i = 0; i < 54; i++) f[i] = i / 9;
        for (int i = 0; i < 8; i++)
            for (int n = 0; n < 3; n++)
                f[CORNER_FACELET[i][(n + co[i]) % 3]] = CORNER_COLOR[cp[i]][n];
        for (int i = 0; i < 12; i++)
            for (int n = 0; n < 2; n++)
                f[EDGE_FACELET[i][(n + eo[i]) % 2]] = EDGE_COLOR[ep[i]][n];
        return f;
    }

    /** Chuyển 54 nhãn mặt (0..5) thành cubie, kiểm tra đầy đủ tính hợp lệ. */
    public static Cubie fromFacelets(int[] f) throws CubeError {
        int[] cnt = new int[6];
        for (int v : f) {
            if (v < 0 || v > 5) throw new CubeError(CubeError.COLOR_COUNT, 0, 0);
            cnt[v]++;
        }
        for (int k = 0; k < 6; k++) if (cnt[k] != 9) throw new CubeError(CubeError.COLOR_COUNT, k, cnt[k]);
        Cubie c = new Cubie();
        for (int i = 0; i < 8; i++) {
            int ori;
            for (ori = 0; ori < 3; ori++) {
                int v = f[CORNER_FACELET[i][ori]];
                if (v == U || v == D) break;
            }
            if (ori == 3) throw new CubeError(CubeError.CORNER_INVALID, i, 0);
            int c0 = f[CORNER_FACELET[i][ori]];
            int c1 = f[CORNER_FACELET[i][(ori + 1) % 3]], c2 = f[CORNER_FACELET[i][(ori + 2) % 3]];
            int found = -1;
            for (int j = 0; j < 8; j++)
                if (CORNER_COLOR[j][0] == c0 && CORNER_COLOR[j][1] == c1 && CORNER_COLOR[j][2] == c2) found = j;
            if (found < 0) throw new CubeError(CubeError.CORNER_INVALID, i, 0);
            c.cp[i] = (byte) found;
            c.co[i] = (byte) ori;
        }
        for (int i = 0; i < 12; i++) {
            int a = f[EDGE_FACELET[i][0]], b = f[EDGE_FACELET[i][1]];
            int found = -1, o = 0;
            for (int j = 0; j < 12; j++) {
                if (EDGE_COLOR[j][0] == a && EDGE_COLOR[j][1] == b) { found = j; o = 0; }
                else if (EDGE_COLOR[j][0] == b && EDGE_COLOR[j][1] == a) { found = j; o = 1; }
            }
            if (found < 0) throw new CubeError(CubeError.EDGE_INVALID, i, 0);
            c.ep[i] = (byte) found;
            c.eo[i] = (byte) o;
        }
        for (int i = 0; i < 8; i++)
            for (int j = i + 1; j < 8; j++)
                if (c.cp[i] == c.cp[j]) throw new CubeError(CubeError.CORNER_DUP, i, j);
        for (int i = 0; i < 12; i++)
            for (int j = i + 1; j < 12; j++)
                if (c.ep[i] == c.ep[j]) throw new CubeError(CubeError.EDGE_DUP, i, j);
        int tw = 0, fl = 0;
        for (int i = 0; i < 8; i++) tw += c.co[i];
        for (int i = 0; i < 12; i++) fl += c.eo[i];
        if (tw % 3 != 0) throw new CubeError(CubeError.TWIST, tw % 3, 0);
        if (fl % 2 != 0) throw new CubeError(CubeError.FLIP, 0, 0);
        if (parity(c.cp) != parity(c.ep)) throw new CubeError(CubeError.PARITY, 0, 0);
        return c;
    }

    static int parity(byte[] p) {
        int s = 0;
        for (int i = 0; i < p.length; i++)
            for (int j = i + 1; j < p.length; j++) if (p[j] < p[i]) s++;
        return s & 1;
    }

    // ---------------- Toạ độ cho thuật toán hai pha ----------------
    static final int[] MASK_TO_IDX = new int[4096];
    static final int[] IDX_TO_MASK = new int[495];
    public static final int SLICE_SOLVED;

    static {
        int n = 0;
        for (int m = 0; m < 4096; m++) {
            if (Integer.bitCount(m) == 4) { MASK_TO_IDX[m] = n; IDX_TO_MASK[n++] = m; }
            else MASK_TO_IDX[m] = -1;
        }
        SLICE_SOLVED = MASK_TO_IDX[0xF00];
    }

    int getTwist() { int t = 0; for (int i = 0; i < 7; i++) t = t * 3 + co[i]; return t; }

    void setTwist(int t) {
        int s = 0;
        for (int i = 6; i >= 0; i--) { co[i] = (byte) (t % 3); s += co[i]; t /= 3; }
        co[7] = (byte) ((3 - s % 3) % 3);
    }

    int getFlip() { int t = 0; for (int i = 0; i < 11; i++) t = t * 2 + eo[i]; return t; }

    void setFlip(int t) {
        int s = 0;
        for (int i = 10; i >= 0; i--) { eo[i] = (byte) (t & 1); s += eo[i]; t >>= 1; }
        eo[11] = (byte) (s & 1);
    }

    int getSlice() {
        int m = 0;
        for (int i = 0; i < 12; i++) if (ep[i] >= 8) m |= 1 << i;
        return MASK_TO_IDX[m];
    }

    void setSlice(int idx) {
        int m = IDX_TO_MASK[idx], s = 8, o = 0;
        for (int i = 0; i < 12; i++) ep[i] = (byte) (((m >> i) & 1) != 0 ? s++ : o++);
    }

    static int rank(byte[] a, int off, int n) {
        int r = 0;
        for (int i = 0; i < n; i++) {
            int c = 0;
            for (int j = i + 1; j < n; j++) if (a[off + j] < a[off + i]) c++;
            r = r * (n - i) + c;
        }
        return r;
    }

    static void unrank(byte[] a, int off, int n, int base, int r) {
        int[] dig = new int[n];
        for (int i = n - 1; i >= 0; i--) { dig[i] = r % (n - i); r /= (n - i); }
        boolean[] used = new boolean[n];
        for (int i = 0; i < n; i++) {
            int k = dig[i];
            for (int v = 0; v < n; v++) {
                if (used[v]) continue;
                if (k-- == 0) { used[v] = true; a[off + i] = (byte) (base + v); break; }
            }
        }
    }

    int getCPerm() { return rank(cp, 0, 8); }
    void setCPerm(int r) { unrank(cp, 0, 8, 0, r); }
    int getUDEPerm() { return rank(ep, 0, 8); }
    void setUDEPerm(int r) { unrank(ep, 0, 8, 0, r); for (int i = 8; i < 12; i++) ep[i] = (byte) i; }
    int getSlicePerm() { return rank(ep, 8, 4); }
    void setSlicePerm(int r) { for (int i = 0; i < 8; i++) ep[i] = (byte) i; unrank(ep, 8, 4, 8, r); }

    /** 18 bước xoay cơ bản, suy ra từ hình học của CubeModel. */
    public static final Cubie[] MOVES = new Cubie[18];

    static {
        int[] solved = CubeModel.solvedLabels();
        for (int f = 0; f < 6; f++) {
            Cubie base;
            try {
                base = fromFacelets(CubeModel.apply(solved, f * 3));
            } catch (CubeError e) {
                throw new IllegalStateException("Bảng di chuyển sai", e);
            }
            MOVES[f * 3] = base;
            MOVES[f * 3 + 1] = base.mul(base);
            MOVES[f * 3 + 2] = MOVES[f * 3 + 1].mul(base);
        }
    }
}
