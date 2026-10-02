package com.dan.heidelberg.lupe;

import com.dan.heidelberg.core.Mat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 9, Stein-Lupe: woraus ein Stoff des Modells besteht. Jeder Stoff ist eine Mischung aus Mineralen oder Verbindungen mit
 * Massenanteilen; die Massenanteile der Elemente rechnet das Programm aus den Formeln (Atommassen der Elemente) selbst aus.
 * Die Anteile sind typische Werte für die Gesteins- und Stoffart, keine Messungen am Heidelberger Stein; wo das Modell nur
 * eine Annahme trifft, sagt der Hinweis es. Die Elementdaten sind eine kleine eigene Tabelle, die Datenbank des
 * Atommodells wird nicht gebraucht.
 */
public final class Stoffe {
    private Stoffe() { }

    /** Ein Element: Ordnungszahl, Symbol, Name, Massenzahl des häufigsten Isotops, Schalenbesetzung, Atommasse, Farbe. */
    public static final class Elem {
        public final int z, massNo;
        public final String sym, name, shells;
        public final double mass;
        public final Color color;
        Elem(int z, String sym, String name, int massNo, String shells, double mass, Color color) {
            this.z = z; this.sym = sym; this.name = name; this.massNo = massNo; this.shells = shells; this.mass = mass; this.color = color;
        }
        public int neutrons() { return massNo - z; }
    }

    private static final Map<String, Elem> EL = new HashMap<>();
    private static void el(int z, String s, String n, int a, String sh, double m, int rgb) { EL.put(s, new Elem(z, s, n, a, sh, m, new Color(rgb))); }
    static {
        el(1, "H", "Wasserstoff", 1, "1", 1.008, 0xdfe8f2);
        el(6, "C", "Kohlenstoff", 12, "2-4", 12.011, 0x6f7479);
        el(7, "N", "Stickstoff", 14, "2-5", 14.007, 0x4d78d8);
        el(8, "O", "Sauerstoff", 16, "2-6", 15.999, 0xd8453d);
        el(11, "Na", "Natrium", 23, "2-8-1", 22.990, 0xe0b53a);
        el(12, "Mg", "Magnesium", 24, "2-8-2", 24.305, 0x59b36a);
        el(13, "Al", "Aluminium", 27, "2-8-3", 26.982, 0xa9b3bf);
        el(14, "Si", "Silicium", 28, "2-8-4", 28.086, 0x8f9fba);
        el(16, "S", "Schwefel", 32, "2-8-6", 32.06, 0xd7c93a);
        el(17, "Cl", "Chlor", 35, "2-8-7", 35.45, 0x6cc46a);
        el(19, "K", "Kalium", 39, "2-8-8-1", 39.098, 0x9a6fd0);
        el(20, "Ca", "Calcium", 40, "2-8-8-2", 40.078, 0xe3dcc5);
        el(22, "Ti", "Titan", 48, "2-8-10-2", 47.867, 0x9aa4ab);
        el(25, "Mn", "Mangan", 55, "2-8-13-2", 54.938, 0xb07aa8);
        el(26, "Fe", "Eisen", 56, "2-8-14-2", 55.845, 0xb5582f);
        el(29, "Cu", "Kupfer", 63, "2-8-18-1", 63.546, 0xc87a4a);
        el(50, "Sn", "Zinn", 120, "2-8-18-18-4", 118.71, 0xb8c0c8);
        el(79, "Au", "Gold", 197, "2-8-18-32-18-1", 196.97, 0xe6b422);
        el(82, "Pb", "Blei", 208, "2-8-18-32-18-4", 207.2, 0x6f7f96);
    }

    public static Elem element(String sym) { return EL.get(sym); }

    /** Ein Bestandteil: Name, Formel, Massenanteil in %. */
    public static final class Teil {
        public final String name, formel;
        public final double anteil;
        Teil(String name, String formel, double anteil) { this.name = name; this.formel = formel; this.anteil = anteil; }
    }

