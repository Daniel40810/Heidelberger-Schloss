package com.dan.heidelberg.ui;

import com.dan.fbutton.FButton;
import com.dan.heidelberg.camera.Viewpoint;
import com.dan.heidelberg.castle.Ruin;
import com.dan.heidelberg.db.Dienst;
import com.dan.heidelberg.db.Zustand;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Das Startbild vor der Szene: Stand der Datenbank, letzter Zustand, und die Wahl „Weiter, wo du aufgehört hast“ oder „Neu
 * beginnen“. Ist alles klar, läuft ein Zähler und das Programm macht allein weiter; bei fehlendem Passwort oder nicht
 * eingerichteter Datenbank wartet es auf einen Klick, damit man den Einrichter sieht.
 */
public final class Startbild extends JDialog {
    /** Die Wahl: laden = Zustand wiederherstellen (zustand ist dann nicht null). */
    public static final class Wahl {
        public final boolean laden;
        public final Zustand zustand;
        Wahl(boolean laden, Zustand zustand) { this.laden = laden; this.zustand = zustand; }
    }

    private static final int SEKUNDEN = 6;
    private final JLabel dbLine = new JLabel(), stateLine = new JLabel(), noteLine = new JLabel(), countLine = new JLabel(" ");
    private final JPanel pwRow = new JPanel(new BorderLayout(8, 0));
    private final JPasswordField pw = new JPasswordField();
    private final FButton weiter = new FButton("Weiter, wo du aufgehört hast"), neu = new FButton("Neu beginnen"), einrichten = new FButton("Einrichten …"), ohne = new FButton("Ohne Datenbank starten");
    private volatile Zustand letzter;
    private volatile boolean letzterGeladen;
    private Wahl wahl;
    private Timer zaehler;
    private int rest = SEKUNDEN;
    private boolean angehalten;
    private AWTEventListener aufmerken;

