package com.dan.heidelberg.tools;

import com.dan.heidelberg.db.Einrichter;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lässt den Einrichter gegen eine Attrappe von Oracle laufen (java.lang.reflect.Proxy statt JDBC-Treiber): sie legt Objekte an, meldet
 * ORA-00955 bei Wiederholung, zählt eingefügte Zeilen und beantwortet die Prüfabfragen. Das prüft den Ablauf des Einrichters
 * (Zerlegung, Zählung, Wiederholbarkeit, Meldungen), nicht die SQL-Dialekte; die prüft nur ein echtes Oracle.
 * Aufruf: EinrichterTest [db-Ordner]
 */
public final class EinrichterTest {
    /** Das Gedächtnis der Attrappe. */
    static final class Modell {
        final Map<String, String> objekte = new TreeMap<>();
        final Map<String, Integer> zeilen = new LinkedHashMap<>();
        final Set<String> eingefuegt = new HashSet<>();
        boolean metadaten;
        int ausgefuehrt;
        boolean ungueltig;   // simuliert Übersetzungsfehler im Paketrumpf
    }

    static Object proxy(Class<?> c, InvocationHandler h) { return Proxy.newProxyInstance(EinrichterTest.class.getClassLoader(), new Class<?>[]{c}, h); }

    static SQLException ora(int code, String text) { return new SQLException("ORA-" + String.format("%05d", code) + ": " + text, "42000", code); }

    static ResultSet rs(List<Object[]> rows) {
        final int[] i = {-1};
        return (ResultSet) proxy(ResultSet.class, (p, m, a) -> {
            switch (m.getName()) {
                case "next": return ++i[0] < rows.size();
                case "getString": return String.valueOf(rows.get(i[0])[(Integer) a[0] - 1]);
                case "getInt": return ((Number) rows.get(i[0])[(Integer) a[0] - 1]).intValue();
                case "getLong": return ((Number) rows.get(i[0])[(Integer) a[0] - 1]).longValue();
                case "close": return null;
                default: throw new UnsupportedOperationException("ResultSet." + m.getName());
            }
        });
    }

    static final Pattern CREATE = Pattern.compile("^CREATE (?:OR REPLACE )?(TABLE|SEQUENCE|UNIQUE INDEX|INDEX|TRIGGER|PACKAGE BODY|PACKAGE) (\\w+)", Pattern.CASE_INSENSITIVE);
    static final Pattern INS = Pattern.compile("^INSERT INTO (\\w+)", Pattern.CASE_INSENSITIVE);

    static int execute(Modell m, String sql) throws SQLException {
        m.ausgefuehrt++;
        String u = sql.trim();
        Matcher c = CREATE.matcher(u);
        if (c.find()) {
            String typ = c.group(1).toUpperCase().replace("UNIQUE ", ""), name = c.group(2).toUpperCase();
            boolean replace = u.toUpperCase().startsWith("CREATE OR REPLACE");
            if (!replace && m.objekte.containsKey(typ + " " + name)) throw ora(955, "name is already used by an existing object");
            m.objekte.put(typ + " " + name, typ.equals("PACKAGE BODY") && m.ungueltig ? "INVALID" : "VALID");
            if (typ.equals("TABLE")) {
                Matcher k = Pattern.compile("CONSTRAINT (\\w+) (?:PRIMARY KEY|UNIQUE)", Pattern.CASE_INSENSITIVE).matcher(u);
                while (k.find()) m.objekte.put("INDEX " + k.group(1).toUpperCase(), "VALID");
                m.zeilen.putIfAbsent(name, 0);
            }
            return 0;
        }
        Matcher i = INS.matcher(u);
        if (i.find()) {
            String t = i.group(1).toUpperCase();
            if (!u.contains("WHERE NOT EXISTS")) return 1;
            if (!m.objekte.containsKey("TABLE " + t)) throw ora(942, "table or view does not exist");
            if (m.eingefuegt.add(u)) { m.zeilen.merge(t, 1, Integer::sum); return 1; }
            return 0;
        }
        if (u.toUpperCase().startsWith("BEGIN") && u.contains("USER_SDO_GEOM_METADATA")) { m.metadaten = true; return 0; }
        if (u.toUpperCase().startsWith("COMMENT ON")) return 0;
        throw ora(900, "invalid SQL statement: " + u.substring(0, Math.min(40, u.length())));
    }

