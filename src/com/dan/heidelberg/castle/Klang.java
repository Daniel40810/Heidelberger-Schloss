package com.dan.heidelberg.castle;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Phase 9, Klang: alle Töne entstehen im Programm aus Schwingungen und Rauschen, es gibt keine Klangdateien. Ein Mischer auf
 * eigenem Thread summiert Quellen (die Wasserorgel läuft dauernd, Knall und Donner sind Einzelklänge) und schreibt sie als
 * Mono-Signal (22,05 kHz, 16 Bit) auf die Soundkarte. Fehlt ein Tonausgang, bleibt das Programm stumm und läuft weiter.
 * Die Prüfwerkzeuge schreiben dieselben Klänge als WAV-Dateien.
 */
public final class Klang {
    public static final int SR = 22050;

    /** Eine Klangquelle: füllt (addiert) n Abtastwerte. */
    public interface Source {
        void mix(float[] out, int n);
        boolean alive();
    }

    private static final class Clip implements Source {
        final float[] d; final float g; int pos;
        Clip(float[] d, float g) { this.d = d; this.g = g; }
        @Override public void mix(float[] out, int n) {
            int m = Math.min(n, d.length - pos);
            for (int i = 0; i < m; i++) out[i] += d[pos + i] * g;
            pos += m;
        }
        @Override public boolean alive() { return pos < d.length; }
    }

    /** Ton an oder aus (Bedienfeld) und Gesamtlautstärke. */
    public static volatile boolean enabled = true;
    public static volatile float master = 0.8f;
    private static final List<Source> sources = new CopyOnWriteArrayList<>();
    private static Thread mixer;
    private static volatile boolean broken;
    private static float[] boomClip, thunderClip;

    private Klang() { }

    // ------------------------------------------------------------ Mischer

    private static synchronized void ensure() {
        if (mixer != null || broken) return;
        mixer = new Thread(Klang::run, "Heidelberg-Klang");
        mixer.setDaemon(true);
        mixer.setPriority(Thread.NORM_PRIORITY + 1);
        mixer.start();
    }

    private static void run() {
        SourceDataLine line = null;
        try {
            AudioFormat f = new AudioFormat(SR, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(f);
            line.open(f, 4096 * 2);
            line.start();
        } catch (Throwable ex) {
            broken = true;
            return;
        }
        final int N = 512;
        float[] mix = new float[N];
        byte[] pcm = new byte[N * 2];
        while (true) {
            java.util.Arrays.fill(mix, 0);
            if (enabled) for (Source s : sources) { if (s.alive()) s.mix(mix, N); }
            sources.removeIf(s -> !s.alive());
            float m = master;
            for (int i = 0; i < N; i++) {
                float v = mix[i] * m;
                v = (float) Math.tanh(v);
                int q = (int) (v * 30000);
                pcm[2 * i] = (byte) q; pcm[2 * i + 1] = (byte) (q >> 8);
            }
            line.write(pcm, 0, pcm.length);
        }
    }

    /** Eine dauernde Quelle (Wasserorgel) anmelden. */
    public static void add(Source s) {
        if (!sources.contains(s)) sources.add(s);
        ensure();
    }

    public static void play(float[] clip, float gain) {
        if (!enabled || clip == null) return;
        sources.add(new Clip(clip, gain));
        ensure();
    }

    /** Knall einer Sprengung; dist in 0..1 (0 = nah, laut). */
    public static void boom(double dist) {
        synchronized (Klang.class) { if (boomClip == null) boomClip = makeBoom(); }
        play(boomClip, (float) (1.0 - 0.6 * Math.min(1, dist)));
    }

    /** Donner nach der Laufzeit des Schalls (Sekunden). */
    public static void thunderIn(double sec, double dist) {
        if (!enabled) return;
        if (sec < 0.05) { thunder(dist); return; }
        Thread t = new Thread(() -> {
            try { Thread.sleep((long) (Math.min(sec, 12) * 1000)); } catch (InterruptedException e) { return; }
            thunder(dist);
        }, "Heidelberg-Donner");
        t.setDaemon(true);
        t.start();
    }

    public static void thunder(double dist) {
        synchronized (Klang.class) { if (thunderClip == null) thunderClip = makeThunder(); }
        play(thunderClip, (float) (0.9 - 0.5 * Math.min(1, dist)));
    }

    // ------------------------------------------------------------ Synthese

    /** Tiefpass erster Ordnung, a in 0..1 (klein = dumpf). */
    static void lowpass(float[] x, float a) {
        float y = 0;
        for (int i = 0; i < x.length; i++) { y += a * (x[i] - y); x[i] = y; }
    }

    static void normalize(float[] x, float peak) {
        float m = 1e-9f;
        for (float v : x) m = Math.max(m, Math.abs(v));
        float k = peak / m;
        for (int i = 0; i < x.length; i++) x[i] *= k;
    }

    /** Sprengung: Druckwelle (fallender Sinus), Rauschstoß und langes Grollen. */
    static float[] makeBoom() {
        int n = (int) (SR * 3.2);
        float[] x = new float[n], z = new float[n];
        Random r = new Random(1693);
        for (int i = 0; i < n; i++) z[i] = (float) (r.nextGaussian());
        lowpass(z, 0.12f);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double f = 26 + 70 * Math.exp(-t * 7);
            ph += 2 * Math.PI * f / SR;
            double env = Math.exp(-t * 1.5) * (1 - Math.exp(-t * 400));
            x[i] = (float) (Math.sin(ph) * env * 0.9 + z[i] * 5.5 * Math.exp(-t * 2.6));
        }
        normalize(x, 0.95f);
        return x;
    }

    /** Donner: Knacken, danach ein langes, schwellendes Rollen. */
    static float[] makeThunder() {
        int n = (int) (SR * 6.5);
        float[] x = new float[n];
        Random r = new Random(1764);
        float[] z = new float[n], z2 = new float[n];
        for (int i = 0; i < n; i++) { z[i] = (float) r.nextGaussian(); z2[i] = (float) r.nextGaussian(); }
        lowpass(z, 0.20f);
        lowpass(z2, 0.04f);
        double lfo = r.nextDouble() * 6;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double crack = Math.exp(-t * 25) * z[i] * 2.2;
            double roll = z2[i] * 9 * (0.55 + 0.45 * Math.sin(t * 5.3 + lfo) * Math.sin(t * 2.1 + 1)) * Math.exp(-t * 0.55) * (1 - Math.exp(-t * 3));
            x[i] = (float) (crack + roll);
        }
        normalize(x, 0.9f);
        return x;
    }

    /** Schreibt einen Klang als WAV (für die Prüfung ohne Soundkarte). */
    public static void writeWav(File f, float[] d) throws IOException {
        byte[] pcm = new byte[d.length * 2];
        for (int i = 0; i < d.length; i++) {
            int q = (int) (Math.max(-1, Math.min(1, Math.tanh(d[i]))) * 30000);
            pcm[2 * i] = (byte) q; pcm[2 * i + 1] = (byte) (q >> 8);
        }
        AudioFormat fmt = new AudioFormat(SR, 16, 1, true, false);
        javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(pcm), fmt, d.length);
        AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, f);
    }

    public static float[] boomClip() { return makeBoom(); }
    public static float[] thunderClip() { return makeThunder(); }
}
