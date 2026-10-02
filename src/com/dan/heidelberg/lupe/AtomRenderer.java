package com.dan.heidelberg.lupe;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Java2D-Renderer fuer das Atommodell.
 * <ul>
 *   <li>Hintergrund, Glaskoerper und Glasrand haengen nicht von der Drehung ab
 *       und werden als Ebenen zwischengespeichert.</li>
 *   <li>Kugeln kommen als vorgerenderte Sprites aus dem {@link SpriteCache}.</li>
 *   <li>Bahnsegmente und Kugeln werden gemeinsam nach Tiefe sortiert
 *       (Maleralgorithmus), damit Baender korrekt vor und hinter dem Kern liegen.</li>
 * </ul>
 */
public final class AtomRenderer {

    /** Segmente je Bahn. */
    private static final int ORBIT_SEGMENTS = 96;
    /** Tiefenvorsprung des Glanzstreifens vor dem Drahtkoerper (Welteinheiten). */
    private static final double WIRE_SHINE_LIFT = 0.8;
    /** Tiefenvorsprung der Elektronen vor ihrer Bahn. */
    private static final double ELECTRON_LIFT = 1.0;

    private final Camera camera;
    private final SpriteCache sprites = new SpriteCache();

    private BufferedImage backLayer;
    private BufferedImage frontLayer;
    private String layerKey;
    private AtomStyle spriteStyle;

    private final List<Drawable> items = new ArrayList<Drawable>(1024);

    /** Etwas, das in Tiefenreihenfolge gezeichnet wird. */
    private abstract static class Drawable {
        final double depth;
        Drawable(double depth) {
            this.depth = depth;
        }
        abstract void draw(Graphics2D g);
    }

    private static final Comparator<Drawable> FAR_FIRST = new Comparator<Drawable>() {
        @Override
        public int compare(Drawable a, Drawable b) {
            return Double.compare(b.depth, a.depth);
        }
    };

    public AtomRenderer(Camera camera) {
        this.camera = camera;
    }

    /** Standbild einer Szene; t bewegt die Elektronen. */
    public void render(Graphics2D g, int width, int height, AtomScene scene, AtomStyle style, double time) {
        render(g, width, height, scene == null ? null : AtomFrame.of(scene, time), style);
    }

    /** Zeichnet ein komplettes Bild aus einer Momentaufnahme. */
    public void render(Graphics2D g, int width, int height, AtomFrame frame, AtomStyle style) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        camera.setViewport(width, height);
        if (spriteStyle != style) {
            sprites.clear();
            spriteStyle = style;
        }
        ensureLayers(g.getDeviceConfiguration(), width, height, style);

        g.drawImage(backLayer, 0, 0, null);
        if (frame == null) {
            return;
        }
        paintCoreGlow(g, frame, style);

