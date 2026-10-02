package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.core.Wetness;
import com.dan.heidelberg.effects.ParticleSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5: Brunnen und Kaskaden des Hortus. Jede Fontäne ist eine Schar Tropfen auf Wurfbahnen, die das
 * {@link ParticleSystem} mit Schwerkraft, Luftwiderstand und Wind rechnet. Jede Düse bekommt ihre
 * Austrittsgeschwindigkeit aus der gewünschten Höhe oder Weite: dieselbe Rechnung wie im Teilchensystem
 * (gleicher Zeitschritt, gleicher Luftwiderstand), die Höhe stimmt also ohne Wind auf wenige Zentimeter.
 * Beim Aufprall entstehen Spritzer und nasse Ränder (vom Teilchensystem) und Ringwellen im Becken (hier).
 *
 * <p>Lagen und Wasserhöhen kommen aus {@link Hortus}; Höhen und Weiten der Strahlen sind geschätzt, die
 * Überlieferung kennt nur "Wasserkünste" (Fontänen, Kaskade, Wandbrunnen), keine Strahlhöhen.
 */
public final class Fountains {
    /** Fester Zeitschritt von Emission und Teilchenrechnung. */
    public static final float STEP = 1f / 60f;

    private static final int NONE = 0, POINT = 1, RING = 2, SHEET = 3;

    /** Eine Düse oder Kante. */
    static final class Jet {
        int type = POINT;
        double x, y, z, floorY;
        /** Anfangsgeschwindigkeit ohne Wind (Betrag und Richtung). */
        float vx, vy, vz;
        float rate, spread, size = 0.04f;
        /** SHEET: Kante läuft entlang z statt entlang x (Fall über eine Mauer nach Westen). */
        boolean alongZ;
        /** RING: Radius der Kante; SHEET: Halbbreite in x. */
        float r;
        /** Gischt je Sekunde am höchsten Punkt (0: keine). */
        float mist;
        /** Soll und berechnet (zur Prüfung): Anstieg über der Düse und Wurfweite bis zum Wasser. */
        float wantRise = Float.NaN, apex, range;
        /** Ringwellen je Sekunde am Auftreffpunkt. */
        float rings;
        double acc, accMist, accRing;
        String name;
    }

    /** Ein Brunnen: Düsen, Mittelpunkt für die Entfernungsstufe. */
    public static final class Fountain {
        public final String name;
        final double x, z;
        final List<Jet> jets = new ArrayList<>();
        Fountain(String name, double x, double z) { this.name = name; this.x = x; this.z = z; }
        public double x() { return x; }
        public double z() { return z; }
        public int jetCount() { return jets.size(); }
    }

    public final List<Fountain> list = new ArrayList<>();
    public volatile boolean enabled = true;
    /** Dichte der Tropfen (1 = Vorgabe), für den Lasttest bis 8. */
    public volatile float density = 1;
    /** Entfernung der Kamera, ab der ein Brunnen nicht mehr sprudelt. */
    public static final double RANGE = 330;

    // Ringwellen als Ringpuffer {x, z, t0, Stärke}
    private static final int RINGS = 40;
    private final float[][] ring = new float[RINGS][];
    private int ringHead;
    private double now;

    public Fountains() { layout(); }

    // ------------------------------------------------------------ Berechnung der Wurfbahn

    /** Wurf mit dem Integrator des Teilchensystems (ohne Wind): {Anstieg, Weite beim Auftreffen auf Höhe floor − y0, Flugzeit}. */
    static float[] flight(double speed, double elevRad, double drop) {
        float vh = (float) (speed * Math.cos(elevRad)), vy = (float) (speed * Math.sin(elevRad));
        float x = 0, y = 0, apex = 0, t = 0;
        for (int i = 0; i < 6000; i++) {
            float sp = (float) Math.sqrt(vh * vh + vy * vy);
            float d = ParticleSystem.K_DROP * sp * STEP;
            if (d > 0.9f) d = 0.9f;
            vh -= vh * d; vy -= vy * d;
            vy -= ParticleSystem.G * STEP;
            x += vh * STEP; y += vy * STEP; t += STEP;
            if (y > apex) apex = y;
            if (y <= -drop && vy < 0) break;
        }
        return new float[]{apex, x, t};
    }

