package com.bee.rubik;

/** Lỗi khi rubik quét được không hợp lệ (không thể có trên rubik thật). */
public class CubeError extends Exception {
    public static final int COLOR_COUNT = 1;   // a = nhãn màu, b = số lượng
    public static final int CORNER_INVALID = 2; // a = vị trí góc
    public static final int EDGE_INVALID = 3;   // a = vị trí cạnh
    public static final int CORNER_DUP = 4;     // a = vị trí góc thứ nhất, b = vị trí góc thứ hai
    public static final int EDGE_DUP = 5;       // a, b = vị trí cạnh
    public static final int TWIST = 6;          // a = tổng độ vặn (1 hoặc 2)
    public static final int FLIP = 7;
    public static final int PARITY = 8;

    public final int type, a, b;

    public CubeError(int type, int a, int b) {
        super("CubeError " + type + " " + a + " " + b);
        this.type = type;
        this.a = a;
        this.b = b;
    }
}
