package agents.protocol;

/**
 * Protocole de communication du système multi-agents.
 * <p>
 * Centralise toutes les constantes d'échange (conversation-id, ontologies,
 * préfixes de contenu) pour éliminer les chaînes magiques dispersées dans les agents.
 */
public final class MessageProtocol {

    private MessageProtocol() {}

    // -------------------------------------------------------------------------
    // Conversation IDs
    // -------------------------------------------------------------------------

    public static final String CONV_VICTIM_FOUND = "drone-found-victim";
    public static final String CONV_DRONE_FEEDBACK = "drone-feedback";
    public static final String CONV_DRONE_BROADCAST = "drone-broadcast";

    // -------------------------------------------------------------------------
    // Ontologies
    // -------------------------------------------------------------------------

    public static final String ONTOLOGY_DRONE_RETURNED = "DRONE_RETURNED";
    public static final String ONTOLOGY_NEW_MISSION = "NEW_MISSION";
    public static final String ONTOLOGY_VICTIM_DETECTED = "VICTIM_DETECTED";
    public static final String ONTOLOGY_VICTIM_CONFIRMED = "VICTIM_CONFIRMED";

    // -------------------------------------------------------------------------
    // Préfixes de contenu
    // -------------------------------------------------------------------------

    /** Drone → Environment : victime détectée. Format : VICTIM_FOUND:factor:signature:x,y */
    public static final String VICTIM_FOUND = "VICTIM_FOUND";
    /** Environment → Drone : chemin validé. Format : PATH_ACCEPTED:factor:confirmations */
    public static final String PATH_ACCEPTED = "PATH_ACCEPTED";
    /** Environment → Drone : chemin rejeté. Format : PATH_REJECTED:raison */
    public static final String PATH_REJECTED = "PATH_REJECTED";
    /** Environment → Drone : ordre de diversification. */
    public static final String DIVERSIFY = "DIVERSIFY";
    /** Drone → Base : victime secourue. */
    public static final String VICTIM_RESCUED = "VICTIM_RESCUED";
    /** VictimAgent → Drone : détection confirmée. Format : victim_confirmed:n */
    public static final String VICTIM_CONFIRMED = "victim_confirmed";
    /** Drone → VictimAgent : détection. Format : victim_detected:x,y */
    public static final String VICTIM_DETECTED = "victim_detected";

    // -------------------------------------------------------------------------
    // Noms de services DF
    // -------------------------------------------------------------------------

    public static final String SERVICE_DRONE = "DroneService";
    public static final String SERVICE_ENVIRONMENT = "EnvironmentService";
    public static final String SERVICE_BASE = "BaseService";
    public static final String SERVICE_VICTIM = "VictimService";

    // -------------------------------------------------------------------------
    // Nommage des agents
    // -------------------------------------------------------------------------

    public static final String AGENT_ENVIRONMENT = "Environment";
    public static final String AGENT_BASE = "Base";
    public static final String AGENT_SNIFFER = "sniffer";
    public static final String PREFIX_DRONE = "Drone_";
    public static final String PREFIX_VICTIM = "Victim_";

    /** Construit le nom d'un agent victime à partir de sa position. */
    public static String victimName(int x, int y) {
        return PREFIX_VICTIM + x + "_" + y;
    }
}