    static Connection verbindung(Modell m) {
        InvocationHandler stmt = (p, meth, a) -> {
            switch (meth.getName()) {
                case "execute": execute(m, (String) a[0]); return true;
                case "getUpdateCount": return 1;
                case "getWarnings": return null;
                case "clearWarnings": case "setEscapeProcessing": case "close": return null;
                case "executeQuery": return abfrage(m, (String) a[0]);
                default: throw new UnsupportedOperationException("Statement." + meth.getName());
            }
        };
        InvocationHandler call = new InvocationHandler() {
            final Map<Integer, Object> par = new LinkedHashMap<>();
            String sql;
            @Override public Object invoke(Object p, java.lang.reflect.Method meth, Object[] a) throws Throwable {
                switch (meth.getName()) {
                    case "registerOutParameter": case "setString": case "setInt": case "setLong": case "setDouble": case "setNull": par.put((Integer) a[0], a.length > 1 ? a[1] : null); return null;
                    case "execute": return true;
                    case "executeUpdate": return 1;
                    case "getString": return "HEI_API 1.0 (Attrappe)";
                    case "getLong": return 7L;
                    case "getGeneratedKeys": return rs(java.util.Collections.singletonList(new Object[]{7L}));
                    case "executeQuery": return rs(java.util.Collections.singletonList(new Object[]{1, 0}));
                    case "close": return null;
                    default: throw new UnsupportedOperationException("CallableStatement." + meth.getName());
                }
            }
        };
        return (Connection) proxy(Connection.class, (p, meth, a) -> {
            switch (meth.getName()) {
                case "createStatement": return proxy(Statement.class, stmt);
                case "prepareCall": return proxy(CallableStatement.class, call);
                case "prepareStatement": return proxy(PreparedStatement.class, call);
                case "getAutoCommit": return true;
                case "setAutoCommit": case "rollback": case "close": return null;
                case "getMetaData": return proxy(DatabaseMetaData.class, (p2, m2, a2) -> "Oracle Database 21c (Attrappe)\nVersion 21");
                default: throw new UnsupportedOperationException("Connection." + meth.getName());
            }
        });
    }

    static ResultSet abfrage(Modell m, String sql) throws SQLException {
        List<Object[]> r = new ArrayList<>();
        if (sql.contains("SYS_CONTEXT")) r.add(new Object[]{"DEMO", "PDBORCL", "PDBORCL"});
        else if (sql.contains("FROM USER_OBJECTS ORDER BY") || sql.contains("USER_OBJECTS WHERE OBJECT_NAME LIKE")) {
            for (Map.Entry<String, String> e : m.objekte.entrySet()) {
                int sp = e.getKey().lastIndexOf(' ');
                r.add(new Object[]{e.getKey().substring(0, sp), e.getKey().substring(sp + 1), e.getValue()});
            }
            r.add(new Object[]{"TABLE", "SEM_BEISPIEL", "VALID"});
        } else if (sql.contains("USER_ERRORS")) { if (m.ungueltig) r.add(new Object[]{"HEI_API", "PACKAGE BODY", 12, "PLS-00201: identifier must be declared"}); }
        else if (sql.contains("USER_SDO_GEOM_METADATA")) r.add(new Object[]{m.metadaten ? 1 : 0});
        else if (sql.contains("USER_INDEXES")) r.add(new Object[]{"VALID", "VALID"});
        else if (sql.contains("COUNT(*) FROM HEI_")) { Matcher t = Pattern.compile("FROM (HEI_\\w+)").matcher(sql); t.find(); r.add(new Object[]{m.zeilen.getOrDefault(t.group(1), 0)}); }
        else throw ora(900, "Abfrage der Attrappe unbekannt: " + sql);
        return rs(r);
    }

