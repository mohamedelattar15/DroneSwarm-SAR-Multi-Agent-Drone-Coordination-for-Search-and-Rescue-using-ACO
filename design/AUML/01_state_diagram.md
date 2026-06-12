# Diagramme d'États AUML - DroneAgent

```
                    ┌─────────────┐
                    │   Initial   │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
              ┌────>│  EXPLORING  │
              │     └──────┬──────┘
              │            │
              │            │ victime détectée
              │            ▼
              │     ┌─────────────┐
              │     │ VICTIM_FOUND│
              │     └──────┬──────┘
              │            │
              │            │ depositPheromones() + notify()
              │            ▼
              │     ┌─────────────┐
              │     │  RETURNING  │
              │     └──────┬──────┘
              │            │
              │            │ arrivée à la base
              │            ▼
              │     ┌─────────────┐
              │     │    IDLE     │
              │     └──────┬──────┘
              │            │
              │            │ new_mission reçu
              └────────────┘

              ┌──────────────────────────────────────┐
              │  Timeout: steps > maxDroneSteps      │
              │  → RETURNING_EMPTY (batterie faible) │
              └──────────────────────────────────────┘
```

## Diagramme de Séquence - Sauvetage

```
DroneAgent         EnvironmentAgent      VictimAgent       BaseAgent
   │                     │                   │              │
   │ (exploration)       │                   │              │
   │──VICTIM_FOUND──────>│                   │              │
   │                     │ (valide signature)│              │
   │<──PATH_ACCEPTED─────│                   │              │
   │                     │                   │              │
   │──VICTIM_DETECTED───────────────────────>│              │
   │<────────────────────────────────────────│              │
   │                     │                   │              │
   │ (retour à la base)  │                   │              │
   │──DRONE_RETURNED───────────────────────────────────────>│
   │<────────────────────────────────────────────────────────│
   │                     │                   │              │
```
