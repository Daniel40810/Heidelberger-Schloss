package com.dan.heidelberg.lupe;

import java.awt.Color;

/**
 * Farb- und Formvorgaben fuer die Atomdarstellung.
 * DEEP_SPACE entspricht Referenzbild 1 (breite Cyan-Baender auf Schwarz),
 * LABOR entspricht Referenzbild 2 (Chrombahnen, Glaskugel, Petrol).
 */
public final class AtomStyle {

    /** Wie die Elektronenbahnen gezeichnet werden. */
    public enum OrbitShape { RIBBON, WIRE }

    public String     name;
    public Color      backgroundInner;
    public Color      backgroundOuter;
    public Color      protonColor;
    public Color      neutronColor;
    public Color      electronColor;
    public Color      orbitColor;
    public Color      glassColor;
    public Color      coreGlowColor;
    public Color      environmentColor;   // Randlicht auf Kugeln
    public OrbitShape orbitShape;
    public double     orbitWidth;         // Weltbreite der Bahn
    public float      orbitAlpha;
    public boolean    glassFill;          // Glaskugel mit Koerper (Bild 2) statt nur Rand (Bild 1)
    public boolean    backgroundLines;    // Wellenlinien im Hintergrund (Bild 2)

    public static AtomStyle deepSpace() {
        AtomStyle s = new AtomStyle();
        s.name             = "Deep Space";
        s.backgroundInner  = new Color(4, 14, 26);
        s.backgroundOuter  = new Color(0, 0, 0);
        s.protonColor      = new Color(214, 22, 22);
        s.neutronColor     = new Color(176, 190, 204);
        s.electronColor    = new Color(38, 132, 205);
        s.orbitColor       = new Color(88, 196, 236);
        s.glassColor       = new Color(30, 130, 210);
        s.coreGlowColor    = new Color(170, 225, 245);
        s.environmentColor = new Color(60, 150, 220);
        s.orbitShape       = OrbitShape.RIBBON;
        s.orbitWidth       = 1.05;
        s.orbitAlpha       = 0.70f;
        s.glassFill        = false;
        s.backgroundLines  = false;
        return s;
    }

    public static AtomStyle labor() {
        AtomStyle s = new AtomStyle();
        s.name             = "Labor";
        s.backgroundInner  = new Color(20, 120, 120);
        s.backgroundOuter  = new Color(4, 34, 36);
        s.protonColor      = new Color(204, 26, 30);
        s.neutronColor     = new Color(222, 226, 230);
        s.electronColor    = new Color(40, 178, 220);
        s.orbitColor       = new Color(200, 206, 212);
        s.glassColor       = new Color(150, 220, 200);
        s.coreGlowColor    = new Color(230, 240, 235);
        s.environmentColor = new Color(90, 200, 190);
        s.orbitShape       = OrbitShape.WIRE;
        s.orbitWidth       = 0.22;
        s.orbitAlpha       = 1.0f;
        s.glassFill        = true;
        s.backgroundLines  = true;
        return s;
    }

    @Override
    public String toString() {
        return name;
    }
}
