package com.dan.heidelberg.ui;

import com.dan.faccordion.DefaultFAccordionModel;
import com.dan.faccordion.FAccordion;
import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;
import com.dan.heidelberg.camera.Viewpoint;
import com.dan.heidelberg.core.Engine3D;
import com.dan.heidelberg.effects.DayNightCycle;
import com.dan.heidelberg.effects.Weather;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Bedienfeld rechts als Ziehharmonika (FAccordion): Ansicht, Sonne und Mond, Luft und Wetter, Bild.
 * Jede Phase hängt ihre Abschnitte an (Schloss, Wasser, Regie, Zugaben, Datenbank).
 */
public final class ControlPanel extends JPanel {
    static final Color BG = new Color(14, 20, 24), INK = new Color(230, 228, 222), MUTED = new Color(146, 154, 158),
            ACCENT = new Color(232, 188, 98);
    static final int W = 262;

    private final ScenePanel scene;
    private boolean fromStage, fromCut;
    private final JLabel timeLbl = new JLabel(), dayLbl = new JLabel(), hazeLbl = new JLabel();
    private final FSlider time = new FSlider(0, 239, (int) Math.round(10 * Double.parseDouble(System.getProperty("heidelberg.hour", "16")))), day = new FSlider(1, 365, Integer.getInteger("heidelberg.day", DayNightCycle.today())), haze = new FSlider(0, 100, 12);
    private boolean fromScene, fromWeather, fromView;
    private FComboBox lapse;
    private FCheckBox fogForce, shower;
    private boolean rideNamesReady, fromRide;
    private FSlider fogSlider;
    private JPanel cur;
    /** Alle Bedienelemente, die zum Zustand gehören, unter dem Namen ihrer Spalte in HEI_STATE. */
    private final java.util.Map<String, javax.swing.JComponent> reg = new java.util.LinkedHashMap<>();
    private FComboBox vpBox;
    private JLabel dbLbl, dbNote;
    private FComboBox gemerktBox;

    private <T extends javax.swing.JComponent> T reg(String spalte, T c) { reg.put(spalte, c); return c; }

