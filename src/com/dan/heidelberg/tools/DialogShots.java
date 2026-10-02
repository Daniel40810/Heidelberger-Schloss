package com.dan.heidelberg.tools;

import javax.swing.JDialog;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;

/** Zeichnet die Dialoge der Phase 10 in PNG-Dateien (Katalog mit Reitern, Einrichter im Ausgangszustand). Aufruf: DialogShots Ordner */
public final class DialogShots {
    static JTabbedPane tabs(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTabbedPane) return (JTabbedPane) k;
            if (k instanceof Container) { JTabbedPane t = tabs((Container) k); if (t != null) return t; }
        }
        return null;
    }

    static void bild(JDialog d, File f) throws Exception {
        SwingUtilities.invokeLater(() -> d.setVisible(true));
        Thread.sleep(700);
        BufferedImage img = new BufferedImage(d.getWidth(), d.getHeight(), BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> d.paint(img.getGraphics()));
        javax.imageio.ImageIO.write(img, "png", f);
        System.out.println("geschrieben " + f);
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "shots7");
        System.setProperty("heidelberg.nodb", "true");
        com.dan.heidelberg.db.Dienst.start();
        com.dan.heidelberg.world.Heidelberg.build(false);   // füllt Castle.PLACES (Lage der Bauwerke)
        Class<?> k = Class.forName("com.dan.heidelberg.ui.KatalogDialog");
        java.lang.reflect.Constructor<?> c = k.getDeclaredConstructor(Window.class);
        c.setAccessible(true);
        final JDialog[] d = new JDialog[1];
        SwingUtilities.invokeAndWait(() -> { try { d[0] = (JDialog) c.newInstance(new Object[]{null}); } catch (Exception e) { throw new RuntimeException(e); } });
        for (int i : new int[]{0, 2, 3}) {
            final int ii = i;
            SwingUtilities.invokeAndWait(() -> tabs(d[0]).setSelectedIndex(ii));
            bild(d[0], new File(dir, "katalog_" + i + ".png"));
        }
        d[0].dispose();
        Class<?> e = Class.forName("com.dan.heidelberg.ui.EinrichterDialog");
        java.lang.reflect.Constructor<?> ec = e.getDeclaredConstructor(Window.class, boolean.class);
        ec.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> { try { d[0] = (JDialog) ec.newInstance(null, false); } catch (Exception x) { throw new RuntimeException(x); } });
        bild(d[0], new File(dir, "einrichter_0.png"));
        System.exit(0);
    }

    private DialogShots() { }
}
