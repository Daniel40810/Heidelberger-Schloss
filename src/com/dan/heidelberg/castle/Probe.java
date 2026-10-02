package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;

import java.util.List;

/**
 * Prüfstand der Engine (Phase 2): ein Probesaal mit hohen Fenstern zur Sonnenseite, Marmorboden und
 * Staubluft, davor ein Probebecken aus Marmor mit ruhigem Wasser. Hier zeigen sich Lichtschacht und
 * Spiegelung, bevor das Schloss gebaut ist; später dienen sie als Vergleich. Aufruf mit
 * {@code -Dheidelberg.probe=true} oder StillRender mit „probe“.
 */
public final class Probe {
    private Probe() { }

    /** Mitte des Saals; Becken davor nach Süden. */
    public static final double HX = 0, HZ = -6, BZ = 14;

    public static void build(MeshBuilder mb, List<float[]> dust) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        Arch.Block hall = new Arch.Block(HX, HZ, 0, 16, 12, 0, 7.5);
        hall.hollow = true;
        hall.T = 1.3;
        // vier hohe Fenster zur Südseite (Sonne), ein Rundbogentor im Westen
        hall.windows(0, 4, 1.6, new double[]{1.4}, 1.3, 4.6, false);
        hall.f[3].ops.add(Arch.arch(6, 0.05, 2.6, 4.2));
        hall.slabs.add(new double[]{0.05, Mat.MARBLE});
        hall.build(mb);
        hall.roof(mb, 4.5, 0.8, false, Mat.ROOF, Mat.SANDSTONE);
        // Säulen im Saal (Marmor) und eine Statue; im Innenraum kommt das Himmelslicht nur durch die Fenster
        mb.skyFn = (x, y, z, nx, ny, nz) -> 0.22f;
        Arch.column(mb, -4, -6, 0.05, 6.0, 0.35, Mat.MARBLE);
        Arch.column(mb, 4, -6, 0.05, 6.0, 0.35, Mat.MARBLE);
        Arch.statue(mb, 0, 0.05, -9.5, 2.6, 0, Mat.STATUE);
        mb.skyFn = null;
        // Staubluft im Saal
        dust.add(new float[]{(float) (HX - 8 + 1.3), 0.05f, (float) (HZ - 6 + 1.3), (float) (HX + 8 - 1.3), 7.2f, (float) (HZ + 6 - 1.3), 0.03f});
        // Probebecken: Marmorrand und Wasser
        double w = 4.5, d = 2.2, rimH = 0.55, rimW = 0.45;
        double cx = HX, cz = BZ;
        mb.box(cx - w - rimW, 0, cz - d - rimW, cx + w + rimW, rimH, cz - d, Mat.MARBLE, false);
        mb.box(cx - w - rimW, 0, cz + d, cx + w + rimW, rimH, cz + d + rimW, Mat.MARBLE, false);
        mb.box(cx - w - rimW, 0, cz - d, cx - w, rimH, cz + d, Mat.MARBLE, false);
        mb.box(cx + w, 0, cz - d, cx + w + rimW, rimH, cz + d, Mat.MARBLE, false);
        mb.rectH(cx - w, cz - d, cx + w, cz + d, 0.02, true, Mat.STATUE);
        mb.rectH(cx - w, cz - d, cx + w, cz + d, 0.40, true, Mat.BASIN);
        // Rückwand aus Sandstein, ein paar Säulen am Beckenrand: Dinge, die sich spiegeln
        for (int i = -2; i <= 2; i += 2) Arch.column(mb, cx + i * 2.2, cz + d + rimW + 1.2, 0, 4.2, 0.28, Mat.MARBLE);
        mb.box(cx - w - 3, 0, cz + d + 4, cx + w + 3, 5.2, cz + d + 5, Mat.SANDSTONE, false);
        mb.maxEdge = keep;
    }
}
