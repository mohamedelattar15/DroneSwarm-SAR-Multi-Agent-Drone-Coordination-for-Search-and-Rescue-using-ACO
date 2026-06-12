# Modèle GAIA - DroneSwarm-SAR

## Rôles

| Rôle | Permissions | Responsabilités | Activités |
|---|---|---|---|
| **DroneAgent** | Lire phéromones, écrire dépôts, perception locale | Explorer zone sinistrée, détecter victimes, retourner à la base | `explore()`, `detectVictim()`, `returnToBase()`, `depositPheromone()` |
| **EnvironmentAgent** | Lecture/écriture matrice phéromones, obstacles | Maintenir carte, gérer évaporation, valider détections | `evaporate()`, `validateDetection()`, `applyEliteReinforcement()`, `detectStagnation()` |
| **BaseAgent** | Créer drones, recevoir retours, logistique | Créer flotte, coordonner missions, compter victimes | `spawnDrones()`, `acknowledgeReturn()`, `logRescue()` |
| **VictimAgent** | Marquer position, confirmer détection | Signaler présence, confirmer identification | `signalDetection()`, `confirmVictim()` |

## Interactions

| Interaction | Initiateur | Répondeur | Description |
|---|---|---|---|
| `VICTIM_FOUND` | DroneAgent | EnvironmentAgent | Drone propose une victime détectée |
| `PATH_ACCEPTED/REJECTED` | EnvironmentAgent | DroneAgent | Validation ou rejet |
| `DIVERSIFY` | EnvironmentAgent | DroneAgent | Ordre de diversification |
| `DRONE_RETURNED` | DroneAgent | BaseAgent | Notification de retour |
| `NEW_MISSION` | BaseAgent | DroneAgent | Nouvelle mission |
| `VICTIM_DETECTED` | DroneAgent | VictimAgent | Notification de détection |
| `VICTIM_CONFIRMED` | VictimAgent | DroneAgent | Confirmation de la victime |
