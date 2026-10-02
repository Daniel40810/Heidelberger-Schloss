package com.dan.heidelberg.lupe;

/**
 * Unveraenderlicher 3D-Vektor fuer den Atom-Renderer.
 */
public final class Vec3 {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 add(Vec3 o)        { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o)        { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(double s)    { return new Vec3(x * s, y * s, z * s); }
    public double dot(Vec3 o)      { return x * o.x + y * o.y + z * o.z; }
    public double length()         { return Math.sqrt(x * x + y * y + z * z); }

    public Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public Vec3 normalize() {
        double l = length();
        return l < 1e-12 ? this : new Vec3(x / l, y / l, z / l);
    }

    /** Drehung um die X-Achse (Bogenmass). */
    public Vec3 rotX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(x, y * c - z * s, y * s + z * c);
    }

    /** Drehung um die Y-Achse (Bogenmass). */
    public Vec3 rotY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(x * c + z * s, y, -x * s + z * c);
    }

    /** Drehung um die Z-Achse (Bogenmass). */
    public Vec3 rotZ(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(x * c - y * s, x * s + y * c, z);
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", x, y, z);
    }
}
