package com.dan.heidelberg.lupe;

import java.awt.Color;

/** Kleine Farbhelfer fuer den Renderer. */
final class Colors {

    private Colors() {
    }

    static Color withAlpha(Color c, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), al);
    }

    static Color shade(Color c, double f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f), c.getAlpha());
    }

    static Color brighter(Color c, float t) {
        return new Color(clamp(c.getRed() + (255 - c.getRed()) * t),
                         clamp(c.getGreen() + (255 - c.getGreen()) * t),
                         clamp(c.getBlue() + (255 - c.getBlue()) * t), c.getAlpha());
    }

    static Color mix(Color a, Color b, double t) {
        return new Color(clamp(a.getRed() + (b.getRed() - a.getRed()) * t),
                         clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                         clamp(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
