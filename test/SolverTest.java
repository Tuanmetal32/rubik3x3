import com.bee.rubik.*;
import java.util.*;

public class SolverTest {
    public static void main(String[] a) throws Exception {
        // 1) Mỗi bước xoay 4 lần = đồng nhất; cubie và facelet khớp nhau
        int[] solved = CubeModel.solvedLabels();
        for (int m = 0; m < 18; m += 3) {
            int[] s = solved;
            for (int i = 0; i < 4; i++) s = CubeModel.apply(s, m);
            if (!Arrays.equals(s, solved)) throw new RuntimeException("move^4 != id " + m);
        }
        Random rnd = new Random(1);
        long t0 = System.currentTimeMillis();
        com.bee.rubik.Solver.init();
        System.out.println("init ms " + (System.currentTimeMillis() - t0));
        int total = 0, N = 20;
        for (int t = 0; t < N; t++) {
            int[] f = solved;
            Cubie c = new Cubie();
            for (int i = 0; i < 30; i++) {
                int m = rnd.nextInt(18);
                f = CubeModel.apply(f, m);
                c = c.mul(Cubie.MOVES[m]);
            }
            if (!Arrays.equals(f, c.toFacelets())) throw new RuntimeException("facelet/cubie mismatch");
            Cubie p = Cubie.fromFacelets(f);
            long s0 = System.currentTimeMillis();
            int[] sol = com.bee.rubik.Solver.solve(p, 3000);
            long dt = System.currentTimeMillis() - s0;
            for (int m : sol) f = CubeModel.apply(f, m);
            if (!Arrays.equals(f, solved)) throw new RuntimeException("NOT SOLVED");
            total += sol.length;
            StringBuilder sb = new StringBuilder();
            for (int m : sol) sb.append(CubeModel.MOVE_NAMES[m]).append(' ');
            System.out.println(sol.length + " moves, " + dt + "ms: " + sb);
        }
        System.out.println("avg " + (total / (double) N));
        // 2) Kiểm tra phát hiện lỗi
        int[] f = solved.clone();
        // vặn góc URF
        int x = f[8]; f[8] = f[20]; f[20] = f[9]; f[9] = x;
        expect(f, CubeError.TWIST);
        f = solved.clone(); x = f[5]; f[5] = f[10]; f[10] = x; expect(f, CubeError.FLIP);
        f = solved.clone(); // đổi chỗ 2 cạnh UR, UF
        x = f[5]; f[5] = f[7]; f[7] = x; x = f[10]; f[10] = f[19]; f[19] = x; expect(f, CubeError.PARITY);
        f = solved.clone(); x = f[8]; f[8] = f[29]; f[29] = x; expect(f, CubeError.CORNER_INVALID);
        System.out.println("ALL OK");
    }

    static void expect(int[] f, int type) {
        try { Cubie.fromFacelets(f); throw new RuntimeException("no error, expected " + type); }
        catch (CubeError e) { if (e.type != type) throw new RuntimeException("got " + e.type + " expected " + type); }
    }
}
