package com.dan.heidelberg.lupe;

/**
 * Orbit-Kamera: dreht die Szene um den Ursprung (yaw/pitch) und projiziert
 * perspektivisch. Die Kamera sitzt im Sichtraum bei (0, 0, distance) und
 * blickt Richtung -z.
 */
public final class Camera {

    private double yaw;              // Bogenmass, um Y
    private double pitch;            // Bogenmass, um X
    private double distance = 44.0;  // Weltabstand zum Ursprung
    private double fovDeg   = 34.0;

    private double cx, cy, focal;

    public double getYaw()               { return yaw; }
    public void   setYaw(double yaw)     { this.yaw = yaw; }
    public double getPitch()             { return pitch; }
    public void   setPitch(double p)     { this.pitch = Math.max(-1.45, Math.min(1.45, p)); }
    public double getDistance()          { return distance; }
    public void   setDistance(double d)  { this.distance = Math.max(18.0, Math.min(90.0, d)); }
    public double getFovDeg()            { return fovDeg; }
    public void   setFovDeg(double f)    { this.fovDeg = f; }

    /** Muss vor jedem Frame mit der Viewport-Groesse aufgerufen werden. */
    public void setViewport(int width, int height) {
        cx = width / 2.0;
        cy = height / 2.0;
        focal = (Math.min(width, height) / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);
    }

    /** Weltpunkt in den Sichtraum drehen. */
    public Vec3 toView(Vec3 p) {
        return p.rotY(yaw).rotX(pitch);
    }

    /** Richtung (ohne Translation) in den Sichtraum drehen. */
    public Vec3 dirToView(Vec3 d) {
        return d.rotY(yaw).rotX(pitch);
    }

    /** Position der Kamera im Sichtraum. */
    public Vec3 eyeInView() {
        return new Vec3(0, 0, distance);
    }

    /** Abstand eines Sichtraumpunkts zur Kamera entlang der Blickachse. */
    public double depth(Vec3 v) {
        return distance - v.z;
    }

    public double screenX(Vec3 v)    { return cx + focal * v.x / depth(v); }
    public double screenY(Vec3 v)    { return cy - focal * v.y / depth(v); }
    /** Pixel pro Welteinheit an der Tiefe des Punkts. */
    public double scaleAt(Vec3 v)    { return focal / depth(v); }

    public double centerX()          { return cx; }
    public double centerY()          { return cy; }
}
