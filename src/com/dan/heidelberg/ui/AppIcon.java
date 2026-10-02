package com.dan.heidelberg.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol, gemalt statt geladen: das Schloss am Hang im Abendlicht, davor der Neckar mit
 * der Alten Brücke. Liefert alle
 * üblichen Größen für Taskleiste, Titel und Alt+Tab.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(paint(s));
        return l;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }

    /** Malt das Symbol in der Kantenlänge s (quadratisch, mit Alpha). */
    public static BufferedImage paint(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(s / 256.0, s / 256.0);
        boolean small = s <= 32;      // grobe Fassung: kräftige Flächen, keine Feinheiten
        boolean tiny = s <= 20;
        RoundRectangle2D tile = new RoundRectangle2D.Double(6, 6, 244, 244, 58, 58);
        // Abendhimmel
        g.setPaint(new GradientPaint(0, 6, new Color(28, 46, 104), 0, 188, new Color(248, 176, 100)));
        g.fill(tile);
        g.setClip(tile);
        // Sonne hinter dem Schloss
        g.setPaint(new RadialGradientPaint(178, 100, 78, new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(255, 246, 206, 255), new Color(255, 214, 140, 170), new Color(255, 190, 120, 0)}));
        g.fill(new Ellipse2D.Double(98, 20, 160, 160));
        // Hang mit Wald (Königstuhl im Rücken)
        g.setColor(new Color(36, 58, 48));
        Path2D hill = new Path2D.Double();
        hill.moveTo(6, 128);
        hill.curveTo(60, 112, 100, 92, 150, 96);
        hill.curveTo(200, 100, 232, 114, 250, 132);
        hill.lineTo(250, 250); hill.lineTo(6, 250); hill.closePath();
        g.fill(hill);
        // Rotes Sandsteinschloss: Dicker Turm (links, Ruine), Ottheinrichsbau (Mitte, Volutengiebel),
        // Friedrichsbau (rechts, steiles Dach mit Türmchen)
        Color stein = new Color(176, 92, 70), steinHell = new Color(214, 126, 92), dach = new Color(104, 50, 44);
        g.setPaint(new GradientPaint(40, 0, steinHell, 216, 0, stein));
        g.fill(new Rectangle2D.Double(36, 138, 184, 38));                  // Mauer
        // Dicker Turm
        g.setPaint(new GradientPaint(40, 0, steinHell, 104, 0, new Color(150, 76, 62)));
        Path2D turm = new Path2D.Double();
        turm.moveTo(40, 176); turm.lineTo(40, 98); turm.lineTo(56, 104); turm.lineTo(66, 88);
        turm.lineTo(80, 100); turm.lineTo(92, 92); turm.lineTo(102, 104); turm.lineTo(102, 176); turm.closePath();
        g.fill(turm);
        // Ottheinrichsbau mit gestuftem Giebel
        g.setPaint(new GradientPaint(0, 96, steinHell, 0, 176, stein));
        Path2D otto = new Path2D.Double();
        otto.moveTo(108, 176); otto.lineTo(108, 112); otto.lineTo(120, 112); otto.lineTo(120, 100);
        otto.lineTo(132, 100); otto.lineTo(132, 90); otto.lineTo(146, 90); otto.lineTo(146, 100);
        otto.lineTo(158, 100); otto.lineTo(158, 112); otto.lineTo(170, 112); otto.lineTo(170, 176); otto.closePath();
        g.fill(otto);
        // Friedrichsbau mit steilem Dach
        g.setColor(stein);
        g.fill(new Rectangle2D.Double(176, 108, 40, 68));
        g.setColor(dach);
        Path2D roof = new Path2D.Double();
        roof.moveTo(172, 110); roof.lineTo(196, 62); roof.lineTo(220, 110); roof.closePath();
        g.fill(roof);
        if (!tiny) {
            g.setColor(new Color(196, 110, 84));
            g.fill(new Rectangle2D.Double(194, 46, 4, 20));                // Wetterfahne
        }
        if (!small) {
            // Fenster im Abendlicht
            g.setColor(new Color(255, 218, 140));
            for (int i = 0; i < 2; i++) g.fill(new Rectangle2D.Double(54 + i * 22, 118, 8, 14));
            for (int r = 0; r < 2; r++) for (int i = 0; i < 3; i++)
                g.fill(new Rectangle2D.Double(114 + i * 18, 118 + r * 24, 8, 13));
            for (int r = 0; r < 2; r++) for (int i = 0; i < 2; i++)
                g.fill(new Rectangle2D.Double(184 + i * 16, 118 + r * 24, 8, 13));
            // Dachfirst und Fugen
            g.setColor(new Color(70, 30, 30, 140));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Line2D.Double(196, 66, 196, 108));
        }
        // Neckar mit Spiegelung
        g.setPaint(new GradientPaint(0, 176, new Color(246, 172, 100), 0, 250, new Color(30, 58, 102)));
        g.fill(new Rectangle2D.Double(6, 176, 244, 74));
        if (!small) {
            g.setColor(new Color(255, 236, 190, 130));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < 4; i++) {
                int y = 192 + i * 13;
                g.draw(new Line2D.Double(120 - i * 8, y, 206 + i * 6, y));
            }
        }
        // Alte Brücke: Fahrbahn und Bögen
        g.setColor(new Color(92, 48, 44));
        g.fill(new Rectangle2D.Double(6, small ? 170 : 172, 118, small ? 12 : 8));
        if (!tiny) {
            g.setColor(new Color(246, 172, 100));
            for (int i = 0; i < 3; i++) g.fill(new Arc2D.Double(16 + i * 36, 176, 24, small ? 12 : 14, 0, 180, Arc2D.PIE));
        }
        g.setClip(null);
        if (!small) {
            g.setColor(new Color(236, 192, 100));
            g.setStroke(new BasicStroke(5f));
            g.draw(new RoundRectangle2D.Double(8.5, 8.5, 239, 239, 55, 55));
        }
        g.dispose();
        return img;
    }
}
