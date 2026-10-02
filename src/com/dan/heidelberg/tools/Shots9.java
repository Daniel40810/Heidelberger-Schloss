package com.dan.heidelberg.tools;

/** Die Aufnahmen der Phase 9: Name, Stufe, Tag, Stunde, Auge x, y (über Boden oder absolut), z, Ziel x, y, z, absolut?, Bildwinkel. */
final class Shots9 {
    private Shots9() { }

    private static Object[] s(String n, int stage, int day, double h, double ex, double ey, double ez, double tx, double ty, double tz, boolean abs, double fov) {
        return new Object[]{n, stage, day, h, ex, ey, ez, tx, ty, tz, abs, fov};
    }

    static Object[][] table() {
        java.util.List<Object[]> l = new java.util.ArrayList<>();
        for (int st = 0; st <= 3; st++) {
            String p = "ru" + st + "_";
            l.add(s(p + "hof_fri", st, 172, 15.0, -8, 1.8, 16, 31, 12, -31, false, 60));
            l.add(s(p + "hof_ott", st, 172, 15.0, 20, 1.8, 12, -17, 10, -29, false, 60));
            l.add(s(p + "luft", st, 172, 16.5, -190, 150, 230, -20, 8, 30, false, 60));
            l.add(s(p + "tal", st, 172, 18.0, -620, 60, -420, 0, 14, 0, false, 60));
            l.add(s(p + "dick", st, 172, 16.0, 118, 14, -96, 60, 14, -44, false, 60));
            l.add(s(p + "kraut", st, 172, 16.0, 120, 10, 78, 68, 12, 48, false, 60));
            l.add(s(p + "ost", st, 172, 16.0, 30, 40, 120, 30, 6, 0, false, 60));
        }
        // Wasserweg (Stufe 0)
        l.add(s("ww_quelle", 0, 172, 15.5, -88, 3.0, 160, -94, 15, 172, false, 60));
        l.add(s("ww_hang", 0, 172, 15.5, -80, 6.0, 190, -94, 10, 140, false, 60));
        l.add(s("ww_fall", 0, 172, 15.5, -80, 2.5, 108, -94, 4.5, 119, false, 60));
        l.add(s("ww_terrasse", 0, 172, 15.5, -40, 38, 190, -92, 3, 120, false, 60));
        l.add(s("ww_abfluss", 0, 172, 15.5, -108, 6.0, 90, -132, -3, 79, false, 60));
        l.add(s("ww_west", 0, 172, 15.5, -160, 7.0, 30, -137, -9, 79, false, 60));
        l.add(s("ww_bach", 0, 172, 15.5, -150, 12.0, 120, -190, -22, 60, false, 60));
        l.add(s("ww_fall2", 0, 172, 15.5, -90, 6.2, 109, -94, 6.0, 120, false, 70));
        l.add(s("ww_west2", 0, 172, 15.5, -150, 3.0, 66, -134, -6, 79, false, 60));
        l.add(s("wo_raum", 0, 172, 15.5, -70, -0.4, 101.0, -70, 0.6, 106.8, true, 70));
        l.add(s("wo_walze", 0, 172, 15.5, -71.5, -0.2, 102.3, -70, -0.7, 103.7, true, 60));
        l.add(s("wo_zahn", 0, 172, 15.5, -71.8, 0.3, 104.0, -66.1, 0.5, 103.0, true, 60));
        l.add(s("wo_vogel", 0, 172, 15.5, -72.0, -0.3, 101.4, -73.4, -0.8, 104.2, true, 55));
        // Schnitte: Name beginnt mit sn0_ oder sn1_ (Ebene), Stufe 0
        l.add(s("sn0_ueber", 0, 172, 15.5, -134, 7, 96, -70, 0.5, 96, true, 70));
        l.add(s("sn0_grotte", 0, 172, 15.5, -100, 2, 96, -70, -0.5, 94, true, 60));
        l.add(s("sn0_nord", 0, 172, 15.5, -120, 9, 60, -70, 1, 90, true, 60));
        l.add(s("sn0_hinten", 0, 172, 15.5, -90, 4, 120, -70, 0.5, 104, true, 60));
        l.add(s("sn1_ueber", 0, 172, 15.5, -80, 6, 160, -80, 0.5, 95, true, 70));
        l.add(s("sn1_gross", 0, 172, 15.5, -70, 3, 120, -70, 0, 95, true, 60));
        l.add(s("sn1_klein", 0, 172, 15.5, -110, 4, 125, -106, 0, 95, true, 60));
        // Stein-Lupe: Name beginnt mit lu, die Lupe sitzt in der Bildmitte
        l.add(s("lu_wand", 0, 172, 15.5, 20, 3.0, 8, -17, 8, -29, false, 60));
        l.add(s("lu_wasser", 0, 172, 15.5, -70, 3.5, 70, -70, 0, 101.5, true, 60));
        l.add(s("lu_gold", 0, 172, 15.5, -70.5, -0.2, 102.6, -70, 1.8, 106.0, true, 50));
        l.add(s("lu_boden", 0, 172, 15.5, -60, 6, 150, -75, 0, 180, false, 60));
        l.add(s("ww_luft", 0, 172, 15.5, 0, 160, 240, -120, 0, 80, false, 60));
        return l.toArray(new Object[0][]);
    }
}
