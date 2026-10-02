package com.dan.heidelberg.tools;

import com.dan.heidelberg.camera.Viewpoint;
import com.dan.heidelberg.db.Dienst;
import com.dan.heidelberg.db.Katalog;
import com.dan.heidelberg.db.Skript;
import com.dan.heidelberg.db.Zustand;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft ohne Datenbank, was sich ohne Oracle prüfen lässt: die Skripte db/NN_*.sql (Zerlegung, erwartete Objekte), dass
 * Dienst.TABELLEN genau die Tabellen des Skripts sind, dass Zustand.SPEC Spalte für Spalte zu HEI_STATE passt (Name, Typ,
 * Vorgabe, Grenzen), den Rundlauf des Zustands über die Datei und die Zeilenzahlen der Grunddaten gegen den Code.
 * Aufruf: DbCheck [db-Ordner]
 */
public final class DbCheck {
    private static int fehler;

    private static void pruefe(boolean ok, String was) {
        System.out.println((ok ? "  ok     " : "  FEHLER ") + was);
        if (!ok) fehler++;
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "db");
        List<Skript> skripte = Skript.laden(dir);
        System.out.println("Skripte: " + skripte.size());
        Set<Skript.Objekt> erwartet = new LinkedHashSet<>();
        Map<String, Integer> zeilen = new LinkedHashMap<>();
        for (Skript s : skripte) {
            erwartet.addAll(s.erwartet());
            zeilen.putAll(s.erwartetZeilen);
            System.out.println("  " + s.name + ": " + s.anweisungen.size() + " Anweisungen, " + s.erwartet().size() + " Objekte");
        }
        Map<String, Integer> proTyp = new LinkedHashMap<>();
        for (Skript.Objekt o : erwartet) proTyp.merge(o.typ, 1, Integer::sum);
        System.out.println("Erwartete Objekte: " + proTyp);

        // ---- Tabellen
        Set<String> tabellen = new LinkedHashSet<>();
        for (Skript.Objekt o : erwartet) if (o.typ.equals("TABLE")) tabellen.add(o.name);
        Set<String> ds = new LinkedHashSet<>(java.util.Arrays.asList(Dienst.TABELLEN));
        pruefe(tabellen.equals(ds), "Dienst.TABELLEN = Tabellen des Skripts (" + tabellen.size() + ")");
        for (String t : tabellen) {
            pruefe(erwartet.contains(objekt("SEQUENCE", t + "_SEQ")), "Folge " + t + "_SEQ");
            pruefe(erwartet.contains(objekt("TRIGGER", t + "_BI")), "Trigger " + t + "_BI");
        }
        pruefe(erwartet.contains(objekt("PACKAGE", "HEI_API")) && erwartet.contains(objekt("PACKAGE BODY", "HEI_API")), "Paket HEI_API mit Rumpf");
        pruefe(erwartet.contains(objekt("INDEX", "HEI_BUILDING_SX")), "räumlicher Index HEI_BUILDING_SX");
        for (String n : new String[]{"HEI_SOURCE_PK", "HEI_STATE_PK", "HEI_EVENT_PK", "HEI_PANEL_UK"}) pruefe(erwartet.contains(objekt("INDEX", n)), "Constraint-Index " + n);