    public ControlPanel(ScenePanel scene) {
        this.scene = scene;
        setBackground(BG);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        DefaultFAccordionModel m = new DefaultFAccordionModel();

        // ---- Ansicht
        begin();
        FComboBox vp = reg("BLICKPUNKT", new FComboBox(Viewpoint.NAMES));
        vpBox = vp;
        vp.addActionListener(e -> { if (!fromView) scene.goViewpoint(vp.getSelectedIndex()); scene.requestFocusInWindow(); });
        scene.setViewListener(i -> { fromView = true; vp.setSelectedIndex(i); fromView = false; });
        put(vp);
        FButton home = new FButton("Übersicht");
        home.addActionListener(e -> { scene.goViewpoint(0); scene.requestFocusInWindow(); });
        put(home);
        FCheckBox orbit = reg("RUNDFLUG", new FCheckBox("Rundflug (Orbit)"));
        orbit.addActionListener(e -> { scene.setOrbit(orbit.isSelected()); scene.requestFocusInWindow(); });
        scene.setOrbitListener(orbit::setSelected);
        put(orbit);
        note("Blickpunkte 1 bis 9, G schaltet weiter. Ziehen dreht, rechts ziehen verschiebt, das Rad zoomt von der Totale bis an die Mauer. Doppelklick macht eine Stelle zum Drehpunkt. Lage von Schloss, Brücke und Kirche ist aus Karten gelesen, auf einige zehn Meter genau.");
        m.addSection("Ansicht", end());

        // ---- Kamera und Regie (Phase 7)
        begin();
        FComboBox rideBox = new FComboBox(new Object[]{"Fahrten werden vorbereitet …"});
        put(rideBox);
        FButton rideGo = new FButton("Fahrt starten");
        rideGo.addActionListener(e -> {
            int i = rideBox.getSelectedIndex();
            if (rideNamesReady && i >= 0) scene.playRide(i);
            scene.requestFocusInWindow();
        });
        put(rideGo);
        FComboBox speedBox = new FComboBox(new Object[]{"Tempo · 0,5 ×", "Tempo · 1 ×", "Tempo · 1,5 ×", "Tempo · 2 ×"});
        speedBox.setSelectedIndex(1);
        speedBox.addActionListener(e -> { scene.setRideSpeed(new double[]{0.5, 1, 1.5, 2}[speedBox.getSelectedIndex()]); scene.requestFocusInWindow(); });
        put(speedBox);
        FButton rideStop = new FButton("Anhalten");
        rideStop.addActionListener(e -> { scene.stopRide(); scene.requestFocusInWindow(); });
        put(rideStop);
        scene.setRideListener(i -> { fromRide = true; rideBox.setSelectedIndex(i); fromRide = false; });
        note("Rundumblick, Anflug, Orbit um das Schloss, Überflug des Hortus, Rundgang durch Tor und Hof, Vom Hof in den Fassbau und Zoom bis zu den Wappen. Jede Fahrt fliegt zuerst von der jetzigen Stelle an ihren Anfang, um Mauern, Gelände und Bäume herum. Maus, Rad oder Tasten übernehmen die Kamera sofort; Taste V schaltet zur nächsten Fahrt.");
        FComboBox sceneBox = new FComboBox(new Object[]{"Drehbuch wird vorbereitet …"});
        put(sceneBox);
        FButton scriptGo = new FButton("Drehbuch „Ein Tag auf dem Jettenbühl“ starten");
        scriptGo.addActionListener(e -> {
            if (rideNamesReady) scene.startScript(Math.max(0, sceneBox.getSelectedIndex()));
            scene.requestFocusInWindow();
        });
        put(scriptGo);
        FButton scriptPause = new FButton("Pause, Weiter");
        scriptPause.addActionListener(e -> { scene.pauseScript(); scene.requestFocusInWindow(); });
        put(scriptPause);
        FButton scriptNext = new FButton("Nächste Szene");
        scriptNext.addActionListener(e -> { scene.nextScene(); scene.requestFocusInWindow(); });
        put(scriptNext);
        FCheckBox tafeln = new FCheckBox("Tafeln mit Quellen zeigen");
        tafeln.setSelected(true);
        tafeln.addActionListener(e -> { scene.setTafeln(tafeln.isSelected()); scene.requestFocusInWindow(); });
        put(tafeln);
        note("Das Drehbuch hat zwölf Szenen und dauert gut acht Minuten: Morgennebel, Sonnenaufgang, Torturm, Rundgang durch Tor und Hof, Mittag im Saal, Das Große Fass, Hortus, Wasserbecken, Schloss von oben, Abendrot, Kerzenlicht im Saal, Fackelnacht. Zeit, Nebel und Licht schaltet die Regie; zu jeder Szene gehören Tafeln mit Angaben und Quellen. Die Angaben stammen aus dem Werkbuch, wo Quellen sich widersprechen, steht beides. Der Tag selbst ist ein Modell. Am Ende kehren Uhrzeit, Nebel und Wetter zurück. Tasten: B an und aus, Leertaste Pause, N nächste Szene, Esc beendet.");
        scene.setRidesListener(n -> {
            rideBox.removeAllItems();
            for (String r : n[0]) rideBox.addItem(r);
            sceneBox.removeAllItems();
            for (String r : n[1]) sceneBox.addItem(r);
            rideNamesReady = true;
        });
        m.addSection("Kamera und Regie", end());

        // ---- Sonne und Mond
        begin();
        reg("TAG", day); reg("STUNDE", time); reg("DUNST", haze);
        put(dayLbl);
        day.addChangeListener(e -> sunChanged());
        put(day);
        put(timeLbl);
        time.addChangeListener(e -> sunChanged());
        put(time);
        lapse = reg("ZEITRAFFER", new FComboBox(new Object[]{"Zeitraffer aus", "Zeitraffer · 1 Std. in 10 s", "Zeitraffer · 1 Std. in 2 s", "Zeitraffer · 1 Tag in 30 s"}));
        lapse.addActionListener(e -> {
            double[] v = {0, 0.1, 0.5, 0.8};
            scene.setTimelapse(v[lapse.getSelectedIndex()]);
            scene.requestFocusInWindow();
        });
        put(lapse);
        note("Uhrzeit wie in Heidelberg: MEZ, von Ende März bis Ende Oktober MESZ. Sonne und Mond stehen für 2026 am richtigen Ort (49,41° N, 8,72° O); am 21. Juni mittags steht die Sonne 64° hoch, am 21. Dezember 17°. + und − verschieben um eine halbe Stunde.");
        scene.setTimeListener(v -> {
            fromScene = true;
            day.setValue((int) v[0]);
            time.setValue((int) Math.floor(v[1] * 10));
            labels((int) v[0], v[1]);
            fromScene = false;
        });
        m.addSection("Sonne und Mond", end());

        // ---- Luft und Wetter
        begin();
        put(hazeLbl);
        haze.addChangeListener(e -> {
            hazeLbl.setText("Dunst  " + haze.getValue() + " %");
            scene.setHaze(haze.getValue() / 100.0);
        });
        put(haze);
        hazeLbl.setText("Dunst  " + haze.getValue() + " %");
        FCheckBox fxRays = new FCheckBox("Lichtstrahlen und Bodennebel"), fxBloom = new FCheckBox("Überstrahlen");
        reg("FX_STRAHLEN", fxRays); reg("FX_UEBERSTRAHLEN", fxBloom);
        for (FCheckBox cb : new FCheckBox[]{fxRays, fxBloom}) {
            cb.setSelected(true);
            cb.addActionListener(e -> { scene.setEffects(fxRays.isSelected(), fxBloom.isSelected()); scene.requestFocusInWindow(); });
            put(cb);
        }
        JLabel fogLbl = lbl("Bodennebel  100 %");
        put(fogLbl);
        FSlider fog = reg("NEBEL", new FSlider(0, 200, 100));
        fog.addChangeListener(e -> { fogLbl.setText("Bodennebel  " + fog.getValue() + " %"); scene.setFog(fog.getValue() / 100.0); });
        put(fog);
        JLabel windLbl = lbl("Wind  35 %");
        put(windLbl);
        FSlider wind = reg("WIND", new FSlider(0, 100, 35));
        wind.addChangeListener(e -> { windLbl.setText("Wind  " + wind.getValue() + " %"); scene.setWind(wind.getValue() / 100.0); });
        put(wind);
        Object[] wm = new Object[Weather.MODES.length];
        for (int i = 0; i < wm.length; i++) wm[i] = "Wetter · " + Weather.MODES[i];
        FComboBox weather = reg("WETTER", new FComboBox(wm));
        weather.addActionListener(e -> { if (!fromWeather) scene.setWeather(weather.getSelectedIndex()); scene.requestFocusInWindow(); });
        scene.setWeatherListener(v -> { fromWeather = true; weather.setSelectedIndex(v); fromWeather = false; });
        fromWeather = true;
        weather.setSelectedIndex(Weather.CLEAR);
        fromWeather = false;
        put(weather);
        note("Nach Jahreszeit: im Sommer Gewitter am Nachmittag, im Winter Schnee, im Frühjahr und Herbst Regen, dazu Frühnebel im Neckartal. Die Lufttemperatur folgt einem Jahresgang für Heidelberg (Mittel etwa 11 °C). Welcher Tag welches Wetter hat, ist ein Modell. Taste Y wechselt.");
        m.addSection("Luft und Wetter", end());

        // ---- Bild
        begin();
        FComboBox q = reg("QUALITAET", new FComboBox(new Object[]{"Qualität · Auto", "Schnell · 50 %", "Mittel · 75 %", "Hoch · 100 %"}));
        q.addActionListener(e -> {
            double[] s = {0, 0.5, 0.75, 1.0};
            int i = q.getSelectedIndex();
            if (i == 0) scene.setAutoQuality(); else scene.setScale(s[i]);
            scene.requestFocusInWindow();
        });
        put(q);
        Object[] sty = new Object[Engine3D.STYLES.length];
        for (int i = 0; i < sty.length; i++) sty[i] = "Farbstil · " + Engine3D.STYLES[i];
        FComboBox style = reg("STIL", new FComboBox(sty));
        style.addActionListener(e -> { scene.setStyle(style.getSelectedIndex()); scene.requestFocusInWindow(); });
        put(style);
        FButton still = new FButton("Standbild speichern");
        still.addActionListener(e -> { scene.requestStill(); scene.requestFocusInWindow(); });
        put(still);
        FButton cine = new FButton("Kinomodus");
        cine.addActionListener(e -> { scene.setCinema(true); scene.requestFocusInWindow(); });
        put(cine);
        note("Auto hält 30 Bilder/s: in Bewegung mit kleinerem Bild, im Stillstand mit voller Auflösung. Standbilder landen doppelt so groß unter Bilder/Heidelberg. Der Kinomodus rechnet nur das Breitbild-Band und lässt das Bedienfeld verschwinden (K, Esc beendet).");
        m.addSection("Bild", end());

        // ---- Schloss und Hortus
        begin();
        FCheckBox planned = reg("Z_GEPLANT", new FCheckBox("Geplantes blau tönen"));
        planned.setSelected(com.dan.heidelberg.core.Materials.tintPlanned);
        planned.addActionListener(e -> { com.dan.heidelberg.core.Materials.tintPlanned = planned.isSelected(); scene.requestFocusInWindow(); });
        put(planned);
        note("Zeigt, was de Caus für den Hortus nur plante und nie fertigstellte (Pyramidentreppe, Achteckbecken, Rhenus-Becken), bläulich. Taste G und das Feld oben springen auch in den Hof, in die Große Grotte und in den Gläsernen Saalbau, wo Sonnenstrahlen durch die Fenster fallen. Lage und Maße der Bauten zueinander sind aus Plänen geschätzt.");
        m.addSection("Schloss und Hortus", end());

        // ---- Wasser
        begin();
        FCheckBox fountainsOn = reg("BRUNNEN", new FCheckBox("Brunnen laufen lassen"));
        fountainsOn.setSelected(true);
        fountainsOn.addActionListener(e -> { scene.setFountains(fountainsOn.isSelected()); scene.requestFocusInWindow(); });
        put(fountainsOn);
        JLabel densLbl = lbl("Tropfendichte  100 %");
        put(densLbl);
        FSlider dens = reg("BRUNNEN_DICHTE", new FSlider(10, 800, 100));
        dens.addChangeListener(e -> { densLbl.setText("Tropfendichte  " + dens.getValue() + " %"); scene.setFountainDensity(dens.getValue() / 100.0); });
        put(dens);
        note("Jede Fontäne ist eine Schar Tropfen auf Wurfbahnen (Schwerkraft, Luftwiderstand, Wind). Die Höhe jedes Strahls ist vorgegeben und stimmt ohne Wind auf wenige Zentimeter. Beim Aufprall entstehen Spritzer, Ringwellen und nasse Ränder, die in der Sonne trocknen. Brunnen sprudeln nur im Umkreis von 330 m um die Kamera. Dichte 800 % ist der Lasttest mit über 20 000 Tropfen. Blickpunkte: Achteckbecken, Säulenbrunnen, Rhenusbecken, Kaskade der Großen Grotte.");
        m.addSection("Wasser und Brunnen", end());

        // ---- Dampf und Licht
        begin();
        FComboBox mood = new FComboBox(MOODS);
        mood.addActionListener(e -> { if (mood.getSelectedIndex() > 0) applyMood(mood.getSelectedIndex()); scene.requestFocusInWindow(); });
        put(mood);
        fogForce = reg("NEBELTAG", new FCheckBox("Nebeltag erzwingen"));
        fogForce.addActionListener(e -> { scene.setValleyFog(fogForce.isSelected(), fogSlider.getValue() / 100.0); scene.requestFocusInWindow(); });
        put(fogForce);
        JLabel vfLbl = lbl("Talnebel  100 %");
        put(vfLbl);
        fogSlider = reg("TALNEBEL", new FSlider(0, 200, 100));
        fogSlider.addChangeListener(e -> { vfLbl.setText("Talnebel  " + fogSlider.getValue() + " %"); scene.setValleyFog(fogForce.isSelected(), fogSlider.getValue() / 100.0); });
        put(fogSlider);
        FCheckBox smokeOn = reg("FX_RAUCH", new FCheckBox("Rauch aus Schornsteinen"));
        smokeOn.setSelected(true);
        smokeOn.addActionListener(e -> { scene.setSmoke(smokeOn.isSelected()); scene.requestFocusInWindow(); });
        put(smokeOn);
        FCheckBox torchOn = reg("FX_FACKELN", new FCheckBox("Fackeln und Fensterlicht bei Nacht"));
        torchOn.setSelected(true);
        torchOn.addActionListener(e -> { scene.setTorches(torchOn.isSelected()); scene.requestFocusInWindow(); });
        put(torchOn);
        shower = reg("FX_SCHAUER", new FCheckBox("Regenschauer mit Regenbogen"));
        shower.addActionListener(e -> { scene.setShower(shower.isSelected()); scene.requestFocusInWindow(); });
        put(shower);
        note("Talnebel ist Strahlungsnebel: Er steht im Herbst und Winter an manchen Tagen (ein Modell, jeden Tag gleich) und baut sich nachts auf. Die Sonne zehrt ihn auf: je höher sie steigt, desto tiefer sinkt die Bank und desto größer werden die Lücken; im Winter reicht ihre Höhe nicht, dann hält er sich bis zum Nachmittag. „Nebeltag erzwingen“ zeigt ihn an jedem Tag. Fenster und Fackeln leuchten, sobald die Sonne unter dem Horizont steht. Der Regenbogen steht 42° vom Gegenpunkt der Sonne, der Nebenbogen bei 51°; er braucht eine Sonne unter 42° Höhe, am besten am frühen Morgen oder späten Nachmittag.");
        m.addSection("Dampf und Licht", end());

        // ---- Räume und Leben
        begin();
        FCheckBox crowdOn = reg("L_HOFSTAAT", new FCheckBox("Hofstaat, Wachen und Küfer"));
        crowdOn.setSelected(true);
        crowdOn.addActionListener(e -> { scene.setCrowd(crowdOn.isSelected()); scene.requestFocusInWindow(); });
        put(crowdOn);
        JLabel crLbl = lbl("Dichte des Hofstaats  85 %");
        put(crLbl);
        FSlider crDens = reg("DICHTE_HOF", new FSlider(0, 100, 85));
        crDens.addChangeListener(e -> { crLbl.setText("Dichte des Hofstaats  " + crDens.getValue() + " %"); scene.setCrowdDensity(crDens.getValue() / 100.0); });
        put(crDens);
        FCheckBox ridersOn = reg("L_REITER", new FCheckBox("Reiter, Kutsche und Fasskarren"));
        ridersOn.setSelected(true);
        ridersOn.addActionListener(e -> { scene.setRiders(ridersOn.isSelected()); scene.requestFocusInWindow(); });
        put(ridersOn);
        FCheckBox birdsOn = reg("L_VOEGEL", new FCheckBox("Vögel (Tauben, Krähen, Schwalben, Bussard, Fledermäuse)"));
        birdsOn.setSelected(true);
        birdsOn.addActionListener(e -> { scene.setBirds(birdsOn.isSelected()); scene.requestFocusInWindow(); });
        put(birdsOn);
        FCheckBox candlesOn = reg("L_KERZEN", new FCheckBox("Kerzenlicht in Saal, Fassbau und Grotten"));
        candlesOn.setSelected(true);
        candlesOn.addActionListener(e -> { scene.setCandles(candlesOn.isSelected()); scene.requestFocusInWindow(); });
        put(candlesOn);
        note("Die Menschen sind Schattenrisse mit Schrittzyklus: Herren und Damen im Hof, Wachen mit Hellebarde am Tor und am Saalbau, Küfer am Fassbau, abends Gäste im Saal. Reiter traben um den Hof, tagsüber fährt eine Kutsche mit vier Pferden durchs Tor vor den Saalbau und ein Karren mit Fässern zum Fassbau. Wer wann da ist, legt das Programm nach der Tageszeit fest; bei Regen bleiben die meisten drinnen. Kleidung, Zahl und Wege sind Annahmen. Blickpunkte: Das Große Fass im Fassbau, Saal bei Kerzenlicht, Hof mit Hofstaat; die Fahrt „Vom Hof in den Fassbau“ und zwei neue Szenen im Drehbuch.");
        m.addSection("Räume und Leben", end());

        // ---- Zugaben (Phase 9): jede einzeln schaltbar, was aus ist, kostet nichts
        begin();
        FComboBox stageBox = reg("STUFE", new FComboBox(new Object[]{"Zeitstufe: 1619 (Blütezeit)", "Zeitstufe: nach 1689 (Brand)", "Zeitstufe: nach 1693 (Sprengung)", "Zeitstufe: nach 1764 (Blitz, heute)"}));
        stageBox.addActionListener(e -> { if (!fromStage) scene.setStage(stageBox.getSelectedIndex()); scene.requestFocusInWindow(); });
        scene.setStageListener(i -> javax.swing.SwingUtilities.invokeLater(() -> { fromStage = true; stageBox.setSelectedIndex(i); fromStage = false; }));
        put(stageBox);
        FButton zeitGo = new FButton("Zeitraffer der Zerstörung (Taste Z)");
        zeitGo.addActionListener(e -> { scene.toggleZeit(); scene.requestFocusInWindow(); });
        put(zeitGo);
        note("Der Zeitraffer dauert 104 Sekunden: Brandlegung 1689, Sprengung der Türme 1693, Blitzschläge 1764 mit Gewitter, danach der Zustand von heute. Feuer, Rauch und Donner sind Modelle; wie hoch die Mauern stehen blieben, ist eine Annahme nach Ansichten und Fotos. Brunnen und Menschen ruhen in den Ruinenstufen.");
        FComboBox cutBox = reg("SCHNITT_NR", new FComboBox(new Object[]{"Schnitt: aus", "Schnitt: Längsschnitt x = −70 (Terrassen, Bogenbau, Grotte)", "Schnitt: Querschnitt z = 95 (beide Grotten)"}));
        cutBox.addActionListener(e -> { if (!fromCut) scene.setSchnitt(cutBox.getSelectedIndex()); scene.requestFocusInWindow(); });
        put(cutBox);
        scene.setCutListener(i -> javax.swing.SwingUtilities.invokeLater(() -> { fromCut = true; cutBox.setSelectedIndex(i); fromCut = false; }));
        note("Eine senkrechte Ebene nimmt eine Hälfte des Geländes weg und legt Terrassen, Stützmauern, Bogenbau, Große Grotte und die Wassermechanik offen. Mauerwerk ist schraffiert, Erdreich gekörnt; die Füllung rechnet das Programm beim Einschalten aus dem Modell selbst (Räume bleiben leer). Im Schnitt entfallen Gras, Wege, Laub und Teilchen der weggenommenen Seite; Zeitstufen und Zeitraffer schalten den Schnitt aus. Taste X wechselt Ebene 1, Ebene 2 und aus.");
        FCheckBox lupeOn = reg("Z_LUPE", new FCheckBox("Stein-Lupe: Klick auf eine Fläche zeigt Stoff und Atome"));
        lupeOn.addActionListener(e -> { scene.setLupe(lupeOn.isSelected()); scene.requestFocusInWindow(); });
        scene.setLupeListener(lupeOn::setSelected);
        put(lupeOn);
        note("Klick auf Mauer, Boden, Wasser, Holz, Glas oder Gold zeigt die Bestandteile des Stoffes mit Massenanteilen und die Elemente, die daraus folgen (aus den Formeln gerechnet), und das Atommodell des gewählten Elements: Kern aus Protonen und Neutronen, Schalen, Elektronen. Der Atomzeichner stammt aus dem Projekt ATOMMODEL. Die Anteile sind typische Werte, am Heidelberger Stein nicht gemessen. Taste L schaltet die Lupe.");
        FCheckBox wwOn = reg("Z_WASSERWEG", new FCheckBox("Wasserweg: Perlen zeigen das fließende Wasser"));
        wwOn.addActionListener(e -> { scene.setWasserweg(wwOn.isSelected()); scene.requestFocusInWindow(); });
        put(wwOn);
        note("Vom Hang über Rinne, Terrasse, Mauerfall, Leitungen und Becken zum Abflussbach bis zum Neckar. Die Fahrt „Dem Wasser folgen“ (Kamera und Regie) begleitet das Wasser mit Tafeln. Quelle und Leitungen sind Annahmen nach de Caus.");
        FCheckBox orgOn = reg("Z_WASSERORGEL", new FCheckBox("Wasserorgel in der Großen Grotte (Ton)"));
        orgOn.setSelected(true);
        orgOn.addActionListener(e -> { scene.setWasserorgel(orgOn.isSelected()); scene.requestFocusInWindow(); });
        put(orgOn);
        FCheckBox klangOn = reg("Z_KLANG", new FCheckBox("Ton überhaupt (Orgel, Knall, Donner)"));
        klangOn.setSelected(true);
        klangOn.addActionListener(e -> { scene.setKlang(klangOn.isSelected()); scene.requestFocusInWindow(); });
        put(klangOn);
        note("Alle Töne entstehen im Programm aus Schwingungen und Rauschen, es gibt keine Klangdateien. Die Orgel hört man im Raum hinter der Großen Grotte und, leiser, in der Halle davor. Die Melodie ist eine eigene Komposition im Modus D-dorisch. Fehlt ein Tonausgang, bleibt das Programm stumm.");
        m.addSection("Zugaben", end());

        // ---- Datenbank (Phase 10)
        begin();
        dbLbl = new JLabel();
        dbLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dbLbl.setForeground(INK);
        put(dbLbl);
        FButton merken = new FButton("Zustand merken");
        merken.addActionListener(e -> merkenKlick());
        put(merken);
        gemerktBox = new FComboBox(new Object[]{"Gemerkte Zustände …"});
        put(gemerktBox);
        FButton laden = new FButton("Gemerkten Zustand laden");
        laden.addActionListener(e -> ladenKlick());
        put(laden);
        FButton einr = new FButton("Einrichter ausführen …");
        einr.addActionListener(e -> { new EinrichterDialog(javax.swing.SwingUtilities.getWindowAncestor(this), false).setVisible(true); scene.requestFocusInWindow(); });
        put(einr);
        FButton kat = new FButton("Quellen, Bauwerke, Chronik …");
        kat.addActionListener(e -> { new KatalogDialog(javax.swing.SwingUtilities.getWindowAncestor(this)).setVisible(true); });
        put(kat);
        dbNote = new JLabel();
        dbNote.setFont(new Font("SansSerif", Font.PLAIN, 11));
        dbNote.setForeground(MUTED);
        dbNote.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        put(dbNote);
        note("Beim Beenden merkt sich das Programm Zeit, Stufe, Wetter, Schalter und Kamera und stellt sie beim nächsten Start wieder her (auf Wunsch im Startbild). Mit Datenbank (Schema DEMO, Tabellen HEI_) liegen Zustand, Protokoll der Sitzungen, Blickpunkte, Fahrten, Drehbuch, Quellen und Bauwerke dort; ohne läuft alles mit den eingebauten Werten, Zustand und Protokoll liegen dann in Dateien unter .heidelberg im Benutzerordner.");
        m.addSection("Datenbank", end());
        com.dan.heidelberg.db.Dienst.beiAenderung(this::dbAnzeige);
        dbAnzeige();

        // ---- Stand
        begin();
        note("Phase 1 bis 8: Himmel, Neckartal, Altstadt und Wald; das Schloss um 1619 mit Prunkbauten, Türmen, Mauern und Hof; der Hortus Palatinus mit Terrassen, Grotten, Knotenfeldern und Becken; Lichtstrahlen durch Fenster, spiegelnder Marmor, ruhige Becken. Fontänen, Kaskaden und Brunnen laufen mit Tropfen, Spritzern und Ringwellen. Dazu Talnebel mit Abbrand durch die Sonne, Gischtwolken, Rauch, Abendrot, Fackeln und Regenbogen. Neu in Phase 7: Kamerafahrten mit Kollisionsschutz, Wappen an drei Bauten und das Drehbuch „Ein Tag auf dem Jettenbühl“. Neu in Phase 8: Fassbau mit dem Großen Fass, Gläserner Saalbau mit Kerzenlicht und die Große Grotte mit Laternen; Hofstaat, Reiter, Kutsche und Vögel. Phase 9: Ruinenstufen 1689, 1693 und 1764, Schnitte, Stein-Lupe, Wasserweg und Wasserorgel. Neu in Phase 10: Startbild, Zustand beim Beenden, Datenbank mit Einrichter und Protokoll.");
        note("F1 oder H zeigt alle Tasten.");
        m.addSection("Stand des Projekts", end());

        FAccordion acc = new FAccordion();
        acc.setModel(m);
        acc.setMultipleOpen(false);
        acc.openSection(Integer.getInteger("heidelberg.panel", 1));
        acc.setOpaque(false);
        add(acc, BorderLayout.CENTER);
        sunChanged();
    }

