package com.dan.heidelberg.lupe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Geometrie eines Atoms in Weltkoordinaten: Nukleonen im Kern, eine geneigte
 * Kreisbahn je Elektronenschale und die Elektronen darauf.
 * Die Szene ist unabhaengig von Kamera und Stil.
 */
public final class AtomScene {

    /** Aussenradius der aeussersten Bahn in Welteinheiten. */
    public static final double ATOM_RADIUS  = 10.0;
    /** Radius der Glashuelle. */
    public static final double GLASS_RADIUS = 11.4;

    /** Ein Proton oder Neutron. */
    public static final class Nucleon {
        public final Vec3 position;
        public final boolean proton;
        Nucleon(Vec3 position, boolean proton) {
            this.position = position;
            this.proton = proton;
        }
    }

    /** Eine Elektronenschale als Kreisbahn. */
    public static final class Orbit {
        public final int    shellNo;     // 1 = K ... 7 = Q
        public final double radius;
        public final Vec3   axisU;       // Einheitsvektor in der Bahnebene
        public final Vec3   axisV;       // zweiter Einheitsvektor in der Bahnebene
        public final Vec3   normal;
        public final int    electrons;
        public final double phase;       // Startwinkel des ersten Elektrons
        public final double angularSpeed;// rad/s, fuer die Animation

        Orbit(int shellNo, double radius, Vec3 u, Vec3 v, int electrons, double phase, double speed) {
            this.shellNo = shellNo;
            this.radius = radius;
            this.axisU = u;
            this.axisV = v;
            this.normal = u.cross(v).normalize();
            this.electrons = electrons;
            this.phase = phase;
            this.angularSpeed = speed;
        }

        /** Punkt auf der Bahn beim Winkel theta. */
        public Vec3 pointAt(double theta) {
            return axisU.scale(radius * Math.cos(theta)).add(axisV.scale(radius * Math.sin(theta)));
        }

        /** Winkel des j-ten Elektrons zur Zeit t (Sekunden). */
        public double electronAngle(int j, double t) {
            return phase + angularSpeed * t + 2.0 * Math.PI * j / Math.max(1, electrons);
        }
    }

    private final int atomicNumber;
    private final int massNumber;
    private double nucleusRadius;
    private final double nucleonRadius;
    private final double electronRadius;
    private final List<Nucleon> nucleons;
    private final List<Orbit> orbits;

    /**
     * @param atomicNumber Protonenzahl
     * @param neutrons     Neutronenzahl
     * @param shells       Elektronen je Schale K..Q (Laenge 1..7)
     */
    public AtomScene(int atomicNumber, int neutrons, int[] shells) {
        this.atomicNumber = atomicNumber;
        this.massNumber = atomicNumber + neutrons;

        // Nukleonen werden mit wachsender Massenzahl kleiner, damit der Kern lesbar bleibt
        double t = Math.cbrt(massNumber) / Math.cbrt(294.0);
        double targetRadius = massNumber == 1 ? 0.95 : 1.35 + 1.75 * t;
        this.nucleonRadius = massNumber == 1 ? 0.95 : targetRadius / (0.93 * Math.cbrt(massNumber));
        this.electronRadius = atomicNumber <= 18 ? 0.50 : atomicNumber <= 54 ? 0.42 : 0.36;
        this.nucleons = Collections.unmodifiableList(buildNucleus(atomicNumber, neutrons));
        this.orbits = Collections.unmodifiableList(buildOrbits(shells));
    }

    public int getAtomicNumber()      { return atomicNumber; }
    public int getMassNumber()        { return massNumber; }
    public double getNucleusRadius()  { return nucleusRadius; }
    public double getNucleonRadius()  { return nucleonRadius; }
    public double getElectronRadius() { return electronRadius; }
    public List<Nucleon> getNucleons(){ return nucleons; }
    public List<Orbit> getOrbits()    { return orbits; }