        items.clear();
        addNucleons(frame, style);
        addOrbits(frame, style);
        addElectrons(frame, style);
        Collections.sort(items, FAR_FIRST);
        for (Drawable d : items) {
            d.draw(g);
        }
        items.clear();
        g.drawImage(frontLayer, 0, 0, null);
    }

    // ==================================================================
    // Zwischengespeicherte Ebenen
    // ==================================================================

    private void ensureLayers(GraphicsConfiguration gc, int w, int h, AtomStyle style) {
        String key = w + "x" + h + ":" + System.identityHashCode(style) + ":" + camera.getDistance()
                   + ":" + camera.getFovDeg();
        if (key.equals(layerKey)) {
            return;
        }
        layerKey = key;
        backLayer = gc != null ? gc.createCompatibleImage(w, h, Transparency.OPAQUE)
                               : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        frontLayer = gc != null ? gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
                                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D b = backLayer.createGraphics();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintBackground(b, w, h, style);
        paintGlassBack(b, style);
        b.dispose();
        Graphics2D f = frontLayer.createGraphics();
        f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        f.setComposite(AlphaComposite.Clear);
        f.fillRect(0, 0, w, h);
        f.setComposite(AlphaComposite.SrcOver);
        paintGlassFront(f, style);
        f.dispose();
    }

    private void paintBackground(Graphics2D g, int w, int h, AtomStyle style) {
        float r = (float) Math.max(w, h) * 0.75f;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.55, h * 0.42), r,
                new float[] {0f, 1f},
                new Color[] {style.backgroundInner, style.backgroundOuter}));
        g.fillRect(0, 0, w, h);

        if (style.backgroundLines) {
            Composite old = g.getComposite();
            g.setStroke(new BasicStroke(1.2f));
            for (int i = 0; i < 14; i++) {
                float a = 0.10f + 0.25f * (i % 3) / 2f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                g.setColor(new Color(40, 150, 230));
                Path2D p = new Path2D.Double();
                double y0 = h * (0.98 - i * 0.012);
                p.moveTo(-10, y0);
                p.curveTo(w * 0.35, y0 - h * 0.25, w * 0.55, y0 - h * (0.05 + i * 0.02),
                          w * 0.82, h * (0.22 + i * 0.012));
                g.draw(p);
            }
            g.setComposite(old);
        }
    }

    private Ellipse2D glassOutline() {
        Vec3 c = camera.toView(Vec3.ZERO);
        double r = AtomScene.GLASS_RADIUS * camera.scaleAt(c);
        return new Ellipse2D.Double(camera.screenX(c) - r, camera.screenY(c) - r, 2 * r, 2 * r);
    }

    private void paintGlassBack(Graphics2D g, AtomStyle style) {
        if (!style.glassFill) {
            return;
        }
        Ellipse2D e = glassOutline();
        double r = e.getWidth() / 2;
        Color gc = style.glassColor;
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(e.getCenterX(), e.getCenterY()), (float) r,
                new Point2D.Double(e.getCenterX() + r * 0.25, e.getCenterY() - r * 0.35),
                new float[] {0f, 0.55f, 0.9f, 1f},
                new Color[] {Colors.withAlpha(Color.WHITE, 0.30f), Colors.withAlpha(gc, 0.22f),
                             Colors.withAlpha(gc.darker(), 0.35f), Colors.withAlpha(gc, 0.55f)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(e);
    }

    private void paintGlassFront(Graphics2D g, AtomStyle style) {
        Ellipse2D e = glassOutline();
        double r = e.getWidth() / 2;
        Color gc = style.glassColor;
        double outer = r * 1.06;
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(e.getCenterX(), e.getCenterY()), (float) outer,
                new float[] {0f, 0.55f, 0.80f, 0.915f, 0.943f, 0.962f, 1f},
                new Color[] {Colors.withAlpha(gc, 0f), Colors.withAlpha(gc, 0.0f), Colors.withAlpha(gc, 0.16f),
                             Colors.withAlpha(gc, 0.50f), Colors.withAlpha(Colors.brighter(gc, 0.45f), 0.95f),
                             Colors.withAlpha(gc, 0.32f), Colors.withAlpha(gc, 0f)}));
        g.fill(new Ellipse2D.Double(e.getCenterX() - outer, e.getCenterY() - outer, 2 * outer, 2 * outer));

        if (style.glassFill) {
            g.setPaint(new RadialGradientPaint(
                    new Point2D.Double(e.getCenterX() + r * 0.45, e.getCenterY() - r * 0.40), (float) (r * 0.55),
                    new float[] {0f, 1f},
                    new Color[] {Colors.withAlpha(Color.WHITE, 0.28f), Colors.withAlpha(Color.WHITE, 0f)}));
            g.fill(e);
        }
    }

    private void paintCoreGlow(Graphics2D g, AtomFrame frame, AtomStyle style) {
        if (frame.glow <= 0.01f) {
            return;
        }
        Vec3 c = camera.toView(Vec3.ZERO);
        double r = (Math.max(1.0, frame.nucleusRadius) * 3.4 + 1.5) * camera.scaleAt(c);
        BufferedImage img = sprites.coreGlow(style.coreGlowColor, style.glassColor, r);
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, frame.glow)));
        drawCentered(g, img, camera.screenX(c), camera.screenY(c));
        g.setComposite(old);
    }

    // ==================================================================
    // Kugeln
    // ==================================================================

    private void addNucleons(AtomFrame frame, final AtomStyle style) {
        final double nr = frame.nucleonRadius;
        final double inner = Math.max(0.01, frame.nucleusRadius - nr);
        for (AtomFrame.NucleonState n : frame.nucleons) {
            if (n.alpha <= 0.01f) {
                continue;
            }
            final Vec3 v = camera.toView(n.position);
            // Kugeln tief im Kern etwas dunkler; in 4 Stufen, damit der Sprite-Cache greift
            double occl = 0.72 + 0.28 * Math.min(1.0, n.position.length() / inner);
            occl = Math.round(occl * 8) / 8.0;
            final Color base = Colors.shade(n.proton ? style.protonColor : style.neutronColor, occl);
            final float alpha = n.alpha;
            items.add(new Drawable(camera.depth(v)) {
                @Override
                void draw(Graphics2D g) {
                    BufferedImage img = sprites.sphere(base, style.environmentColor, nr * camera.scaleAt(v), 0.9);
                    drawCentered(g, img, camera.screenX(v), camera.screenY(v), alpha);
                }
            });
        }
    }

    private void addElectrons(AtomFrame frame, final AtomStyle style) {
        final double er = frame.electronRadius;
        for (AtomFrame.ElectronState e : frame.electrons) {
            if (e.alpha <= 0.01f) {
                continue;
            }
            final Vec3 v = camera.toView(e.position);
            final float alpha = e.alpha;
            // minimal vor die Bahn schieben, damit das Elektron ueber dem Band liegt
            items.add(new Drawable(camera.depth(v) - ELECTRON_LIFT) {
                @Override
                void draw(Graphics2D g) {
                    double sx = camera.screenX(v), sy = camera.screenY(v);
                    double r = er * camera.scaleAt(v);
                    BufferedImage halo = sprites.glow(Colors.brighter(style.electronColor, 0.4f),
                            style.electronColor, r * 2.6, 0.55f, 0.18f);
                    drawCentered(g, halo, sx, sy, alpha);
                    drawCentered(g, sprites.sphere(style.electronColor, style.environmentColor, r, 1.0), sx, sy, alpha);
                }
            });
        }
    }

    private static void drawCentered(Graphics2D g, BufferedImage img, double x, double y) {
        g.drawImage(img, (int) Math.round(x - img.getWidth() / 2.0), (int) Math.round(y - img.getHeight() / 2.0), null);
    }

    private static void drawCentered(Graphics2D g, BufferedImage img, double x, double y, float alpha) {
        if (alpha >= 0.99f) {
            drawCentered(g, img, x, y);
            return;
        }
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        drawCentered(g, img, x, y);
        g.setComposite(old);
    }

    // ==================================================================
    // Bahnen
    // ==================================================================

    private void addOrbits(AtomFrame frame, AtomStyle style) {
        Vec3 eye = camera.eyeInView();
        Vec3 center = camera.toView(Vec3.ZERO);
        for (AtomFrame.OrbitState o : frame.orbits) {
            if (o.alpha <= 0.01f) {
                continue;
            }
            Vec3 halfN = camera.dirToView(o.normal()).scale(style.orbitWidth / 2.0);
            BasicStroke edgeStroke = null;
            BasicStroke[] wireStrokes = null;
            Vec3 prev = camera.toView(o.pointAt(0));
            for (int i = 0; i < ORBIT_SEGMENTS; i++) {
                double a1 = 2.0 * Math.PI * (i + 1) / ORBIT_SEGMENTS;
                Vec3 p0 = prev;
                Vec3 p1 = camera.toView(o.pointAt(a1));
                prev = p1;
                Vec3 mid = p0.add(p1).scale(0.5);
                double facing = mid.sub(center).normalize().dot(eye.sub(mid).normalize());
                if (style.orbitShape == AtomStyle.OrbitShape.RIBBON) {
                    if (edgeStroke == null) {
                        float w = (float) Math.max(0.8, camera.scaleAt(center) * 0.05);
                        edgeStroke = new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
                    }
                    items.add(ribbonSegment(p0, p1, halfN, facing, style, o.alpha, edgeStroke));
                } else {
                    if (wireStrokes == null) {
                        float w = (float) Math.max(1.2, style.orbitWidth * camera.scaleAt(center));
                            // deckend: runde Enden schliessen die Fugen; beim Ein-/Ausblenden flache Enden
                        int cap = o.alpha >= 0.99f ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT;
                        wireStrokes = new BasicStroke[] {
                            new BasicStroke(w, cap, BasicStroke.JOIN_ROUND),
                            new BasicStroke(Math.max(0.6f, w * 0.35f), cap, BasicStroke.JOIN_ROUND)};
                    }
                    addWireSegment(p0, p1, facing, style, o.alpha, wireStrokes);
                }
            }
        }
    }

    /** Band wie ein Reifen: Breite entlang der Bahnnormalen. */
    private Drawable ribbonSegment(Vec3 p0, Vec3 p1, Vec3 halfN, double facing, AtomStyle style,
                                   float orbitAlpha, final BasicStroke edgeStroke) {
        Vec3 a = p0.add(halfN), b = p1.add(halfN), c = p1.sub(halfN), d = p0.sub(halfN);
        final double ax = camera.screenX(a), ay = camera.screenY(a);
        final double bx = camera.screenX(b), by = camera.screenY(b);
        final double cx = camera.screenX(c), cy = camera.screenY(c);
        final double dx = camera.screenX(d), dy = camera.screenY(d);
        double f = Math.abs(facing);
        final Color fill = Colors.withAlpha(
                Colors.shade(style.orbitColor, facing >= 0 ? 0.62 + 0.45 * f : 0.42 + 0.30 * f),
                (float) (style.orbitAlpha * (0.55 + 0.45 * f)) * orbitAlpha);
        final Color edge = Colors.withAlpha(Colors.brighter(style.orbitColor, 0.35f),
                Math.min(1f, (float) (style.orbitAlpha * (0.55 + 0.45 * f)) + 0.2f) * 0.55f * orbitAlpha);
        return new Drawable((camera.depth(p0) + camera.depth(p1)) / 2.0) {
            @Override
            void draw(Graphics2D g) {
                GeneralPath quad = new GeneralPath(Path2D.WIND_NON_ZERO, 5);
                quad.moveTo(ax, ay);
                quad.lineTo(bx, by);
                quad.lineTo(cx, cy);
                quad.lineTo(dx, dy);
                quad.closePath();
                g.setColor(fill);
                g.fill(quad);
                g.setStroke(edgeStroke);
                g.setColor(edge);
                g.draw(new Line2D.Double(ax, ay, bx, by));
                g.draw(new Line2D.Double(dx, dy, cx, cy));
            }
        };
    }

    /**
     * Duenne Chrombahn (Bild 2). Koerper und Glanzstreifen sind getrennte
     * Elemente; der Glanz liegt in der Tiefe knapp davor, damit die runden
     * Enden des naechsten Segments ihn nicht zerschneiden.
     */
    private void addWireSegment(Vec3 p0, Vec3 p1, double facing, AtomStyle style, float orbitAlpha,
                                final BasicStroke[] strokes) {
        final double x0 = camera.screenX(p0), y0 = camera.screenY(p0);
        final double x1 = camera.screenX(p1), y1 = camera.screenY(p1);
        double f = Math.abs(facing);
        Color metal = Colors.shade(style.orbitColor, 0.45 + 0.55 * f * f);
        final Color body = Colors.withAlpha(metal, orbitAlpha);
        final Color shine = Colors.withAlpha(Colors.brighter(metal, (float) (0.35 + 0.55 * f * f)), orbitAlpha);
        double depth = (camera.depth(p0) + camera.depth(p1)) / 2.0;
        items.add(new Drawable(depth) {
            @Override
            void draw(Graphics2D g) {
                g.setStroke(strokes[0]);
                g.setColor(body);
                g.draw(new Line2D.Double(x0, y0, x1, y1));
            }
        });
        items.add(new Drawable(depth - WIRE_SHINE_LIFT) {
            @Override
            void draw(Graphics2D g) {
                g.setStroke(strokes[1]);
                g.setColor(shine);
                g.draw(new Line2D.Double(x0, y0, x1, y1));
            }
        });
    }
}
