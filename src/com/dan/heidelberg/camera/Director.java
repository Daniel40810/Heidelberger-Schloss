package com.dan.heidelberg.camera;

import com.dan.heidelberg.core.Solids;
import com.dan.heidelberg.core.Terrain;
import com.dan.heidelberg.effects.Weather;

import java.util.ArrayList;
import java.util.List;

/**
 * Regie: das Drehbuch „Ein Tag auf dem Jettenbühl“. Zwölf Szenen führen die Kamera vom Morgennebel bis in die Mondnacht;
 * zu jeder Szene gehören eine Kamerafahrt, ein Stück Tageszeit, Einstellungen für Nebel und Licht und Tafeln mit
 * Angaben und Quellen. Die Fahrten sind {@link Shot}s, die die Steuerung abspielt; die Regie schaltet nur Zeit, Wetter und
 * Tafeln. Greift der Benutzer in die Kamera ein, endet das Drehbuch und die Einstellungen kehren zurück.
 */
public final class Director {
    /** Der Tag des Drehbuchs: 12. Oktober (Nebel im Tal, früher Abend, Sonne zwischen 33° und Horizont). */
    public static final int DAY = 285;

    /** Was die Regie an der Szene schalten darf. */
    public interface Stage {
        CameraController camera();
        void setSunTime(int day, double hour);
        void setWeather(int mode);
        void setValleyFog(boolean forced, double scale);
        void setTorches(boolean on);
        void setHaze(double h);
        void setShower(boolean on);
        void setTimelapse(double hoursPerSecond);
        void setSnowCover(double c);
        void toast(String s, long ms);
        /** Zustand vor dem Drehbuch, damit er danach wiederkehrt. */
        int day();
        double hour();
        double haze();
        boolean fogForced();
        int weatherMode();
        boolean torches();
    }

    /** Eine Tafel: erscheint von from bis to (Sekunden der Szenenfahrt bei Tempo 1). */
    public static final class Tafel {
        public final double from, to;
        public final String kopf, text, quelle;
        Tafel(double from, double to, String kopf, String text, String quelle) { this.from = from; this.to = to; this.kopf = kopf; this.text = text; this.quelle = quelle; }
    }

    /** Eine Szene des Drehbuchs. */
    public static final class Scene {
        public final String name;
        public final Shot shot;
        public final double h0, h1, haze;
        public final boolean fog;
        public final Tafel[] tafeln;
        Scene(String name, Shot shot, double h0, double h1, boolean fog, double haze, Tafel... tafeln) {
            this.name = name; this.shot = shot; this.h0 = h0; this.h1 = h1; this.fog = fog; this.haze = haze; this.tafeln = tafeln;
        }
    }

    /** Was gerade auf dem Bildschirm steht. */
    public static final class Card {
        public final String kopf, text, quelle;
        public final float alpha;
        public Card(String kopf, String text, String quelle, float alpha) { this.kopf = kopf; this.text = text; this.quelle = quelle; this.alpha = alpha; }
    }

    private final Stage stage;
    private final List<Scene> scenes;
    private volatile boolean active, paused;
    private volatile int idx;
    private volatile boolean sceneDone;
    private int cancels;
    private double lastHour = -1;
    private volatile Card card;
    private volatile String status = "";
    // Zustand vor dem Drehbuch
    private int sDay, sWeather;
    private double sHour, sHaze;
    private boolean sFog, sTorches;

    public Director(Stage stage, List<Scene> scenes) {
        this.stage = stage;
        this.scenes = scenes;
    }

    public List<Scene> scenes() { return scenes; }
    public boolean active() { return active; }
    public boolean paused() { return paused; }
    public int sceneIndex() { return idx; }
    public Card card() { return active ? card : null; }
    public String status() { return active ? status : ""; }

