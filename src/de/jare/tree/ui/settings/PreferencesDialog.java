package de.jare.tree.ui.settings;

import de.jare.tree.settings.WoodSettings;
import de.jare.tree.settings.theme.ThemeSuite;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

public class PreferencesDialog extends JDialog {

    private final JTabbedPane tabbedPane;

    private final JPanel woodJsonJackPane;
    private final ThemesPreferencesPane themesPane;
    private final JPanel projectsPane;

    private final WoodSettings settings;
    private final ThemeSuite themeSuite;

    public PreferencesDialog(Frame owner, WoodSettings settings, ThemeSuite themeSuite) {
        super(owner, "Preferences", false);

        this.settings = settings;
        this.themeSuite = themeSuite;

        this.tabbedPane = new JTabbedPane();

        this.woodJsonJackPane = new JPanel(new BorderLayout());
        this.themesPane = new ThemesPreferencesPane(themeSuite);
        this.projectsPane = new JPanel(new BorderLayout());

        buildUi();
    }

    private void buildUi() {
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setLayout(new BorderLayout());

        tabbedPane.addTab("Wood Json Jack", woodJsonJackPane);
        tabbedPane.addTab("Themes", themesPane);
        tabbedPane.addTab("Projects", projectsPane);

        add(tabbedPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(900, 600));
        pack();
        setLocationRelativeTo(getOwner());
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public JPanel getWoodJsonJackPane() {
        return woodJsonJackPane;
    }

    public ThemesPreferencesPane getThemesPane() {
        return themesPane;
    }

    public JPanel getProjectsPane() {
        return projectsPane;
    }
}
