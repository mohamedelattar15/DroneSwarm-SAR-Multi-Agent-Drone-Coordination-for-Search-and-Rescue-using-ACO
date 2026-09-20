package environment;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.JProgressBar;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import utils.SimulationConfig;
import utils.SimulationRuntimeControl;

/**
 * Interface utilisateur pour la simulation de sauvetage par drones.
 */
public class SimulationFrame extends JFrame {

    private final Grid grid;
    private final GridPanel gridPanel;
    private final JLabel statsLabel;
    private final JLabel victimsLabel;
    private final JLabel bestPathLabel;
    private final JTextArea eventArea;
    private final JButton pauseButton;
    private final JLabel speedValueLabel;
    private final JLabel exploreValueLabel;
    private final JLabel alphaValueLabel;
    private final JLabel betaValueLabel;
    private final JLabel evapValueLabel;
    private final Timer refreshTimer;
    private final int totalVictims;
    private final JProgressBar progressBar;
    private int maxIterations = 500;

    public SimulationFrame(Grid grid, SimulationConfig config) {
        super("🚁 DroneSwarm-SAR - Search and Rescue");
        this.totalVictims = config.getVictimCount();

        this.grid = grid;

        int cellSize = Math.max(6, Math.min(12,
                800 / Math.max(grid.getWidth(), grid.getHeight())));

        gridPanel = new GridPanel(grid, cellSize);

        statsLabel = makeLabel("Drones: 0 | Exploration: 20%");
        victimsLabel = makeLabel("Victimes: 0/" + config.getVictimCount() + " trouvées");
        bestPathLabel = makeLabel("Meilleur chemin: --");
        progressBar = new JProgressBar(0, maxIterations);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setString("Victimes trouvées: 0 / " + maxIterations);
        eventArea = new JTextArea(8, 28);
        eventArea.setEditable(false);
        eventArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        eventArea.setBackground(new Color(245, 245, 250));
        eventArea.setText("🚁 Simulation prête.");

        pauseButton = new JButton("⏸ Pause");
        pauseButton.addActionListener(e -> togglePause());

        JSlider speedSlider = new JSlider(20, 500, (int) config.getTickTime());
        speedValueLabel = makeLabel(speedSlider.getValue() + " ms");
        speedSlider.addChangeListener(e -> {
            int val = speedSlider.getValue();
            SimulationRuntimeControl.setDroneStepIntervalMs(val);
            speedValueLabel.setText(val + " ms");
        });

        JSlider exploreSlider = new JSlider(0, 100, (int) (config.getRandomExploration() * 100));
        exploreValueLabel = makeLabel(exploreSlider.getValue() + "%");
        exploreSlider.addChangeListener(e -> {
            int val = exploreSlider.getValue();
            SimulationRuntimeControl.setExplorationRate(val / 100.0);
            exploreValueLabel.setText(val + "%");
        });

        JSlider alphaSlider = new JSlider(0, 50, (int) (config.getAlpha() * 10));
        alphaValueLabel = makeLabel(String.format("%.1f", alphaSlider.getValue() / 10.0));
        alphaSlider.addChangeListener(e -> {
            double val = alphaSlider.getValue() / 10.0;
            SimulationRuntimeControl.setAlpha(val);
            alphaValueLabel.setText(String.format("%.1f", val));
        });

        JSlider betaSlider = new JSlider(0, 50, (int) (config.getBeta() * 10));
        betaValueLabel = makeLabel(String.format("%.1f", betaSlider.getValue() / 10.0));
        betaSlider.addChangeListener(e -> {
            double val = betaSlider.getValue() / 10.0;
            SimulationRuntimeControl.setBeta(val);
            betaValueLabel.setText(String.format("%.1f", val));
        });

        JSlider evapSlider = new JSlider(1, 50, (int) (config.getRho() * 1000));
        evapValueLabel = makeLabel(String.format("%.3f", evapSlider.getValue() / 1000.0));
        evapSlider.addChangeListener(e -> {
            double val = evapSlider.getValue() / 1000.0;
            SimulationRuntimeControl.setEvaporationRate(val);
            evapValueLabel.setText(String.format("%.3f", val));
        });

        JButton clearButton = new JButton("🗑 Reset");
        clearButton.addActionListener(e -> {
            grid.resetPheromones();
            addEvent("🗑 Phéromones réinitialisées");
        });

        // Layout
        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        rightPanel.setBackground(new Color(242, 245, 250));
        rightPanel.setPreferredSize(new java.awt.Dimension(330, 100));

        JPanel metricsPanel = new JPanel(new GridLayout(4, 1, 2, 2));
        metricsPanel.setBorder(BorderFactory.createTitledBorder("📊 Métriques"));
        metricsPanel.add(statsLabel);
        metricsPanel.add(victimsLabel);
        metricsPanel.add(bestPathLabel);
        metricsPanel.add(progressBar);

        JPanel controlsPanel = new JPanel(new GridLayout(7, 1, 2, 4));
        controlsPanel.setBorder(BorderFactory.createTitledBorder("🎛 Contrôles"));
        JPanel pausePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pausePanel.add(pauseButton);
        pausePanel.add(clearButton);
        controlsPanel.add(pausePanel);
        controlsPanel.add(makeSliderPanel("Vitesse:", speedSlider, speedValueLabel));
        controlsPanel.add(makeSliderPanel("Exploration:", exploreSlider, exploreValueLabel));
        controlsPanel.add(makeSliderPanel("Alpha:", alphaSlider, alphaValueLabel));
        controlsPanel.add(makeSliderPanel("Beta:", betaSlider, betaValueLabel));
        controlsPanel.add(makeSliderPanel("Évap.:", evapSlider, evapValueLabel));

        JPanel eventPanel = new JPanel(new BorderLayout());
        eventPanel.setBorder(BorderFactory.createTitledBorder("📝 Événements"));
        eventPanel.add(new JScrollPane(eventArea), BorderLayout.CENTER);

        rightPanel.add(metricsPanel, BorderLayout.NORTH);
        rightPanel.add(controlsPanel, BorderLayout.CENTER);
        rightPanel.add(eventPanel, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new JScrollPane(gridPanel), BorderLayout.CENTER);
        getContentPane().add(rightPanel, BorderLayout.EAST);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        refreshTimer = new Timer(200, e -> refresh());
        refreshTimer.start();
    }