        // ---- Zustand.SPEC gegen HEI_STATE
        String ddl = new String(Files.readAllBytes(new File(dir, "01_tables.sql").toPath()), StandardCharsets.UTF_8);
        int p0 = ddl.indexOf("CREATE TABLE HEI_STATE"), p1 = ddl.indexOf("\n/\n", p0);
        String blk = ddl.substring(p0, p1);
        Pattern col = Pattern.compile("^\\s+(\\w+)\\s+(NUMBER\\(\\d+(?:,\\d+)?\\)|CHAR\\(1\\)|VARCHAR2\\(\\d+(?: CHAR)?\\)|TIMESTAMP\\(6\\))\\s+(?:DEFAULT\\s+('?[\\w.]+'?)\\s+)?NOT NULL|^\\s+(\\w+)\\s+(NUMBER\\(\\d+\\))\\s*,?\\s*$", Pattern.MULTILINE);
        Map<String, String[]> spalten = new LinkedHashMap<>();
        Matcher m = col.matcher(blk);
        while (m.find()) {
            if (m.group(1) != null) spalten.put(m.group(1), new String[]{m.group(2), m.group(3)});
            else spalten.put(m.group(4), new String[]{m.group(5), null});
        }
        List<String> namen = new ArrayList<>(spalten.keySet());
        int start = namen.indexOf(Zustand.SPEC[0].name);
        pruefe(start == 5 || start == 4, "HEI_STATE: Kopfspalten vor " + Zustand.SPEC[0].name + " (" + namen.subList(0, Math.max(0, start)) + ")");
        boolean gleich = namen.size() - start == Zustand.SPEC.length;
        pruefe(gleich, "HEI_STATE hat " + (namen.size() - start) + " Zustandsspalten, Zustand.SPEC " + Zustand.SPEC.length);
        for (int i = 0; i < Zustand.SPEC.length && start + i < namen.size(); i++) {
            Zustand.Feld f = Zustand.SPEC[i];
            String n = namen.get(start + i);
            String[] s = spalten.get(n);
            boolean ok = n.equals(f.name);
            String typ = s[0];
            switch (f.typ) {
                case FLAG: ok &= typ.equals("CHAR(1)") && s[1] != null && s[1].equals(((Boolean) f.vorgabe) ? "'J'" : "'N'"); break;
                case TEXT: ok &= typ.startsWith("VARCHAR2") && s[1] != null && s[1].equals("'" + f.vorgabe + "'"); break;
                default: ok &= typ.startsWith("NUMBER") && s[1] != null && Math.abs(Double.parseDouble(s[1]) - ((Number) f.vorgabe).doubleValue()) < 1e-9;
            }
            if (f.typ == Zustand.Typ.ZAHL) {
                Matcher c = Pattern.compile("CHECK \\(" + f.name + " BETWEEN (-?[\\d.]+) AND (-?[\\d.]+)\\)").matcher(blk);
                if (c.find()) ok &= Double.parseDouble(c.group(1)) <= f.min && f.max <= Double.parseDouble(c.group(2));
                // Genauigkeit: NUMBER(p,s): der größte Wert muss hineinpassen
                Matcher pm = Pattern.compile("NUMBER\\((\\d+)(?:,(\\d+))?\\)").matcher(typ);
                if (pm.find()) {
                    int p = Integer.parseInt(pm.group(1)), sc = pm.group(2) == null ? 0 : Integer.parseInt(pm.group(2));
                    double lim = Math.pow(10, p - sc);
                    ok &= Math.abs(f.max) < lim && Math.abs(f.min) < lim;
                }
            }
            if (!ok) System.out.println("     Spalte " + n + " " + typ + " " + s[1] + " gegen Feld " + f.name + " " + f.typ + " " + f.vorgabe + " [" + f.min + ", " + f.max + "]");
            if (!ok) fehler++;
        }
        System.out.println("  " + (fehler == 0 ? "ok     " : "       ") + "Zustand.SPEC stimmt Spalte für Spalte mit HEI_STATE überein (Name, Typ, Vorgabe, Grenzen, Genauigkeit)");

        // ---- Rundlauf Datei
        Random r = new Random(7);
        boolean rund = true;
        for (int k = 0; k < 50; k++) {
            Zustand z = new Zustand();
            for (Zustand.Feld f : Zustand.SPEC) {
                switch (f.typ) {
                    case ZAHL: z.setze(f.name, f.min + r.nextDouble() * (Math.min(f.max, 1e5) - f.min)); break;
                    case FLAG: z.setze(f.name, r.nextBoolean()); break;
                    default: z.setze(f.name, new String[]{"AUTO", "50", "75", "100"}[r.nextInt(4)]);
                }
            }
            z.kameraGesetzt = r.nextBoolean();
            z.name = "Test " + k;
            File t = File.createTempFile("zustand", ".properties");
            Zustand.speichern(t, z);
            Zustand b = Zustand.laden(t);
            t.delete();
            if (b == null || b.kameraGesetzt != z.kameraGesetzt || !b.name.equals(z.name)) { rund = false; break; }
            for (Zustand.Feld f : Zustand.SPEC) {
                boolean g;
                switch (f.typ) {
                    case ZAHL: g = Math.abs(b.zahl(f.name) - z.zahl(f.name)) < 1e-9; break;
                    case FLAG: g = b.flag(f.name) == z.flag(f.name); break;
                    default: g = b.text(f.name).equals(z.text(f.name));
                }
                if (!g) { rund = false; System.out.println("     Rundlauf: " + f.name); }
            }
        }
        pruefe(rund, "Zustand: 50 Zufallszustände über die Datei und zurück");
        Zustand v = Zustand.vorgabe();
        v.setze("STUNDE", 99).setze("STUFE", -4).setze("TAG", Double.NaN);
        pruefe(v.zahl("STUNDE") == 24 && v.zahl("STUFE") == 0 && v.zahl("TAG") == 172, "Zustand begrenzt Werte (99 → 24, −4 → 0, NaN → Vorgabe)");
        Properties kaputt = new Properties();
        kaputt.setProperty("STUNDE", "abc"); kaputt.setProperty("TAG", "400"); kaputt.setProperty("WETTER", "x");
        Zustand kz = Zustand.ausProperties(kaputt);
        pruefe(kz.zahl("STUNDE") == 16 && kz.zahl("TAG") == 366 && kz.zahl("WETTER") == 0, "kaputte Datei fällt auf Vorgaben zurück");

