package com.dan.heidelberg.core;

/** Materialnummern der Szene. 0 bleibt frei (Himmel im G-Puffer). */
public final class Mat {
    /** Gelände: die Farbe kommt aus der Bodenkarte (Sinter, Wiese, Wald, Ufer, Fels) und den Quellen. */
    public static final int TERRAIN = 1;
    /** Kieselsinter (Geyserit) als glatte Fläche. */
    public static final int SINTER = 2;
    /** Holz: Pfosten. */
    public static final int WOOD = 3;
    /** Signalfarbe am Kopf der Pfosten. */
    public static final int MARK = 4;
    /** Rhyolith, der vulkanische Fels des Plateaus. */
    public static final int ROCK = 5;
    /** Geyserit am Beckenrand: perlig, feucht glänzend. */
    public static final int RIM = 6;
    /** Rinde der Drehkiefer, Nadeln, tote Stämme (unten weiß: „Bobby Socks“). */
    public static final int BARK = 7, NEEDLES = 8, SNAG = 9;
    /** Bretter der Stege. */
    public static final int BOARD = 10;
    /** Sinterkegel der Geysire: Lagen, Perlen, braune Streifen vom Abfluss. */
    public static final int CONE = 11;
    /** Tiere (Bisons, Wapitis), nur im G-Puffer gesetzt. */
    public static final int ANIMAL = 12;
    /** Wasser: der Fluss und die heißen Quellen (Farbe aus Tiefe und Temperatur). */
    public static final int WATER = 13, RIVER = 14, POOL = 15;
    /** Laub der Espen: Farbe nach Jahreszeit ({@link Materials#leafLut}). */
    public static final int LEAVES = 19;
    /** Rinde der Espe: hell, grünlich weiß, mit dunklen Narben. */
    public static final int WHITEBARK = 20;
    /** Nadeln von Fichte und Tanne: dunkler und blaugrüner als die der Kiefern. */
    public static final int SPRUCE = 21;
    /** Putz der Häuser (Töne je Haus) und Dachziegel. */
    public static final int WALL = 22, ROOF = 23;
    /** Roter Sandstein der Schlossmauern (Buntsandstein). */
    public static final int SANDSTONE = 24;
    /** Poliertes Gestein (Marmor, Alabaster): Äderung, spiegelt Himmel, Lichter und Umgebung. */
    public static final int MARBLE = 25;
    /** Fensterglas: spiegelt den Himmel, wirft keinen Schatten (das Licht geht hindurch). */
    public static final int GLASS = 26;
    /** Standbilder und Wappen (heller Sandstein, feine Zeichnung). */
    public static final int STATUE = 27;
    /** Kies der Wege und Beete (bunte Kiesel in den Knotenfeldern). */
    public static final int GRAVEL = 28;
    /** Hecken, Buchsbaum und Kräuterbänder der Knotenfelder. */
    public static final int HEDGE = 29;
    /** Heller Kalkputz innen und an Mauern. */
    public static final int PLASTER = 30;
    /** Wie Sandstein, aber blau getönt: der Teil des Gartens, der nur geplant war und nie gebaut wurde. */
    public static final int PLANNED = 31;
    /** Orangenbäume (Pomeranzen) in den Kästen: dunkles Laub mit Früchten. */
    public static final int ORANGE = 32;
    /** Wasser in Becken und Brunnenschalen: ruhig, spiegelnd, mit Ringwellen (Phase 5). */
    public static final int BASIN = 16;
    /** Rasen der Gartenterrassen: gemäht, mit Streifen. */
    public static final int LAWN = 33;
    /** Leuchtfenster und Laternenköpfe: tagsüber dunkles Glas, nachts warmes Licht (Phase 6). */
    public static final int LIGHT = 34;
    /** Wappenplatte: das Wappen der Kurpfalz entsteht im Materialprogramm (Phase 7). */
    public static final int HERALD = 35;
    /** Phase 8, Innenräume: Wandbehänge und Teppiche, Eiche der Fässer, Eisen, Wachs, venezianischer Spiegel, Vergoldung, Leinen. */
    public static final int CLOTH = 36, OAK = 37, IRON = 38, WAX = 39, MIRROR = 40, GOLD = 41, LINEN = 42;
    /** Phase 9, Ruine: berußter und vom Brand geröteter Sandstein, Schutt mit Asche und Moos, verkohltes Holz. */
    public static final int SOOT = 43, RUBBLE = 44, CHAR = 45;
    /** Phase 9, Schnitt: Schnittfläche durch Mauerwerk (Schraffur) und durch Erdreich (Körnung). */
    public static final int SCHNITT_STEIN = 46, SCHNITT_ERDE = 47;
    public static final int COUNT = 48;

    public static boolean water(int m) { return m >= WATER && m <= 18; }

    /** Flächen, die nass werden können (Gischt, Abfluss). */
    public static boolean wettable(int m) { return m == TERRAIN || m == SINTER || m == RIM || m == CONE || m == BOARD || m == ROCK || m == SANDSTONE || m == MARBLE || m == PLANNED || m == GRAVEL || m == STATUE; }

    private Mat() { }
}