    // ------------------------------------------------------------ Datenbank und Zustand (Phase 10)

    private static final double[] LAPSE = {0, 0.1, 0.5, 0.8};
    private static final String[] QUAL = {"AUTO", "50", "75", "100"};

    private void dbAnzeige() {
        com.dan.heidelberg.db.Dienst.Lage l = com.dan.heidelberg.db.Dienst.lage();
        dbLbl.setText("<html><body style='width:" + (W - 84) + "px'>" + com.dan.heidelberg.db.Dienst.kurz() + "</body></html>");
        String m = com.dan.heidelberg.db.Dienst.meldung();
        dbNote.setText("<html><body style='width:" + (W - 84) + "px'>" + (m.isEmpty() ? "" : m.replace("<", "&lt;")) + "</body></html>");
        if (l == com.dan.heidelberg.db.Dienst.Lage.BEREIT) gemerkteAuffrischen();
    }

    private java.util.List<com.dan.heidelberg.db.ZustandDb.Eintrag> gemerkte = new java.util.ArrayList<>();

    private void gemerkteAuffrischen() {
        new Thread(() -> {
            java.util.List<com.dan.heidelberg.db.ZustandDb.Eintrag> l = com.dan.heidelberg.db.Dienst.gemerkte();
            javax.swing.SwingUtilities.invokeLater(() -> {
                gemerkte = l;
                gemerktBox.removeAllItems();
                gemerktBox.addItem(l.isEmpty() ? "Noch nichts gemerkt" : "Gemerkte Zustände (" + l.size() + ")");
                for (com.dan.heidelberg.db.ZustandDb.Eintrag e : l) gemerktBox.addItem(e.name);
            });
        }, "Heidelberg-Gemerkte").start();
    }

