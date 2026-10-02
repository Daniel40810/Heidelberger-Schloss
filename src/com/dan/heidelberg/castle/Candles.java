package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Kerzen und Lampen der Innenräume (Phase 8). Beim Bau entstehen die Wachskerzen als Dreiecke, ihre Flammen und die
 * Lichter, die sie auf die Wände werfen, stehen in zwei Listen. {@link Torches} liest sie in jedem Bild und wählt die
 * nächsten aus. Jede Kerze gehört zu einer Gruppe: der Saal brennt, wenn es draußen dunkel wird; Fass und Grotte
 * brennen immer, weil dort kaum Tageslicht hinkommt.
 */
public final class Candles {
    private Candles() { }

    public static final int SAAL = 1, FASS = 2, GROTTE = 3;
    public static final int GROUPS = 4;

    public static final class Flame {
        public final double x, y, z;
        public final int group;
        public final float size;
        Flame(double x, double y, double z, int group, float size) { this.x = x; this.y = y; this.z = z; this.group = group; this.size = size; }
    }

    public static final class Light {
        public final double x, y, z;
        public final float r, g, b, radius;
        public final int group;
        Light(double x, double y, double z, float k, float radius, int group) {
            this.x = x; this.y = y; this.z = z; this.radius = radius; this.group = group;
            this.r = k; this.g = k * 0.50f; this.b = k * 0.18f;
        }
    }

    public static final List<Flame> FLAMES = new ArrayList<>();
    public static final List<Light> LIGHTS = new ArrayList<>();

    public static void clear() { FLAMES.clear(); LIGHTS.clear(); }

    /** Wachskerze von 22 cm auf einem Halter bei (x, y, z) und ihre Flamme. */
    static void candle(MeshBuilder mb, double x, double y, double z, int group) {
        mb.cylinder(x, z, y, y + 0.22, 0.024, 0.021, 6, Mat.WAX, true);
        FLAMES.add(new Flame(x, y + 0.27, z, group, 1f));
    }

    /** Ein Licht, das die Wände ringsum anstrahlt (k = Stärke, radius in Metern). */
    static void light(double x, double y, double z, float k, float radius, int group) {
        LIGHTS.add(new Light(x, y, z, k, radius, group));
    }
}
