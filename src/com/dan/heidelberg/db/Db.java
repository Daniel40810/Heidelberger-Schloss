package com.dan.heidelberg.db;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Zugang zur Oracle-Datenbank (Schema DEMO, Dienst PDBORCL). Die Zugangsdaten stehen in {@code db/db.properties}
 * (Schlüssel url, user, password); die Datei gehört nicht ins Git. Ist password leer, wird die Umgebungsvariable
 * HEIDELBERG_DB_PASSWORD gelesen, sonst fragt das Startbild; das Passwort bleibt nur im Speicher.
 */
public final class Db {
    private Db() { }

    public static final String STANDARD_URL = "jdbc:oracle:thin:@//localhost:1521/PDBORCL";

    public static final class Konfig {
        public final String url, user;
        public final File datei;
        private final char[] passwort;
        Konfig(String url, String user, char[] passwort, File datei) { this.url = url; this.user = user; this.passwort = passwort; this.datei = datei; }
        public boolean hatPasswort() { return passwort != null && passwort.length > 0; }
        public Konfig mitPasswort(char[] pw) { return new Konfig(url, user, pw, datei); }
        char[] passwort() { return passwort; }
        /** Kurzform für Anzeigen, ohne Passwort. */
        public String kurz() { return user + "@" + url.replaceFirst("^jdbc:oracle:thin:@/{0,2}", ""); }
    }

    /** Der Ordner db: -Dheidelberg.db.dir, sonst ./db, sonst db neben dem Jar oder eine Ebene darüber. */
    public static File ordner() {
        String p = System.getProperty("heidelberg.db.dir");
        if (p != null && !p.isEmpty()) return new File(p);
        File d = new File("db");
        if (d.isDirectory()) return d;
        try {
            File jar = new File(Db.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File base = jar.isFile() ? jar.getParentFile() : jar;
            for (int i = 0; i < 3 && base != null; i++, base = base.getParentFile()) {
                File c = new File(base, "db");
                if (c.isDirectory()) return c;
            }
        } catch (Exception e) { /* weiter */ }
        return d;
    }

    /** Liest db/db.properties; wirft FileNotFoundException mit einer lesbaren Meldung, wenn es die Datei nicht gibt. */
    public static Konfig konfig() throws IOException {
        File f = new File(ordner(), "db.properties");
        if (!f.isFile()) throw new FileNotFoundException("db/db.properties fehlt (Vorlage: db/db.properties.example)");
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f.toPath())) { p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)); }
        String url = p.getProperty("url", STANDARD_URL).trim();
        String user = p.getProperty("user", "DEMO").trim();
        String pw = p.getProperty("password", "").trim();
        if (pw.isEmpty()) {
            String env = System.getenv("HEIDELBERG_DB_PASSWORD");
            if (env != null) pw = env;
        }
        return new Konfig(url, user, pw.toCharArray(), f);
    }

    /** Öffnet eine Verbindung mit kurzen Zeitgrenzen (5 s Verbindungsaufbau, 30 s je Antwort). */
    public static Connection oeffnen(Konfig k) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", k.user);
        p.setProperty("password", new String(k.passwort()));
        p.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        p.setProperty("oracle.jdbc.ReadTimeout", "30000");
        DriverManager.setLoginTimeout(6);
        Connection c = DriverManager.getConnection(k.url, p);
        c.setAutoCommit(true);
        return c;
    }
}
