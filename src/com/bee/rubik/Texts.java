package com.bee.rubik;

/** Các câu hướng dẫn và thông báo lỗi tiếng Việt. */
final class Texts {
    static final String[] FACE_VN = {"TRÊN", "PHẢI", "TRƯỚC", "DƯỚI", "TRÁI", "SAU"};
    static final String[] CORNER_VN = {"Trên-Phải-Trước", "Trên-Trước-Trái", "Trên-Trái-Sau", "Trên-Sau-Phải",
            "Dưới-Trước-Phải", "Dưới-Trái-Trước", "Dưới-Sau-Trái", "Dưới-Phải-Sau"};
    static final String[] EDGE_VN = {"Trên-Phải", "Trên-Trước", "Trên-Trái", "Trên-Sau", "Dưới-Phải", "Dưới-Trước",
            "Dưới-Trái", "Dưới-Sau", "Trước-Phải", "Trước-Trái", "Sau-Trái", "Sau-Phải"};

    private Texts() {}

    static String face(int f, String[] names) { return "mặt " + FACE_VN[f] + " (tâm " + names[f] + ")"; }

    static String moveDesc(int m, String[] n) {
        int f = m / 3, k = m % 3;
        String c = " (tâm " + n[f] + ")";
        if (k == 1) {
            switch (f) {
                case 0: return "Xoay tầng TRÊN" + c + " 180° (2 lần, chiều nào cũng được)";
                case 3: return "Xoay tầng DƯỚI" + c + " 180° (2 lần)";
                case 1: return "Xoay cột PHẢI" + c + " 180° (2 lần)";
                case 4: return "Xoay cột TRÁI" + c + " 180° (2 lần)";
                case 2: return "Xoay mặt TRƯỚC" + c + " 180° (2 lần)";
                default: return "Xoay mặt SAU" + c + " 180° (2 lần)";
            }
        }
        boolean cw = k == 0;
        switch (f) {
            case 0: return "Xoay tầng TRÊN" + c + " sang " + (cw ? "TRÁI" : "PHẢI") + " 90°";
            case 3: return "Xoay tầng DƯỚI" + c + " sang " + (cw ? "PHẢI" : "TRÁI") + " 90°";
            case 1: return "Xoay cột PHẢI" + c + (cw ? " LÊN trên" : " XUỐNG dưới") + " 90°";
            case 4: return "Xoay cột TRÁI" + c + (cw ? " XUỐNG dưới" : " LÊN trên") + " 90°";
            case 2: return "Xoay mặt TRƯỚC" + c + (cw ? " THEO" : " NGƯỢC") + " chiều kim đồng hồ 90°";
            default: return "Xoay mặt SAU" + c + (cw ? " NGƯỢC" : " THEO") + " chiều kim đồng hồ 90° (khi nhìn từ phía trước)";
        }
    }

    static String cornerName(int i, String[] n) {
        int[] c = Cubie.CORNER_COLOR[i];
        return "ô góc " + CORNER_VN[i] + " (giữa 3 mặt tâm " + n[c[0]] + ", " + n[c[1]] + ", " + n[c[2]] + ")";
    }

    static String edgeName(int i, String[] n) {
        int[] c = Cubie.EDGE_COLOR[i];
        return "ô cạnh " + EDGE_VN[i] + " (giữa 2 mặt tâm " + n[c[0]] + ", " + n[c[1]] + ")";
    }