    private void merkenKlick() {
        if (!com.dan.heidelberg.db.Dienst.bereit()) { dbNote.setText("<html><body style='width:" + (W - 84) + "px'>Merken geht nur mit eingerichteter Datenbank.</body></html>"); return; }
        com.dan.heidelberg.db.Zustand z = capture();
        if (z == null) return;
        String name = "Merk " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm"));
        new Thread(() -> {
            boolean ok = com.dan.heidelberg.db.Dienst.merken(z, name);
            javax.swing.SwingUtilities.invokeLater(() -> {
                dbNote.setText("<html><body style='width:" + (W - 84) + "px'>" + (ok ? "Gemerkt als „" + name + "“." : "Nicht gespeichert, siehe Statuszeile.") + "</body></html>");
                if (ok) gemerkteAuffrischen();
            });
        }, "Heidelberg-Merken").start();
        scene.requestFocusInWindow();
    }

    private void ladenKlick() {
        int i = gemerktBox.getSelectedIndex() - 1;
        if (i < 0 || i >= gemerkte.size()) return;
        long id = gemerkte.get(i).id;
        new Thread(() -> {
            com.dan.heidelberg.db.Zustand z = com.dan.heidelberg.db.Dienst.ladeGemerkt(id);
            if (z != null) javax.swing.SwingUtilities.invokeLater(() -> restore(z));
        }, "Heidelberg-Laden").start();
        scene.requestFocusInWindow();
    }

