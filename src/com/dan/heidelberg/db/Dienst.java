package com.dan.heidelberg.db;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * Die Datenbank im Programm: Lage der Verbindung, Sitzung, Protokoll und Zustand. Alles läuft auf einem eigenen Thread,
 * die Bildschleife und die Oberfläche warten nie auf Oracle. Ohne Datenbank (nicht erreichbar, nicht eingerichtet, abgeschaltet
 * mit -Dheidelberg.nodb=true) arbeitet das Programm mit den eingebauten Werten; Zustand und Protokoll gehen dann in Dateien unter
 * ~/.heidelberg (oder -Dheidelberg.state.dir).
 */
public final class Dienst {
    private Dienst() { }

    public enum Lage {
        PRUEFT("Verbindung wird geprüft …"), AUS("ohne Datenbank (abgeschaltet)"), KEINE_KONFIG("ohne Datenbank (db/db.properties fehlt)"),
        KEIN_PASSWORT("Passwort fehlt"), OFFLINE("ohne Datenbank (nicht erreichbar)"), NICHT_EINGERICHTET("verbunden, HEI_ noch nicht eingerichtet"),
        BEREIT("verbunden, HEI_ eingerichtet");
        public final String text;
        Lage(String t) { text = t; }
    }

    public static final String APP_VERSION = "10.0";
    /** Die Tabellen, die zu einer eingerichteten Datenbank gehören (die Skripte db/01 legen genau diese an). */
    public static final String[] TABELLEN = {"HEI_SOURCE", "HEI_STAGE", "HEI_BUILDING", "HEI_CHRONIK", "HEI_VIEW", "HEI_CLIP", "HEI_SHOT", "HEI_PANEL", "HEI_SESSION", "HEI_STATE", "HEI_EVENT"};

    private static volatile Lage lage = Lage.PRUEFT;
    private static volatile String meldung = "";
    private static volatile Db.Konfig konfig;
    private static volatile String anpassung = "";
    private static Connection conn;
    private static volatile long sessionId;
    private static volatile long startNs = System.nanoTime();
    private static volatile IntSupplier ctxStufe = () -> 0;
    private static volatile DoubleSupplier ctxStunde = () -> 0;
    private static final AtomicBoolean beendet = new AtomicBoolean();
    private static final List<Runnable> lauscher = new CopyOnWriteArrayList<>();
    private static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Heidelberg-DB"); t.setDaemon(true); return t; });

    // ------------------------------------------------------------ Lage

    public static Lage lage() { return lage; }
    public static String meldung() { return meldung; }
    public static String anpassung() { return anpassung; }
    public static Db.Konfig konfig() { return konfig; }
    public static boolean bereit() { return lage == Lage.BEREIT; }

    /** Eine Zeile für Statusleiste und Startbild. */
    public static String kurz() {
        Db.Konfig k = konfig;
        String z = lage.text;
        if (k != null && (lage == Lage.BEREIT || lage == Lage.NICHT_EINGERICHTET)) z = k.kurz() + ": " + z;
        return "Datenbank: " + z;
    }

    /** Wird auf dem Ereignis-Thread aufgerufen, wenn sich die Lage ändert. */
    public static void beiAenderung(Runnable r) { lauscher.add(r); }

    private static void lage(Lage l, String m) {
        lage = l;
        meldung = m == null ? "" : m;
        for (Runnable r : lauscher) javax.swing.SwingUtilities.invokeLater(r);
    }

    public static File lokalerOrdner() {
        String p = System.getProperty("heidelberg.state.dir");
        return p != null && !p.isEmpty() ? new File(p) : new File(System.getProperty("user.home"), ".heidelberg");
    }

    private static File zustandsDatei() { return new File(lokalerOrdner(), "zustand.properties"); }

    // ------------------------------------------------------------ Start

    /** Liest die Zugangsdaten und prüft die Datenbank im Hintergrund. */
    public static void start() {
        startNs = System.nanoTime();
        if (Boolean.getBoolean("heidelberg.nodb")) { lage(Lage.AUS, ""); return; }
        try {
            konfig = Db.konfig();
        } catch (IOException e) {
            lage(Lage.KEINE_KONFIG, e.getMessage());
            return;
        }
        if (!konfig.hatPasswort()) { lage(Lage.KEIN_PASSWORT, "Passwort für " + konfig.user + " eingeben (bleibt nur im Speicher)"); return; }
        EX.submit(Dienst::pruefen);
    }

