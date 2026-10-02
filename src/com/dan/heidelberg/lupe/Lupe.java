package com.dan.heidelberg.lupe;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 9, Stein-Lupe: Ein Klick auf eine Fläche im Bild zeigt, woraus der Stoff besteht (Bestandteile mit Massenanteilen,
 * Elemente nach Anteil) und dazu das Atommodell des gewählten Elements: Kern aus Protonen und Neutronen, Schalen,
 * Elektronen auf ihren Bahnen. Der Atomzeichner stammt aus dem Projekt ATOMMODEL (AtomRenderer), hier ohne Datenbank.
 * Zeichnet eine Karte über das Bild; ein Klick auf ein Element der Liste wählt es, sonst wechselt es alle sechs Sekunden.
 */
public final class Lupe {
    private Stoffe.Stoff stoff;
    private int sel;
    private double t0, tSel;
    private int markX, markY;
    private boolean active;

    private final Camera cam = new Camera();
    private final AtomRenderer renderer = new AtomRenderer(cam);
    private final AtomStyle style = AtomStyle.deepSpace();
    private AtomScene scene;
    private int sceneEl = -1;
    private BufferedImage atomImg;

    private int cardX, cardY, cardW, cardH;
    private final List<int[]> chips = new ArrayList<>();

    public boolean active() { return active; }

    public void setActive(boolean on) { active = on; if (!on) stoff = null; }

    /** Zeigt den Stoff an der Klickstelle (Bildpunkt). */
    public void show(Stoffe.Stoff s, int px, int py, double time) {
        stoff = s; sel = 0; t0 = time; tSel = time; markX = px; markY = py; sceneEl = -1;
    }

    public void clear() { stoff = null; }

    /** Element Nummer i der Liste wählen (für die Prüfbilder). */
    public void select(int i, double time) { sel = i; sceneEl = -1; tSel = time; }

    public Stoffe.Stoff stoff() { return stoff; }

    /** Liegt der Bildpunkt auf der Karte? */
    public boolean hit(int x, int y) {
        return stoff != null && x >= cardX && y >= cardY && x <= cardX + cardW && y <= cardY + cardH;
    }

    /** Klick auf die Karte: Element wählen. */
    public void click(int x, int y, double time) {
        for (int i = 0; i < chips.size(); i++) {
            int[] c = chips.get(i);
            if (x >= c[0] && x <= c[0] + c[2] && y >= c[1] && y <= c[1] + c[3]) { sel = i; sceneEl = -1; tSel = time; return; }
        }
    }