    /** Liest den Zustand aus den Bedienelementen und der Kamera; null, solange die Szene baut. */
    public com.dan.heidelberg.db.Zustand capture() {
        double[] pose = scene.cameraPose();
        if (pose == null) return null;
        com.dan.heidelberg.db.Zustand z = new com.dan.heidelberg.db.Zustand();
        for (java.util.Map.Entry<String, JComponent> e : reg.entrySet()) {
            String k = e.getKey();
            JComponent c = e.getValue();
            if (k.equals("STUNDE")) z.setze(k, time.getValue() / 10.0);
            else if (k.equals("ZEITRAFFER")) z.setze(k, LAPSE[Math.max(0, Math.min(3, lapse.getSelectedIndex()))]);
            else if (k.equals("QUALITAET")) z.setze(k, QUAL[Math.max(0, Math.min(3, ((FComboBox) c).getSelectedIndex()))]);
            else if (c instanceof javax.swing.AbstractButton) z.setze(k, ((javax.swing.AbstractButton) c).isSelected());
            else if (c instanceof javax.swing.JSlider) z.setze(k, ((javax.swing.JSlider) c).getValue());
            else if (c instanceof javax.swing.JComboBox) z.setze(k, ((javax.swing.JComboBox<?>) c).getSelectedIndex());
        }
        z.setze("DREHPUNKT_X", pose[0]).setze("DREHPUNKT_Y", pose[1]).setze("DREHPUNKT_Z", pose[2]);
        double g = pose[3] % 360;
        if (g < 0) g += 360;
        z.setze("GIER_GRAD", g).setze("NICK_GRAD", pose[4]).setze("ABSTAND_M", pose[5]);
        z.kameraGesetzt = true;
        return z;
    }

