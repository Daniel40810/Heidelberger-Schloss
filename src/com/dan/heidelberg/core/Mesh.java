package com.dan.heidelberg.core;

/**
 * Fertiges Dreiecksnetz der Szene. Dreiecke sind nach räumlichen Blöcken (Chunks)
 * sortiert, damit der Renderer ganze Blöcke gegen das Sichtfeld prüfen kann.
 * Übernommen aus Semiramis, ohne das Wachsen.
 */
public final class Mesh {
    public final int nv, nt;
    /** Wird erhöht, wenn Ecken nachträglich verschoben werden (Sinter-Zeitraffer). */
    public volatile int version;
    /** Ecken, die sich von selbst bewegen (Wind, Drehkörper); einmal ermittelt. */
    private int[] moving;

    public int[] moving() {
        int[] m = moving;
        if (m != null) return m;
        int n = 0;
        for (int v = 0; v < nv; v++) if (sway[v] != 0 || flutter[v] != 0 || spin[v] >= 0) n++;
        m = new int[n];
        n = 0;
        for (int v = 0; v < nv; v++) if (sway[v] != 0 || flutter[v] != 0 || spin[v] >= 0) m[n++] = v;
        return moving = m;
    }
    public final float[] pos, nrm;   // 3 je Ecke (können länger sein als nv, wenn Netze Ecken teilen)
    public final float[] sky;        // Himmelssicht je Ecke, 0..1 (1 = frei)
    /** Ausschlag im Wind je Ecke in Metern bei vollem Wind (0 = starr). */
    public final float[] sway;
    /** Zittern der Blätter je Ecke in Metern bei vollem Wind, entlang der Normale (0 = keins). */
    public final float[] flutter;
    /** Drehkörper je Ecke (−1 = keiner) und die Drehachsen: Ursprung, Richtung, Winkelgeschwindigkeit. */
    public final int[] spin;
    public double[][] spinners = new double[0][];
    public final int[] idx;          // 3 je Dreieck
    public final byte[] mat, grp;    // je Dreieck
    public final float[] fn;         // Flächennormale, 3 je Dreieck
    public final int nChunks;
    public final int[] chunkStart, chunkCount, chunkGrp;
    public final float[] chunkBox;   // minX,minY,minZ,maxX,maxY,maxZ

    /** Netz einer anderen Zeitstufe: gleiche Ecken, Normalen und Bewegungsdaten wie {@code share}, eigene Dreiecke. */
    Mesh(int nv, int nt, Mesh share, int[] idx, byte[] mat, byte[] grp, float[] fn,
         int nChunks, int[] cs, int[] cc, int[] cg, float[] cb) {
        this.nv = nv; this.nt = nt; this.pos = share.pos; this.nrm = share.nrm; this.idx = idx;
        this.mat = mat; this.grp = grp; this.fn = fn; this.nChunks = nChunks;
        this.chunkStart = cs; this.chunkCount = cc; this.chunkGrp = cg; this.chunkBox = cb;
        this.sky = share.sky; this.sway = share.sway; this.flutter = share.flutter; this.spin = share.spin;
        this.spinners = share.spinners;
    }

    Mesh(int nv, int nt, float[] pos, float[] nrm, int[] idx, byte[] mat, byte[] grp, float[] fn,
         int nChunks, int[] cs, int[] cc, int[] cg, float[] cb) {
        this.nv = nv; this.nt = nt; this.pos = pos; this.nrm = nrm; this.idx = idx;
        this.mat = mat; this.grp = grp; this.fn = fn; this.nChunks = nChunks;
        this.chunkStart = cs; this.chunkCount = cc; this.chunkGrp = cg; this.chunkBox = cb;
        int na = pos.length / 3;
        this.sky = new float[na];
        java.util.Arrays.fill(sky, 1f);
        this.sway = new float[na];
        this.flutter = new float[na];
        this.spin = new int[na];
        java.util.Arrays.fill(spin, -1);
    }
}
