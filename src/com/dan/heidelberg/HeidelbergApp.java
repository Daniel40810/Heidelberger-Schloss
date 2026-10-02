package com.dan.heidelberg;

import com.dan.fframe.FFrame;
import com.dan.heidelberg.db.Dienst;
import com.dan.heidelberg.db.Zustand;
import com.dan.heidelberg.ui.AppIcon;
import com.dan.heidelberg.ui.ControlPanel;
import com.dan.heidelberg.ui.ScenePanel;
import com.dan.heidelberg.ui.Startbild;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Heidelberger Schloss — Rekonstruktion als animierte 3D-Szene.
 * Einstieg der Anwendung: FFrame maximiert, Szene in der Mitte, Bedienfeld rechts, Statuszeile unten.
 * Phase 10: Startbild (Zustand vom letzten Mal, Datenbank, Einrichter), Zustand beim Beenden und Protokoll der Sitzung.
 * Die Datenbank (Schema DEMO, Präfix HEI_) ist nie Voraussetzung: ohne sie läuft das Programm mit den eingebauten Werten.
 * Start ohne Startbild: -Dheidelberg.splash=false; ohne Datenbank: -Dheidelberg.nodb=true.
 */
public final class HeidelbergApp {
    static final Color BG = new Color(14, 20, 24), STATUS_BG = new Color(8, 12, 15), STATUS_INK = new Color(166, 170, 170);

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        Dienst.start();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Dienst.ereignis("FEHLER", t.getName(), String.valueOf(e));
            e.printStackTrace();
        });
        SwingUtilities.invokeLater(HeidelbergApp::open);
    }

    /** Testläufe (-Dheidelberg.day, .view, .stufe …) starten ohne Startbild und ohne gemerkten Zustand und sichern nichts. */
    private static boolean testlauf() {
        java.util.Set<String> ok = new java.util.HashSet<>(java.util.Arrays.asList("heidelberg.nodb", "heidelberg.splash", "heidelberg.db.dir", "heidelberg.state.dir", "heidelberg.panel"));
        for (Object k : System.getProperties().keySet()) {
            String n = String.valueOf(k);
            if (n.startsWith("heidelberg.") && !ok.contains(n)) return true;
        }
        return false;
    }

    /** Ohne Startbild: wartet kurz auf die Prüfung der Datenbank und nimmt den letzten Zustand. */
    private static Zustand stillLaden() {
        long ende = System.currentTimeMillis() + 8000;
        while (Dienst.lage() == Dienst.Lage.PRUEFT && System.currentTimeMillis() < ende) {
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        return Dienst.ladeLetzten();
    }

    private static void open() {
        boolean test = testlauf();
        Zustand start = null;
        if (!test) {
            if (!"false".equals(System.getProperty("heidelberg.splash"))) {
                Startbild.Wahl w = Startbild.zeigen();
                if (w != null && w.laden) start = w.zustand;
            } else {
                start = stillLaden();
            }
        }
        final Zustand beginn = start;
        FFrame f = new FFrame("Heidelberger Schloss · Rekonstruktion in 3D");
        AppIcon.install(f);
        ScenePanel scene = new ScenePanel();
        ControlPanel controls = new ControlPanel(scene);
        JLabel status = new JLabel(" ");
        status.setForeground(STATUS_INK);
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        scene.setStatusListener(status::setText);
        Dienst.kontext(scene::stage, scene::hour);
        JLabel dbStatus = new JLabel(Dienst.kurz());
        dbStatus.setForeground(STATUS_INK);
        dbStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dbStatus.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        Dienst.beiAenderung(() -> dbStatus.setText(Dienst.kurz()));

        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(scene, BorderLayout.CENTER);
        JPanel side = controls;
        root.add(side, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(STATUS_BG);
        south.add(status, BorderLayout.CENTER);
        south.add(dbStatus, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        scene.setCinemaListener(on -> {
            side.setVisible(!on);
            south.setVisible(!on);
            root.revalidate();
            scene.requestFocusInWindow();
        });

        f.setPreferredFrameSize(new Dimension(1480, 900));
        f.setSize(1480, 900);
        f.setLocationRelativeTo(null);
        f.setResizable(true);
        f.setVisible(true);
        startMaximized(f);
        scene.setOnReady(() -> {
            Dienst.sitzungStart(scene.getWidth(), scene.getHeight(), scene.stage(), beginn != null);
            if (beginn != null) controls.restore(beginn);
        });
        if (!test) {
            f.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) { beenden(controls, scene); }
            });
            Runtime.getRuntime().addShutdownHook(new Thread(() -> beenden(controls, scene), "Heidelberg-Ende"));
        }
        scene.start();
        scene.requestFocusInWindow();
    }

    private static void beenden(ControlPanel controls, ScenePanel scene) {
        Zustand z = null;
        try { z = controls.capture(); } catch (RuntimeException e) { Dienst.ereignis("WARN", "Zustand", String.valueOf(e)); }
        Dienst.beenden(z, scene.leistung());
    }

    /**
     * Startet maximiert (ExtendedState MAXIMIZED_BOTH), ohne die Taskleiste zu verdecken. Die normale
     * Größe von 1480 × 900 bleibt als Rückfall erhalten; die Schaltfläche „Wiederherstellen“ des FFrame
     * kennt den Zustand und stellt die normale Größe wieder her. Übernommen aus Caracalla und Semiramis.
     */
    private static void startMaximized(FFrame f) {
        java.awt.Rectangle normal = f.getBounds();
        f.setMaximizedBounds(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        f.setExtendedState(f.getExtendedState() | java.awt.Frame.MAXIMIZED_BOTH);
        try {
            // Die Titelleiste des FFrame führt einen eigenen Maximiert-Zustand; ihn angleichen
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                Object bar = fd.get(f);
                Class<?> bc = bar.getClass();
                java.lang.reflect.Field mx = bc.getDeclaredField("maximized"), rb = bc.getDeclaredField("restoreBounds"),
                        btn = bc.getDeclaredField("btnMaximize");
                mx.setAccessible(true); rb.setAccessible(true); btn.setAccessible(true);
                mx.setBoolean(bar, true);
                rb.set(bar, normal);
                Object icon = btn.get(bar);
                icon.getClass().getMethod("setType", com.dan.ficons.FIconType.class).invoke(icon, com.dan.ficons.FIconType.RESTORE);
                ((javax.swing.JComponent) icon).setToolTipText("Wiederherstellen");
                ((java.awt.Component) icon).addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                        SwingUtilities.invokeLater(() -> {
                            if ((f.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0) {
                                f.setExtendedState(f.getExtendedState() & ~java.awt.Frame.MAXIMIZED_BOTH);
                                f.setBounds(normal);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("FFrame-Titelleiste nicht angeglichen: " + e);
        }
    }

    private HeidelbergApp() { }
}
