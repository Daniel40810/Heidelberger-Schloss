package com.dan.heidelberg.db;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Zustand des Programms zum Wiederherstellen: Zeit und Stufe, Wetter, Effekte, Leben, Zugaben, Bild und Kamera.
 * Die Felder sind genau die Spalten von HEI_STATE (Reihenfolge wie in {@code db/01_tables.sql}); als Datei
 * ({@code ~/.heidelberg/zustand.properties}) dient derselbe Satz als Rückfall ohne Datenbank.
 */
public final class Zustand {
    public enum Typ { ZAHL, FLAG, TEXT }

    /** Ein Feld: Name (= Spaltenname), Typ, Vorgabe und Grenzen (Zahlen). */
    public static final class Feld {
        public final String name;
        public final Typ typ;
        public final Object vorgabe;
        public final double min, max;
        Feld(String name, Typ typ, Object vorgabe, double min, double max) { this.name = name; this.typ = typ; this.vorgabe = vorgabe; this.min = min; this.max = max; }
    }

    private static Feld z(String n, double v, double min, double max) { return new Feld(n, Typ.ZAHL, v, min, max); }
    private static Feld j(String n, boolean v) { return new Feld(n, Typ.FLAG, v, 0, 1); }
    private static Feld t(String n, String v) { return new Feld(n, Typ.TEXT, v, 0, 0); }

    /** Reihenfolge und Vorgaben wie die Spalten von HEI_STATE ab STUFE. */
    public static final Feld[] SPEC = {
            z("STUFE", 0, 0, 3), z("TAG", 172, 1, 366), z("STUNDE", 16, 0, 24), z("ZEITRAFFER", 0, 0, 20),
            z("WETTER", 0, 0, 20), z("DUNST", 12, 0, 100), z("NEBEL", 100, 0, 200), z("TALNEBEL", 100, 0, 200), j("NEBELTAG", false), z("WIND", 35, 0, 100),
            j("FX_STRAHLEN", true), j("FX_UEBERSTRAHLEN", true), j("FX_RAUCH", true), j("FX_FACKELN", true), j("FX_SCHAUER", false),
            j("L_HOFSTAAT", true), z("DICHTE_HOF", 85, 0, 100), j("L_REITER", true), j("L_VOEGEL", true), j("L_KERZEN", true),
            j("BRUNNEN", true), z("BRUNNEN_DICHTE", 100, 10, 800),
            j("Z_WASSERWEG", false), j("Z_WASSERORGEL", true), j("Z_KLANG", true), j("Z_LUPE", false), z("SCHNITT_NR", 0, 0, 2), j("Z_GEPLANT", true),
            t("QUALITAET", "AUTO"), z("STIL", 0, 0, 20), j("RUNDFLUG", false),
            z("BLICKPUNKT", 0, 0, 999),
            z("DREHPUNKT_X", 0, -1e6, 1e6), z("DREHPUNKT_Y", 0, -1e4, 1e5), z("DREHPUNKT_Z", 0, -1e6, 1e6),
            z("GIER_GRAD", 0, -1e4, 1e4), z("NICK_GRAD", 0, -90, 90), z("ABSTAND_M", 100, 0.1, 1e5),
    };

    private static final Map<String, Feld> BY_NAME = new LinkedHashMap<>();
    static { for (Feld f : SPEC) BY_NAME.put(f.name, f); }

    /** Kopf der Zeile (kein Teil von SPEC). */
    public String name = "AUTO", art = "AUTO";
    public long gespeichertMs;
    public long sessionId;
    /** Ob die Kamera (Drehpunkt, Gier, Nick, Abstand) gesetzt wurde; sonst bleibt der Blickpunkt. */
    public boolean kameraGesetzt;

    private final Map<String, Object> werte = new LinkedHashMap<>();

    public Zustand() { for (Feld f : SPEC) werte.put(f.name, f.vorgabe); }

    public static Zustand vorgabe() { return new Zustand(); }

    public double zahl(String n) { return ((Number) werte.get(check(n))).doubleValue(); }
    public int ganz(String n) { return (int) Math.round(zahl(n)); }
    public boolean flag(String n) { return (Boolean) werte.get(check(n)); }
    public String text(String n) { return (String) werte.get(check(n)); }