    static int fehler;

    static void pruefe(boolean ok, String was) { System.out.println((ok ? "  ok     " : "  FEHLER ") + was); if (!ok) fehler++; }

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "db");
        File tmp = java.nio.file.Files.createTempDirectory("einr").toFile();
        for (File f : dir.listFiles((d, n) -> n.endsWith(".sql"))) java.nio.file.Files.copy(f.toPath(), new File(tmp, f.getName()).toPath());
        Modell m = new Modell();
        Connection c = verbindung(m);

        System.out.println("--- Lauf 1 (leeres Schema)");
        List<String> z1 = new ArrayList<>();
        Einrichter.Ergebnis e1 = Einrichter.lauf(c, tmp, z1::add);
        z1.forEach(s -> System.out.println("   | " + s));
        pruefe(e1.ok, "Lauf 1: alles in Ordnung (" + e1.probleme + " Probleme)");
        pruefe(m.objekte.size() == 11 + 30 + 11 + 11 + 2, "Objekte angelegt: " + m.objekte.size() + " (11 Tabellen, 30 Indizes, 11 Folgen, 11 Trigger, 2 Pakete)");
        int rowsNach1 = m.zeilen.values().stream().mapToInt(Integer::intValue).sum();
        pruefe(new File(tmp, "einrichtung.txt").isFile() && new File(tmp, "bestand.txt").isFile(), "einrichtung.txt und bestand.txt geschrieben");

        System.out.println("--- Lauf 2 (alles schon da)");
        List<String> z2 = new ArrayList<>();
        Einrichter.Ergebnis e2 = Einrichter.lauf(c, tmp, z2::add);
        pruefe(e2.ok, "Lauf 2: alles in Ordnung (" + e2.probleme + " Probleme)");
        pruefe(m.zeilen.values().stream().mapToInt(Integer::intValue).sum() == rowsNach1, "Lauf 2 fügt keine Zeilen hinzu");
        boolean vorh = false;
        for (String s : z2) if (s.contains("01_tables.sql") && s.contains("schon vorhanden") && !s.contains(" 0 schon vorhanden")) vorh = true;
        pruefe(vorh, "Lauf 2 meldet die Tabellen als schon vorhanden");

        System.out.println("--- Lauf 3 (Paketrumpf ungültig)");
        Modell m3 = new Modell();
        m3.ungueltig = true;
        List<String> z3 = new ArrayList<>();
        Einrichter.Ergebnis e3 = Einrichter.lauf(verbindung(m3), tmp, z3::add);
        pruefe(!e3.ok && e3.probleme >= 1, "Lauf 3 meldet Probleme (" + e3.probleme + ")");
        boolean ung = false, pls = false;
        for (String s : z3) { if (s.contains("UNGÜLTIG: PACKAGE BODY HEI_API")) ung = true; if (s.contains("PLS-00201")) pls = true; }
        pruefe(ung && pls, "Lauf 3 nennt das ungültige Paket und den Übersetzungsfehler");
        pruefe(z3.get(z3.size() - 1).startsWith("ERGEBNIS: "), "letzte Zeile: " + z3.get(z3.size() - 1));
        System.out.println(e1.text.lines().filter(s -> s.startsWith("ERGEBNIS")).findFirst().orElse("?"));
        System.out.println(fehler == 0 ? "ERGEBNIS: Ablauf des Einrichters in Ordnung" : "ERGEBNIS: " + fehler + " Fehler");
        if (fehler > 0) System.exit(1);
    }

    private EinrichterTest() { }
}
