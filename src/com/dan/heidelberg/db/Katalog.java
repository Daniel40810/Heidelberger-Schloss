package com.dan.heidelberg.db;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Der eingebaute Katalog: Zeitstufen, Quellen, Bauwerke und Chronik, wie sie im Werkbuch stehen. Er ist die Vorlage
 * für die Grunddaten (db/04_seed.sql, erzeugt von tools.SeedGen) und der Rückfall, wenn keine Datenbank da ist.
 * Wo Quellen sich widersprechen, steht beides im Text.
 */
public final class Katalog {
    private Katalog() { }

    public record Stufe(int nr, int jahr, LocalDate datum, String name, String kurz, String beschreibung, String quelle) { }

    public record Quelle(String code, String autor, String lebenszeit, String werk, String stelle, String sprache, String ueberlieferung,
                         Integer abstandJahre, String bezug, String kern, String masse, String beleg) { }

    /** ort: Anfang des Namens in Castle.PLACES, daraus kommt die Lage (x, z). */
    public record Bau(String code, String name, String historisch, String bezug, String detail, int stufen, String rolle, String quelle, String ort, Double x, Double z) {
        public Bau(String code, String name, String historisch, String bezug, String detail, int stufen, String rolle, String quelle, String ort) {
            this(code, name, historisch, bezug, detail, stufen, rolle, quelle, ort, null, null);
        }
    }

    public record Ereignis(int stufe, int jahr, LocalDate datum, String titel, String text, String quelle) { }

    public static final class Daten {
        public final List<Stufe> stufen = new ArrayList<>();
        public final List<Quelle> quellen = new ArrayList<>();
        public final List<Bau> bauten = new ArrayList<>();
        public final List<Ereignis> chronik = new ArrayList<>();
    }

    public static final String WIKI_SCHLOSS = "WIKI_SCHLOSS", WIKI_HORTUS = "WIKI_HORTUS", WIKI_FASS = "WIKI_FASS", SSG_BW = "SSG_BW",
            ZUM = "ZUM", DECAUS = "DECAUS_1620", EML = "EML_2003", MODELL = "MODELL";

