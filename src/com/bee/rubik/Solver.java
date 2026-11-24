package com.bee.rubik;

import java.util.Arrays;

/**
 * Bộ giải hai pha Kociemba. Sau khi tìm được lời giải đầu tiên, tiếp tục tìm kiếm
 * với giới hạn độ dài giảm dần cho tới khi hết thời gian, để trả về lời giải ngắn nhất tìm được.
 */
public final class Solver {
    static final int N_TWIST = 2187, N_FLIP = 2048, N_SLICE = 495, N_PERM8 = 40320, N_PERM4 = 24;
    static final int[] P2 = {0, 1, 2, 4, 7, 9, 10, 11, 13, 16};
    static final boolean[] IS_P2 = new boolean[18];

    static int[] twistMove, flipMove, sliceMove;   // [coord*18 + m]
    static int[] cpMove, epMove, spMove;           // [coord*10 + k]
    static byte[] prunST, prunSF, prunCS, prunES;
    private static volatile boolean ready;

    static { for (int m : P2) IS_P2[m] = true; }

    public static boolean isReady() { return ready; }

    public static synchronized void init() {
        if (ready) return;
        Cubie[] mv = Cubie.MOVES;
        twistMove = new int[N_TWIST * 18];
        flipMove = new int[N_FLIP * 18];
        sliceMove = new int[N_SLICE * 18];
        Cubie c = new Cubie();
        for (int i = 0; i < N_TWIST; i++) {
            c.setTwist(i);
            for (int m = 0; m < 18; m++) twistMove[i * 18 + m] = c.mul(mv[m]).getTwist();
        }
        c = new Cubie();
        for (int i = 0; i < N_FLIP; i++) {
            c.setFlip(i);
            for (int m = 0; m < 18; m++) flipMove[i * 18 + m] = c.mul(mv[m]).getFlip();
        }
        c = new Cubie();
        for (int i = 0; i < N_SLICE; i++) {
            c.setSlice(i);
            for (int m = 0; m < 18; m++) sliceMove[i * 18 + m] = c.mul(mv[m]).getSlice();
        }
        cpMove = new int[N_PERM8 * 10];
        epMove = new int[N_PERM8 * 10];
        spMove = new int[N_PERM4 * 10];
        c = new Cubie();
        Cubie e = new Cubie();
        for (int i = 0; i < N_PERM8; i++) {
            c.setCPerm(i);
            e.setUDEPerm(i);
            for (int k = 0; k < 10; k++) {
                cpMove[i * 10 + k] = c.mul(mv[P2[k]]).getCPerm();
                epMove[i * 10 + k] = e.mul(mv[P2[k]]).getUDEPerm();
            }
        }
        c = new Cubie();
        for (int i = 0; i < N_PERM4; i++) {
            c.setSlicePerm(i);
            for (int k = 0; k < 10; k++) spMove[i * 10 + k] = c.mul(mv[P2[k]]).getSlicePerm();
        }
        int ss = Cubie.SLICE_SOLVED;
        prunST = bfs(N_SLICE, N_TWIST, sliceMove, twistMove, 18, ss * N_TWIST);
        prunSF = bfs(N_SLICE, N_FLIP, sliceMove, flipMove, 18, ss * N_FLIP);
        prunCS = bfs(N_PERM8, N_PERM4, cpMove, spMove, 10, 0);
        prunES = bfs(N_PERM8, N_PERM4, epMove, spMove, 10, 0);
        ready = true;
    }

    static byte[] bfs(int nA, int nB, int[] mA, int[] mB, int nm, int start) {
        int total = nA * nB;
        byte[] p = new byte[total];
        Arrays.fill(p, (byte) -1);
        p[start] = 0;
        for (int depth = 0; depth < 30; depth++) {
            int added = 0;
            for (int i = 0; i < total; i++) {
                if (p[i] != depth) continue;
                int a = i / nB, b = i % nB;
                for (int m = 0; m < nm; m++) {
                    int j = mA[a * nm + m] * nB + mB[b * nm + m];
                    if (p[j] == -1) { p[j] = (byte) (depth + 1); added++; }
                }
            }
            if (added == 0) break;
        }
        return p;
    }

