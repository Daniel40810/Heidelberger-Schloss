package com.dan.heidelberg.world;

import com.dan.heidelberg.core.Scene;

/** Was der Bau liefert: die Szene fürs Bild, Wald, Neckar, Wiese und Wege für die Simulation. */
public final class World {
    public final Scene scene;
    /** Laubwald: Jahreszeit des Laubs und fallende Blätter. */
    public final Grove grove;
    /** Der Neckar: Strömung, Wellen, Schaum, Treibgut. */
    public final Neckar neckar;
    /** Gras, Blumen, Steine und Erde um die Kamera. */
    public final Sward sward;
    /** Feste Körper für die Kamera (Mauern, Dächer, Stämme): Kollisionsschutz ab Phase 7. */
    public volatile com.dan.heidelberg.core.Solids solids;
    /** Wege. */
    public final Roadways roadways;
    /** Staubluft in Sälen (Kästen), damit Lichtschächte sichtbar werden. */
    public volatile float[][] dust = new float[0][];
    /** Fackelständer im Hof und Garten (leer im Prüfstand). */
    public volatile com.dan.heidelberg.castle.Torches torches = new com.dan.heidelberg.castle.Torches();
    /** Hofstaat, Reiter, Wagen und Vögel (Phase 8). */
    public volatile com.dan.heidelberg.castle.Life life = new com.dan.heidelberg.castle.Life();
    /** Schornsteine der Stadt (x, y der Oberkante, z) für den Rauch. */
    public volatile java.util.List<double[]> townChimneys = new java.util.ArrayList<>();

    /** Baukasten mit allen Zeitstufen (Phase 9) und die Eckenzahl am Ende jeder Ruinenstufe; die Netze der Stufen entstehen bei Bedarf. */
    volatile com.dan.heidelberg.core.MeshBuilder builder;
    volatile int[] stageEnds;
    private final Scene[] stageScenes = new Scene[4];
    private final com.dan.heidelberg.core.Solids[] stageSolids = new com.dan.heidelberg.core.Solids[4];

    /** Anzahl der Zeitstufen: 1619 und drei Ruinen (null Ruinen im Prüfstand). */
    public int stages() { return stageEnds == null ? 1 : 4; }

    /** Szene der Zeitstufe s (0 = 1619, 1 = nach 1689, 2 = nach 1693, 3 = nach 1764); baut das Netz beim ersten Mal (rund eine Sekunde). */
    public synchronized Scene stageScene(int s) {
        if (s <= 0 || stageEnds == null) return scene;
        if (stageScenes[s] == null) {
            long t0 = System.nanoTime();
            int[] era = {0, com.dan.heidelberg.core.MeshBuilder.ERA_1689, com.dan.heidelberg.core.MeshBuilder.ERA_1693, com.dan.heidelberg.core.MeshBuilder.ERA_1764};
            com.dan.heidelberg.core.Mesh m = builder.build(64, era[s], stageEnds[s], scene.mesh);
            stageScenes[s] = new Scene(scene.name + " · " + com.dan.heidelberg.castle.Ruin.STAGE_NAMES[s], m, scene.terrain, scene.thermal);
            System.out.printf("Zeitstufe %d: %,d Dreiecke, Netz in %.1f s%n", s, m.nt, (System.nanoTime() - t0) / 1e9);
        }
        return stageScenes[s];
    }

    /** Feste Körper der Zeitstufe s. */
    public synchronized com.dan.heidelberg.core.Solids stageSolids(int s) {
        if (s <= 0 || stageEnds == null) return solids0();
        if (stageSolids[s] == null) stageSolids[s] = new com.dan.heidelberg.core.Solids(stageScene(s).mesh);
        return stageSolids[s];
    }

    private com.dan.heidelberg.core.Solids solid0;
    private com.dan.heidelberg.core.Solids solids0() {
        if (solid0 == null) solid0 = new com.dan.heidelberg.core.Solids(scene.mesh);
        return solid0;
    }

    /** Vorhandene feste Körper von 1619 festhalten, damit sie nach dem Zeitschalter wiederkehren. */
    void keepSolids() { solid0 = solids; }

    World(Scene scene, Grove grove, Neckar neckar, Sward sward, Roadways roadways) {
        this.scene = scene;
        this.grove = grove;
        this.neckar = neckar;
        this.sward = sward;
        this.roadways = roadways;
    }
}
