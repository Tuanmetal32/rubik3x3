package com.bee.rubik;

/** Quaternion tối giản (w, x, y, z) cho việc xoay và nội suy góc nhìn 3D. */
final class Quat {
    static final float[] IDENT = {1, 0, 0, 0};

    private Quat() {}

    static float[] axisAngle(float x, float y, float z, float deg) {
        double r = Math.toRadians(deg) / 2;
        float s = (float) Math.sin(r);
        return new float[]{(float) Math.cos(r), x * s, y * s, z * s};
    }

    static float[] mul(float[] a, float[] b) {
        return new float[]{
                a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
                a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
                a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
                a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]};
    }

    static float[] slerp(float[] a, float[] b, float t) {
        float d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        float[] bb = b;
        if (d < 0) { d = -d; bb = new float[]{-b[0], -b[1], -b[2], -b[3]}; }
        float s0, s1;
        if (d > 0.9995f) { s0 = 1 - t; s1 = t; }
        else {
            double th = Math.acos(d), sn = Math.sin(th);
            s0 = (float) (Math.sin((1 - t) * th) / sn);
            s1 = (float) (Math.sin(t * th) / sn);
        }
        float[] r = new float[4];
        float n = 0;
        for (int i = 0; i < 4; i++) { r[i] = s0 * a[i] + s1 * bb[i]; n += r[i] * r[i]; }
        n = (float) Math.sqrt(n);
        for (int i = 0; i < 4; i++) r[i] /= n;
        return r;
    }

    static float[] toMatrix(float[] q) {
        float w = q[0], x = q[1], y = q[2], z = q[3];
        return new float[]{
                1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
                2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
                2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)};
    }

    static float[] fromMatrix(float[] m) {
        float tr = m[0] + m[4] + m[8], w, x, y, z;
        if (tr > 0) {
            float s = (float) Math.sqrt(tr + 1) * 2;
            w = 0.25f * s; x = (m[7] - m[5]) / s; y = (m[2] - m[6]) / s; z = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            float s = (float) Math.sqrt(1 + m[0] - m[4] - m[8]) * 2;
            w = (m[7] - m[5]) / s; x = 0.25f * s; y = (m[1] + m[3]) / s; z = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            float s = (float) Math.sqrt(1 + m[4] - m[0] - m[8]) * 2;
            w = (m[2] - m[6]) / s; x = (m[1] + m[3]) / s; y = 0.25f * s; z = (m[5] + m[7]) / s;
        } else {
            float s = (float) Math.sqrt(1 + m[8] - m[0] - m[4]) * 2;
            w = (m[3] - m[1]) / s; x = (m[2] + m[6]) / s; y = (m[5] + m[7]) / s; z = 0.25f * s;
        }
        return new float[]{w, x, y, z};
    }

    /** Hướng sao cho mặt "front" (thế giới) quay về người xem, "up" hướng lên trên màn hình. */
    static float[] orient(int[] front, int[] up) {
        int[] r = {up[1] * front[2] - up[2] * front[1], up[2] * front[0] - up[0] * front[2], up[0] * front[1] - up[1] * front[0]};
        return fromMatrix(new float[]{r[0], r[1], r[2], up[0], up[1], up[2], front[0], front[1], front[2]});
    }

    /** Góc nhìn nghiêng: yaw quanh trục y, rồi pitch quanh trục x. */
    static float[] tilt(float yaw, float pitch) {
        return mul(axisAngle(1, 0, 0, pitch), axisAngle(0, 1, 0, yaw));
    }
}
