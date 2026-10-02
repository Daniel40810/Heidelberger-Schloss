package com.dan.heidelberg.db;

import com.dan.heidelberg.camera.Director;
import com.dan.heidelberg.camera.Viewpoint;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Liest Katalog, Blickpunkte und Drehbuch aus den HEI_-Tabellen; Blickpunkte und Szenen ersetzen die eingebauten Werte. */
public final class KatalogDb {
    private KatalogDb() { }

    /** Code des Drehbuchs in HEI_CLIP. */
    public static final String DREHBUCH = "DREHBUCH_JETTENBUEHL";

    private static java.time.LocalDate tag(Date d) { return d == null ? null : d.toLocalDate(); }

    private static Double zahl(ResultSet rs, int i) throws SQLException { double v = rs.getDouble(i); return rs.wasNull() ? null : v; }

    public static Katalog.Daten laden(Connection c) throws SQLException {
        Katalog.Daten d = new Katalog.Daten();
        try (Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT s.NR, s.JAHR, s.DATUM, s.NAME, s.KURZ, s.BESCHREIBUNG, q.CODE FROM HEI_STAGE s LEFT JOIN HEI_SOURCE q ON q.SOURCE_ID = s.SOURCE_ID ORDER BY s.NR")) {
                while (rs.next()) d.stufen.add(new Katalog.Stufe(rs.getInt(1), rs.getInt(2), tag(rs.getDate(3)), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
            try (ResultSet rs = st.executeQuery("SELECT CODE, AUTOR, LEBENSZEIT, WERK, STELLE, SPRACHE, UEBERLIEFERUNG, ABSTAND_JAHRE, BEZUG, KERNAUSSAGE, MASSE, BELEG FROM HEI_SOURCE ORDER BY SOURCE_ID")) {
                while (rs.next()) {
                    int a = rs.getInt(8);
                    Integer abstand = rs.wasNull() ? null : a;
                    d.quellen.add(new Katalog.Quelle(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), abstand,
                            rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12)));
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT b.CODE, b.NAME, b.NAME_HISTORISCH, b.BEZUG, b.DETAIL, b.STUFEN, b.ROLLE, q.CODE, b.X_M, b.Z_M FROM HEI_BUILDING b LEFT JOIN HEI_SOURCE q ON q.SOURCE_ID = b.SOURCE_ID ORDER BY b.BUILDING_ID")) {
                while (rs.next()) d.bauten.add(new Katalog.Bau(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getString(7), rs.getString(8), null, zahl(rs, 9), zahl(rs, 10)));
            }
            try (ResultSet rs = st.executeQuery("SELECT s.NR, c.JAHR, c.DATUM, c.TITEL, c.TEXT, q.CODE FROM HEI_CHRONIK c JOIN HEI_STAGE s ON s.STAGE_ID = c.STAGE_ID LEFT JOIN HEI_SOURCE q ON q.SOURCE_ID = c.SOURCE_ID ORDER BY c.JAHR, c.CHRONIK_ID")) {
                while (rs.next()) d.chronik.add(new Katalog.Ereignis(rs.getInt(1), rs.getInt(2), tag(rs.getDate(3)), rs.getString(4), rs.getString(5), rs.getString(6)));
            }
        }
        return d;
    }

    /**
     * Setzt Blickpunkte und Szenen aus der Datenbank, soweit sie vollständig und stimmig sind (gleiche Zahl und Namen wie die
     * eingebauten, Zahlen in Grenzen); sonst bleiben die eingebauten. Gibt eine Zeile für das Protokoll zurück.
     */
    public static String anpassungen(Connection c) {
        StringBuilder r = new StringBuilder();
        try {
            List<Viewpoint> v = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT NAME, DREHPUNKT_X, DREHPUNKT_Z, HOEHE_UEBER_BODEN, GIER_GRAD, NICK_GRAD, ABSTAND_M, INNEN FROM HEI_VIEW ORDER BY NR")) {
                while (rs.next()) {
                    double x = rs.getDouble(2), z = rs.getDouble(3), up = rs.getDouble(4), yaw = rs.getDouble(5), pitch = rs.getDouble(6), dist = rs.getDouble(7);
                    if (!(Math.abs(x) < 1e5 && Math.abs(z) < 1e5 && Math.abs(up) < 1e4 && Math.abs(pitch) <= 90 && dist >= 1 && dist < 1e5)) { v.clear(); r.append("Blickpunkte: unplausible Werte in ").append(rs.getString(1)).append("; eingebaute bleiben. "); break; }
                    v.add(Viewpoint.of(rs.getString(1), x, z, up, yaw, pitch, dist, "J".equals(rs.getString(8))));
                }
            }
            if (!v.isEmpty()) {
                if (Viewpoint.ersetze(v.toArray(new Viewpoint[0]))) r.append(v.size()).append(" Blickpunkte aus der Datenbank. ");
                else r.append("Blickpunkte: Zahl oder Namen weichen von den eingebauten ab (").append(v.size()).append(" statt ").append(Viewpoint.BUILTIN.length).append("); eingebaute bleiben. ");
            } else if (r.length() == 0) r.append("Blickpunkte: keine Zeilen; eingebaute bleiben. ");

            Map<String, Director.Anpassung> m = new LinkedHashMap<>();
            Map<Long, List<Director.Tafel>> tafeln = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT p.SHOT_ID, p.VON_S, p.BIS_S, p.KOPF, p.TEXT, p.QUELLE_TEXT FROM HEI_PANEL p JOIN HEI_SHOT s ON s.SHOT_ID = p.SHOT_ID JOIN HEI_CLIP c ON c.CLIP_ID = s.CLIP_ID WHERE c.CODE = ? ORDER BY p.SHOT_ID, p.VON_S")) {
                ps.setString(1, DREHBUCH);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double von = rs.getDouble(2), bis = rs.getDouble(3);
                        if (!(von >= 0 && bis > von && bis < 3600)) continue;
                        String q = rs.getString(6);
                        tafeln.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(Director.tafel(von, bis, rs.getString(4), rs.getString(5), q == null ? "" : q));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT s.SHOT_ID, s.NAME, s.STUNDE_VON, s.STUNDE_BIS, s.NEBEL, s.DUNST FROM HEI_SHOT s JOIN HEI_CLIP c ON c.CLIP_ID = s.CLIP_ID WHERE c.CODE = ? ORDER BY s.NR")) {
                ps.setString(1, DREHBUCH);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double h0 = rs.getDouble(3), h1 = rs.getDouble(4), haze = rs.getDouble(6);
                        if (!(h0 >= 0 && h0 <= 24 && h1 >= 0 && h1 <= 24 && haze >= 0 && haze <= 1)) { r.append("Szene ").append(rs.getString(2)).append(": unplausible Werte; eingebaute bleiben. "); continue; }
                        List<Director.Tafel> t = tafeln.get(rs.getLong(1));
                        m.put(rs.getString(2), new Director.Anpassung(h0, h1, "J".equals(rs.getString(5)), haze, t == null || t.isEmpty() ? null : t.toArray(new Director.Tafel[0])));
                    }
                }
            }
            if (!m.isEmpty()) { Director.DB = m; r.append(m.size()).append(" Szenen des Drehbuchs aus der Datenbank."); }
            else r.append("Drehbuch: keine Zeilen; eingebaute Szenen bleiben.");
        } catch (SQLException e) {
            r.append("Anpassungen nicht gelesen: ").append(String.valueOf(e.getMessage()).split("\n")[0]);
        }
        return r.toString().trim();
    }
}