    // ------------------------------------------------------------------
    // Kern: Punkte gleichmaessig in einer Kugel (Fibonacci-Spirale je Radius),
    // Protonen und Neutronen per festem Zufall gemischt -> stabiles Bild.
    // ------------------------------------------------------------------
    private List<Nucleon> buildNucleus(int protons, int neutrons) {
        int n = protons + neutrons;
        List<Nucleon> list = new ArrayList<Nucleon>(n);
        if (n == 1) {
            list.add(new Nucleon(Vec3.ZERO, protons == 1));
            nucleusRadius = nucleonRadius;
            return list;
        }
        boolean[] isProton = new boolean[n];
        for (int i = 0; i < protons; i++) {
            isProton[i] = true;
        }
        Random rnd = new Random(1000L + n * 31L + protons);
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            boolean tmp = isProton[i];
            isProton[i] = isProton[j];
            isProton[j] = tmp;
        }
        double inner = nucleonRadius * 0.93 * Math.cbrt(n) - nucleonRadius;
        Vec3[] pos = new Vec3[n];
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < n; i++) {
            double f = (i + 0.5) / n;
            double r = inner * Math.cbrt(f);
            double y = 1.0 - 2.0 * f;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double a = golden * i;
            Vec3 dir = new Vec3(Math.cos(a) * ring, y, Math.sin(a) * ring);
            // kleine Unordnung, damit der Kern wie eine Beere wirkt
            Vec3 jitter = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5)
                    .scale(nucleonRadius * 0.25);
            pos[i] = dir.scale(r).add(jitter);
        }
        relax(pos);
        double maxR = 0;
        for (int i = 0; i < n; i++) {
            list.add(new Nucleon(pos[i], isProton[i]));
            maxR = Math.max(maxR, pos[i].length());
        }
        nucleusRadius = maxR + nucleonRadius;
        return list;
    }

    /**
     * Dichte Kugelpackung: Nukleonen stossen sich ab, wenn sie sich zu stark
     * ueberlappen, und werden gleichzeitig zur Mitte gezogen.
     */
    private void relax(Vec3[] pos) {
        int n = pos.length;
        double minDist = 2.0 * nucleonRadius * 0.86;
        for (int iter = 0; iter < 80; iter++) {
            Vec3[] move = new Vec3[n];
            for (int i = 0; i < n; i++) {
                move[i] = pos[i].scale(-0.035);               // Zug zur Mitte
            }
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Vec3 d = pos[i].sub(pos[j]);
                    double len = d.length();
                    if (len < minDist) {
                        Vec3 push = (len < 1e-6 ? new Vec3(0.01, 0.0, 0.0) : d.scale(1.0 / len))
                                .scale((minDist - len) * 0.5);
                        move[i] = move[i].add(push);
                        move[j] = move[j].sub(push);
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                pos[i] = pos[i].add(move[i]);
            }
        }
    }

    // ------------------------------------------------------------------
    // Bahnen: je Schale eine Kreisbahn. Die Ebenen sind um die Blickachse
    // verteilt (wie die drei Baender in Bild 1) und leicht gekippt.
    // ------------------------------------------------------------------
    private List<Orbit> buildOrbits(int[] shells) {
        int count = 0;
        for (int e : shells) {
            if (e > 0) {
                count++;
            }
        }
        List<Orbit> list = new ArrayList<Orbit>(count);
        double innerRadius = Math.max(nucleusRadius + 2.6, 5.4);
        int k = 0;
        for (int s = 0; s < shells.length; s++) {
            if (shells[s] <= 0) {
                continue;
            }
            double radius = count == 1
                    ? ATOM_RADIUS * 0.82
                    : innerRadius + (ATOM_RADIUS - innerRadius) * k / (count - 1.0);
            // Grundebene: waagerecht, zum Betrachter gekippt; dann um die Blickachse gedreht
            double tilt = Math.toRadians(k % 2 == 0 ? 62 : 69);
            double spin = Math.PI * k / Math.max(3, count) + Math.toRadians(8);
            Vec3 u = new Vec3(1, 0, 0).rotZ(spin);
            Vec3 v = new Vec3(0, 0, -1).rotX(-tilt + Math.PI / 2).rotZ(spin);
            v = v.sub(u.scale(v.dot(u))).normalize();
            double speed = 0.9 / Math.sqrt(k + 1.0);
            if (k % 2 == 1) {
                speed = -speed;
            }
            list.add(new Orbit(s + 1, radius, u, v, shells[s], 0.6 + 1.3 * k, speed));
            k++;
        }
        return list;
    }

    /** Orbitale in Madelung-Reihenfolge: {n, Kapazitaet}. */
    private static final int[][] MADELUNG = {
        {1, 2}, {2, 2}, {2, 6}, {3, 2}, {3, 6}, {4, 2}, {3, 10}, {4, 6}, {5, 2}, {4, 10},
        {5, 6}, {6, 2}, {4, 14}, {5, 10}, {6, 6}, {7, 2}, {5, 14}, {6, 10}, {7, 6}};

    /**
     * Reihenfolge, in der die Elektronen die Schalen besetzen (Aufbauprinzip).
     * Liefert je Elektron die Schalennummer 1..7; die Summe je Schale entspricht
     * genau der uebergebenen Besetzung, auch bei Ausnahmen wie Chrom.
     */
    public static int[] aufbauOrder(int[] shells) {
        int total = 0;
        int[] remaining = new int[8];
        for (int i = 0; i < shells.length && i < 7; i++) {
            remaining[i + 1] = shells[i];
            total += shells[i];
        }
        int[] order = new int[total];
        int k = 0;
        for (int[] orb : MADELUNG) {
            for (int c = 0; c < orb[1] && k < total; c++) {
                if (remaining[orb[0]] > 0) {
                    remaining[orb[0]]--;
                    order[k++] = orb[0];
                }
            }
        }
        for (int n = 1; n <= 7 && k < total; n++) {
            while (remaining[n] > 0 && k < total) {
                remaining[n]--;
                order[k++] = n;
            }
        }
        return order;
    }

    /** Schalenbesetzung aus Text wie "2-8-14-2". */
    public static int[] parseShells(String shellConfig) {
        if (shellConfig == null || shellConfig.trim().isEmpty()) {
            return new int[0];
        }
        String[] parts = shellConfig.trim().split("-");
        int[] res = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            res[i] = Integer.parseInt(parts[i].trim());
        }
        return res;
    }
}