    static String colorsAt(int[] facelets, int[] labels, String[] n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < facelets.length; i++) {
            if (i > 0) sb.append(" - ");
            sb.append(n[labels[facelets[i]]]);
        }
        return sb.toString();
    }

    static final String RESCAN_TIP = "\n\nNếu bạn chắc rubik chưa bị tháo/sửa, lỗi có thể do quét sai màu: "
            + "hãy quét lại ở nơi đủ sáng, tránh lóa, giữ rubik vừa khung lưới.";

    static String title(CubeError e) {
        switch (e.type) {
            case CubeError.TWIST: return "⚠ Ô góc bị vặn lệch";
            case CubeError.FLIP: return "⚠ Ô cạnh bị lật ngược";
            case CubeError.PARITY: return "⚠ Hai ô bị đổi chỗ";
            case CubeError.CORNER_INVALID:
            case CubeError.CORNER_DUP: return "⚠ Ô góc không hợp lệ";
            case CubeError.EDGE_INVALID:
            case CubeError.EDGE_DUP: return "⚠ Ô cạnh không hợp lệ";
            default: return "⚠ Màu sắc không hợp lệ";
        }
    }

    static String error(CubeError e, int[] labels, String[] n) {
        switch (e.type) {
            case CubeError.TWIST: {
                String dir = e.a == 1 ? "NGƯỢC chiều kim đồng hồ" : "THEO chiều kim đồng hồ";
                return "Phần mềm phát hiện một ô GÓC đã bị vặn tại chỗ (rubik đã bị chỉnh sửa vị trí ô góc). "
                        + "Ở trạng thái này rubik KHÔNG THỂ giải được bằng cách xoay thông thường.\n\n"
                        + "Cách sửa: dùng tay vặn " + cornerName(0, n) + " (đang nhấp nháy trên hình 3D) "
                        + dir + " 1 nấc (120°) khi nhìn thẳng vào góc đó. "
                        + "Nếu bạn biết chính xác ô góc nào đã bị vặn, hãy vặn ô đó theo cùng chiều.\n\n"
                        + "Sau đó bấm \"Quét lại từ đầu\"." + RESCAN_TIP;
            }
            case CubeError.FLIP:
                return "Phần mềm phát hiện một ô CẠNH đã bị lật ngược (bị tháo ra và lắp ngược). "
                        + "Rubik ở trạng thái này không thể giải được.\n\n"
                        + "Cách sửa: tháo " + edgeName(1, n) + " (đang nhấp nháy), lật ngược 2 màu rồi lắp lại, "
                        + "sau đó bấm \"Quét lại từ đầu\"." + RESCAN_TIP;
            case CubeError.PARITY:
                return "Phần mềm phát hiện hai ô đã bị đổi chỗ cho nhau (tháo ra lắp lại sai vị trí). "
                        + "Rubik ở trạng thái này không thể giải được.\n\n"
                        + "Cách sửa: tháo " + edgeName(0, n) + " và " + edgeName(1, n)
                        + " (đang nhấp nháy), đổi chỗ 2 ô này cho nhau rồi lắp lại, sau đó quét lại." + RESCAN_TIP;
            case CubeError.CORNER_INVALID:
                return "Tại vị trí " + cornerName(e.a, n) + " có tổ hợp màu không tồn tại trên rubik thật: "
                        + colorsAt(Cubie.CORNER_FACELET[e.a], labels, n) + ".\n\n"
                        + "Có thể sticker ô góc này đã bị bóc/dán lại sai, hoặc màu bị nhận diện nhầm. "
                        + "Hãy kiểm tra ô góc đang nhấp nháy, sửa lại sticker nếu cần, rồi quét lại các mặt chứa ô này."
                        + RESCAN_TIP;
            case CubeError.EDGE_INVALID:
                return "Tại vị trí " + edgeName(e.a, n) + " có tổ hợp màu không tồn tại: "
                        + colorsAt(Cubie.EDGE_FACELET[e.a], labels, n) + ".\n\n"
                        + "Hãy kiểm tra ô cạnh đang nhấp nháy rồi quét lại các mặt chứa ô này." + RESCAN_TIP;
            case CubeError.CORNER_DUP:
                return "Có 2 ô góc giống hệt nhau: " + cornerName(e.a, n) + " và " + cornerName(e.b, n)
                        + ". Trên rubik thật mỗi ô góc chỉ có một.\n\nHãy quét lại các mặt chứa những ô đang nhấp nháy."
                        + RESCAN_TIP;
            case CubeError.EDGE_DUP:
                return "Có 2 ô cạnh giống hệt nhau: " + edgeName(e.a, n) + " và " + edgeName(e.b, n)
                        + ".\n\nHãy quét lại các mặt chứa những ô đang nhấp nháy." + RESCAN_TIP;
            default:
                return "Số ô của mỗi màu không bằng 9 hoặc các ô tâm bị trùng màu.\n\nHãy quét lại." + RESCAN_TIP;
        }
    }

    static int[] errorFacelets(CubeError e) {
        switch (e.type) {
            case CubeError.TWIST: return Cubie.CORNER_FACELET[0];
            case CubeError.FLIP: return Cubie.EDGE_FACELET[1];
            case CubeError.PARITY: return concat(Cubie.EDGE_FACELET[0], Cubie.EDGE_FACELET[1]);
            case CubeError.CORNER_INVALID: return Cubie.CORNER_FACELET[e.a];
            case CubeError.EDGE_INVALID: return Cubie.EDGE_FACELET[e.a];
            case CubeError.CORNER_DUP: return concat(Cubie.CORNER_FACELET[e.a], Cubie.CORNER_FACELET[e.b]);
            case CubeError.EDGE_DUP: return concat(Cubie.EDGE_FACELET[e.a], Cubie.EDGE_FACELET[e.b]);
            default: return null;
        }
    }

    private static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
