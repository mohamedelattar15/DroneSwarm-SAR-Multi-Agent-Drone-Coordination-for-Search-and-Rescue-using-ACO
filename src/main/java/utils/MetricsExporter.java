package utils;

import environment.Position;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exporte les métriques de la simulation vers un fichier CSV.
 * Utilisation : appeler recordIteration() à chaque tick, puis close() à la fin.
 */
public class MetricsExporter {

    private static final Logger log = LoggerFactory.getLogger(MetricsExporter.class);

    private final PrintWriter writer;
    private final String filePath;
    private int iteration = 0;

    public MetricsExporter() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.filePath = "logs/metrics_" + timestamp + ".csv";

        try {
            Path parentDir = Paths.get("logs");
            if (!Files.exists(parentDir)) Files.createDirectories(parentDir);

            writer = new PrintWriter(new FileWriter(filePath));
            writer.println("iteration,shortest_path,confirmations,discoveries,stagnation_cycles,drones_active,victims_found");
            log.info("📊 Métriques exportées vers {}", filePath);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer le fichier CSV: " + filePath, e);
        }
    }

    /**
     * Enregistre une ligne de métriques pour l'itération courante.
     */
    public void recordIteration(double shortestPath, int confirmations, int discoveries,
                                 int stagnationCycles, int dronesActive, int victimsFound) {
        writer.printf("%d,%.2f,%d,%d,%d,%d,%d%n",
                iteration++, shortestPath, confirmations, discoveries,
                stagnationCycles, dronesActive, victimsFound);
        writer.flush();
    }

    /**
     * Enregistre le meilleur chemin trouvé à la fin de la simulation.
     */
    public void recordBestPath(List<Position> bestPath, double pathLength) {
        writer.println();
        writer.println("# BEST_PATH_FOUND");
        writer.println("# path_length," + String.format("%.2f", pathLength));
        writer.println("# path_steps," + (bestPath != null ? bestPath.size() : 0));
        if (bestPath != null) {
            StringBuilder sb = new StringBuilder("# path_coordinates,");
            for (Position p : bestPath) {
                sb.append(p.x).append(":").append(p.y).append(";");
            }
            writer.println(sb.toString());
        }
        writer.flush();
    }

    /**
     * Ferme le fichier CSV proprement.
     */
    public void close() {
        writer.close();
        log.info("📊 Fichier CSV fermé: {} ({} itérations)", filePath, iteration);
    }

    public String getFilePath() {
        return filePath;
    }
}