    /** Ein Stoff mit Herkunft, Bestandteilen und den daraus gerechneten Elementanteilen. */
    public static final class Stoff {
        public final String titel, text, hinweis;
        public final List<Teil> teile = new ArrayList<>();
        /** Elemente nach Massenanteil absteigend. */
        public final List<Elem> elemente = new ArrayList<>();
        public final List<Double> prozent = new ArrayList<>();
        Stoff(String titel, String text, String hinweis) { this.titel = titel; this.text = text; this.hinweis = hinweis; }
        Stoff t(String n, String f, double a) { teile.add(new Teil(n, f, a)); return this; }
        Stoff fertig() {
            Map<String, Double> mass = new HashMap<>();
            double total = 0;
            for (Teil p : teile) {
                Map<String, Double> c = parse(p.formel);
                double mm = 0;
                for (Map.Entry<String, Double> e : c.entrySet()) mm += e.getValue() * EL.get(e.getKey()).mass;
                for (Map.Entry<String, Double> e : c.entrySet()) {
                    double w = p.anteil * e.getValue() * EL.get(e.getKey()).mass / mm;
                    mass.merge(e.getKey(), w, Double::sum);
                    total += w;
                }
            }
            List<Map.Entry<String, Double>> es = new ArrayList<>(mass.entrySet());
            es.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            for (Map.Entry<String, Double> e : es) { elemente.add(EL.get(e.getKey())); prozent.add(100 * e.getValue() / total); }
            return this;
        }
    }

    // ------------------------------------------------------------ Formeln

    /** Zerlegt eine Formel wie Ca(HCO3)2 oder Al2Si2O5(OH)4 in Atomzahlen je Element. */
    static Map<String, Double> parse(String f) {
        int[] pos = {0};
        Map<String, Double> m = group(f, pos);
        return m;
    }

    private static Map<String, Double> group(String f, int[] p) {
        Map<String, Double> m = new HashMap<>();
        while (p[0] < f.length()) {
            char c = f.charAt(p[0]);
            if (c == ')') { p[0]++; return m; }
            Map<String, Double> sub;
            if (c == '(') {
                p[0]++;
                sub = group(f, p);
            } else if (Character.isUpperCase(c)) {
                int s = p[0]++;
                while (p[0] < f.length() && Character.isLowerCase(f.charAt(p[0]))) p[0]++;
                sub = new HashMap<>();
                String sym = f.substring(s, p[0]);
                if (!EL.containsKey(sym)) throw new IllegalArgumentException("Element fehlt: " + sym);
                sub.put(sym, 1.0);
            } else { p[0]++; continue; }
            int s = p[0];
            while (p[0] < f.length() && Character.isDigit(f.charAt(p[0]))) p[0]++;
            double k = p[0] > s ? Double.parseDouble(f.substring(s, p[0])) : 1;
            for (Map.Entry<String, Double> e : sub.entrySet()) m.merge(e.getKey(), e.getValue() * k, Double::sum);
        }
        return m;
    }

    // ------------------------------------------------------------ die Stoffe

    private static final Map<String, Stoff> ALL = new HashMap<>();

    private static Stoff def(String key, Stoff s) { s.fertig(); ALL.put(key, s); return s; }

