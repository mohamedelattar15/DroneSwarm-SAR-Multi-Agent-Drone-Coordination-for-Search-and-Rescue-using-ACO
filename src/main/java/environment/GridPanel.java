package environment;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;

/**
 * Panneau de rendu de la zone sinistrée avec heatmap, obstacles et victimes.
 */
public class GridPanel extends JPanel {

    private final Grid grid;
    private final int cellSize;
    private Map<String, Position> dronePositions;
    private List<Position> bestPath = new java.util.ArrayList<>();
    /** ✅ Liste de tous les chemins vers les victimes */
    private List<List<Position>> victimPaths = new java.util.ArrayList<>();

    public GridPanel(Grid grid, int cellSize) {
        this.grid = grid;
        this.cellSize = cellSize;
        setPreferredSize(new java.awt.Dimension(grid.getWidth() * cellSize, grid.getHeight() * cellSize));
        setBackground(Color.WHITE);
    }

    public void setDronePositions(Map<String, Position> positions) { this.dronePositions = positions; }
    public void setBestPath(List<Position> path) { this.bestPath = (path == null) ? new java.util.ArrayList<>() : path; }
    public void setVictimPaths(List<List<Position>> paths) { this.victimPaths = (paths == null) ? new java.util.ArrayList<>() : paths; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double[][] pheromones = grid.getPheromoneMap();
        boolean[][] obstacles = grid.getObstacleMap();

        for (int i = 0; i < grid.getWidth(); i++) {
            for (int j = 0; j < grid.getHeight(); j++) {
                int px = i * cellSize;
                int py = j * cellSize;

                if (obstacles[i][j]) {
                    // Obstacle : gris foncé
                    g2.setColor(new Color(60, 60, 60));
                    g2.fillRect(px, py, cellSize, cellSize);
                    g2.setColor(new Color(80, 80, 80));
                    g2.drawRect(px, py, cellSize, cellSize);
                    continue;
                }

                double p = pheromones[i][j];
                if (p > 0.01) {
                    double intensity = Math.min(1.0, p / 15.0);
                    int r = 255;
                    int gv = (int) (255 * (1.0 - intensity * 0.6));
                    int b = (int) (255 * (1.0 - intensity * 0.85));
                    g2.setColor(new Color(r, gv, b));
                } else if (p < -0.01) {
                    // Phéromone négative (zone dangereuse) : rouge clair
                    double intensity = Math.min(1.0, -p / 10.0);
                    int r = (int) (255);
                    int gv = (int) (200 * (1.0 - intensity));
                    int b = (int) (200 * (1.0 - intensity));
                    g2.setColor(new Color(r, gv, b));
                } else {
                    g2.setColor(new Color(248, 248, 252));
                }
                g2.fillRect(px, py, cellSize, cellSize);
                g2.setColor(new Color(235, 235, 238));
                g2.drawRect(px, py, cellSize, cellSize);
            }
        }

        // ✅ Chemins vers les victimes (chaque victime = couleur différente)
        Color[] pathColors = {
            new Color(30, 80, 220, 150),   // Bleu
            new Color(220, 30, 180, 150),  // Rose
            new Color(30, 180, 80, 150),   // Vert
            new Color(220, 120, 30, 150),  // Orange
            new Color(120, 30, 220, 150),  // Violet
        };
        int colorIndex = 0;
        for (List<Position> path : victimPaths) {
            if (path.size() > 1) {
                Color c = pathColors[colorIndex % pathColors.length];
                g2.setColor(c);
                g2.setStroke(new BasicStroke(Math.max(2, cellSize / 4)));
                for (int k = 0; k < path.size() - 1; k++) {
                    Position a = path.get(k);
                    Position b = path.get(k + 1);
                    g2.drawLine(a.x * cellSize + cellSize / 2, a.y * cellSize + cellSize / 2,
                            b.x * cellSize + cellSize / 2, b.y * cellSize + cellSize / 2);
                }
                colorIndex++;
            }
        }
        g2.setStroke(new BasicStroke(1));

        // Meilleur chemin (bleu épais) - toujours affiché
        if (bestPath.size() > 1) {
            g2.setColor(new Color(30, 80, 220, 200));
            g2.setStroke(new BasicStroke(Math.max(3, cellSize / 3)));
            for (int k = 0; k < bestPath.size() - 1; k++) {
                Position a = bestPath.get(k);
                Position b = bestPath.get(k + 1);
                g2.drawLine(a.x * cellSize + cellSize / 2, a.y * cellSize + cellSize / 2,
                        b.x * cellSize + cellSize / 2, b.y * cellSize + cellSize / 2);
            }
            g2.setStroke(new BasicStroke(1));
        }

        // Victimes (rouge avec croix)
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, cellSize / 2)));
        for (Position v : grid.getVictimPositions()) {
            int px = v.x * cellSize;
            int py = v.y * cellSize;
            g2.setColor(new Color(200, 30, 30));
            g2.fillRect(px + 2, py + 2, cellSize - 4, cellSize - 4);
            g2.setColor(Color.WHITE);
            g2.drawString("✚", px + cellSize / 3 - 1, py + cellSize * 2 / 3);
        }

        // Base (bleu)
        Position nest = grid.getNestPosition();
        g2.setColor(new Color(30, 100, 200));
        g2.fillRect(nest.x * cellSize + 2, nest.y * cellSize + 2, cellSize - 4, cellSize - 4);
        g2.setColor(Color.WHITE);
        g2.drawString("B", nest.x * cellSize + cellSize / 3, nest.y * cellSize + cellSize * 2 / 3);

        // Drones (noir)
        if (dronePositions != null && !dronePositions.isEmpty()) {
            int droneSize = Math.max(5, cellSize / 2);
            g2.setColor(Color.BLACK);
            for (Position p : dronePositions.values()) {
                int cx = p.x * cellSize + cellSize / 2;
                int cy = p.y * cellSize + cellSize / 2;
                g2.fillOval(cx - droneSize / 2, cy - droneSize / 2, droneSize, droneSize);
            }
        }
    }
}