        // ---- Grunddaten gegen den Code
        Katalog.Daten d = Katalog.eingebaut();
        pruefe(zeilen.getOrDefault("HEI_SOURCE", 0) == d.quellen.size(), "Seed HEI_SOURCE " + zeilen.get("HEI_SOURCE") + " = Katalog " + d.quellen.size());
        pruefe(zeilen.getOrDefault("HEI_STAGE", 0) == d.stufen.size(), "Seed HEI_STAGE " + zeilen.get("HEI_STAGE") + " = Katalog " + d.stufen.size());
        pruefe(zeilen.getOrDefault("HEI_CHRONIK", 0) == d.chronik.size(), "Seed HEI_CHRONIK " + zeilen.get("HEI_CHRONIK") + " = Katalog " + d.chronik.size());
        pruefe(zeilen.getOrDefault("HEI_BUILDING", 0) == d.bauten.size(), "Seed HEI_BUILDING " + zeilen.get("HEI_BUILDING") + " = Katalog " + d.bauten.size());
        pruefe(zeilen.getOrDefault("HEI_VIEW", 0) == Viewpoint.BUILTIN.length, "Seed HEI_VIEW " + zeilen.get("HEI_VIEW") + " = Blickpunkte " + Viewpoint.BUILTIN.length);
        pruefe(zeilen.getOrDefault("HEI_CLIP", 0) == 13 && zeilen.getOrDefault("HEI_SHOT", 0) == 12 && zeilen.getOrDefault("HEI_PANEL", 0) > 0, "Seed Clips 13 (12 Fahrten und das Drehbuch), Szenen 12, Tafeln " + zeilen.get("HEI_PANEL"));
        File seed = new File(dir, "04_seed.sql");
        String st = new String(Files.readAllBytes(seed.toPath()), StandardCharsets.UTF_8);
        int inserts = 0;
        for (Skript s : skripte) if (s.name.startsWith("04")) for (String x : s.anweisungen) if (x.startsWith("INSERT INTO") && x.contains("WHERE NOT EXISTS")) inserts++;
        int summe = 0;
        for (int n : zeilen.values()) summe += n;
        pruefe(inserts == summe, "Seed: " + inserts + " INSERT … WHERE NOT EXISTS, erwartet " + summe);
        pruefe(!st.contains("DELETE") && !st.contains("UPDATE ") && !st.contains("DROP "), "Seed ändert und löscht nichts");
        // Kein Passwort in den Skripten
        boolean pw = false;
        for (Skript s : skripte) for (String x : s.anweisungen) if (x.toUpperCase().contains("IDENTIFIED BY") || x.toUpperCase().contains("PASSWORD")) pw = true;
        pruefe(!pw, "kein Passwort in den Skripten");

        System.out.println(fehler == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + fehler + " Fehler");
        if (fehler > 0) System.exit(1);
    }

    private static Skript.Objekt objekt(String typ, String name) {
        try {
            java.lang.reflect.Constructor<Skript.Objekt> c = Skript.Objekt.class.getDeclaredConstructor(String.class, String.class);
            c.setAccessible(true);
            return c.newInstance(typ, name);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private DbCheck() { }
}
