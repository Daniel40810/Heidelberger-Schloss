package com.dan.heidelberg.tools;

import com.dan.heidelberg.camera.Director;
import com.dan.heidelberg.camera.Rides;
import com.dan.heidelberg.camera.Viewpoint;
import com.dan.heidelberg.castle.Castle;
import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.db.Katalog;
import com.dan.heidelberg.db.KatalogDb;
import com.dan.heidelberg.world.Heidelberg;
import com.dan.heidelberg.world.World;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erzeugt db/04_seed.sql, die Grunddaten der HEI_-Tabellen, aus dem Code: Katalog (Quellen, Zeitstufen, Chronik, Bauwerke),
 * Blickpunkte, Fahrten und das Drehbuch mit seinen Tafeln. Jede Zeile ist ein INSERT … WHERE NOT EXISTS, das Skript kann
 * also beliebig oft laufen und überschreibt nie, was in der Datenbank schon steht. Prüft die Längen gegen 01_tables.sql
 * (in Bytes, denn VARCHAR2(n) zählt in Oracle ohne weitere Angabe Bytes, ein Umlaut sind zwei).
 * Aufruf: SeedGen [db-Ordner]
 */
public final class SeedGen {
    private static final Map<String, Map<String, Integer>> LIMIT = new LinkedHashMap<>();
    private static final List<String> FEHLER = new ArrayList<>();
    private static final StringBuilder OUT = new StringBuilder();
    private static final Map<String, Integer> ZAHL = new LinkedHashMap<>();

