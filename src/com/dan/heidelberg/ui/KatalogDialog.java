package com.dan.heidelberg.ui;

import com.dan.fbutton.FButton;
import com.dan.heidelberg.castle.Castle;
import com.dan.heidelberg.db.Dienst;
import com.dan.heidelberg.db.Katalog;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Zeitstufen, Chronik, Bauwerke und Quellen; aus der Datenbank, wenn sie bereit ist, sonst der eingebaute Katalog. */
final class KatalogDialog extends JDialog {
    private static final Color PANEL = new Color(8, 12, 15), GRID = new Color(34, 46, 54), SEL = new Color(38, 58, 70);
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMANY);

    KatalogDialog(Window owner) {
        super(owner, "Quellen, Bauwerke, Chronik", ModalityType.MODELESS);
        boolean[] db = new boolean[1];
        Katalog.Daten d = Dienst.katalog(db);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(ControlPanel.BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JLabel head = new JLabel(db[0] ? "Aus der Datenbank (HEI_STAGE, HEI_CHRONIK, HEI_BUILDING, HEI_SOURCE)" : "Eingebauter Katalog (ohne Datenbank oder noch nicht eingerichtet)");
        head.setForeground(ControlPanel.MUTED);
        head.setFont(new Font("SansSerif", Font.PLAIN, 12));
        p.add(head, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(ControlPanel.BG);
        tabs.setForeground(ControlPanel.INK);

        DefaultTableModel zs = modell("Nr", "Jahr", "Datum", "Name", "Kurz", "Beschreibung", "Quelle");
        for (Katalog.Stufe s : d.stufen) zs.addRow(new Object[]{s.nr(), s.jahr(), s.datum() == null ? "" : s.datum().format(TAG), s.name(), s.kurz(), s.beschreibung(), s.quelle()});
        tabs.addTab("Zeitstufen", tabelle(zs, 40, 50, 130, 190, 300, 420, 120));

        DefaultTableModel ch = modell("Stufe", "Jahr", "Datum", "Ereignis", "Text", "Quelle");
        for (Katalog.Ereignis e : d.chronik) ch.addRow(new Object[]{e.stufe(), e.jahr(), e.datum() == null ? "" : e.datum().format(TAG), e.titel(), e.text(), e.quelle()});
        tabs.addTab("Chronik", tabelle(ch, 45, 50, 130, 190, 520, 120));

        DefaultTableModel bw = modell("Bauwerk", "Gruppe", "Detail", "x (m)", "z (m)", "Rolle und Angaben", "Quelle");
        for (Katalog.Bau b : d.bauten) {
            Double x = b.x(), z = b.z();
            if (x == null && b.ort() != null) {
                for (Object[] pl : Castle.PLACES) if (((String) pl[0]).startsWith(b.ort())) { x = (Double) pl[1]; z = (Double) pl[3]; break; }
            }
            bw.addRow(new Object[]{b.name(), b.bezug(), b.detail().toLowerCase(Locale.ROOT), x == null ? "" : String.format(Locale.GERMANY, "%.1f", x), z == null ? "" : String.format(Locale.GERMANY, "%.1f", z), b.rolle(), b.quelle()});
        }
        tabs.addTab("Bauwerke (" + d.bauten.size() + ")", tabelle(bw, 220, 70, 70, 60, 60, 560, 110));

        DefaultTableModel qs = modell("Kürzel", "Autor", "Werk", "Aussage", "Widersprüche und Beleg");
        for (Katalog.Quelle q : d.quellen) qs.addRow(new Object[]{q.code(), q.autor(), q.werk(), q.kern(), q.beleg() == null ? "" : q.beleg()});
        tabs.addTab("Quellen (" + d.quellen.size() + ")", tabelle(qs, 110, 180, 260, 520, 260));

        // Der gewählte Reiter ist hell hinterlegt: dunkle Schrift dort, helle auf den anderen
        Runnable tint = () -> {
            for (int i = 0; i < tabs.getTabCount(); i++) tabs.setForegroundAt(i, i == tabs.getSelectedIndex() ? new Color(14, 20, 24) : ControlPanel.INK);
        };
        tabs.addChangeListener(e -> tint.run());
        tint.run();
        p.add(tabs, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        south.setOpaque(false);
        FButton close = new FButton("Schließen");
        close.addActionListener(e -> dispose());
        south.add(close);
        p.add(south, BorderLayout.SOUTH);
        setContentPane(p);
        setPreferredSize(new Dimension(1100, 620));
        pack();
        setLocationRelativeTo(owner);
    }

    private static DefaultTableModel modell(String... spalten) {
        return new DefaultTableModel(spalten, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }

    private static JScrollPane tabelle(DefaultTableModel m, int... breiten) {
        JTable t = new JTable(m);
        t.setBackground(PANEL);
        t.setForeground(ControlPanel.INK);
        t.setGridColor(GRID);
        t.setSelectionBackground(SEL);
        t.setSelectionForeground(Color.WHITE);
        t.setFont(new Font("SansSerif", Font.PLAIN, 12));
        t.setRowHeight(24);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setFillsViewportHeight(true);
        for (int i = 0; i < breiten.length && i < t.getColumnCount(); i++) t.getColumnModel().getColumn(i).setPreferredWidth(breiten[i]);
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(tb, v, sel, foc, row, col);
                if (c instanceof JLabel) ((JLabel) c).setToolTipText(v == null ? null : "<html><body style='width:480px'>" + String.valueOf(v).replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
                if (!sel) { c.setBackground(PANEL); c.setForeground(ControlPanel.INK); }
                return c;
            }
        };
        t.setDefaultRenderer(Object.class, r);
        JTableHeader h = t.getTableHeader();
        h.setBackground(new Color(20, 30, 36));
        h.setForeground(ControlPanel.ACCENT);
        h.setFont(new Font("SansSerif", Font.BOLD, 12));
        JScrollPane sp = new JScrollPane(t);
        sp.getViewport().setBackground(PANEL);
        sp.setBorder(BorderFactory.createLineBorder(GRID));
        return sp;
    }
}