    public Zustand setze(String n, double v) {
        Feld f = BY_NAME.get(check(n));
        if (f.typ != Typ.ZAHL) throw new IllegalArgumentException(n + " ist keine Zahl");
        werte.put(n, Double.isFinite(v) ? Math.max(f.min, Math.min(f.max, v)) : f.vorgabe);
        return this;
    }

    public Zustand setze(String n, boolean v) {
        if (BY_NAME.get(check(n)).typ != Typ.FLAG) throw new IllegalArgumentException(n + " ist kein Schalter");
        werte.put(n, v);
        return this;
    }

    public Zustand setze(String n, String v) {
        if (BY_NAME.get(check(n)).typ != Typ.TEXT) throw new IllegalArgumentException(n + " ist kein Text");
        werte.put(n, v == null ? BY_NAME.get(n).vorgabe : v);
        return this;
    }

    private static String check(String n) {
        if (!BY_NAME.containsKey(n)) throw new IllegalArgumentException("unbekanntes Feld " + n);
        return n;
    }

    public Zustand kopie() {
        Zustand k = new Zustand();
        k.name = name; k.art = art; k.gespeichertMs = gespeichertMs; k.sessionId = sessionId; k.kameraGesetzt = kameraGesetzt;
        k.werte.putAll(werte);
        return k;
    }

    // ------------------------------------------------------------ Datei (Rückfall ohne Datenbank)

    public Properties alsProperties() {
        Properties p = new Properties();
        p.setProperty("name", name);
        p.setProperty("art", art);
        p.setProperty("gespeichert", Long.toString(gespeichertMs));
        p.setProperty("kamera", Boolean.toString(kameraGesetzt));
        for (Feld f : SPEC) {
            Object v = werte.get(f.name);
            p.setProperty(f.name, v instanceof Double ? Double.toString((Double) v) : String.valueOf(v));
        }
        return p;
    }

    /** Liest einen Zustand; unlesbare oder fehlende Werte fallen auf die Vorgabe zurück. */
    public static Zustand ausProperties(Properties p) {
        Zustand z = new Zustand();
        z.name = p.getProperty("name", "AUTO");
        z.art = p.getProperty("art", "AUTO");
        try { z.gespeichertMs = Long.parseLong(p.getProperty("gespeichert", "0")); } catch (NumberFormatException e) { z.gespeichertMs = 0; }
        z.kameraGesetzt = Boolean.parseBoolean(p.getProperty("kamera", "false"));
        for (Feld f : SPEC) {
            String s = p.getProperty(f.name);
            if (s == null) continue;
            try {
                switch (f.typ) {
                    case ZAHL: z.setze(f.name, Double.parseDouble(s)); break;
                    case FLAG: z.setze(f.name, Boolean.parseBoolean(s)); break;
                    default: z.setze(f.name, s);
                }
            } catch (NumberFormatException e) { /* Vorgabe bleibt */ }
        }
        return z;
    }

    public static void speichern(File datei, Zustand z) throws IOException {
        File dir = datei.getAbsoluteFile().getParentFile();
        if (dir != null) Files.createDirectories(dir.toPath());
        File tmp = new File(datei.getPath() + ".tmp");
        try (OutputStream o = Files.newOutputStream(tmp.toPath())) { z.alsProperties().store(o, "Heidelberger Schloss: Zustand beim Beenden"); }
        Files.move(tmp.toPath(), datei.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Der Zustand aus der Datei, oder null, wenn es keine gibt oder sie unlesbar ist. */
    public static Zustand laden(File datei) {
        if (!datei.isFile()) return null;
        Properties p = new Properties();
        try (InputStream i = Files.newInputStream(datei.toPath())) { p.load(i); } catch (IOException e) { return null; }
        return ausProperties(p);
    }

    @Override public String toString() {
        return String.format("Zustand[%s %s Stufe %d Tag %d %.1f h Blickpunkt %d]", art, name, ganz("STUFE"), ganz("TAG"), zahl("STUNDE"), ganz("BLICKPUNKT"));
    }
}
