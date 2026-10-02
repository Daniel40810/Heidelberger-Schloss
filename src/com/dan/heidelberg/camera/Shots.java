package com.dan.heidelberg.camera;

/** Bausteine für Einstellungen: Hintereinanderschalten und Zeitdehnung. */
public final class Shots {
    private Shots() { }

    /** Mehrere Einstellungen nacheinander. */
    public static Shot chain(String name, Shot... parts) {
        final double[] end = new double[parts.length];
        double t = 0;
        for (int i = 0; i < parts.length; i++) { t += parts[i].duration(); end[i] = t; }
        final double total = t;
        return new Shot() {
            @Override public String name() { return name; }
            @Override public double duration() { return total; }
            @Override public void pose(double tt, double[] out) {
                int i = 0;
                while (i < parts.length - 1 && tt >= end[i]) i++;
                double t0 = i == 0 ? 0 : end[i - 1];
                parts[i].pose(Math.max(0, Math.min(parts[i].duration(), tt - t0)), out);
            }
        };
    }
}