    private static Color a(Color c, int al) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), al); }

    public void paint(Graphics2D g, int W, int H, double time) {
        if (!active || stoff == null) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Stoffe.Stoff s = stoff;
        int n = Math.min(6, s.elemente.size());
        // Element wechselt von selbst, solange niemand klickt
        if (time - tSel > 6) { sel = (sel + 1) % n; tSel = time; sceneEl = -1; }
        sel = Math.min(sel, n - 1);
        cardW = Math.min(640, W - 40);
        cardH = 352;
        cardX = W - cardW - 14;
        cardY = 54;
        if (cardH > H - 80) cardH = H - 80;
        // Lupe auf dem Bild und Leitlinie
        g.setColor(new Color(255, 255, 255, 220));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(markX - 14, markY - 14, 28, 28));
        g.draw(new Line2D.Double(markX + 10, markY + 10, markX + 20, markY + 20));
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(markX + 20, markY + 20, Math.max(cardX, Math.min(cardX + cardW, markX + 20)), cardY + cardH / 2.0));
        // Karte
        g.setColor(new Color(8, 14, 22, 232));
        g.fillRoundRect(cardX, cardY, cardW, cardH, 14, 14);
        g.setColor(new Color(120, 170, 220, 120));
        g.drawRoundRect(cardX, cardY, cardW, cardH, 14, 14);
        int x = cardX + 16, y = cardY + 30;
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(new Color(240, 214, 150));
        g.drawString("Stein-Lupe  ·  " + s.titel, x, y);
        // Text
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        int fullW = cardW - 32;
        final int as = Math.min(170, cardW / 4);
        int textW = fullW - as - 18;
        y += 10;
        g.setColor(new Color(214, 224, 236));
        y = wrap(g, fm, s.text, x, y + 14, textW, 16);
        // Bestandteile
        y += 8;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(150, 204, 238));
        g.drawString("Bestandteile (Massenanteil)", x, y + 12);
        y += 16;
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        int barX = x + 140, barW = 100;
        int rows = Math.min(6, s.teile.size());
        for (int i = 0; i < rows; i++) {
            Stoffe.Teil p = s.teile.get(i);
            y += 17;
            g.setColor(new Color(214, 224, 236));
            g.drawString(p.name, x, y);
            g.setColor(new Color(40, 56, 74));
            g.fillRect(barX, y - 10, barW, 9);
            Color mc = firstColor(p.formel);
            g.setColor(mc);
            g.fillRect(barX, y - 10, (int) Math.max(1, barW * p.anteil / 100.0), 9);
            g.setColor(new Color(214, 224, 236));
            g.drawString(fmtPct(p.anteil), barX + barW + 8, y);
            g.setColor(new Color(150, 170, 190));
            g.drawString(p.formel, barX + barW + 62, y);
        }
        // Elemente als Kacheln
        y += 14;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(150, 204, 238));
        g.drawString("Elemente (Massenanteil, gerechnet aus den Formeln) – anklicken", x, y + 12);
        chips.clear();
        int cw = (fullW - 5 * 6) / 6, chH = 46;
        int cy = Math.max(y + 20, cardY + 42 + as + 50);
        for (int i = 0; i < n; i++) {
            Stoffe.Elem e = s.elemente.get(i);
            int cx = x + i * (cw + 6);
            chips.add(new int[]{cx, cy, cw, chH});
            g.setColor(i == sel ? a(e.color, 70) : new Color(30, 44, 60));
            g.fillRoundRect(cx, cy, cw, chH, 8, 8);
            g.setColor(i == sel ? e.color : a(e.color, 120));
            g.setStroke(new BasicStroke(i == sel ? 2f : 1f));
            g.drawRoundRect(cx, cy, cw, chH, 8, 8);
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(Color.WHITE);
            g.drawString(e.sym, cx + 7, cy + 21);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(214, 224, 236));
            double pr = s.prozent.get(i);
            g.drawString(pr >= 10 ? String.format(Locale.GERMANY, "%.0f %%", pr) : pr >= 0.1 ? String.format(Locale.GERMANY, "%.1f %%", pr) : String.format(Locale.GERMANY, "%.2f %%", pr), cx + 7, cy + 38);
            g.setColor(new Color(150, 170, 190));
            g.drawString(String.valueOf(e.z), cx + cw - 22, cy + 16);
        }
        // Atommodell rechts oben in der Karte
        Stoffe.Elem e = s.elemente.get(sel);
        int ax = cardX + cardW - as - 16, ay = cardY + 44;
        drawAtom(g, e, ax, ay, as, time);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        g.drawString(e.name + " (" + e.sym + ")", ax, ay + as + 14);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(170, 190, 210));
        g.drawString(e.z + " Protonen, " + e.neutrons() + " Neutronen", ax, ay + as + 28);
        g.drawString(e.z + " Elektronen, Schalen " + e.shells, ax, ay + as + 41);
        // Hinweis unten
        g.setFont(new Font("SansSerif", Font.ITALIC, 11));
        g.setColor(new Color(160, 176, 192));
        wrap(g, g.getFontMetrics(), "Hinweis: " + s.hinweis, x, cardY + cardH - 30, cardW - 32, 14);
    }

    private static String fmtPct(double v) {
        return v >= 10 ? String.format(Locale.GERMANY, "%.0f %%", v) : v >= 1 ? String.format(Locale.GERMANY, "%.1f %%", v) : String.format(Locale.GERMANY, "%.2f %%", v);
    }

    private static Color firstColor(String formel) {
        for (String sym : new String[]{"Fe", "Ca", "Si", "Al", "K", "Na", "Mg", "Au", "Cu", "Sn", "C", "H", "O"}) {
            if (formel.contains(sym)) {
                if (sym.equals("C") && (formel.contains("Ca") || formel.contains("Cl") || formel.contains("Cu"))) { if (!formel.replace("Ca", "").replace("Cl", "").replace("Cu", "").contains("C")) continue; }
                if (sym.equals("H") && formel.equals("H2O")) return new Color(70, 140, 220);
                if (sym.equals("O") && formel.equals("H2O")) return new Color(70, 140, 220);
                return a(Stoffe.element(sym).color, 255);
            }
        }
        return new Color(150, 170, 190);
    }

    private void drawAtom(Graphics2D g, Stoffe.Elem e, int x, int y, int size, double time) {
        if (sceneEl != e.z || scene == null) {
            scene = new AtomScene(e.z, e.neutrons(), AtomScene.parseShells(e.shells));
            sceneEl = e.z;
        }
        if (atomImg == null || atomImg.getWidth() != size) atomImg = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = atomImg.createGraphics();
        ig.setComposite(java.awt.AlphaComposite.Clear);
        ig.fillRect(0, 0, size, size);
        ig.setComposite(java.awt.AlphaComposite.SrcOver);
        ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double tt = time - t0;
        cam.setYaw(0.5 * tt);
        cam.setPitch(-0.25);
        cam.setDistance(30);
        renderer.render(ig, size, size, scene, style, tt);
        ig.dispose();
        g.drawImage(atomImg, x, y, null);
        g.setColor(new Color(90, 130, 170, 100));
        g.drawRoundRect(x, y, size, size, 10, 10);
    }

    private static int wrap(Graphics2D g, FontMetrics fm, String text, int x, int y, int w, int lh) {
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && fm.stringWidth(cur + " " + word) > w) { g.drawString(cur.toString(), x, y); y += lh; cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) { g.drawString(cur.toString(), x, y); y += lh; }
        return y;
    }
}