    /** Stellt einen Zustand wieder her. Auf dem Ereignis-Thread aufrufen, wenn die Szene fertig ist. */
    public void restore(com.dan.heidelberg.db.Zustand z) {
        if (z == null) return;
        // erst die Stufe (baut die Welt um) und der Schnitt, dann alles andere, zuletzt Blickpunkt und Kamera
        int stufe = z.ganz("STUFE");
        if (stufe != ((FComboBox) reg.get("STUFE")).getSelectedIndex()) ((FComboBox) reg.get("STUFE")).setSelectedIndex(stufe);
        for (com.dan.heidelberg.db.Zustand.Feld f : com.dan.heidelberg.db.Zustand.SPEC) {
            String k = f.name;
            JComponent c = reg.get(k);
            if (c == null || k.equals("STUFE") || k.equals("BLICKPUNKT") || k.equals("RUNDFLUG")) continue;
            if (k.equals("SCHNITT_NR")) { if (stufe == 0) setzeCombo((FComboBox) c, z.ganz(k), false); continue; }
            if (k.equals("WETTER")) { setzeCombo((FComboBox) c, z.ganz(k), false); continue; }
            if (k.equals("STUNDE")) { time.setValue(Math.max(0, Math.min(239, (int) Math.round(z.zahl(k) * 10)))); continue; }
            if (k.equals("ZEITRAFFER")) {
                int best = 0;
                for (int i = 1; i < LAPSE.length; i++) if (Math.abs(LAPSE[i] - z.zahl(k)) < Math.abs(LAPSE[best] - z.zahl(k))) best = i;
                setzeCombo((FComboBox) c, best, true);
                continue;
            }
            if (k.equals("QUALITAET")) {
                int i = java.util.Arrays.asList(QUAL).indexOf(z.text(k));
                setzeCombo((FComboBox) c, Math.max(0, i), true);
                continue;
            }
            if (c instanceof javax.swing.AbstractButton) {
                javax.swing.AbstractButton b = (javax.swing.AbstractButton) c;
                b.setSelected(z.flag(k));
                for (java.awt.event.ActionListener l : b.getActionListeners()) l.actionPerformed(new java.awt.event.ActionEvent(b, java.awt.event.ActionEvent.ACTION_PERFORMED, "restore"));
            } else if (c instanceof javax.swing.JSlider) {
                javax.swing.JSlider sl = (javax.swing.JSlider) c;
                int v = z.ganz(k);
                if (sl.getValue() != v) sl.setValue(v);
                else for (javax.swing.event.ChangeListener l : sl.getChangeListeners()) l.stateChanged(new javax.swing.event.ChangeEvent(sl));
            } else if (c instanceof javax.swing.JComboBox) setzeCombo((FComboBox) c, z.ganz(k), true);
        }
        javax.swing.JComponent orbit = reg.get("RUNDFLUG");
        if (orbit instanceof javax.swing.AbstractButton) {
            javax.swing.AbstractButton b = (javax.swing.AbstractButton) orbit;
            b.setSelected(z.flag("RUNDFLUG"));
            scene.setOrbit(z.flag("RUNDFLUG"));
        }
        int vp = Math.max(0, Math.min(Viewpoint.ALL.length - 1, z.ganz("BLICKPUNKT")));
        if (z.kameraGesetzt) {
            fromView = true;
            vpBox.setSelectedIndex(vp);
            fromView = false;
            scene.restoreCamera(new double[]{z.zahl("DREHPUNKT_X"), z.zahl("DREHPUNKT_Y"), z.zahl("DREHPUNKT_Z"), z.zahl("GIER_GRAD"), z.zahl("NICK_GRAD"), z.zahl("ABSTAND_M")}, vp);
        } else {
            scene.goViewpoint(vp);
        }
    }