    public static Daten eingebaut() {
        Daten d = new Daten();

        // ---- Quellen
        d.quellen.add(new Quelle(WIKI_SCHLOSS, "Wikipedia", null, "Heidelberger Schloss (deutsch) und Heidelberg Castle (englisch)", null, "de, en", "Online-Enzyklopädie",
                null, "SCHLOSS",
                "Baugeschichte: Ruprechtsbau um 1400 (Wikipedia nennt ca. 1430), Ottheinrichsbau ab 1556, Friedrichsbau 1601 bis 1607 (Architekt Johannes Schoch), Englischer Bau 1612/13. Brandlegung am 2. März 1689, Sprengung am 6. September 1693, Blitzschlag am 24. Juni 1764. Lage auf der Jettenbühl-Terrasse am Nordhang des Königstuhls, rund 80 m über der Altstadt.",
                null, "Die Staatlichen Schlösser und Gärten nennen für die Zerstörung 1688 statt 1689."));
        d.quellen.add(new Quelle(SSG_BW, "Staatliche Schlösser und Gärten Baden-Württemberg", null, "Schloss Heidelberg: Zeitreise und Geschichte (schloss-heidelberg.de)", null, "de", "Amtliche Darstellung des Landes",
                null, "SCHLOSS",
                "Das Schloss auf dem Jettenbühl wird 1303 als Sitz der Pfalzgrafen erstmals fassbar. Ruprechtsbau um 1400, Hortus Palatinus ab 1616, Gläserner Saalbau unter Friedrich II. 1544 bis 1556, Zerstörung 1688, doppelter Blitzeinschlag 1764.",
                "Fass von 1591: 130 000 Liter", "Wikipedia nennt für den Ruprechtsbau ca. 1430, für den Beginn des Hortus Juli 1614 (englisch) oder 1616 (deutsch)."));
        d.quellen.add(new Quelle(WIKI_HORTUS, "Wikipedia", null, "Hortus Palatinus (deutsch und englisch)", null, "de, en", "Online-Enzyklopädie",
                null, "HORTUS",
                "Salomon de Caus legte den Garten ab Juli 1614 (englisch) oder 1616 (deutsch) an, rund 5 ha groß, L-förmig um das Schloss. Der Garten wurde nie fertig: Die Arbeit ruhte ab Ende 1619, im Dreißigjährigen Krieg wurde er zerstört. Das Stichwerk von 1620 zeigt den Plan.",
                "Rund 5 ha; Obere Terrasse 8,5 m breit und 2,5 m höher als die mittlere; Bogenbau 20 m hoch; Pomeranzenbäume etwa 60 Jahre alt",
                "Die Quellen nennen als Beginn Juli 1614 oder 1616."));
        d.quellen.add(new Quelle(WIKI_FASS, "Wikipedia", null, "Großes Fass des Heidelberger Schlosses", null, "de", "Online-Enzyklopädie",
                null, "FASSBAU",
                "Pfalzgraf Johann Casimir ließ das Fass 1589 bis 1591 von Baumeister Michael Werner aus Landau bauen; es fasste rund 127 000 Liter. Im Dreißigjährigen Krieg wurde es zerstört. Maße sind nicht überliefert; ein Tanzboden ist erst vom Fass von 1664 überliefert.",
                "Rund 127 000 Liter", "zum.de nennt 128 000 Liter, die Staatlichen Schlösser und Gärten 130 000 Liter."));
        d.quellen.add(new Quelle(ZUM, "zum.de", null, "Das Heidelberger Fass", null, "de", "Lehrmaterial im Netz",
                null, "FASSBAU", "Das Große Fass von 1591 fasste 128 000 Liter.", "128 000 Liter", "Wikipedia nennt rund 127 000 Liter."));
        d.quellen.add(new Quelle(DECAUS, "Salomon de Caus", null, "Hortus Palatinus, Stichwerk von 1620", null, "französisch, lateinisch", "Gedruckte Stiche, ein Jahr nach dem Stand der Szene",
                1, "HORTUS",
                "Das Stichwerk von 1620 zeigt den Plan des Gartens, nicht den Stand des Gebauten. Höhen der Wasserstrahlen nennt de Caus nicht.",
                null, "Der Garten wurde nie fertig; was die Stiche zeigen, ist der Plan."));
        d.quellen.add(new Quelle(EML, "European Media Laboratory, Heidelberg", null, "Digitale Rekonstruktion der Gärten (2003)", null, "de", "Frühere Rekonstruktion, dient hier nur als Vorarbeit",
                null, "HORTUS", "Eine digitale Rekonstruktion des Hortus Palatinus entstand 2003 am European Media Laboratory in Heidelberg.", null, null));
        d.quellen.add(new Quelle(MODELL, "Rekonstruktion (Modell)", null, "Annahmen und Modelle dieses Programms", null, "de", "Eigenleistung",
                null, null,
                "Wo keine Quelle Maße oder Lage nennt, gilt eine Annahme der Rekonstruktion: Lage und Maße der Bauten zueinander (geschätzt, einige Meter), Wappen, Hofstaat, Kleidung, Wege, Nebeltage, Wetter, Tageslauf, Strahlhöhen der Fontänen, Quelle und Leitungen des Wasserwegs, Höhe der Ruinenmauern.",
                null, "Im Programm als „Annahme der Rekonstruktion“ oder „Modell“ gekennzeichnet."));

        // ---- Zeitstufen
        d.stufen.add(new Stufe(0, 1619, null, "1619 (Blütezeit)", "Schloss und Hortus Palatinus im Herbst 1619, vor jeder Zerstörung",
                "Prunkbauten voll, übrige Bauten mittel, Türme einfach; der Hortus ist nach de Caus gebaut, was nur geplant war, lässt sich bläulich tönen. Im Herbst 1619 wird Friedrich V. zum König von Böhmen gewählt, die Gartenarbeiten stehen danach still.", WIKI_SCHLOSS));
        d.stufen.add(new Stufe(1, 1689, LocalDate.of(1689, 3, 2), "Nach dem Brand 1689", "Brandlegung im Pfälzischen Erbfolgekrieg: Dächer, Decken und Einbauten sind verloren",
                "Mauern ohne Dächer, Decken, Einbauten und Glas, mit gebrochener Krone. Wikipedia nennt den 2. März 1689, die Staatlichen Schlösser 1688. Wie hoch jede Mauer stehen blieb, ist Annahme der Rekonstruktion.", WIKI_SCHLOSS));
        d.stufen.add(new Stufe(2, 1693, LocalDate.of(1693, 9, 6), "Nach der Sprengung 1693", "Französische Pioniere sprengen Türme, darunter den Krautturm",
                "Türme und Mauern weiter gebrochen; der Notdach-Zustand des Ottheinrichsbaus ist Annahme der Rekonstruktion.", WIKI_SCHLOSS));
        d.stufen.add(new Stufe(3, 1764, LocalDate.of(1764, 6, 24), "Nach dem Blitz 1764 (heute)", "Blitzschlag und Brände; der Wiederaufbau wird aufgegeben, es bleibt die Ruine von heute",
                "Der Zustand von heute als Ruine. Der Friedrichsbau wurde 1897 bis 1900 als einziger Bau wiederhergestellt; das Modell zeigt auch ihn als Ruine.", WIKI_SCHLOSS));

        // ---- Chronik
        d.chronik.add(new Ereignis(0, 1303, null, "Sitz der Pfalzgrafen", "Das Schloss auf dem Jettenbühl wird als Sitz der Pfalzgrafen erstmals fassbar.", SSG_BW));
        d.chronik.add(new Ereignis(0, 1400, null, "Ruprechtsbau", "Der Ruprechtsbau entsteht unter Ruprecht III. um 1400 (Wikipedia nennt ca. 1430).", WIKI_SCHLOSS));
        d.chronik.add(new Ereignis(0, 1510, null, "Bauten Ludwigs V.", "1510 bis 1541: Frauenzimmerbau, Bibliotheksbau, Ludwigsbau (1524), Dicker Turm (1533) und Torturm (1531 bis 1541).", WIKI_SCHLOSS));
        d.chronik.add(new Ereignis(0, 1556, null, "Ottheinrichsbau", "Ab 1556 entsteht der Ottheinrichsbau, das Hauptwerk der Renaissance am Schloss.", WIKI_SCHLOSS));
        d.chronik.add(new Ereignis(0, 1589, null, "Fassbau und Großes Fass", "1589 bis 1592 wird der Fassbau für das Große Fass von 1591 gebaut, damals rund 127 000 Liter.", WIKI_FASS));
        d.chronik.add(new Ereignis(0, 1601, null, "Friedrichsbau", "1601 bis 1607 baut Friedrich IV. den Friedrichsbau, Architekt Johannes Schoch, mit der ersten Stadtfassade.", WIKI_SCHLOSS));
        d.chronik.add(new Ereignis(0, 1612, null, "Englischer Bau", "1612/13 lässt Friedrich V. den Englischen Bau errichten, benannt nach seiner Frau Elisabeth Stuart.", WIKI_SCHLOSS));
        d.chronik.add(new Ereignis(0, 1614, null, "Hortus Palatinus", "Salomon de Caus legt den Hortus Palatinus an; die Quellen nennen als Beginn Juli 1614 oder 1616. Ende 1619 bleibt die Arbeit liegen.", WIKI_HORTUS));
        d.chronik.add(new Ereignis(0, 1619, null, "König von Böhmen", "Im Herbst 1619 wird Friedrich V. zum König von Böhmen gewählt; die Arbeit am Garten ruht danach.", WIKI_HORTUS));
        d.chronik.add(new Ereignis(1, 1689, LocalDate.of(1689, 3, 2), "Brandlegung", "Brandlegung im Pfälzischen Erbfolgekrieg am 2. März 1689 (Staatliche Schlösser und Gärten nennen 1688).", WIKI_SCHLOSS));
        d.chronik.add(new Ereignis(2, 1693, LocalDate.of(1693, 9, 6), "Sprengung der Türme", "Französische Pioniere sprengen Türme, darunter den Krautturm.", WIKI_SCHLOSS));
        d.chronik.add(new Ereignis(3, 1764, LocalDate.of(1764, 6, 24), "Blitzschlag", "Ein Blitzschlag löst Brände aus; der Wiederaufbau wird aufgegeben.", WIKI_SCHLOSS));
        d.chronik.add(new Ereignis(3, 1897, null, "Friedrichsbau wiederhergestellt", "1897 bis 1900 wird der Friedrichsbau als einziger Bau wiederhergestellt.", WIKI_SCHLOSS));

        // ---- Bauwerke (ort: Name in Castle.PLACES)
        Bau[] b = {
                new Bau("SCHLOSSHOF", "Schlosshof", null, "HOF", "MITTEL", 15, "Ursprung des Modells (0, 0), Hofniveau y = 0, Pflaster; Lage der Bauten zueinander aus Plänen geschätzt.", WIKI_SCHLOSS, "Schlosshof"),
                new Bau("TORTURM", "Torturm", null, "TURM", "VOLL", 15, "Ludwig V., 1531 bis 1541. 52 m hoch, Zugang zum Schloss. Die Barockhaube stammt von etwa 1716, die Dachform von 1619 ist als Pyramide angenommen.", WIKI_SCHLOSS, "Torturm"),
                new Bau("TORBRUECKE", "Torbrücke", null, "BAU", "MITTEL", 15, "Brücke über den Graben vor dem Torturm; Form und Lage sind Annahmen der Rekonstruktion.", MODELL, "Torbrücke"),
                new Bau("DICKER_TURM", "Dicker Turm", null, "TURM", "VOLL", 15, "Ludwig V., 1533. 40 m hoch, 7 m Mauerstärke.", WIKI_SCHLOSS, "Dicker Turm"),
                new Bau("OTTHEINRICHSBAU", "Ottheinrichsbau", null, "BAU", "VOLL", 15, "Ottheinrich, ab 1556. Manierismus, Bildhauer A. Colin; Architekt unbekannt.", WIKI_SCHLOSS, "Ottheinrichsbau"),
                new Bau("FRIEDRICHSBAU", "Friedrichsbau", null, "BAU", "VOLL", 15, "Friedrich IV., 1601 bis 1607. Architekt Johannes Schoch, Stadtfassade; 1897 bis 1900 als einziger Bau wiederhergestellt.", WIKI_SCHLOSS, "Friedrichsbau"),
                new Bau("SAALBAU", "Gläserner Saalbau", null, "BAU", "VOLL", 15, "Friedrich II., 16. Jahrhundert (1544 bis 1556). Spiegelsaal mit venezianischem Glas; im Modell innen hohl und ebenerdig.", SSG_BW, "Gläserner Saalbau"),
                new Bau("FASSBAU", "Fassbau", null, "BAU", "VOLL", 15, "Johann Casimir, 1589 bis 1592. Fass von 1591, rund 127 000 Liter (Staatliche Schlösser: 130 000); im Modell innen hohl.", WIKI_FASS, "Fassbau"),
                new Bau("ENGLISCHER_BAU", "Englischer Bau", null, "BAU", "MITTEL", 15, "Friedrich V., 1612/13. Architekt vermutlich Inigo Jones.", WIKI_SCHLOSS, "Englischer Bau"),
                new Bau("RUPRECHTSBAU", "Ruprechtsbau", null, "BAU", "MITTEL", 15, "Ruprecht III., um 1400 (Wikipedia: ca. 1430). Älteste erhaltene Teile.", WIKI_SCHLOSS, "Ruprechtsbau"),
                new Bau("FRAUENZIMMERBAU", "Frauenzimmerbau", null, "BAU", "MITTEL", 15, "Ludwig V., um 1510. Hofstube 34,65 m × 16,70 m.", WIKI_SCHLOSS, "Frauenzimmerbau"),
                new Bau("BIBLIOTHEKSBAU", "Bibliotheksbau", null, "BAU", "MITTEL", 15, "Ludwig V., um 1520. Mauern im Erdgeschoss 3 m dick.", WIKI_SCHLOSS, "Bibliotheksbau"),
                new Bau("LUDWIGSBAU", "Ludwigsbau", null, "BAU", "MITTEL", 15, "Ludwig V., 1524. Mauern im Erdgeschoss 3 m dick.", WIKI_SCHLOSS, "Ludwigsbau"),
                new Bau("KRAUTTURM", "Krautturm", null, "TURM", "MITTEL", 15, "15. Jahrhundert. Pulvermagazin, 1693 gesprengt.", WIKI_SCHLOSS, "Krautturm"),
                new Bau("GLOCKENTURM", "Glockenturm", null, "TURM", "EINFACH", 15, "Um 1490. Wehrturm am Rand der Anlage.", WIKI_SCHLOSS, "Glockenturm"),
                new Bau("APOTHEKERTURM", "Apothekerturm", null, "TURM", "EINFACH", 15, "Um 1490. Wehrturm am Rand der Anlage.", WIKI_SCHLOSS, "Apothekerturm"),
                new Bau("BRUNNENHALLE", "Brunnenhalle", null, "BAU", "MITTEL", 15, "Ludwig V. Ziehbrunnen, rund 16 m tief, seit 1508; vier Monolithe.", WIKI_SCHLOSS, "Brunnenhalle"),
                new Bau("ELISABETHENTOR", "Elisabethentor", null, "HORTUS", "MITTEL", 15, "Friedrich V., vor 1620. Am Stückgarten, Eingang zum Hortus.", WIKI_HORTUS, "Elisabethentor"),
                new Bau("HORTUS_HAUPT", "Hortus Palatinus, Hauptterrasse", null, "HORTUS", "MITTEL", 15, "Salomon de Caus, ab 1614 oder 1616. Hauptterrasse mit Knotenfeldern aus Thymian, Rosmarin, Lavendel und Salbei, dazwischen farbige Kiesel; rund 5 ha Gesamtfläche, L-förmig um das Schloss.", WIKI_HORTUS, "Hortus Palatinus"),
                new Bau("PYRAMIDENTREPPE", "Pyramidentreppe", null, "HORTUS", "MITTEL", 15, "Nach de Caus nur geplant, nie gebaut; im Modell bläulich getönt (Schalter „Geplantes blau tönen“).", DECAUS, "Pyramidentreppe"),
                new Bau("BOGENBAU", "Scheffelterrasse und Bogenbau", null, "HORTUS", "MITTEL", 15, "Die Scheffelterrasse ruhte auf einem 20 m hohen Bogenbau mit Nischen.", WIKI_HORTUS, "Scheffelterrasse"),
                new Bau("GROSSE_GROTTE", "Große Grotte", null, "HORTUS", "VOLL", 15, "Vier Räume. Im ersten fließt Wasser kaskadenartig in ein Becken mit Springbrunnen, im zweiten steht ein Steintisch mit Wassermechanik, dazu kommt die Wasserorgel nach Vitruv. Im Modell begehbar.", WIKI_HORTUS, "Große Grotte"),
                new Bau("KLEINE_GROTTE", "Kleine Grotte", null, "HORTUS", "VOLL", 15, "Liegt neben der Großen Grotte; im Modell begehbar, mit Überlauf und Maul-Bogen.", WIKI_HORTUS, "Kleine Grotte"),
                new Bau("SAEULENBRUNNEN", "Säulenbrunnen", null, "WASSER", "VOLL", 15, "Säulenbrunnen im Zentrum eines Knotenfeldes.", WIKI_HORTUS, "Säulenbrunnen"),
                new Bau("ACHTECKBECKEN", "Achteckbecken", null, "WASSER", "VOLL", 15, "Achteckiges Bassin mit Fratzengesichtern aus Metall. Nach de Caus nur geplant, nie fertiggestellt; im Modell bläulich getönt.", WIKI_HORTUS, "Achteckiges Becken"),
                new Bau("RHENUSBECKEN", "Rhenusbecken", null, "WASSER", "VOLL", 15, "Becken mit dem Rhenus vor der Großen Grotte. Nach de Caus nur geplant, nie fertiggestellt; im Modell bläulich getönt.", WIKI_HORTUS, "Rhenusbecken"),
                new Bau("POMERANZEN", "Pomeranzenbäume in Kästen", null, "HORTUS", "MITTEL", 15, "Pomeranzenbäume, rund 60 Jahre alt, in hochsitzenden Erdkästen (nach der englischen Quelle dreißig Stück).", WIKI_HORTUS, "Pomeranzenbäume"),
                new Bau("WASSERORGEL", "Wasserorgel", null, "WASSER", "VOLL", 15, "Wasserorgel nach Vitruv in der Großen Grotte. Melodie (D-dorisch) und Klang sind Eigenleistung der Rekonstruktion.", MODELL, "Wasserorgel"),
                new Bau("BRUNNENSTUBE", "Brunnenstube des Wasserwegs", null, "WASSER", "MITTEL", 15, "Quelle am Nordhang; Lage und Leitungen sind Annahmen nach de Caus.", MODELL, "Brunnenstube"),
                new Bau("MAUERFALL", "Mauerfall und Trog", null, "WASSER", "MITTEL", 15, "Station des Wasserwegs; Annahme der Rekonstruktion.", MODELL, "Mauerfall"),
                new Bau("WASSERSPEIER", "Wasserspeier an der Westmauer", null, "WASSER", "MITTEL", 15, "Abfluss des Wasserwegs; Annahme der Rekonstruktion.", MODELL, "Abfluss"),
                new Bau("ABFLUSSBACH", "Abflussbach", null, "WASSER", "MITTEL", 15, "Der Abfluss geht ins Friesentälchen und von dort zum Neckar; der Verlauf ist Annahme der Rekonstruktion.", MODELL, "Abflussbach"),
        };
        for (Bau x : b) d.bauten.add(x);
        return d;
    }
}