    static {
        String sandHint = "Typische Zusammensetzung eines Buntsandsteins, nicht am Heidelberger Stein gemessen. Die rote Farbe kommt vom Eisenoxid im Bindemittel.";
        def("sand", new Stoff("Roter Sandstein", "Das Schloss ist aus rotem Sandstein des Mittleren Buntsandsteins gebaut, der im Steinbruch am Königstuhl über der Stadt gebrochen wurde. Er besteht aus verkitteten Sandkörnern.", sandHint)
                .t("Quarz", "SiO2", 74).t("Kalifeldspat", "KAlSi3O8", 11).t("Tonmineral (Kaolinit)", "Al2Si2O5(OH)4", 9)
                .t("Hämatit (Eisenoxid)", "Fe2O3", 3).t("Porenwasser", "H2O", 3));
        def("tuff", new Stoff("Kalktuff", "Poröser Kalkstein, den Quellwasser absetzt; in den Grotten des Hortus gibt er den Wänden ihr höhlenhaftes Aussehen.", "Annahme: de Caus plante Grotten aus Tuff und Muscheln; die Zusammensetzung ist die eines üblichen Kalktuffs."
        ).t("Calcit", "CaCO3", 90).t("Quarz", "SiO2", 3).t("Tonmineral (Kaolinit)", "Al2Si2O5(OH)4", 3).t("Moos und Humus", "C6H10O5", 2).t("Wasser", "H2O", 2));
        def("marmor", new Stoff("Marmor", "Umgewandelter Kalkstein; für Tische, Becken und Standbilder verwendet. Er ist fast reiner Calcit.", "Typischer Marmor; woher der Marmor der Becken stammt, ist nicht bekannt."
        ).t("Calcit", "CaCO3", 96).t("Dolomit", "CaMg(CO3)2", 3).t("Quarz", "SiO2", 1));
        def("boden", new Stoff("Boden (Lehm und Humus)", "Verwitterter Sandstein mit Ton und Humus, wie er am Hang des Jettenbühls liegt.", "Typischer lehmiger Sandboden; der Wassergehalt schwankt mit dem Wetter."
        ).t("Quarz", "SiO2", 50).t("Tonmineral (Kaolinit)", "Al2Si2O5(OH)4", 20).t("Kalifeldspat", "KAlSi3O8", 8).t("Hämatit", "Fe2O3", 3)
                .t("Humus (vereinfacht)", "C6H10O5", 5).t("Wasser", "H2O", 14));
        def("kies", new Stoff("Kies", "Flusskies und Splitt der Wege: abgerundete Brocken von Quarz, Sandstein und etwas Kalk.", "Typischer Neckarkies."
        ).t("Quarz", "SiO2", 80).t("Kalifeldspat", "KAlSi3O8", 12).t("Hämatit", "Fe2O3", 3).t("Calcit", "CaCO3", 5));
        def("pflanze", new Stoff("Pflanzengewebe", "Gras, Blätter und Hecken bestehen zu vier Fünfteln aus Wasser; der Rest ist Zellulose, Holzstoff und Eiweiß.", "Vereinfachte Mischung aus Zellulose, Lignin und Eiweiß."
        ).t("Wasser", "H2O", 80).t("Zellulose", "C6H10O5", 13).t("Lignin (vereinfacht)", "C10H12O3", 4).t("Eiweiß (vereinfacht)", "C5H7NO2", 3));
        def("holz", new Stoff("Eichenholz", "Balken, Fässer und Schnitzwerk. Holz ist ein Gerüst aus Zellulose-Fasern, das Lignin zusammenhält.", "Trockenes Eichenholz bei etwa zehn Prozent Feuchte, vereinfachte Mischung."
        ).t("Zellulose", "C6H10O5", 40).t("Hemizellulose (Xylan)", "C5H8O4", 25).t("Lignin (vereinfacht)", "C10H12O3", 25).t("Wasser", "H2O", 10));
        def("kohle", new Stoff("Holzkohle und Brandschutt", "Verkohltes Holz aus den Bränden von 1689 und 1764: fast reiner Kohlenstoff mit etwas Asche.", "Vereinfachte Mischung."
        ).t("Kohlenstoff", "C", 88).t("Asche (Calcit)", "CaCO3", 6).t("Wasser", "H2O", 6));
        def("wasser", new Stoff("Wasser", "Quell- und Flusswasser des Neckars: Wassermoleküle, dazu ein Hauch gelöster Kalk, der die Tuffe baut.", "Vereinfacht: Wasser mit 0,05 Prozent Calciumhydrogencarbonat."
        ).t("Wasser", "H2O", 99.95).t("Calciumhydrogencarbonat", "Ca(HCO3)2", 0.05));
        def("putz", new Stoff("Kalkputz", "Gelöschter Kalk, der mit der Luft wieder zu Kalkstein abbindet und Sand umschließt. Putz der Stadthäuser und Wände.", "Typischer Kalkputz."
        ).t("Calcit", "CaCO3", 85).t("Sand (Quarz)", "SiO2", 12).t("Wasser", "H2O", 3));
        def("ziegel", new Stoff("Dachziegel", "Gebrannter Ton: Der Brand bei rund 900 Grad macht aus Lehm ein hartes Gemenge von Oxiden; das Eisen färbt ihn rot.", "Typische Ziegelzusammensetzung in Oxidform."
        ).t("Siliciumdioxid", "SiO2", 62).t("Aluminiumoxid", "Al2O3", 20).t("Eisenoxid", "Fe2O3", 8).t("Calciumoxid", "CaO", 5).t("Kaliumoxid", "K2O", 3).t("Magnesiumoxid", "MgO", 2));
        def("glas", new Stoff("Kalk-Natron-Glas", "Fensterglas der Prunkbauten: Sand, Soda und Kalk, zusammengeschmolzen.", "Typisches Kalk-Natron-Glas; Waldglas des 17. Jahrhunderts enthielt mehr Kalium."
        ).t("Siliciumdioxid", "SiO2", 72).t("Natriumoxid", "Na2O", 14).t("Calciumoxid", "CaO", 10).t("Magnesiumoxid", "MgO", 4));
        def("spiegel", new Stoff("Venezianischer Spiegel", "Glas mit einem Metallbelag auf der Rückseite; der Belag war Zinn mit Quecksilber.", "Vereinfacht: Quecksilber ist nicht in der Elementtafel dieser Lupe, der Belag steht als Zinn."
        ).t("Siliciumdioxid", "SiO2", 68).t("Natriumoxid", "Na2O", 13).t("Calciumoxid", "CaO", 9).t("Magnesiumoxid", "MgO", 5).t("Zinn", "Sn", 5));
        def("eisen", new Stoff("Schmiedeeisen", "Bänder der Fässer, Beschläge und Gitter.", "Vereinfacht: Eisen mit einem Hauch Kohlenstoff."
        ).t("Eisen", "Fe", 99.8).t("Kohlenstoff", "C", 0.2));
        def("gold", new Stoff("Blattgold", "Vergoldung von Schnitzwerk, Wappen und Orgelpfeifen: hauchdünn geschlagenes Gold.", "Vereinfacht: Gold mit etwas Kupfer, wie es zum Schlagen üblich ist."
        ).t("Gold", "Au", 96).t("Kupfer", "Cu", 4));
        def("wachs", new Stoff("Bienenwachs", "Kerzen im Saal und in den Grotten. Bienenwachs besteht vor allem aus langkettigen Estern.", "Vereinfacht als Myricylpalmitat."
        ).t("Wachsester", "C46H92O2", 100));
        def("stoff", new Stoff("Leinen und Gewebe", "Wandbehänge, Teppiche und Baldachine: Fasern aus Zellulose.", "Vereinfacht: Zellulose mit Feuchte."
        ).t("Zellulose", "C6H10O5", 92).t("Wasser", "H2O", 8));
        def("russ", new Stoff("Berußter Sandstein", "Sandstein der Ruine, den der Brand geschwärzt hat: feiner Ruß auf Quarz, Feldspat und Ton.", "Annahme des Modells: etwa ein Achtel der Masse ist Ruß."
        ).t("Quarz", "SiO2", 63).t("Kalifeldspat", "KAlSi3O8", 9).t("Tonmineral (Kaolinit)", "Al2Si2O5(OH)4", 8).t("Hämatit", "Fe2O3", 3).t("Ruß", "C", 12).t("Wasser", "H2O", 5));
        def("schutt", new Stoff("Schutt", "Brocken von Sandstein, Mörtel und Kohle am Fuß der gesprengten Türme.", "Annahme des Modells: Mischung aus Sandstein, Kalkmörtel und Brandresten."
        ).t("Quarz", "SiO2", 55).t("Kalifeldspat", "KAlSi3O8", 8).t("Tonmineral (Kaolinit)", "Al2Si2O5(OH)4", 7).t("Hämatit", "Fe2O3", 3).t("Mörtel (Calcit)", "CaCO3", 12).t("Kohle und Asche", "C", 8).t("Wasser", "H2O", 7));
    }