    /** Wählt einen Eintrag; ist er schon gewählt, löst er die Wirkung nur aus, wenn fire gesetzt ist. */
    private void setzeCombo(FComboBox box, int i, boolean fire) {
        i = Math.max(0, Math.min(box.getItemCount() - 1, i));
        if (box.getSelectedIndex() != i) box.setSelectedIndex(i);
        else if (fire) for (java.awt.event.ActionListener l : box.getActionListeners()) l.actionPerformed(new java.awt.event.ActionEvent(box, java.awt.event.ActionEvent.ACTION_PERFORMED, "restore"));
    }

    private static final Object[] MOODS = {"Stimmung wählen …", "Morgennebel über dem Tal", "Nebel löst sich auf (Zeitraffer)",
            "Abendrot über der Rheinebene", "Fackelnacht im Schlosshof", "Fackeln im Hortus", "Regenbogen nach dem Schauer", "Rauch über der Altstadt", "Schneefall über dem Schloss"};

    /** Stimmungen: Tag, Uhrzeit, Blickpunkt, Nebeltag, Schauer, Dunst in %, Zeitraffer (Nummer). */
    private void applyMood(int i) {
        Object[][] t = {
                null,
                {285, 8.4, "Schloss über dem Nebelmeer", true, false, 12, 0},
                {285, 7.6, "Schloss über dem Nebelmeer", true, false, 12, 1},
                {172, 21.0, "Abendrot über der Rheinebene", false, false, 30, 0},
                {285, 19.8, "Schlosshof bei Nacht", false, false, 12, 0},
                {285, 19.8, "Hortus bei Nacht", false, false, 12, 0},
                {150, 18.9, "Regenbogen über dem Schlossberg", false, true, 12, 0},
                {335, 8.2, "Altstadt im Morgenrauch", false, false, 12, 0},
                {20, 10.5, "Schloss vom Neckar", false, false, 12, 0},
        };
        Object[] m = t[i];
        scene.setWeather(i == 8 ? Weather.SNOW : Weather.CLEAR);
        scene.setSnowCover(i == 8 ? 0.5 : 0);
        day.setValue((Integer) m[0]);
        time.setValue((int) Math.floor((Double) m[1] * 10));
        sunChanged();
        fogForce.setSelected((Boolean) m[3]);
        fogSlider.setValue(100);
        scene.setValleyFog((Boolean) m[3], 1.0);
        shower.setSelected((Boolean) m[4]);
        scene.setShower((Boolean) m[4]);
        haze.setValue((Integer) m[5]);
        lapse.setSelectedIndex((Integer) m[6]);
        scene.goViewpoint(Viewpoint.index((String) m[2]));
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(W + 24, d.height);
    }

