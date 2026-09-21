# 🏛️ Architecture — DroneSwarm-SAR

> Documentation technique détaillée de l'architecture logicielle du système multi-agents.

---

## 📋 Table des matières

- [1. Principes directeurs](#1-principes-directeurs)
- [2. Vue en couches](#2-vue-en-couches)
- [3. Couche Domaine](#3-couche-domaine)
- [4. Couche Agents (JADE)](#4-couche-agents-jade)
- [5. Protocole de communication](#5-protocole-de-communication)
- [6. Flux d'exécution](#6-flux-dexécution)
- [7. Concurrence & thread-safety](#7-concurrence--thread-safety)
- [8. Décisions de conception](#8-décisions-de-conception)
- [9. Évolution & extension](#9-évolution--extension)

---

## 1. Principes directeurs

L'architecture repose sur **quatre principes** non négociables :

| Principe | Application concrète |
|---|---|
| **Séparation des préoccupations** | Domaine ≠ Infrastructure ≠ UI |
| **Inversion de dépendance** | Les agents dépendent du domaine, jamais l'inverse |
| **Source unique de vérité** | Un état = un seul endroit qui le détient |
| **Testabilité** | Le domaine se teste sans JADE ni Swing |

### Règle d'or

> Le package `domain/` ne contient **jamais** d'import `jade.*` ni `javax.swing.*`.

Cette contrainte garantit que la logique métier (ACO, phéromones, victimes) est **réutilisable** et **testable unitairement**, indépendamment du framework d'agents.

---

## 2. Vue en couches

```mermaid
graph TD
    subgraph L4["COUCHE 4 — INFRASTRUCTURE JADE"]
        A1[DroneAgent]
        A2[EnvironmentAgent]
        A3[BaseAgent]
        A4[VictimAgent]
    end

    subgraph L3["COUCHE 3 — PROTOCOLE & ORCHESTRATION"]
        P1[MessageProtocol]
        P2[Statistics]
    end

    subgraph L2["COUCHE 2 — DOMAINE"]
        D1[PheromoneField]
        D2[VictimRegistry]
        D3[AntColonyOptimizer]
        D4[DroneModel]
        D5[Grid]
        D6[Position]
    end

    subgraph L1["COUCHE 1 — CONFIG & RUNTIME"]
        C1[SimulationConfig]
        C2[SimulationRuntimeControl]
        C3[SimulationScenario]
    end

    L4 --> L3
    L3 --> L2
    L2 --> L1

    style L4 fill:#4dabf7,color:#fff
    style L3 fill:#9775fa,color:#fff
    style L2 fill:#51cf66,color:#fff
    style L1 fill:#ffd43b,color:#000
```

### Responsabilités par couche

| Couche | Responsabilité | Dépendances autorisées |
|---|---|---|
| **4 — Agents** | Cycle de vie JADE, behaviours, messagerie ACL | Couches 1-3, JADE |
| **3 — Protocole** | Constantes de communication, façade métriques | Couches 1-2 |
| **2 — Domaine** | Logique métier pure (ACO, phéromones, victimes, états) | Couche 1 uniquement |
| **1 — Config** | Paramétrage, contrôle runtime | Aucune |

---

## 3. Couche Domaine

### 3.1 `PheromoneField`

**Responsabilité :** stocker, évaporer et renforcer les phéromones.

```mermaid
classDiagram
    class PheromoneField {
        -double[][] matrix
        -Set~Long~ confirmedCells
        -int cachedConfirmationCount
        +deposit(Position, double)
        +repel(Position, double)
        +evaporate(List~Position~, int)
        +reinforceElite(List~Position~, double)
        +resetExcept(List~Position~)
        +snapshot() double[][]
    }
```

**Points clés :**

- **Évaporation différenciée** : les cellules d'un chemin confirmé s'évaporent à `ρ × 0.25`, le reste à `ρ × 2.0`.
- **Cache** : le set des cellules confirmées est reconstruit uniquement quand le nombre de confirmations change (optimisation).
- **Bornes** : phéromones positives plafonnées à `100`, négatives plancher à `-20`.

### 3.2 `VictimRegistry`

**Responsabilité :** **source unique de vérité** pour les victimes.

```mermaid
classDiagram
    class VictimRegistry {
        -Map~Position,VictimRecord~ victims
        -Position currentTarget
        +record(List~Position~, double) DetectionResult
        +getVictimCount() int
        +isFound(Position) boolean
        +getBestPath() List~Position~
        +similarityToCurrentBest(List~Position~) double
    }
    class VictimRecord {
        -List~Position~ bestPath
        -double bestDistance
        -String signature
        -int confirmations
    }
    VictimRegistry "1" --> "*" VictimRecord
```

**Pourquoi une source unique ?**

Avant le refactoring, le nombre de victimes était stocké à **3 endroits** (`Grid`, `Statistics`, `SimulationFrame`), ce qui causait des désynchronisations (ex. l'UI affichait `1/5` alors que 3 victimes étaient trouvées).

**Désormais :** `VictimRegistry` détient l'état, et `Grid`, `Statistics` et l'UI **délèguent**.

### 3.3 `AntColonyOptimizer`

**Responsabilité :** sélection probabiliste du prochain pas (cœur ACO).

```mermaid
classDiagram
    class AntColonyOptimizer {
        -Random random
        +selectNext(List~Position~, PheromoneField, StepContext) Position
        -weight(Position, PheromoneField, StepContext) double
        -persistence(Position, StepContext) double
        -victimAttraction(Position, List~Position~) double
        -directionalBias(Position, StepContext) double
        -repulsion(Position, StepContext) double
    }
    class StepContext {
        +Position current
        +Position lastDirection
        +List~Position~ otherDrones
        +List~Position~ undiscoveredVictims
        +double alpha
        +double beta
        +double explorationRate
    }
    AntColonyOptimizer ..> StepContext : utilise
    AntColonyOptimizer ..> PheromoneField : lit
```

**Découplage :** `StepContext` est un **DTO immuable** qui transporte tout le contexte de décision. L'optimiseur ne connaît ni JADE, ni `Grid`, ni `DroneAgent` — il reçoit juste des données et retourne un choix.

### 3.4 `DroneModel`

**Responsabilité :** machine à états + gestion de l'autonomie.

```mermaid
stateDiagram-v2
    [*] --> EXPLORING
    EXPLORING --> RETURNING : onVictimFound()
    EXPLORING --> RETURNING_EMPTY : onBatteryLow()
    RETURNING --> IDLE : onArrivedAtBase()
    RETURNING_EMPTY --> IDLE : onArrivedAtBase()
    IDLE --> EXPLORING : onDeparture()
    EXPLORING --> IDLE : onMissionComplete()
    RETURNING --> IDLE : onLost()
```

**Transitions explicites** via des méthodes nommées (`onVictimFound`, `onBatteryLow`…) plutôt que des affectations directes → logique centralisée et testable.

### 3.5 `Grid`

**Responsabilité :** topologie (dimensions, obstacles, voisinage) et positions des drones.

**Délégation :**
- Phéromones → `PheromoneField`
- Victimes → `VictimRegistry`

`Grid` conserve une API de compatibilité (`addPheromone`, `getVictimsFound`…) qui **délègue** aux composants du domaine.

---

## 4. Couche Agents (JADE)

### 4.1 Rôles

| Agent | Service DF | Behaviour principal | Responsabilité |
|---|---|---|---|
| `EnvironmentAgent` | `EnvironmentService` | `EvaporationBehaviour`, `RecruitmentListener` | Orchestration, validation, stagnation |
| `DroneAgent` | `DroneService` | `DroneMoveBehaviour`, `EnvironmentFeedbackListener` | Exploration, détection |
| `BaseAgent` | `BaseService` | `HandleReturningDrones` | Logistique |
| `VictimAgent` | `VictimService` | `HandleDetection` | Confirmation |

### 4.2 Cycle de vie d'un agent

```mermaid
sequenceDiagram
    participant L as LauncherMain
    participant R as JADE Runtime
    participant E as EnvironmentAgent
    participant D as DroneAgent

    L->>R: createMainContainer()
    L->>E: createNewAgent(Environment)
    E->>E: setup()
    E->>E: new Grid(config)
    E->>R: createNewAgent(Drone_i) × N
    R->>D: setup()
    D->>D: registerWithDF()
    D->>E: DFService.search(EnvironmentService)
    D->>D: addBehaviour(DroneMoveBehaviour)
    Note over D: Boucle périodique (150 ms)
```

### 4.3 Délégation agents → domaine

```mermaid
flowchart LR
    subgraph Agent["DroneAgent (infrastructure)"]
        B[DroneMoveBehaviour]
    end
    subgraph Domain["Domain (métier)"]
        M[DroneModel]
        O[AntColonyOptimizer]
        P[PheromoneField]
    end

    B -->|"model.tick()"| M
    B -->|"aco.selectNext()"| O
    B -->|"field.get()"| P

    style Agent fill:#e7f5ff
    style Domain fill:#ebfbee
```

**Principe :** l'agent **orchestre** (quand appeler quoi), le domaine **décide** (comment).

---

## 5. Protocole de communication

### 5.1 Diagramme de séquence complet

```mermaid
sequenceDiagram
    autonumber
    participant D as DroneAgent
    participant E as EnvironmentAgent
    participant V as VictimAgent
    participant B as BaseAgent

    loop Chaque tick (150 ms)
        D->>D: Détection (rayon 4)
    end

    D->>E: VICTIM_FOUND:factor:sig:x,y
    D->>V: victim_detected:x,y
    V-->>D: victim_confirmed:n

    alt Validé (cooldown OK + similarité ≥ 80%)
        E->>E: reinforceElite()
        E-->>D: PATH_ACCEPTED:factor:n
    else Rejeté
        E-->>D: PATH_REJECTED:SIGNATURE
        D->>D: boostExploration()
    end

    opt Stagnation détectée
        E-->>D: DIVERSIFY (broadcast)
    end

    D->>B: VICTIM_RESCUED
    B-->>D: NEW_MISSION
```

### 5.2 Table des messages

| Constante | Émetteur → Récepteur | Format | Rôle |
|---|---|---|---|
| `VICTIM_FOUND` | Drone → Environment | `VICTIM_FOUND:factor:sig:x,y` | Proposer une détection |
| `PATH_ACCEPTED` | Environment → Drone | `PATH_ACCEPTED:factor:n` | Chemin validé |
| `PATH_REJECTED` | Environment → Drone | `PATH_REJECTED:raison` | Chemin rejeté |
| `DIVERSIFY` | Environment → Tous | `DIVERSIFY` | Ordre de diversification |
| `VICTIM_DETECTED` | Drone → Victim | `victim_detected:x,y` | Notification victime |
| `VICTIM_CONFIRMED` | Victim → Drone | `victim_confirmed:n` | Accusé de réception |
| `VICTIM_RESCUED` | Drone → Base | `VICTIM_RESCUED` | Victime ramenée |
| `NEW_MISSION` | Base → Drone | `new_mission` | Nouvelle mission |

### 5.3 Ontologies

| Ontologie | Usage |
|---|---|
| `DRONE_RETURNED` | Notification de retour à la base |
| `NEW_MISSION` | Attribution d'une nouvelle mission |
| `VICTIM_DETECTED` | Détection d'une victime |
| `VICTIM_CONFIRMED` | Confirmation d'une victime |

---

## 6. Flux d'exécution

### 6.1 Démarrage

```mermaid
flowchart TD
    A[LauncherMain.main] --> B[Runtime.instance]
    B --> C[createMainContainer]
    C --> D[createNewAgent Environment]
    D --> E[EnvironmentAgent.setup]
    E --> F[new Grid config]
    E --> G[createDrones × N]
    E --> H[createVictims × M]
    E --> I[SimulationFrame]
    E --> J[addBehaviour Evaporation]
    E --> K[addBehaviour Recruitment]

    style A fill:#ffd43b
    style E fill:#4dabf7,color:#fff
```

### 6.2 Boucle principale

```mermaid
flowchart TD
    T[Tick 150 ms] --> P{Pause ?}
    P -->|Oui| T
    P -->|Non| S[stats.tick]
    S --> EV["grid.evaporateDifferentiated()"]
    EV --> RE{Renfort élite ?}
    RE -->|"iteration % 20 == 0"| EL[applyEliteReinforcement]
    RE -->|Non| ST{Stagnation ?}
    EL --> ST
    ST -->|Oui| DIV["partialReset + broadcast DIVERSIFY"]
    ST -->|Non| SYNC[syncVictimDisplay]
    DIV --> SYNC
    SYNC --> MET{iteration % 10 == 0}
    MET -->|Oui| CSV[Export CSV]
    MET -->|Non| T
    CSV --> T
```

---

## 7. Concurrence & thread-safety

### 7.1 Modèle de concurrence

| Composant | Thread | Synchronisation |
|---|---|---|
| `DroneAgent` | 1 thread JADE par drone | Aucun état partagé mutable |
| `EnvironmentAgent` | 1 thread JADE | `synchronized` sur le domaine |
| `PheromoneField` | Partagé | `synchronized` sur toutes les mutations |
| `VictimRegistry` | Partagé | `synchronized` sur toutes les mutations |
| `Grid.dronePositions` | Partagé | `ConcurrentHashMap` |
| `SimulationRuntimeControl` | Partagé | `AtomicInteger` / `AtomicBoolean` |

### 7.2 Stratégie

```mermaid
flowchart LR
    subgraph Threads["Threads JADE"]
        T1[Drone 1]
        T2[Drone 2]
        T3[Environment]
    end
    subgraph Shared["État partagé (synchronisé)"]
        PF[PheromoneField]
        VR[VictimRegistry]
        DP[ConcurrentHashMap]
    end
    T1 --> Shared
    T2 --> Shared
    T3 --> Shared

    style Shared fill:#fff3bf
```

**Règle :** toute mutation d'état partagé passe par une méthode `synchronized` du domaine. Les lectures exposent des **snapshots défensifs** (copies).

---

## 8. Décisions de conception

### 8.1 Pourquoi `domain/` séparé de `agents/` ?

| Avant | Après |
|---|---|
| ACO mélangé à `DroneAgent` (600 lignes) | `AntColonyOptimizer` isolé (~220 lignes) |
| Impossible de tester sans JADE | Testable avec un simple `new` |
| 4 responsabilités par classe | 1 responsabilité par classe |

### 8.2 Pourquoi `VictimRegistry` centralisé ?

Trois compteurs désynchronisés causaient des bugs d'affichage. Une source unique élimine la classe entière de bugs.

### 8.3 Pourquoi `StepContext` (DTO) ?

Passer 16 paramètres à `selectNext()` serait illisible. Un DTO immuable rend la signature claire et le test facile.

### 8.4 Pourquoi `MessageProtocol` ?

Les chaînes magiques (`"PATH_ACCEPTED"`) dispersées sont une source de fautes de frappe silencieuses. Centraliser → une seule référence.

### 8.5 Pourquoi la détection AVANT et APRÈS le déplacement ?

Un drone qui se déplace de 1 case peut traverser une zone de perception. Tester les deux positions garantit qu'aucune victime n'est ratée.

---

## 9. Évolution & extension

### 9.1 Ajouter un nouveau comportement de drone

1. Créer la logique dans `domain/` (POJO testable)
2. Ajouter un `Behaviour` dans `DroneAgent` qui **délègue** au domaine
3. Si nouveau message → ajouter la constante dans `MessageProtocol`

### 9.2 Ajouter un nouveau type d'agent

1. Créer la classe dans `agents/`
2. Enregistrer un service DF (`MessageProtocol.SERVICE_*`)
3. Ajouter les constantes de communication

### 9.3 Points d'extension prévus

| Extension | Où intervenir |
|---|---|
| Nouvelle heuristique ACO | `AntColonyOptimizer.weight()` |
| Nouveau type de phéromone | `PheromoneField` |
| Nouveau scénario | `SimulationScenario` |
| Nouvelle métrique | `MetricsExporter` + `Statistics` |
| Nouvel état de drone | `DroneModel.State` |

### 9.4 Dette technique restante

| Point | Priorité | Note |
|---|---|---|
| Tests JUnit sur `domain/` | Haute | La couche est prête, tests à écrire |
| `serialVersionUID` sur classes JADE | Basse | Warnings uniquement |
| Optimisation évaporation (sparse) | Basse | 60×60 = 3600 cellules, acceptable |

---

<div align="center">

**DroneSwarm-SAR** — Architecture en couches pour un SMA robuste et testable.

</div>