    /** Stoff zum Material des Modells; null, wenn keiner angegeben ist (Himmel, Licht, Tiere). */
    public static Stoff fuer(int m) {
        String k;
        switch (m) {
            case Mat.SANDSTONE: case Mat.STATUE: case Mat.PLANNED: case Mat.HERALD: case Mat.SCHNITT_STEIN: k = "sand"; break;
            case Mat.ROCK: case Mat.SINTER: case Mat.CONE: case Mat.RIM: k = "tuff"; break;
            case Mat.MARBLE: k = "marmor"; break;
            case Mat.TERRAIN: case Mat.SCHNITT_ERDE: k = "boden"; break;
            case Mat.GRAVEL: k = "kies"; break;
            case Mat.LAWN: case Mat.HEDGE: case Mat.LEAVES: case Mat.ORANGE: case Mat.NEEDLES: case Mat.SPRUCE: k = "pflanze"; break;
            case Mat.WOOD: case Mat.BARK: case Mat.WHITEBARK: case Mat.SNAG: case Mat.BOARD: case Mat.OAK: k = "holz"; break;
            case Mat.CHAR: k = "kohle"; break;
            case Mat.WATER: case Mat.RIVER: case Mat.POOL: case Mat.BASIN: k = "wasser"; break;
            case Mat.WALL: case Mat.PLASTER: k = "putz"; break;
            case Mat.ROOF: k = "ziegel"; break;
            case Mat.GLASS: k = "glas"; break;
            case Mat.MIRROR: k = "spiegel"; break;
            case Mat.IRON: k = "eisen"; break;
            case Mat.GOLD: k = "gold"; break;
            case Mat.WAX: k = "wachs"; break;
            case Mat.CLOTH: case Mat.LINEN: k = "stoff"; break;
            case Mat.SOOT: k = "russ"; break;
            case Mat.RUBBLE: k = "schutt"; break;
            default: return null;
        }
        return ALL.get(k);
    }

    public static Iterable<Stoff> alle() { return ALL.values(); }
}