    private Startbild() {
        super((java.awt.Frame) null, "Heidelberger Schloss", true);
        setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout(0, 14)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0, 0, new Color(18, 28, 34), 0, getHeight(), new Color(10, 14, 18)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ControlPanel.ACCENT);
                g2.fillRect(0, 0, getWidth(), 3);
            }
        };
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(48, 62, 70)), BorderFactory.createEmptyBorder(22, 28, 20, 28)));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("HEIDELBERGER SCHLOSS");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(ControlPanel.INK);
        JLabel sub = new JLabel("Rekonstruktion um 1619 in 3D · Hortus Palatinus · Zeitstufen bis heute");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(ControlPanel.ACCENT);
        top.add(title);
        top.add(sub);
        root.add(top, BorderLayout.NORTH);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        for (JLabel l : new JLabel[]{dbLine, stateLine, noteLine, countLine}) {
            l.setForeground(ControlPanel.INK);
            l.setFont(new Font("SansSerif", Font.PLAIN, 13));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        }
        noteLine.setForeground(ControlPanel.MUTED);
        noteLine.setFont(new Font("SansSerif", Font.PLAIN, 11));
        countLine.setForeground(ControlPanel.ACCENT);
        mid.add(dbLine);
        mid.add(stateLine);
        mid.add(noteLine);
        pwRow.setOpaque(false);
        pwRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pwRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        pw.setBackground(new Color(8, 12, 15));
        pw.setForeground(ControlPanel.INK);
        pw.setCaretColor(ControlPanel.INK);
        pw.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(60, 78, 88)), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        FButton conn = new FButton("Verbinden");
        conn.addActionListener(e -> verbinden());
        pw.addActionListener(e -> verbinden());
        pwRow.add(pw, BorderLayout.CENTER);
        pwRow.add(conn, BorderLayout.EAST);
        mid.add(pwRow);
        mid.add(countLine);
        root.add(mid, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.GridLayout(0, 2, 10, 10));
        south.setOpaque(false);
        weiter.addActionListener(e -> fertig(true));
        neu.addActionListener(e -> fertig(false));
        einrichten.addActionListener(e -> {
            stoppen();
            new EinrichterDialog(this, true).setVisible(true);
            neuLaden();
        });
        ohne.addActionListener(e -> {
            Dienst.abschalten();
            letzter = Zustand.laden(new java.io.File(Dienst.lokalerOrdner(), "zustand.properties"));
            letzterGeladen = true;
            fertig(letzter != null);
        });
        south.add(weiter);
        south.add(neu);
        south.add(einrichten);
        south.add(ohne);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setPreferredSize(new Dimension(700, 420));
        pack();
        setLocationRelativeTo(null);
        getRootPane().setDefaultButton(null);

        Dienst.beiAenderung(this::aktualisieren);
        aufmerken = ev -> {
            if (ev instanceof java.awt.event.MouseEvent && ev.getID() != java.awt.event.MouseEvent.MOUSE_PRESSED) return;
            if (ev instanceof KeyEvent && ((KeyEvent) ev).getID() == KeyEvent.KEY_PRESSED) {
                int k = ((KeyEvent) ev).getKeyCode();
                if (k == KeyEvent.VK_ENTER && !pwRow.isVisible()) { fertig(weiter.isEnabled()); return; }
            }
            stoppen();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(aufmerken, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        aktualisieren();
        neuLaden();
    }

    /** Zeigt das Startbild und wartet auf die Wahl. */
    public static Wahl zeigen() {
        Startbild s = new Startbild();
        s.zaehler = new Timer(1000, e -> s.tick());
        s.zaehler.start();
        s.setVisible(true);
        return s.wahl;
    }

    private void verbinden() {
        char[] p = pw.getPassword();
        pw.setText("");
        if (p.length == 0) return;
        Dienst.verbinden(p);
        rest = SEKUNDEN;
        angehalten = false;
    }

    private void neuLaden() {
        letzterGeladen = false;
        Thread t = new Thread(() -> {
            while (Dienst.lage() == Dienst.Lage.PRUEFT) {
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
            }
            Zustand z = Dienst.lage() == Dienst.Lage.KEIN_PASSWORT ? Zustand.laden(new java.io.File(Dienst.lokalerOrdner(), "zustand.properties")) : Dienst.ladeLetzten();
            letzter = z;
            letzterGeladen = true;
            SwingUtilities.invokeLater(this::aktualisieren);
        }, "Heidelberg-Startbild");
        t.setDaemon(true);
        t.start();
    }

    private static String beschreibe(Zustand z) {
        String wann = z.gespeichertMs <= 0 ? "" : DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMANY).format(Instant.ofEpochMilli(z.gespeichertMs).atZone(ZoneId.systemDefault())) + " · ";
        int st = Math.max(0, Math.min(Ruin.STAGE_NAMES.length - 1, z.ganz("STUFE")));
        int vp = Math.max(0, Math.min(Viewpoint.NAMES.length - 1, z.ganz("BLICKPUNKT")));
        return wann + Ruin.STAGE_NAMES[st] + " · " + Viewpoint.NAMES[vp];
    }

    private void aktualisieren() {
        Dienst.Lage l = Dienst.lage();
        dbLine.setText(Dienst.kurz());
        boolean ok = l == Dienst.Lage.BEREIT;
        dbLine.setForeground(ok ? new Color(150, 210, 160) : l == Dienst.Lage.PRUEFT ? ControlPanel.MUTED : new Color(230, 190, 120));
        String m = Dienst.meldung();
        StringBuilder n = new StringBuilder();
        if (!m.isEmpty()) n.append(m);
        if (ok && !Dienst.anpassung().isEmpty()) n.append(n.length() > 0 ? " · " : "").append(Dienst.anpassung());
        noteLine.setText("<html><body style='width:620px'>" + (n.length() == 0 ? " " : n.toString().replace("&", "&amp;").replace("<", "&lt;")) + "</body></html>");
        pwRow.setVisible(l == Dienst.Lage.KEIN_PASSWORT);
        Zustand z = letzter;
        stateLine.setText(!letzterGeladen ? "Letzter Zustand: wird gesucht …" : z == null ? "Noch kein gespeicherter Zustand." : "Letzter Zustand: " + beschreibe(z));
        weiter.setEnabled(z != null);
        einrichten.setVisible(l == Dienst.Lage.NICHT_EINGERICHTET || l == Dienst.Lage.BEREIT);
        einrichten.setText(l == Dienst.Lage.BEREIT ? "Einrichter prüfen …" : "Einrichten …");
        ohne.setVisible(l != Dienst.Lage.AUS && l != Dienst.Lage.KEINE_KONFIG);
        revalidate();
        repaint();
        if (pwRow.isVisible()) pw.requestFocusInWindow();
    }

    private boolean laeuft() {
        Dienst.Lage l = Dienst.lage();
        return !angehalten && letzterGeladen && (l == Dienst.Lage.BEREIT || l == Dienst.Lage.OFFLINE || l == Dienst.Lage.AUS || l == Dienst.Lage.KEINE_KONFIG);
    }

    private void tick() {
        if (!laeuft()) { countLine.setText(angehalten ? " " : " "); rest = SEKUNDEN; return; }
        rest--;
        countLine.setText(rest > 0 ? "Weiter in " + rest + " s (jede Eingabe hält an)" : " ");
        if (rest <= 0) fertig(letzter != null);
    }

    private void stoppen() {
        angehalten = true;
        countLine.setText(" ");
    }

    private void fertig(boolean laden) {
        if (wahl != null) return;
        Zustand z = letzter;
        wahl = new Wahl(laden && z != null, laden ? z : null);
        if (zaehler != null) zaehler.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(aufmerken);
        dispose();
    }
}