    /** Austrittsgeschwindigkeit für einen senkrechten Strahl mit Anstieg rise (Meter über der Düse). */
    static double speedForRise(double rise) {
        double lo = 0.5, hi = 60;
        for (int i = 0; i < 50; i++) {
            double m = (lo + hi) / 2;
            if (flight(m, Math.PI / 2, 0)[0] < rise) lo = m; else hi = m;
        }
        return (lo + hi) / 2;
    }

    /** Austrittsgeschwindigkeit für eine Wurfweite range bei Neigung elev, wenn das Wasser drop Meter unter der Düse liegt. */
    static double speedForRange(double range, double elevRad, double drop) {
        double lo = 0.5, hi = 40;
        for (int i = 0; i < 50; i++) {
            double m = (lo + hi) / 2;
            if (flight(m, elevRad, drop)[1] < range) lo = m; else hi = m;
        }
        return (lo + hi) / 2;
    }

    // ------------------------------------------------------------ Düsen anlegen

    private Fountain cur;

    private Fountain begin(String name, double x, double z) {
        cur = new Fountain(name, x, z);
        list.add(cur);
        return cur;
    }

    /** Senkrechter Strahl von der Höhe y, Anstieg rise, aufs Wasser in floorY. */
    private Jet vertical(double x, double y, double z, double floorY, double rise, float rate, float spread, float mist) {
        Jet j = new Jet();
        j.x = x; j.y = y; j.z = z; j.floorY = floorY;
        double v = speedForRise(rise);
        j.vy = (float) v;
        j.rate = rate; j.spread = spread; j.mist = mist; j.wantRise = (float) rise;
        float[] f = flight(v, Math.PI / 2, y - floorY);
        j.apex = f[0]; j.range = 0; j.rings = Math.min(4, rate / 70f);
        cur.jets.add(j);
        return j;
    }

    /** Bogen aus Richtung az (Bogenmaß, 0 = +x, π/2 = +z) mit Neigung elevDeg und Wurfweite range bis aufs Wasser. */
    private Jet arc(double x, double y, double z, double floorY, double az, double elevDeg, double range, float rate, float spread, float size) {
        Jet j = new Jet();
        j.x = x; j.y = y; j.z = z; j.floorY = floorY;
        double e = Math.toRadians(elevDeg), drop = y - floorY;
        double v = speedForRange(range, e, drop);
        float vh = (float) (v * Math.cos(e));
        j.vx = vh * (float) Math.cos(az); j.vz = vh * (float) Math.sin(az); j.vy = (float) (v * Math.sin(e));
        j.rate = rate; j.spread = spread; j.size = size;
        float[] f = flight(v, e, drop);
        j.apex = f[0]; j.range = f[1]; j.rings = Math.min(2, rate / 60f);
        cur.jets.add(j);
        return j;
    }

    /** Wasserschleier über einen Kreisrand: Tropfen fallen mit wenig Schwung nach außen. */
    private Jet veil(double cx, double y, double cz, double r, double floorY, float rate) {
        Jet j = new Jet();
        j.type = RING;
        j.x = cx; j.y = y; j.z = cz; j.floorY = floorY; j.r = (float) r;
        j.rate = rate; j.spread = 0.15f; j.size = 0.03f; j.vy = 0;
        j.vx = 0.5f; // radial nach außen
        j.rings = Math.min(3, rate / 50f);
        cur.jets.add(j);
        return j;
    }

    /** Wasserschleier über eine gerade Kante (x0..x1 bei z), Fall mit Geschwindigkeit vz (negativ: nach Norden). */
    private Jet sheet(double x0, double x1, double y, double z, double floorY, float vz, float rate) {
        Jet j = new Jet();
        j.type = SHEET;
        j.x = (x0 + x1) / 2; j.r = (float) ((x1 - x0) / 2); j.y = y; j.z = z; j.floorY = floorY;
        j.vz = vz; j.rate = rate; j.spread = 0.12f; j.size = 0.03f;
        j.rings = Math.min(3, rate / 60f);
        cur.jets.add(j);
        return j;
    }

    /** Wasserschleier über eine Kante entlang z (z0..z1 bei x), Fall mit Geschwindigkeit vx (negativ: nach Westen). */
    private Jet sheetZ(double x, double z0, double z1, double y, double floorY, float vx, float rate) {
        Jet j = new Jet();
        j.type = SHEET;
        j.alongZ = true;
        j.x = x; j.r = (float) ((z1 - z0) / 2); j.y = y; j.z = (z0 + z1) / 2; j.floorY = floorY;
        j.vx = vx; j.rate = rate; j.spread = 0.12f; j.size = 0.03f;
        j.rings = Math.min(3, rate / 60f);
        cur.jets.add(j);
        return j;
    }

