package com.dan.heidelberg.castle;

import com.dan.heidelberg.effects.ParticleSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Rauch aus den Schornsteinen: die acht des Schlosses und die der Häuser in der Nähe der Kamera. Je kälter
 * die Luft, desto öfter wird geheizt; die Küchen des Schlosses rauchen immer. Die Schwaden steigen, wachsen und
 * treiben mit dem Wind davon (Teilchenart SMOKE).
 */
public final class Smoke {
    /** Ein- und ausschaltbar. */
    public volatile boolean enabled = true;
    /** Phase 9: in der Ruine rauchen die Schornsteine des Schlosses nicht mehr (die der Stadt schon). */
    public volatile boolean ruin;
    /** Umkreis um die Kamera, in dem Häuser rauchen, und höchste Zahl rauchender Schornsteine in der Stadt. */
    private static final double TOWN_RANGE = 480;
    private static final int TOWN_MAX = 70;

    private final List<double[]> castle = new ArrayList<>(), town = new ArrayList<>(), active = new ArrayList<>();
    private double refresh;
    private int lastTownSize = -1;

    public void attach(List<double[]> castleChimneys, List<double[]> townChimneys) {
        castle.clear(); castle.addAll(castleChimneys);
        town.clear(); town.addAll(townChimneys);
        lastTownSize = -1;
    }

    public int chimneys() { return castle.size() + town.size(); }

    /** Rauch für dt Sekunden; airTemp in °C. */
    public void emit(ParticleSystem ps, double dt, double camX, double camZ, double airTemp) {
        if (!enabled) return;
        refresh -= dt;
        if (refresh <= 0 || lastTownSize != town.size()) {
            refresh = 2; lastTownSize = town.size();
            active.clear();
            final double cx = camX, cz = camZ;
            List<double[]> near = new ArrayList<>();
            for (double[] c : town) if (Math.hypot(c[0] - cx, c[2] - cz) < TOWN_RANGE) near.add(c);
            near.sort((a, b) -> Double.compare(Math.hypot(a[0] - cx, a[2] - cz), Math.hypot(b[0] - cx, b[2] - cz)));
            for (int i = 0; i < Math.min(TOWN_MAX, near.size()); i++) active.add(near.get(i));
        }
        double cold = Math.max(0.12, Math.min(1, (19 - airTemp) / 14));
        for (double[] c : castle) {
            if (ruin) break;
            if (Math.hypot(c[0] - camX, c[2] - camZ) > 900) continue;
            puff(ps, c, dt, 1 / 0.5 * Math.max(cold, 0.45), 1.0f);
        }
        for (double[] c : active) puff(ps, c, dt, 1 / 1.0 * cold, 0.8f);
    }

    private void puff(ParticleSystem ps, double[] c, double dt, double rate, float size) {
        if (ps.n > ParticleSystem.CAP - 6000) return;
        double p = rate * dt;
        int k = (int) p;
        if (ps.rand() < p - k) k++;
        for (int q = 0; q < k; q++) {
            float jx = (ps.rand() - 0.5f) * 0.3f, jz = (ps.rand() - 0.5f) * 0.3f;
            int i = ps.spawn(ParticleSystem.SMOKE, (float) c[0] + jx, (float) c[1], (float) c[2] + jz, 0, 0.9f, 0, 0.3f * size,
                    13 + 5 * ps.rand(), 0.42f, -1e9f, -1);
            if (i >= 0) ps.grow[i] = 0.34f * size;
        }
    }
}