    /** Das Startbild gibt das Passwort ein; es bleibt nur im Speicher. */
    public static void verbinden(char[] passwort) {
        Db.Konfig k = konfig;
        if (k == null) return;
        konfig = k.mitPasswort(passwort);
        lage(Lage.PRUEFT, "");
        EX.submit(Dienst::pruefen);
    }

    /** Nach dem Einrichter: erneut prüfen. */
    public static void neuPruefen() {
        if (konfig == null || !konfig.hatPasswort()) return;
        lage(Lage.PRUEFT, "");
        EX.submit(Dienst::pruefen);
    }

    private static void pruefen() {
        try {
            Connection c = verb();
            StringBuilder in = new StringBuilder();
            for (String t : TABELLEN) in.append(in.length() == 0 ? "" : ",").append('\'').append(t).append('\'');
            int n;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM USER_OBJECTS WHERE STATUS = 'VALID' AND ((OBJECT_TYPE = 'TABLE' AND OBJECT_NAME IN (" + in
                    + ")) OR (OBJECT_TYPE = 'PACKAGE BODY' AND OBJECT_NAME = 'HEI_API'))");
                 ResultSet rs = ps.executeQuery()) { rs.next(); n = rs.getInt(1); }
            if (n == TABELLEN.length + 1) {
                anpassung = KatalogDb.anpassungen(c);
                lage(Lage.BEREIT, "");
            } else {
                anpassung = "";
                lage(Lage.NICHT_EINGERICHTET, n + " von " + (TABELLEN.length + 1) + " Objekten vorhanden");
            }
        } catch (SQLException e) {
            abbruch(e);
        }
    }

    private static Connection verb() throws SQLException {
        if (conn != null) {
            try { if (conn.isValid(2)) return conn; } catch (SQLException e) { /* neu öffnen */ }
            try { conn.close(); } catch (SQLException e) { /* egal */ }
            conn = null;
        }
        Db.Konfig k = konfig;
        if (k == null || !k.hatPasswort()) throw new SQLException("keine Zugangsdaten");
        conn = Db.oeffnen(k);
        return conn;
    }

    private static void abbruch(SQLException e) {
        String m = String.valueOf(e.getMessage()).split("\n")[0].trim();
        // Ein Fehler der Anweisung (zu langer Text, verletzte Regel) ist kein Verbindungsfehler: die Verbindung bleibt, die Zeile geht ins Dateiprotokoll
        try {
            if (e.getErrorCode() != 1017 && conn != null && conn.isValid(2)) { lokal("WARN", "Datenbank", m); return; }
        } catch (SQLException x) { /* dann ist sie wirklich weg */ }
        if (e.getErrorCode() == 1017) { konfig = konfig == null ? null : konfig.mitPasswort(null); lage(Lage.KEIN_PASSWORT, "Benutzer oder Passwort stimmen nicht: " + m); }
        else lage(Lage.OFFLINE, m);
        try { if (conn != null) conn.close(); } catch (SQLException x) { /* egal */ }
        conn = null;
    }

    /** Eine eigene Verbindung für den Einrichter-Dialog (der Einrichter läuft auf seinem Thread). */
    public static Connection neueVerbindung() throws SQLException {
        Db.Konfig k = konfig;
        if (k == null || !k.hatPasswort()) throw new SQLException("keine Zugangsdaten");
        return Db.oeffnen(k);
    }

    public static <T> Future<T> aufgabe(Callable<T> c) { return EX.submit(c); }

    /** Der Katalog: aus der Datenbank, wenn sie bereit ist und Zeilen hat (ausDb[0] wird dann wahr), sonst der eingebaute. */
    public static Katalog.Daten katalog(boolean[] ausDb) {
        if (lage == Lage.BEREIT) {
            try {
                Katalog.Daten d = EX.submit(() -> KatalogDb.laden(verb())).get(8, TimeUnit.SECONDS);
                if (!d.quellen.isEmpty() || !d.bauten.isEmpty()) { if (ausDb != null) ausDb[0] = true; return d; }
            } catch (Exception e) {
                if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            }
        }
        return Katalog.eingebaut();
    }

    /** Schaltet die Datenbank für diese Sitzung ab (Zustand und Protokoll gehen in Dateien). */
    public static void abschalten() { lage(Lage.AUS, ""); }

    // ------------------------------------------------------------ Zustand

    /** Der letzte Zustand beim Beenden: aus der Datenbank, sonst aus der Datei; null, wenn es keinen gibt. */
    public static Zustand ladeLetzten() {
        try {
            if (lage == Lage.BEREIT) {
                Zustand z = EX.submit(() -> {
                    Connection c = verb();
                    long id = ZustandDb.juengster(c, "AUTO");
                    return id == 0 ? null : ZustandDb.laden(c, id);
                }).get(8, TimeUnit.SECONDS);
                if (z != null) return z;
            }
        } catch (Exception e) {
            if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
        }
        return Zustand.laden(zustandsDatei());
    }

    public static List<ZustandDb.Eintrag> gemerkte() {
        if (lage != Lage.BEREIT) return new ArrayList<>();
        try {
            return EX.submit(() -> ZustandDb.gemerkte(verb())).get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            return new ArrayList<>();
        }
    }

    public static Zustand ladeGemerkt(long id) {
        if (lage != Lage.BEREIT) return null;
        try {
            return EX.submit(() -> ZustandDb.laden(verb(), id)).get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            return null;
        }
    }

    /** Merkt einen Zustand unter einem Namen (nur mit Datenbank); gibt true zurück, wenn er gespeichert ist. */
    public static boolean merken(Zustand z, String name) {
        if (lage != Lage.BEREIT) return false;
        z.art = "MANUELL";
        z.name = name;
        try {
            EX.submit(() -> { ZustandDb.speichern(verb(), z, sessionId); return null; }).get(8, TimeUnit.SECONDS);
            ereignis("STAND", "gemerkt", name);
            return true;
        } catch (Exception e) {
            if (e.getCause() instanceof SQLException) abbruch((SQLException) e.getCause());
            return false;
        }
    }

    // ------------------------------------------------------------ Sitzung und Protokoll

    public static void kontext(IntSupplier stufe, DoubleSupplier stunde) { ctxStufe = stufe; ctxStunde = stunde; }

    public static void sitzungStart(int fensterB, int fensterH, int stufe, boolean zustandGeladen) {
        startNs = System.nanoTime();
        EX.submit(() -> {
            String rechner = "";
            try { rechner = java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception e) { /* egal */ }
            final String r = rechner;
            if (lage != Lage.BEREIT) return;
            try (CallableStatement cs = verb().prepareCall("{? = call HEI_API.session_start(?,?,?,?,?,?,?,?,?,?)}")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setString(2, r); cs.setString(3, System.getProperty("user.name")); cs.setString(4, APP_VERSION); cs.setString(5, System.getProperty("java.version"));
                cs.setInt(6, Runtime.getRuntime().availableProcessors()); cs.setLong(7, Runtime.getRuntime().maxMemory() >> 20);
                cs.setInt(8, fensterB); cs.setInt(9, fensterH); cs.setInt(10, stufe); cs.setString(11, zustandGeladen ? "J" : "N");
                cs.execute();
                sessionId = cs.getLong(1);
                try (CallableStatement p = conn.prepareCall("{call HEI_API.prune_events(90)}")) { p.execute(); }
            } catch (SQLException e) {
                abbruch(e);
                lokal("FEHLER", "Sitzung", e.getMessage());
            }
        });
        ereignis("START", "Heidelberg " + APP_VERSION, zustandGeladen ? "Zustand geladen" : "neu");
    }

    /** Ein Ereignis ins Protokoll (Datenbank, sonst Datei); blockiert den Aufrufer nie. */
    public static void ereignis(String art, String ziel, String wert) {
        final double sek = (System.nanoTime() - startNs) / 1e9;
        final int stufe = ctxStufe.getAsInt();
        final double stunde = ctxStunde.getAsDouble();
        EX.submit(() -> {
            if (lage == Lage.BEREIT && sessionId > 0) {
                try (CallableStatement cs = verb().prepareCall("{call HEI_API.log_event(?,?,?,?,?,?,?)}")) {
                    cs.setLong(1, sessionId); cs.setDouble(2, Math.round(sek * 10) / 10.0); cs.setString(3, art); cs.setString(4, ziel); cs.setString(5, wert);
                    cs.setInt(6, stufe); cs.setDouble(7, Math.round(stunde * 100) / 100.0);
                    cs.execute();
                    if (art.equals("FEHLER") || art.equals("WARN")) lokal(art, ziel, wert);
                    return;
                } catch (SQLException e) {
                    abbruch(e);
                }
            }
            lokal(art, ziel, wert);
        });
    }

    private static void lokal(String art, String ziel, String wert) {
        try {
            File f = new File(lokalerOrdner(), "protokoll.log");
            Files.createDirectories(f.getParentFile().toPath());
            if (f.length() > 512 * 1024) Files.move(f.toPath(), new File(f.getPath() + ".alt").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String z = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\t" + art + "\t" + (ziel == null ? "" : ziel) + "\t" + (wert == null ? "" : wert).replace('\n', ' ') + "\n";
            Files.write(f.toPath(), z.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { /* das Protokoll darf das Programm nie stören */ }
    }

    /**
     * Beim Beenden: Zustand in die Datei und, wenn die Datenbank bereit ist, nach HEI_STATE (Art AUTO, die letzten 10 bleiben),
     * Sitzung schließen. z darf null sein (Szene nie fertig geworden): dann bleibt der letzte Zustand erhalten.
     */
    public static void beenden(Zustand z, double[] leistung) {
        if (!beendet.compareAndSet(false, true)) return;
        if (z != null) {
            z.art = "AUTO";
            z.name = "AUTO";
            z.gespeichertMs = System.currentTimeMillis();
            try { Zustand.speichern(zustandsDatei(), z); } catch (IOException e) { lokal("WARN", "Zustand", "Datei nicht geschrieben: " + e.getMessage()); }
        }
        final double fps = leistung == null ? 0 : leistung[0], ms = leistung == null ? 0 : leistung[1], mb = leistung == null ? 0 : leistung[2];
        final double sek = (System.nanoTime() - startNs) / 1e9;
        Future<?> f = EX.submit(() -> {
            if (lage != Lage.BEREIT) { lokal("ENDE", "ohne Datenbank", String.format("%.0f s", sek)); return; }
            try {
                Connection c = verb();
                if (z != null) {
                    ZustandDb.speichern(c, z, sessionId);
                    try (CallableStatement p = c.prepareCall("{call HEI_API.prune_states('AUTO', 10)}")) { p.execute(); }
                }
                if (sessionId > 0) {
                    try (CallableStatement cs = c.prepareCall("{call HEI_API.log_event(?,?,?,?,?,?,?)}")) {
                        cs.setLong(1, sessionId); cs.setDouble(2, Math.round(sek * 10) / 10.0); cs.setString(3, "ENDE"); cs.setString(4, "Heidelberg " + APP_VERSION);
                        cs.setString(5, z != null ? "Zustand gesichert" : "ohne Zustand"); cs.setInt(6, ctxStufe.getAsInt()); cs.setDouble(7, ctxStunde.getAsDouble());
                        cs.execute();
                    }
                    try (CallableStatement cs = c.prepareCall("{call HEI_API.session_end(?,?,?,?)}")) {
                        cs.setLong(1, sessionId); cs.setDouble(2, Math.round(fps * 10) / 10.0); cs.setDouble(3, Math.round(ms * 10) / 10.0); cs.setLong(4, Math.round(mb));
                        cs.execute();
                    }
                }
            } catch (SQLException e) {
                lokal("WARN", "Beenden", e.getMessage());
            }
        });
        try { f.get(5, TimeUnit.SECONDS); } catch (Exception e) { /* nach 5 s nicht länger warten */ }
    }
}