    private void layout() {
        final double ML = Hortus.ML, KL = Hortus.KL, GX = Hortus.GROSS_X, KX = Hortus.KLEIN_X;
        // 1. Achteckiges Becken, Mitte der Hauptterrasse: Mittelstrahl und acht Strahlen aus den Fratzen am Rand
        {
            double cx = -70, cz = 102, wy = ML + 0.5;
            begin("Achteckiges Becken", cx, cz);
            { Jet m = vertical(cx, ML + 1.45, cz, wy, 5.5, 650, 0.012f, 40); m.name = "Mittelstrahl"; m.size = 0.062f; }
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                arc(cx + 5.0 * Math.cos(a), ML + 0.95, cz + 5.0 * Math.sin(a), wy, a + Math.PI, 38, 3.3, 70, 0.02f, 0.032f).name = "Fratze " + (i + 1);
            }
        }
        // 2. Säulenbrunnen im ersten Knotenfeld: Sprudel in der oberen Schale, Schleier in das untere Becken
        {
            double cx = -118, cz = 111, wy = ML + 0.4;   // cols[0] + 10, rows[1] + 6.5
            begin("Säulenbrunnen", cx, cz);
            vertical(cx, ML + 4.5, cz, wy, 1.6, 110, 0.03f, 6).name = "Sprudel";
            veil(cx, ML + 4.45, cz, 0.72, wy, 150).name = "Schleier";
        }
        // 3. Rhenusbecken vor der Großen Grotte: der Gott gießt aus dem Krug, vier Sprudel in den Ecken
        {
            double bz0 = Hortus.M[1] - 7.5, bz1 = Hortus.M[1] - 2.5, y0 = KL, wy = y0 + 0.42, cz = (bz0 + bz1) / 2;
            begin("Rhenusbecken", GX, cz);
            arc(GX - 2.0, y0 + 1.5, cz, wy, -Math.PI / 2, 22, 1.9, 90, 0.03f, 0.03f).name = "Krug";
            for (int k = 0; k < 4; k++) {
                double bx = GX + (k % 2 == 0 ? -5.2 : 5.2), bz = cz + (k < 2 ? -1.3 : 1.3);
                vertical(bx, wy, bz, wy, 0.9, 45, 0.03f, 0).name = "Sprudel " + (k + 1);
            }
        }
        // 4. Kaskade in der Großen Grotte: drei Stufen, Wasser läuft nach Norden (zur Tür) ins Becken
        {
            double zf = Hortus.M[1] + 1.4, gz1 = zf + 14;
            begin("Kaskade der Großen Grotte", GX, gz1 - 3);
            for (int i = 0; i < 3; i++) {
                double zz = gz1 - 1.6 * (i + 1), h = KL + 0.05 + 1.5 - 0.5 * i;
                double below = i < 2 ? KL + 0.05 + 1.5 - 0.5 * (i + 1) + 0.02 : KL + 0.38;   // Stufe 3 fällt ins Becken der Halle
                sheet(GX - 4.4, GX + 4.4, h + 0.02, zz + 0.3, below, i < 2 ? -2f : -6f, 520).name = "Stufe " + (i + 1);
            }
            // Springbrunnen im Becken der Halle
            double wy = KL + 0.38;
            vertical(GX, wy, zf + 6.5, wy, 1.8, 120, 0.02f, 8).name = "Springbrunnen";
        }
        // 5. Wandbrunnen der Kleinen Grotte: Schale an der Rückwand, Strahl ins Becken
        {
            double kz1 = Hortus.M[1] + 1.4 + 9, wyTrough = KL + 1.12, wyBasin = KL + 0.4;
            begin("Wandbrunnen der Kleinen Grotte", KX, kz1 - 2);
            arc(KX, wyTrough + 0.05, kz1 - 1.0, wyBasin, -Math.PI / 2, 8, 1.7, 160, 0.03f, 0.03f).name = "Maul";
            sheet(KX - 0.5, KX + 0.5, wyTrough, kz1 - 1.0, wyBasin, -0.8f, 120).name = "Überlauf";
        }
        // 6. Wasserweg (Phase 9): Mauerfall in den Trog der Hauptterrasse
        {
            begin("Mauerfall (Wasserweg)", Wasserweg.QX, 119);
            sheet(Wasserweg.QX - 0.6, Wasserweg.QX + 0.6, Hortus.UL + 0.05, 120.1, ML + 0.55, -1.2f, 420).name = "Mauerfall";
        }
        // 7. Wasserweg: Fall des Abflusses über die Westmauer der Koniferenterrasse ins Tal (Landung auf dem Gelände)
        {
            begin("Abflussfall (Wasserweg)", Wasserweg.FALL_X, Wasserweg.FALL_Z);
            sheetZ(Wasserweg.FALL_X - 0.5, Wasserweg.FALL_Z - 0.4, Wasserweg.FALL_Z + 0.4, KL + 0.1, -1e9, -1.8f, 380).name = "Abflussfall";
        }
    }

    // ------------------------------------------------------------ Lauf

    /**
     * Gischtwolken für das Streulicht der Engine: je senkrechtem Strahl über 1,2 m eine Wolke um die Strahlachse
     * {x, z, y0, y1, Radius, Dichte je Meter, Drift x, Drift z}. Nur Brunnen im Umkreis {@link #RANGE}.
     */
    public float[][] sprays(double camX, double camZ, float windX, float windZ) {
        List<float[]> out = new ArrayList<>();
        if (!enabled) return new float[0][];
        for (Fountain f : list) {
            if (Math.hypot(f.x - camX, f.z - camZ) > RANGE) continue;
            for (Jet j : f.jets) {
                if (j.type != POINT || Float.isNaN(j.wantRise) || j.wantRise < 1.2f || j.vy < 3) continue;
                float rise = j.wantRise;
                out.add(new float[]{(float) j.x, (float) j.z, (float) (j.y + rise * 0.3), (float) (j.y + rise * 1.05),
                        0.5f + 0.16f * rise, Math.min(0.16f, 0.03f + 0.018f * rise) * Math.min(2f, density), windX * 0.05f, windZ * 0.05f});
            }
        }
        return out.toArray(new float[0][]);
    }

    /**
     * Ein fester Zeitschritt {@link #STEP}: Tropfen aus den Düsen, Gischt, Ringwellen. Danach ruft der Aufrufer
     * ps.step(STEP, …). Nur Brunnen im Umkreis {@link #RANGE} um die Kamera sprudeln.
     */
    public void emit(ParticleSystem ps, double camX, double camZ) {
        now += STEP;
        if (!enabled) return;
        float dens = Math.max(0.05f, Math.min(10f, density));
        for (Fountain f : list) {
            double dx = f.x - camX, dz = f.z - camZ;
            if (dx * dx + dz * dz > RANGE * RANGE) continue;
            for (Jet j : f.jets) {
                if (ps.n > ParticleSystem.CAP - 4000) return;
                j.acc += j.rate * dens * STEP;
                while (j.acc >= 1) { j.acc -= 1; drop(ps, j); }
                if (j.mist > 0) {
                    j.accMist += j.mist * Math.sqrt(dens) * STEP;
                    while (j.accMist >= 1) { j.accMist -= 1; mist(ps, j); }
                }
                if (j.rings > 0) {
                    j.accRing += j.rings * STEP;
                    while (j.accRing >= 1) { j.accRing -= 1; ring(ps, j); }
                }
            }
        }
    }

    private void drop(ParticleSystem ps, Jet j) {
        float px, py = (float) j.y, pz, ux = j.vx, uy = j.vy, uz = j.vz;
        px = (float) j.x; pz = (float) j.z;
        if (j.type == RING) {
            float a = ps.rand() * 6.2832f;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            px += ca * j.r; pz += sa * j.r;
            ux = ca * j.vx; uz = sa * j.vx;
        } else if (j.type == SHEET) {
            if (j.alongZ) pz += (ps.rand() * 2 - 1) * j.r; else px += (ps.rand() * 2 - 1) * j.r;
        } else {
            px += (ps.rand() - 0.5f) * 0.05f; pz += (ps.rand() - 0.5f) * 0.05f;
        }
        float sp = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        float s = j.spread * Math.max(sp, 1.5f);
        ux += (ps.rand() - 0.5f) * 2 * s; uz += (ps.rand() - 0.5f) * 2 * s; uy += (ps.rand() - 0.5f) * s * 0.3f;
        float k = 1 + (ps.rand() - 0.5f) * 0.008f;
        ux *= k; uy *= k; uz *= k;
        // gleichmäßig über den Zeitschritt verteilt, damit kein Takt entsteht
        float fr = ps.rand() * STEP;
        px += ux * fr; py += uy * fr - 0.5f * ParticleSystem.G * fr * fr; pz += uz * fr;
        uy -= ParticleSystem.G * fr;
        ps.spawn(ParticleSystem.DROP, px, py, pz, ux, uy, uz, j.size * (0.75f + 0.5f * ps.rand()), 9f, 0.75f, (float) j.floorY, 0);
    }

    private void mist(ParticleSystem ps, Jet j) {
        float top = (float) (j.y + j.apex);
        float px = (float) j.x + (ps.rand() - 0.5f) * 0.8f, pz = (float) j.z + (ps.rand() - 0.5f) * 0.8f;
        float py = top - ps.rand() * Math.min(1.5f, j.apex * 0.4f);
        int i = ps.spawn(ParticleSystem.SPRAY, px, py, pz, (ps.rand() - 0.5f) * 0.6f, 0.4f * ps.rand(), (ps.rand() - 0.5f) * 0.6f,
                0.35f + 0.2f * ps.rand(), 1.6f + ps.rand(), 0.16f, (float) j.floorY, 0);
        if (i >= 0) ps.grow[i] = 0.35f;
    }

    /** Ringwelle am Auftreffpunkt. */
    private void ring(ParticleSystem ps, Jet j) {
        double x = j.x, z = j.z;
        if (j.type == RING) {
            double a = ps.rand() * 6.2832;
            x += Math.cos(a) * (j.r + 0.4 + 0.3 * ps.rand()); z += Math.sin(a) * (j.r + 0.4 + 0.3 * ps.rand());
        } else if (j.type == SHEET) {
            if (j.alongZ) { z += (ps.rand() * 2 - 1) * j.r; x += j.vx * 0.35 * (0.5 + ps.rand()); }
            else { x += (ps.rand() * 2 - 1) * j.r; z += j.vz * 0.35 * (0.5 + ps.rand()); }
        } else {
            double len = Math.hypot(j.vx, j.vz);
            if (len > 1e-3) { x += j.vx / len * j.range; z += j.vz / len * j.range; }
            x += (ps.rand() - 0.5) * 0.6; z += (ps.rand() - 0.5) * 0.6;
        }
        ring[ringHead] = new float[]{(float) x, (float) z, (float) now, 0.03f + 0.025f * ps.rand()};
        ringHead = (ringHead + 1) % RINGS;
    }

    /** Ringwellen für den Bildrechner (x, z, Zeit des Aufpralls, Stärke), Zeit wie die Uhr des Bildrechners (Sekunden seit Start). */
    public float[][] ripples(double clockOffset) {
        int n = 0;
        for (float[] r : ring) if (r != null && now - r[2] < 5) n++;
        float[][] out = new float[n][];
        int k = 0;
        for (float[] r : ring) if (r != null && now - r[2] < 5) out[k++] = new float[]{r[0], r[1], (float) (r[2] + clockOffset), r[3]};
        return out;
    }

    /** Uhr der Brunnen in Sekunden. */
    public double clock() { return now; }

    // ------------------------------------------------------------ Nässe

    /** Nässeraster über dem Garten (128 m × 128 m). */
    public static Wetness.Set wetness(Terrain t) {
        Wetness.Set s = new Wetness.Set();
        s.list.add(new Wetness(t, -70, 70));
        return s;
    }

    /** Prüfung: Soll und berechneter Anstieg je Düse (für den Lasttest und das README). */
    public String report() {
        StringBuilder sb = new StringBuilder();
        for (Fountain f : list) for (Jet j : f.jets) {
            if (!Float.isNaN(j.wantRise)) sb.append(String.format("%s / %s: Soll %.2f m, gerechnet %.2f m%n", f.name, j.name, j.wantRise, j.apex));
            else if (j.type == POINT) sb.append(String.format("%s / %s: Wurfweite %.2f m, Scheitel %.2f m%n", f.name, j.name, j.range, j.apex));
        }
        return sb.toString();
    }
}
