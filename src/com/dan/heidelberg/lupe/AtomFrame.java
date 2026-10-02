package com.dan.heidelberg.lupe;

import java.util.ArrayList;
import java.util.List;

/**
 * Momentaufnahme eines (animierten) Atoms: alles, was der Renderer fuer
 * genau ein Bild braucht. Wird vom {@link AtomAnimator} je Frame erzeugt
 * oder fuer Standbilder direkt aus einer {@link AtomScene}.
 */
public final class AtomFrame {

    /** Proton oder Neutron an seiner aktuellen Position. */
    public static final class NucleonState {
        public final Vec3 position;
        public final boolean proton;
        public final float alpha;
        public NucleonState(Vec3 position, boolean proton, float alpha) {
            this.position = position;
            this.proton = proton;
            this.alpha = alpha;
        }
    }

    /** Aktuelle Geometrie einer Bahn. */
    public static final class OrbitState {
        public final int shellNo;
        public final double radius;
        public final Vec3 axisU;
        public final Vec3 axisV;
        public final float alpha;
        public OrbitState(int shellNo, double radius, Vec3 axisU, Vec3 axisV, float alpha) {
            this.shellNo = shellNo;
            this.radius = radius;
            this.axisU = axisU;
            this.axisV = axisV;
            this.alpha = alpha;
        }
        public Vec3 normal() {
            return axisU.cross(axisV).normalize();
        }
        public Vec3 pointAt(double theta) {
            return axisU.scale(radius * Math.cos(theta)).add(axisV.scale(radius * Math.sin(theta)));
        }
    }

    /** Elektron an seiner aktuellen Position. */
    public static final class ElectronState {
        public final Vec3 position;
        public final float alpha;
        public ElectronState(Vec3 position, float alpha) {
            this.position = position;
            this.alpha = alpha;
        }
    }

    public double nucleusRadius;
    public double nucleonRadius;
    public double electronRadius;
    /** Staerke des Kernleuchtens, 1 = normal. */
    public float glow = 1f;

    public final List<NucleonState>  nucleons  = new ArrayList<NucleonState>();
    public final List<OrbitState>    orbits    = new ArrayList<OrbitState>();
    public final List<ElectronState> electrons = new ArrayList<ElectronState>();

    /** Standbild einer Szene zur Zeit t (Elektronen bewegen sich mit t). */
    public static AtomFrame of(AtomScene scene, double t) {
        AtomFrame f = new AtomFrame();
        f.nucleusRadius = scene.getNucleusRadius();
        f.nucleonRadius = scene.getNucleonRadius();
        f.electronRadius = scene.getElectronRadius();
        for (AtomScene.Nucleon n : scene.getNucleons()) {
            f.nucleons.add(new NucleonState(n.position, n.proton, 1f));
        }
        for (AtomScene.Orbit o : scene.getOrbits()) {
            f.orbits.add(new OrbitState(o.shellNo, o.radius, o.axisU, o.axisV, 1f));
            for (int j = 0; j < o.electrons; j++) {
                f.electrons.add(new ElectronState(o.pointAt(o.electronAngle(j, t)), 1f));
            }
        }
        return f;
    }
}