    /** Fängt mit Szene i an (0 = Anfang). */
    public void start(int i) {
        if (scenes.isEmpty()) return;
        if (!active) {
            sDay = stage.day(); sHour = stage.hour(); sHaze = stage.haze(); sFog = stage.fogForced(); sWeather = stage.weatherMode(); sTorches = stage.torches();
        }
        active = true;
        paused = false;
        stage.setTimelapse(0);
        stage.setWeather(Weather.CLEAR);
        stage.setSnowCover(0);
        stage.setShower(false);
        stage.setTorches(true);
        begin(Math.max(0, Math.min(scenes.size() - 1, i)));
    }

    /** Beendet das Drehbuch; die Einstellungen von vorher kehren zurück, die Kamera bleibt stehen. */
    public void stop() { end(true); }

    private void end(boolean cancelCamera) {
        if (!active) return;
        active = false;
        paused = false;
        card = null;
        status = "";
        CameraController c = stage.camera();
        if (c != null) { c.setRideSpeed(1); if (cancelCamera) c.cancelRide(); }
        stage.setValleyFog(sFog, 1.0);
        stage.setHaze(sHaze);
        stage.setWeather(sWeather);
        stage.setTorches(sTorches);
        stage.setSunTime(sDay, sHour);
    }

    public void pause(boolean p) {
        if (!active) return;
        paused = p;
        CameraController c = stage.camera();
        if (c != null) c.setRideSpeed(p ? 0 : 1);
    }

    public void next() { if (active) begin(Math.min(scenes.size() - 1, idx + 1)); }

    public void previous() { if (active) begin(Math.max(0, idx - 1)); }

    private void begin(int i) {
        idx = i;
        Scene s = scenes.get(i);
        stage.setValleyFog(s.fog, 1.0);
        stage.setHaze(s.haze);
        stage.setSunTime(DAY, s.h0);
        lastHour = s.h0;
        sceneDone = false;
        card = null;
        status = "Drehbuch " + (i + 1) + " von " + scenes.size() + " · " + s.name;
        CameraController c = stage.camera();
        c.setRideSpeed(1);
        c.playRide(s.shot, 1.0, () -> sceneDone = true);
        cancels = c.rideCancels;
        stage.toast(s.name, 2600);
        paused = false;
    }

    /** Einmal je Bild aus der Bildschleife. */
    public void update(double dt) {
        if (!active) return;
        CameraController c = stage.camera();
        if (c.rideCancels != cancels) { end(false); stage.toast("Drehbuch beendet", 2500); return; }
        if (sceneDone) {
            if (idx + 1 < scenes.size()) begin(idx + 1);
            else { stop(); stage.toast("Ende des Drehbuchs", 4000); }
            return;
        }
        Scene s = scenes.get(idx);
        double st = Math.max(0, c.rideTime - c.rideLead), dur = Math.max(1e-6, s.shot.duration());
        double f = Math.min(1, st / dur);
        double h = s.h0 + (s.h1 - s.h0) * f;
        if (Math.abs(h - lastHour) >= 0.004) { lastHour = h; stage.setSunTime(DAY, h); }
        Card cd = null;
        for (Tafel t : s.tafeln) {
            if (st >= t.from && st <= t.to) {
                float a = (float) Math.max(0, Math.min(1, Math.min((st - t.from) / 0.8, (t.to - st) / 0.8)));
                cd = new Card(t.kopf, t.text, t.quelle, a);
                break;
            }
        }
        card = cd;
    }

    // ------------------------------------------------------------ Das Drehbuch

    private static Tafel tf(double from, double to, String kopf, String text, String quelle) { return new Tafel(from, to, kopf, text, quelle); }

    /** Werte einer Szene aus der Datenbank (HEI_SHOT, HEI_PANEL); tafeln null lässt die eingebauten Tafeln stehen. */
    public static final class Anpassung {
        public final double h0, h1, haze;
        public final boolean fog;
        public final Tafel[] tafeln;
        public Anpassung(double h0, double h1, boolean fog, double haze, Tafel[] tafeln) { this.h0 = h0; this.h1 = h1; this.fog = fog; this.haze = haze; this.tafeln = tafeln; }
    }