    // ------------------------------------------------------------ Bausteine

    private void begin() {
        cur = new Sheet();
        cur.setLayout(new BoxLayout(cur, BoxLayout.Y_AXIS));
        cur.setOpaque(false);
        cur.setBorder(BorderFactory.createEmptyBorder(6, 6, 20, 6));
    }

    /** Inhaltsfläche, die die Breite des Sichtfensters übernimmt (kein Querlauf). */
    private static final class Sheet extends JPanel implements javax.swing.Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private JComponent end() {
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(cur, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        com.dan.fscrollbar.FScrollBar bar = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
        bar.setUnitIncrement(16);
        bar.setBlockIncrement(120);
        sp.setVerticalScrollBar(bar);
        return sp;
    }

    private void put(Component c) {
        if (c == timeLbl || c == dayLbl || c == hazeLbl) {
            ((JLabel) c).setForeground(INK);
            ((JLabel) c).setFont(new Font("SansSerif", Font.PLAIN, 13));
        }
        if (c instanceof FCheckBox) ((FCheckBox) c).setTextColor(INK);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 4));
        cur.add(p);
    }

    private JLabel lbl(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    private void note(String s) {
        JLabel l = new JLabel("<html><body style='width:" + (W - 84) + "px'>" + s + "</body></html>");
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(l, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        cur.add(p);
    }

    private void labels(int d, double h) {
        timeLbl.setText("Uhrzeit  " + DayNightCycle.timeLabel(h) + " " + DayNightCycle.zone(d, h));
        dayLbl.setText("Tag  " + DayNightCycle.dateLabel(d) + " " + DayNightCycle.YEAR);
    }

    private void sunChanged() {
        double h = time.getValue() / 10.0;
        int d = day.getValue();
        labels(d, h);
        if (!fromScene) scene.setSunTime(d, h);
    }
}