    public void refresh() {
        gridPanel.setDronePositions(grid.getDronePositions());
        gridPanel.setBestPath(grid.getBestPath());
        gridPanel.setVictimPaths(grid.getVictimPaths());
        gridPanel.repaint();
        statsLabel.setText(String.format("Drones: %d | Exploration: %d%%",
                grid.getDronePositions().size(),
                (int) (SimulationRuntimeControl.getExplorationRate() * 100)));
        victimsLabel.setText(String.format("Victimes: %d/%d trouvées",
                grid.getVictimsFound(), grid.getVictimPositions().size()));
        updatePauseButton();
        updateProgress(grid.getVictimsFound());
    }

    public void onBestPathFound(int steps) {
        bestPathLabel.setText(String.format("Meilleur chemin: %d étapes", steps));
        addEvent("📍 Nouveau meilleur chemin: " + steps + " étapes");
    }

    public void onVictimFound() {
        SwingUtilities.invokeLater(() -> {
            int found = grid.getVictimsFound();
            victimsLabel.setText(String.format("Victimes: %d/%d trouvées", found, totalVictims));
            addEvent("🆘 Victime détectée! (" + found + "/" + totalVictims + ")");
        });
    }

    public void updateProgress(int victimsFound) {
        progressBar.setValue(Math.min(victimsFound, maxIterations));
        progressBar.setString("Victimes trouvées: " + victimsFound + " / " + maxIterations);
    }

    public void addEvent(String text) {
        SwingUtilities.invokeLater(() -> {
            String current = eventArea.getText();
            if (current.isBlank()) {
                eventArea.setText("> " + text);
            } else {
                String[] lines = current.split("\n");
                StringBuilder sb = new StringBuilder("> ").append(text);
                for (int i = 0; i < Math.min(14, lines.length); i++) {
                    sb.append("\n").append(lines[i]);
                }
                eventArea.setText(sb.toString());
            }
        });
    }

    private void togglePause() {
        boolean paused = !SimulationRuntimeControl.isPaused();
        SimulationRuntimeControl.setPaused(paused);
        updatePauseButton();
        addEvent(paused ? "⏸ Simulation en pause" : "▶ Simulation reprise");
    }

    private void updatePauseButton() {
        pauseButton.setText(SimulationRuntimeControl.isPaused() ? "▶ Reprendre" : "⏸ Pause");
    }

    public void shutdown() { refreshTimer.stop(); dispose(); }

    private JLabel makeLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return label;
    }

    private JPanel makeSliderPanel(String label, JSlider slider, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(slider, BorderLayout.CENTER);
        panel.add(valueLabel, BorderLayout.EAST);
        return panel;
    }
}