    /** Anpassungen je Szenenname; null = nur die eingebauten Werte. Vor dem Aufbau der Welt setzen. */
    public static volatile java.util.Map<String, Anpassung> DB = null;

    public static Tafel tafel(double from, double to, String kopf, String text, String quelle) { return new Tafel(from, to, kopf, text, quelle); }

    private static List<Scene> anpassen(List<Scene> l) {
        java.util.Map<String, Anpassung> m = DB;
        if (m == null || m.isEmpty()) return l;
        List<Scene> r = new ArrayList<>();
        for (Scene s : l) {
            Anpassung a = m.get(s.name);
            r.add(a == null ? s : new Scene(s.name, s.shot, a.h0, a.h1, a.fog, a.haze, a.tafeln != null ? a.tafeln : s.tafeln));
        }
        return r;
    }

    /** Die zwölf Szenen „Ein Tag auf dem Jettenbühl“. Die Fahrten stehen in {@link Rides}. */
    public static List<Scene> scenes(Terrain t, Solids so) {
        List<Scene> l = new ArrayList<>();
        l.add(new Scene("Vor Sonnenaufgang",
                Rides.arc(t, so, "Schloss über dem Nebelmeer", 235, 4.2, 540, 215, 5, 430, 50, "Vor Sonnenaufgang"),
                6.6, 7.9, true, 0.12,
                tf(2, 12, "Ein Tag auf dem Jettenbühl",
                        "Das Heidelberger Schloss um 1619, als Rekonstruktion. Der Tag ist ein Modell: Sonnenstand, Nebel und Wetter folgen dem Programm, nicht einer Überlieferung.",
                        "Rekonstruktion (Modell)"),
                tf(18, 29, "Lage",
                        "Das Schloss steht auf der Jettenbühl-Terrasse am Nordhang des Königstuhls, rund 80 m über der Altstadt.",
                        "Wikipedia: Heidelberger Schloss, Heidelberg Castle"),
                tf(35, 46, "Talnebel",
                        "Nebel baut sich nachts im Neckartal auf und wird von der Sonne aufgezehrt. Welche Tage Nebel haben, legt das Programm als Modell fest.",
                        "Modell der Rekonstruktion")));
        l.add(new Scene("Sonnenaufgang",
                Rides.arc(t, so, "Schloss über dem Nebelmeer", 215, 5, 430, 190, 9, 250, 45, "Sonnenaufgang"),
                7.9, 9.0, true, 0.12,
                tf(5, 16, "Roter Sandstein",
                        "Das Schloss besteht aus rotem Neckartäler Sandstein, dem Buntsandstein. Die Mauern leuchten, sobald die Sonne über den Berg kommt.",
                        "Wikipedia: Heidelberger Schloss"),
                tf(24, 38, "Der Nebel zieht ab",
                        "Je höher die Sonne steigt, desto tiefer sinkt die Nebelbank und desto größer werden die Lücken.",
                        "Modell der Rekonstruktion")));
        l.add(new Scene("Der Torturm",
                Rides.anflug(t, "Der Torturm"),
                9.0, 10.0, true, 0.12,
                tf(10, 21, "Torturm",
                        "Ludwig V. ließ ihn von 1531 bis 1541 bauen; er ist 52 m hoch und führt in das Schloss. Die Barockhaube stammt von etwa 1716, die Dachform von 1619 ist hier als Pyramide angenommen.",
                        "Wikipedia: Heidelberger Schloss"),
                tf(25, 36, "Der Hof",
                        "Um den Hof stehen Bauten aus vier Jahrhunderten: der Ruprechtsbau um 1400 (Wikipedia nennt ca. 1430), der Ottheinrichsbau ab 1556, der Friedrichsbau 1601 bis 1607.",
                        "Staatliche Schlösser und Gärten; Wikipedia")));
        l.add(new Scene("Zu Fuß durch Tor und Hof",
                Rides.rundgang(t, "Zu Fuß durch Tor und Hof"),
                10.0, 11.2, true, 0.12,
                tf(5, 13, "Wappen am Torturm",
                        "Pfälzer Löwe, bayerische Rauten, Reichsapfel und Kurhut. Form, Ort und Größe der Wappen sind hier angenommen und stilisiert.",
                        "Annahme der Rekonstruktion"),
                tf(23, 35, "Friedrichsbau",
                        "Gebaut 1601 bis 1607 unter Friedrich IV., Architekt Johannes Schoch. Er ist der einzige Bau, der 1897 bis 1900 wiederhergestellt wurde.",
                        "Wikipedia: Heidelberger Schloss"),
                tf(37, 44, "Ottheinrichsbau",
                        "Das Hauptwerk der Renaissance am Schloss, ab 1556. Der Bildhauer war A. Colin; der Architekt ist unbekannt.",
                        "Wikipedia: Heidelberger Schloss"),
                tf(45, 49, "Hinein",
                        "Der Weg führt in den Gläsernen Saalbau.",
                        "Rundgang der Rekonstruktion")));
        l.add(new Scene("Mittag im Saal",
                Rides.look(8, 1.7, -27, 270, 95, -4, 6, 62, 28, "Mittag im Saal"),
                11.2, 12.5, false, 0.12,
                tf(2, 12, "Gläserner Saalbau",
                        "Ein Saal mit venezianischem Glas, unter Friedrich II. im 16. Jahrhundert. Die Säulen sind hier so gestellt, dass die Tür frei bleibt.",
                        "Wikipedia: Heidelberger Schloss; Annahme der Rekonstruktion"),
                tf(15, 27, "Lichtschacht",
                        "Durch die hohen Fenster fallen Sonnenstrahlen auf den Marmorboden. Staubluft im Saal macht sie sichtbar; das Licht ist gerechnet, nicht gemalt.",
                        "Technik der Rekonstruktion")));
        l.add(new Scene("Das Große Fass",
                Rides.fassbau(t, "Das Große Fass"),
                12.5, 13.3, false, 0.12,
                tf(2, 13, "Das Große Fass von 1591",
                        "Pfalzgraf Johann Casimir ließ es 1589 bis 1591 von Baumeister Michael Werner aus Landau bauen; es fasste rund 127.000 Liter (zum.de nennt 128.000). Im Dreißigjährigen Krieg wurde es zerstört. Maße sind nicht überliefert: Länge und Bauch sind hier angenommen.",
                        "Wikipedia: Großes Fass des Heidelberger Schlosses; zum.de; Annahme der Rekonstruktion"),
                tf(15, 28, "Küfer und Kerzen",
                        "Küfer, Leiter, Kerzen und das Wappen am Fassboden sind Annahmen der Rekonstruktion. Ein Tanzboden auf dem Fass ist erst vom Fass von 1664 überliefert und hier nicht gebaut.",
                        "Wikipedia: Großes Fass des Heidelberger Schlosses; Annahme der Rekonstruktion")));
        l.add(new Scene("Der Hortus Palatinus",
                Rides.hortus(t, "Der Hortus Palatinus"),
                13.3, 14.2, false, 0.12,
                tf(3, 13, "Hortus Palatinus",
                        "Salomon de Caus legte ihn ab 1614 oder 1616 an (die Quellen sagen es verschieden), rund 5 ha groß, L-förmig um das Schloss.",
                        "Wikipedia: Hortus Palatinus (deutsch, englisch); Staatliche Schlösser und Gärten"),
                tf(14, 24, "Wasserkünste",
                        "Lage, Zahl und Höhe der Strahlen sind geschätzt; de Caus nennt keine Höhen. Die Fontänen laufen hier als Tropfen auf Wurfbahnen.",
                        "Wikipedia: Hortus Palatinus; Annahme der Rekonstruktion")));
        l.add(new Scene("Das Achteckbecken",
                Rides.arc(t, so, "Achteckbecken", 306, -6, 10, 350, -3, 8, 16, "Das Achteckbecken"),
                14.2, 15.0, false, 0.12,
                tf(2, 14, "Achteckbecken",
                        "Ein achteckiges Bassin mit Fratzengesichtern aus Metall. Der Garten wurde nie fertig; das Stichwerk von 1620 zeigt den Plan.",
                        "Wikipedia: Hortus Palatinus (deutsch, englisch)")));
        l.add(new Scene("Das Schloss von oben",
                Rides.orbit(t, "Das Schloss von oben"),
                15.0, 17.2, false, 0.14,
                tf(4, 16, "Bauzeiten",
                        "1303 erstmals als Sitz fassbar · um 1400 Ruprechtsbau · 1531 bis 1541 Torturm · ab 1556 Ottheinrichsbau · 1601 bis 1607 Friedrichsbau.",
                        "Staatliche Schlösser und Gärten: Zeitreise; Wikipedia"),
                tf(22, 34, "Englischer Bau",
                        "Friedrich V. ließ ihn 1612/13 bauen, benannt nach seiner Frau Elisabeth Stuart. Als Architekt gilt vermutlich Inigo Jones.",
                        "Wikipedia: Heidelberger Schloss"),
                tf(42, 56, "Herbst 1619",
                        "Im Herbst 1619 wird Friedrich V. zum König von Böhmen gewählt. Die Arbeit am Garten ruht danach.",
                        "Wikipedia: Hortus Palatinus; Staatliche Schlösser und Gärten")));
        l.add(new Scene("Abendrot",
                Rides.arc(t, so, "Abendrot über der Rheinebene", 62, -3.6, 40, 100, -2, 30, 32, "Abendrot"),
                17.2, 18.7, false, 0.30,
                tf(4, 16, "Abend",
                        "Mit der Sonnenhöhe färbt sich das Licht; im Westen liegt die Rheinebene. Sobald die Sonne unter dem Horizont steht, gehen Fenster und Fackeln an.",
                        "Modell der Rekonstruktion"),
                tf(18, 30, "Nie fertig",
                        "Die Arbeit am Garten ruhte ab Ende 1619; im Dreißigjährigen Krieg wurde er zerstört.",
                        "Wikipedia: Hortus Palatinus")));
        l.add(new Scene("Kerzenlicht im Saal",
                Rides.look(8, 1.7, -25.2, 235, 125, -3, 3, 62, 26, "Kerzenlicht im Saal"),
                18.7, 19.7, false, 0.12,
                tf(2, 13, "Gläserner Saalbau am Abend",
                        "Friedrich II. ließ den Bau 1544 bis 1556 errichten; die Quellen nennen einen Saal im Obergeschoss mit venezianischem Spiegelglas. Hier steht der Saal ebenerdig; Kronleuchter, Kerzen, Spiegel und Möbel sind Annahmen.",
                        "Staatliche Schlösser und Gärten; zum.de; Annahme der Rekonstruktion"),
                tf(14, 25, "Hofstaat",
                        "Die Menschen sind Schattenrisse; Halskrause, Wams, Reifrock und Hellebarde folgen der Mode um 1619 in stark vereinfachter Form. Wer wann im Hof ist, legt das Programm fest.",
                        "Annahme der Rekonstruktion")));
        l.add(new Scene("Fackelnacht und Mond",
                Rides.arc(t, so, "Schlosshof bei Nacht", 280, -1.3, 56, 252, 1, 50, 36, "Fackelnacht und Mond"),
                19.7, 20.6, false, 0.12,
                tf(3, 16, "Was folgt",
                        "Brandlegung 1688 (Staatliche Schlösser) oder am 2. März 1689 (Wikipedia). 1693 sprengen französische Pioniere Türme. Am 24. Juni 1764 schlägt der Blitz ein; der Wiederaufbau wird aufgegeben.",
                        "Wikipedia: Heidelberger Schloss; Staatliche Schlösser und Gärten"),
                tf(19, 32, "Ende",
                        "Ein Tag auf dem Jettenbühl. Die Angaben sind nachgeschlagen; wo Quellen sich widersprechen, steht beides.",
                        "Wikipedia; Staatliche Schlösser und Gärten Baden-Württemberg")));
        return anpassen(l);
    }
}
