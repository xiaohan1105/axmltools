package red.jiuzhou.ui;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;

/**
 * Non-breaking UI/UX upgrade for Swing using built-in Nimbus LAF.
 * Call SwingModernTheme.apply() once at startup before creating any UI.
 */
public final class SwingModernTheme {
    private SwingModernTheme() {}

    private static final Color SURFACE = color("#151933");
    private static final Color SURFACE2 = color("#1B2140");
    private static final Color BORDER = color("#2A315A");
    private static final Color TEXT = color("#E5E7EB");
    private static final Color TEXT_WEAK = color("#CBD5E1");
    private static final Color PRIMARY = color("#6C9EF8");
    private static final Color PRIMARY_STRONG = color("#3F6FD4");

    public static void apply() {
        try { for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) { if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; } } } catch (Exception ignored) {}

        UIDefaults d = UIManager.getDefaults();
        d.put("control", new ColorUIResource(SURFACE));
        d.put("info", new ColorUIResource(SURFACE2));
        d.put("nimbusBase", new ColorUIResource(PRIMARY_STRONG));
        d.put("nimbusBlueGrey", new ColorUIResource(BORDER));
        d.put("nimbusLightBackground", new ColorUIResource(SURFACE));
        d.put("text", new ColorUIResource(TEXT));
        d.put("nimbusSelectedText", new ColorUIResource(TEXT));
        d.put("nimbusSelectionBackground", new ColorUIResource(tint(PRIMARY, 0.2f)));
        d.put("nimbusFocus", new ColorUIResource(tint(PRIMARY, 0.6f)));
        d.put("nimbusDisabledText", new ColorUIResource(alpha(TEXT, 120)));

        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        setDefaultFont(d, base);
        UIManager.put("Component.arrowType", "triangle");

        UIManager.put("Panel.background", SURFACE);
        UIManager.put("Viewport.background", SURFACE);
        UIManager.put("ScrollPane.background", SURFACE);
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("MenuBar.background", SURFACE2);
        UIManager.put("ToolBar.background", SURFACE2);
        UIManager.put("PopupMenu.background", SURFACE2);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("Menu.background", SURFACE2);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("Menu.selectionBackground", tint(PRIMARY, .20f));
        UIManager.put("MenuItem.background", SURFACE2);
        UIManager.put("MenuItem.selectionBackground", tint(PRIMARY, .20f));

        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.disabledText", alpha(TEXT, 120));
        UIManager.put("Button.focus", tint(PRIMARY, .5f));
        UIManager.put("Button.select", tint(PRIMARY, .12f));

        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextField.selectionBackground", tint(PRIMARY, .25f));

        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("PasswordField.background", SURFACE);
        UIManager.put("PasswordField.foreground", TEXT);

        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", tint(PRIMARY, .20f));

        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.showGrid", Boolean.TRUE);
        UIManager.put("Table.alternateRowColor", new ColorUIResource(alpha(Color.WHITE, 20)));
        UIManager.put("Table.selectionBackground", tint(PRIMARY, .20f));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("TableHeader.background", SURFACE2);
        UIManager.put("TableHeader.foreground", TEXT_WEAK);

        UIManager.put("List.background", SURFACE);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", tint(PRIMARY, .20f));
        UIManager.put("List.selectionForeground", TEXT);

        UIManager.put("TabbedPane.background", SURFACE);
        UIManager.put("TabbedPane.foreground", TEXT_WEAK);
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.contentAreaColor", SURFACE);
        UIManager.put("TabbedPane.highlight", BORDER);
        UIManager.put("TabbedPane.shadow", BORDER);

        UIManager.put("Tree.background", SURFACE);
        UIManager.put("Tree.foreground", TEXT);
        UIManager.put("Tree.textForeground", TEXT);
        UIManager.put("Tree.selectionBackground", tint(PRIMARY, .20f));
        UIManager.put("Tree.selectionForeground", TEXT);

        UIManager.put("ProgressBar.background", SURFACE2);
        UIManager.put("ProgressBar.foreground", PRIMARY_STRONG);
        UIManager.put("ProgressBar.selectionForeground", TEXT);

        UIManager.put("SplitPane.background", SURFACE);
        UIManager.put("SplitPane.dividerSize", 6);

        UIManager.put("ScrollBar.thumb", new ColorUIResource(BORDER));
        UIManager.put("ToolTip.background", SURFACE2);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("controlText", TEXT);
        UIManager.put("infoText", TEXT);
        UIManager.put("TitledBorder.titleColor", TEXT_WEAK);
    }

    private static void setDefaultFont(UIDefaults d, Font f) { for (Object k : d.keySet()) { if (k != null && k.toString().toLowerCase().contains("font")) { Object v = d.get(k); if (v instanceof Font) d.put(k, f); else if (v instanceof javax.swing.plaf.FontUIResource) d.put(k, new javax.swing.plaf.FontUIResource(f)); } } }
    private static Color color(String hex) { return Color.decode(hex.replace("#", "0x")); }
    private static Color tint(Color c, float amt) { int r=(int)(c.getRed()*(1-amt)+255*amt), g=(int)(c.getGreen()*(1-amt)+255*amt), b=(int)(c.getBlue()*(1-amt)+255*amt); return new Color(r,g,b,c.getAlpha()); }
    private static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    // Optional profiles
    /** Apply a compact density profile (smaller paddings and row heights). */
    public static void applyCompactDensity() {
        UIManager.put("Button.margin", new Insets(4,8,4,8));
        UIManager.put("ToggleButton.margin", new Insets(4,8,4,8));
        UIManager.put("TextField.margin", new Insets(2,6,2,6));
        UIManager.put("TextArea.margin", new Insets(2,6,2,6));
        UIManager.put("ComboBox.padding", new Insets(2,6,2,6));
        UIManager.put("Table.rowHeight", 20);
        UIManager.put("List.rowHeight", 20);
        UIManager.put("Tree.rowHeight", 20);
    }
    /** Apply a high-contrast accent profile (stronger borders and primary). */
    public static void applyHighContrast() {
        UIManager.put("nimbusBlueGrey", new ColorUIResource(0x8aa0ff));
        UIManager.put("nimbusBase", new ColorUIResource(0x6f9fff));
        UIManager.put("Table.gridColor", new ColorUIResource(0x8aa0ff));
        UIManager.put("TabbedPane.highlight", new ColorUIResource(0x8aa0ff));
        UIManager.put("TabbedPane.shadow", new ColorUIResource(0x8aa0ff));
    }
}