    /** Ein SQL-Ausdruck, der unverändert eingesetzt wird. */
    private record Roh(String sql) { }

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "db");
        grenzen(new File(dir, "01_tables.sql"));
        World w = Heidelberg.build(false);
        Terrain t = w.scene.terrain;

        Katalog.Daten d = Katalog.eingebaut();
        StringBuilder kopf = new StringBuilder();

        // ---- Quellen
        for (Katalog.Quelle q : d.quellen) {
            zeile("HEI_SOURCE", new String[]{"CODE"}, "CODE", q.code(), "AUTOR", q.autor(), "LEBENSZEIT", q.lebenszeit(), "WERK", q.werk(), "STELLE", q.stelle(), "SPRACHE", q.sprache(),
                    "UEBERLIEFERUNG", q.ueberlieferung(), "ABSTAND_JAHRE", q.abstandJahre(), "BEZUG", q.bezug(), "KERNAUSSAGE", q.kern(), "MASSE", q.masse(), "BELEG", q.beleg());
        }
        // ---- Zeitstufen
        for (Katalog.Stufe s : d.stufen) {
            zeile("HEI_STAGE", new String[]{"NR"}, "NR", s.nr(), "JAHR", s.jahr(), "DATUM", s.datum(), "NAME", s.name(), "KURZ", s.kurz(), "BESCHREIBUNG", s.beschreibung(), "SOURCE_ID", quelle(s.quelle()));
        }
        // ---- Chronik
        for (Katalog.Ereignis e : d.chronik) {
            zeile("HEI_CHRONIK", new String[]{"JAHR", "TITEL"}, "STAGE_ID", new Roh("(SELECT STAGE_ID FROM HEI_STAGE WHERE NR = " + e.stufe() + ")"), "JAHR", e.jahr(), "DATUM", e.datum(),
                    "TITEL", e.titel(), "TEXT", e.text(), "SOURCE_ID", quelle(e.quelle()));
        }
        // ---- Bauwerke
        int ohneLage = 0;
        for (Katalog.Bau b : d.bauten) {
            Double x = b.x(), z = b.z();
            if (x == null && b.ort() != null) {
                for (Object[] pl : Castle.PLACES) if (((String) pl[0]).startsWith(b.ort())) { x = (Double) pl[1]; z = (Double) pl[3]; break; }
            }
            Object geom = null;
            if (x != null) {
                double lat = Terrain.LAT - z / 111320.0, lon = Terrain.LON + x / (111320.0 * Math.cos(Math.toRadians(Terrain.LAT)));
                geom = new Roh(String.format(Locale.ROOT, "SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(%.8f, %.8f, NULL), NULL, NULL)", lon, lat));
            } else { ohneLage++; System.out.println("ohne Lage: " + b.name() + " (ort " + b.ort() + ")"); }
            zeile("HEI_BUILDING", new String[]{"CODE"}, "CODE", b.code(), "NAME", b.name(), "NAME_HISTORISCH", b.historisch(), "BEZUG", b.bezug(), "DETAIL", b.detail(), "STUFEN", b.stufen(),
                    "ROLLE", b.rolle(), "X_M", x == null ? null : Double.valueOf(Math.round(x * 100) / 100.0), "Z_M", z == null ? null : Double.valueOf(Math.round(z * 100) / 100.0), "GEOM", geom, "SOURCE_ID", quelle(b.quelle()));
        }
        // ---- Blickpunkte
        Viewpoint[] v = Viewpoint.BUILTIN;
        for (int i = 0; i < v.length; i++) {
            String notiz = i <= 7 ? "Phase 1: Tal und Stadt" : i <= 14 ? "Phase 3 und 4: Schloss und Hortus" : i <= 18 ? "Phase 5: Brunnen" : i <= 24 ? "Phase 6: Dampf und Licht" : "Phase 8: Räume und Leben";
            zeile("HEI_VIEW", new String[]{"NR"}, "NR", i, "NAME", v[i].name, "NOTIZ", notiz, "DREHPUNKT_X", v[i].x, "DREHPUNKT_Z", v[i].z, "HOEHE_UEBER_BODEN", v[i].up,
                    "GIER_GRAD", v[i].yawDeg, "NICK_GRAD", v[i].pitchDeg, "ABSTAND_M", v[i].dist, "INNEN", v[i].inside ? "J" : "N");
        }
        // ---- Fahrten
        List<Rides.Ride> rides = Rides.build(t, w.solids);
        for (int i = 0; i < rides.size(); i++) {
            Rides.Ride r = rides.get(i);
            zeile("HEI_CLIP", new String[]{"CODE"}, "CODE", String.format("FAHRT_%02d", i + 1), "ART", "FAHRT", "STUFE", 0, "NR", i + 1, "NAME", r.name, "INFO", r.info, "DAUER_S", Math.round(r.shot.duration() * 100) / 100.0);
        }
        // ---- Drehbuch
        List<Director.Scene> sc = Director.scenes(t, w.solids);
        double dauer = 0;
        for (Director.Scene s : sc) dauer += s.shot.duration();
        zeile("HEI_CLIP", new String[]{"CODE"}, "CODE", KatalogDb.DREHBUCH, "ART", "DREHBUCH", "STUFE", 0, "NR", 1, "NAME", "Ein Tag auf dem Jettenbühl",
                "INFO", "Zwölf Szenen vom Sonnenaufgang bis zur Nacht mit Tafeln und Quellen; Tag, Wetter und Nebel sind ein Modell.", "DAUER_S", Math.round(dauer * 100) / 100.0);
        String clip = "(SELECT CLIP_ID FROM HEI_CLIP WHERE CODE = '" + KatalogDb.DREHBUCH + "')";
        TreeSet<String> quellenTexte = new TreeSet<>();
        for (int i = 0; i < sc.size(); i++) {
            Director.Scene s = sc.get(i);
            zeile("HEI_SHOT", new String[]{"CLIP_ID", "NR"}, "CLIP_ID", new Roh(clip), "NR", i + 1, "NAME", s.name, "STUNDE_VON", s.h0, "STUNDE_BIS", s.h1, "NEBEL", s.fog ? "J" : "N", "DUNST", s.haze);
            String shot = "(SELECT SHOT_ID FROM HEI_SHOT WHERE CLIP_ID = " + clip + " AND NR = " + (i + 1) + ")";
            for (Director.Tafel f : s.tafeln) {
                quellenTexte.add(f.quelle);
                zeile("HEI_PANEL", new String[]{"SHOT_ID", "VON_S"}, "SHOT_ID", new Roh(shot), "VON_S", f.from, "BIS_S", f.to, "KOPF", f.kopf, "TEXT", f.text, "QUELLE_TEXT", f.quelle, "SOURCE_ID", quelle(codeFuer(f.quelle)));
            }
        }

        // ---- Datei
        StringBuilder datei = new StringBuilder();
        datei.append("-- ============================================================================\n");
        datei.append("-- Heidelberger Schloss, Phase 10: Grunddaten der HEI_-Tabellen (erzeugt von tools.SeedGen, nicht von Hand aendern)\n");
        datei.append("-- Jede Anweisung fuegt eine Zeile nur ein, wenn es sie noch nicht gibt; Aenderungen in der Datenbank bleiben stehen.\n");
        datei.append("-- Quellen, Zeitstufen, Chronik, Bauwerke (mit Lage als SDO_GEOMETRY, SRID 8307), Blickpunkte, Fahrten, Drehbuch mit Tafeln.\n");
        datei.append("-- ============================================================================\n\n");
        datei.append("SET DEFINE OFF\n\n");
        for (Map.Entry<String, Integer> e : ZAHL.entrySet()) datei.append("-- ERWARTET ").append(e.getKey()).append("=").append(e.getValue()).append("\n");
        datei.append("\n").append(OUT);
        File f = new File(dir, "04_seed.sql");
        Files.write(f.toPath(), datei.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("geschrieben: " + f + " (" + datei.length() + " Zeichen)");
        System.out.println("Zeilen je Tabelle: " + ZAHL);
        System.out.println("Bauwerke ohne Lage: " + ohneLage);
        System.out.println("Quellen der Tafeln -> Kürzel:");
        for (String q : quellenTexte) System.out.println("   " + q + " -> " + codeFuer(q));
        if (!FEHLER.isEmpty()) {
            System.out.println("FEHLER (" + FEHLER.size() + "):");
            for (String s : FEHLER) System.out.println("   " + s);
            System.exit(1);
        }
        System.out.println("OK: alle Längen in Bytes innerhalb der Spaltengrenzen.");
    }

    private static final String[][] KEYS = {
            {"de Caus", "DECAUS_1620"}, {"Staatliche", "SSG_BW"}, {"Wikipedia", "WIKI_SCHLOSS"}, {"zum.de", "ZUM"},
            {"Modell", "MODELL"}, {"Annahme", "MODELL"}, {"Technik", "MODELL"}, {"Rundgang", "MODELL"}, {"Rekonstruktion", "MODELL"}};

    /** Das Kürzel der zuerst genannten Quelle; „Wikipedia: Hortus …“ und „Wikipedia: Großes Fass …“ nennen den Artikel. */
    private static String codeFuer(String quelle) {
        if (quelle == null) return null;
        if (quelle.startsWith("Wikipedia:")) {
            String art = quelle.split(";")[0];
            if (art.contains("Hortus")) return "WIKI_HORTUS";
            if (art.contains("Fass")) return "WIKI_FASS";
            return "WIKI_SCHLOSS";
        }
        String best = null;
        int pos = Integer.MAX_VALUE;
        for (String[] k : KEYS) {
            int i = quelle.indexOf(k[0]);
            if (i >= 0 && i < pos) { pos = i; best = k[1]; }
        }
        if (best == null) FEHLER.add("Tafelquelle ohne Kürzel: " + quelle);
        return best;
    }

    private static Object quelle(String code) {
        return code == null ? null : new Roh("(SELECT SOURCE_ID FROM HEI_SOURCE WHERE CODE = '" + code + "')");
    }

    private static final Pattern COL = Pattern.compile("^\\s+(\\w+)\\s+VARCHAR2\\((\\d+)\\)"), TAB = Pattern.compile("^CREATE TABLE (\\w+)"), CHR = Pattern.compile("^\\s+(\\w+)\\s+CHAR\\((\\d+)\\)");

    private static void grenzen(File f) throws Exception {
        String tab = null;
        for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            Matcher m = TAB.matcher(l);
            if (m.find()) { tab = m.group(1); LIMIT.put(tab, new LinkedHashMap<>()); continue; }
            m = COL.matcher(l);
            if (tab != null && m.find()) { LIMIT.get(tab).put(m.group(1), Integer.parseInt(m.group(2))); continue; }
            m = CHR.matcher(l);
            if (tab != null && m.find()) LIMIT.get(tab).put(m.group(1), Integer.parseInt(m.group(2)));
        }
    }

    private static String lit(String tab, String col, Object v) {
        if (v == null) return "NULL";
        if (v instanceof Roh) return ((Roh) v).sql();
        if (v instanceof LocalDate) return "DATE '" + v + "'";
        if (v instanceof Double) {
            double x = (Double) v;
            return x == Math.rint(x) && Math.abs(x) < 1e9 ? Long.toString((long) x) : String.format(Locale.ROOT, "%.4f", x).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        if (v instanceof Number) return v.toString();
        String s = v.toString();
        Integer max = LIMIT.getOrDefault(tab, Map.of()).get(col);
        int bytes = s.getBytes(StandardCharsets.UTF_8).length;
        if (max != null && bytes > max) FEHLER.add(tab + "." + col + ": " + bytes + " Bytes, erlaubt " + max + ": " + (s.length() > 50 ? s.substring(0, 50) + " …" : s));
        if (max == null && !col.equals("NEBEL")) FEHLER.add(tab + "." + col + ": keine Grenze in 01_tables.sql gefunden (Zahl oder Text?)");
        if (s.contains("&")) FEHLER.add(tab + "." + col + ": enthält & (SET DEFINE OFF nötig): " + s);
        if (s.contains("\n")) FEHLER.add(tab + "." + col + ": enthält Zeilenumbruch");
        return "'" + s.replace("'", "''") + "'";
    }

    /** Eine Zeile: INSERT … SELECT … FROM DUAL WHERE NOT EXISTS (Schlüssel). */
    private static void zeile(String tab, String[] schluessel, Object... kv) {
        StringBuilder cols = new StringBuilder(), vals = new StringBuilder(), where = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            String c = (String) kv[i];
            String l = lit(tab, c, kv[i + 1]);
            if (cols.length() > 0) { cols.append(", "); vals.append(", "); }
            cols.append(c);
            vals.append(l);
            for (String k : schluessel) if (k.equals(c)) { if (where.length() > 0) where.append(" AND "); where.append(c).append(" = ").append(l); }
        }
        OUT.append("INSERT INTO ").append(tab).append(" (").append(cols).append(")\nSELECT ").append(vals).append(" FROM DUAL\nWHERE NOT EXISTS (SELECT 1 FROM ").append(tab).append(" WHERE ").append(where).append(")\n/\n\n");
        ZAHL.merge(tab, 1, Integer::sum);
    }

    private SeedGen() { }
}