    // ---------------- Tìm kiếm ----------------
    private final int[] moves = new int[64];
    private Cubie start;
    private int maxTotal, maxPhase2;
    private int[] best;
    private long softDeadline, hardDeadline;
    private boolean aborted;
    private int nodes;

    /** Trả về dãy bước xoay (chỉ số 0..17), hoặc null nếu không tìm được trong thời gian cho phép. */
    public static int[] solve(Cubie c, long timeMs) {
        init();
        return new Solver().run(c, timeMs);
    }

    private int[] run(Cubie c, long timeMs) {
        start = c;
        long now = System.currentTimeMillis();
        softDeadline = now + timeMs;
        hardDeadline = now + Math.max(timeMs, 30000);
        int tw = c.getTwist(), fl = c.getFlip(), sl = c.getSlice();
        maxTotal = 26;
        maxPhase2 = 12;
        int d1 = 0;
        while (d1 <= maxTotal && !aborted) {
            if (search1(tw, fl, sl, 0, d1)) {
                maxTotal = best.length - 1;
                maxPhase2 = 99;
                continue; // thử lại cùng độ sâu pha 1 với giới hạn mới
            }
            if (aborted) break;
            d1++;
            if (d1 > maxTotal && best == null) { // dự phòng: nới giới hạn pha 2
                maxPhase2 = 99;
                d1 = 0;
                maxTotal = 30;
            }
        }
        return best;
    }

    private void tick() {
        if ((++nodes & 0x3FFF) != 0) return;
        long t = System.currentTimeMillis();
        if ((best != null && t > softDeadline) || t > hardDeadline) aborted = true;
    }

    private static boolean allowed(int last, int m) {
        if (last < 0) return true;
        int lf = last / 3, f = m / 3;
        if (lf == f) return false;
        return !(lf % 3 == f % 3 && f < lf);
    }

    private boolean search1(int tw, int fl, int sl, int n, int togo) {
        int h = Math.max(prunST[sl * N_TWIST + tw], prunSF[sl * N_FLIP + fl]);
        if (h > togo) return false;
        if (togo == 0) {
            if (n > 0 && IS_P2[moves[n - 1]]) return false;
            return phase2(n);
        }
        tick();
        if (aborted) return false;
        int last = n > 0 ? moves[n - 1] : -1;
        for (int m = 0; m < 18; m++) {
            if (!allowed(last, m)) continue;
            moves[n] = m;
            if (search1(twistMove[tw * 18 + m], flipMove[fl * 18 + m], sliceMove[sl * 18 + m], n + 1, togo - 1))
                return true;
            if (aborted) return false;
        }
        return false;
    }

    private boolean phase2(int n) {
        Cubie c = start;
        for (int i = 0; i < n; i++) c = c.mul(Cubie.MOVES[moves[i]]);
        int cp = c.getCPerm(), ep = c.getUDEPerm(), sp = c.getSlicePerm();
        int lim = Math.min(maxTotal - n, maxPhase2);
        int h = Math.max(prunCS[cp * N_PERM4 + sp], prunES[ep * N_PERM4 + sp]);
        for (int d2 = h; d2 <= lim; d2++) {
            if (search2(cp, ep, sp, n, d2)) {
                best = Arrays.copyOf(moves, n + d2);
                return true;
            }
            if (aborted) return false;
        }
        return false;
    }

    private boolean search2(int cp, int ep, int sp, int n, int togo) {
        if (togo == 0) return cp == 0 && ep == 0 && sp == 0;
        tick();
        if (aborted) return false;
        int last = n > 0 ? moves[n - 1] : -1;
        for (int k = 0; k < 10; k++) {
            int m = P2[k];
            if (!allowed(last, m)) continue;
            int ncp = cpMove[cp * 10 + k], nep = epMove[ep * 10 + k], nsp = spMove[sp * 10 + k];
            int h = Math.max(prunCS[ncp * N_PERM4 + nsp], prunES[nep * N_PERM4 + nsp]);
            if (h > togo - 1) continue;
            moves[n] = m;
            if (search2(ncp, nep, nsp, n + 1, togo - 1)) return true;
        }
        return false;
    }
}
