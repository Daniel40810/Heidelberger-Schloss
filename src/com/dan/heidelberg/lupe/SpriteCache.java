package com.dan.heidelberg.lupe;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vorgerenderte Kugeln und Leuchtpunkte. Eine schattierte Kugel mit drei
 * Verlaeufen kostet einige hundert Mikrosekunden; als Bild nur einen Kopiervorgang.
 * Radien werden auf halbe Pixel gerundet, damit der Cache klein bleibt.
 */
final class SpriteCache {

    private static final int MAX_ENTRIES = 600;

    private final Map<String, BufferedImage> cache =
            new LinkedHashMap<String, BufferedImage>(256, 0.75f, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) {
                    return size() > MAX_ENTRIES;
                }
            };

    void clear() {
        cache.clear();
    }

    /** Schattierte Kugel; Bildmitte = Kugelmitte. */
    BufferedImage sphere(Color base, Color env, double radius, double gloss) {
        double r = Math.max(0.5, Math.round(radius * 2.0) / 2.0);
        String key = "S" + base.getRGB() + ':' + env.getRGB() + ':' + r + ':' + gloss;
        BufferedImage img = cache.get(key);
        if (img == null) {
            img = paintSphere(base, env, r, gloss);
            cache.put(key, img);
        }
        return img;
    }

    /** Weicher Leuchtpunkt (Elektronen-Halo, Kernleuchten). */
    BufferedImage glow(Color inner, Color outer, double radius, float innerAlpha, float midAlpha) {
        double r = Math.max(1.0, Math.round(radius));
        String key = "G" + inner.getRGB() + ':' + outer.getRGB() + ':' + r + ':' + innerAlpha + ':' + midAlpha;
        BufferedImage img = cache.get(key);
        if (img == null) {
            int size = (int) Math.ceil(r * 2) + 2;
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double c = size / 2.0;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(c, c), (float) r,
                    new float[] {0f, 0.4f, 1f},
                    new Color[] {Colors.withAlpha(inner, innerAlpha), Colors.withAlpha(outer, midAlpha),
                                 Colors.withAlpha(outer, 0f)}));
            g.fill(new Ellipse2D.Double(c - r, c - r, 2 * r, 2 * r));
            g.dispose();
            cache.put(key, img);
        }
        return img;
    }

    /** Kernleuchten: weisses Zentrum, Kernfarbe, auslaufend in die Glasfarbe. */
    BufferedImage coreGlow(Color core, Color glass, double radius) {
        double r = Math.max(1.0, Math.round(radius));
        String key = "C" + core.getRGB() + ':' + glass.getRGB() + ':' + r;
        BufferedImage img = cache.get(key);
        if (img == null) {
            int size = (int) Math.ceil(r * 2) + 2;
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double c = size / 2.0;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(c, c), (float) r,
                    new float[] {0f, 0.28f, 0.55f, 1f},
                    new Color[] {Colors.withAlpha(Color.WHITE, 0.85f), Colors.withAlpha(core, 0.55f),
                                 Colors.withAlpha(glass, 0.18f), Colors.withAlpha(glass, 0f)}));
            g.fill(new Ellipse2D.Double(c - r, c - r, 2 * r, 2 * r));
            g.dispose();
            cache.put(key, img);
        }
        return img;
    }

    private static BufferedImage paintSphere(Color base, Color env, double r, double gloss) {
        int size = (int) Math.ceil(r * 2) + 2;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double cx = size / 2.0, cy = size / 2.0;
        Ellipse2D circle = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);

        // Grundverlauf, Lichtquelle oben links
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx, cy), (float) r,
                new Point2D.Double(cx - r * 0.38, cy - r * 0.42),
                new float[] {0f, 0.30f, 0.78f, 1f},
                new Color[] {Colors.brighter(base, 0.55f), base, Colors.shade(base, 0.52), Colors.shade(base, 0.30)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(circle);

        // Umgebungslicht unten rechts
        Shape oldClip = g.getClip();
        g.clip(circle);
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx + r * 0.55, cy + r * 0.60), (float) (r * 0.95),
                new float[] {0f, 1f},
                new Color[] {Colors.withAlpha(env, 0.45f), Colors.withAlpha(env, 0f)}));
        g.fill(circle);
        g.setClip(oldClip);

        // Glanzpunkt
        double hr = r * 0.34;
        double hx = cx - r * 0.36, hy = cy - r * 0.40;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(hx, hy), (float) hr,
                new float[] {0f, 1f},
                new Color[] {Colors.withAlpha(Color.WHITE, (float) (0.85 * gloss)), Colors.withAlpha(Color.WHITE, 0f)}));
        g.fill(new Ellipse2D.Double(hx - hr, hy - hr, 2 * hr, 2 * hr));
        g.dispose();
        return img;
    }
}
