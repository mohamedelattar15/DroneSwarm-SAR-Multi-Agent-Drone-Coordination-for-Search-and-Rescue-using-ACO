# 🚁 DroneSwarm-SAR

**Coordination multi-agents de drones pour la recherche et le sauvetage (SAR) par optimisation par colonies de fourmis (ACO)**

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![JADE](https://img.shields.io/badge/Framework-JADE%204.6-blue?style=for-the-badge)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

> Un essaim de drones autonomes qui **explorent une zone sinistrée**, **détectent les victimes**, **valident collectivement** l'information via des phéromones virtuelles, et **optimisent les trajets de secours** — le tout de manière **100 % décentralisée**.

---

## 📋 Table des matières

- [🎯 Vue d'ensemble](#-vue-densemble)
- [🧠 Comment ça marche ?](#-comment-ça-marche-)
  - [1. Le principe ACO](#1-le-principe-aco)
  - [2. La stigmergie](#2-la-stigmergie)
  - [3. Le cycle d'un drone](#3-le-cycle-dun-drone)
- [🔍 Que se passe-t-il quand un drone trouve une victime ?](#-que-se-passe-t-il-quand-un-drone-trouve-une-victime-)
- [🤝 Collaboration entre agents](#-collaboration-entre-agents)
  - [Protocole de communication](#protocole-de-communication)
  - [Validation collective](#validation-collective)
- [🏛️ Architecture](#️-architecture)
  - [Vue en couches](#vue-en-couches)
  - [Les agents JADE](#les-agents-jade)
  - [Structure des fichiers](#structure-des-fichiers)
- [⚙️ Formule ACO détaillée](#️-formule-aco-détaillée)
- [🚀 Installation & Lancement](#-installation--lancement)
- [🎛️ Scénarios & Configuration](#️-scénarios--configuration)
- [📊 Métriques & Observabilité](#-métriques--observabilité)
- [🧪 Tests](#-tests)
- [📚 Références](#-références)
- [📄 Licence](#-licence)

---

## 🎯 Vue d'ensemble

Dans une zone post-catastrophe, chaque minute compte : le taux de survie chute de **7 à 10 % par heure** après un séisme. Les secouristes humains ne peuvent pas couvrir rapidement une vaste zone dangereuse.

**DroneSwarm-SAR** résout ce problème avec un **essaim de drones autonomes** qui :

| Capacité | Description |
|---|---|
| 🗺️ **Explorer** | Couvrir la zone de façon coordonnée, sans carte centrale |
| 👁️ **Détecter** | Repérer les victimes dans un rayon de perception local |
| 🐜 **Communiquer** | Déposer/lire des phéromones virtuelles (stigmergie) |
| ✅ **Valider** | Confirmer une victime par consensus (plusieurs drones) |
| 📍 **Optimiser** | Mémoriser le meilleur chemin base ↔ victime |
| 🔋 **S'adapter** | Rentrer à la base si la batterie faiblit |

**Aucun serveur central** : chaque drone décide localement, et l'intelligence **émerge** de la collaboration.

---

## 🧠 Comment ça marche ?

### 1. Le principe ACO

L'**Ant Colony Optimization** (Dorigo, 1992) s'inspire du comportement des fourmis réelles :

```
🐜 Une fourmi explore au hasard
   ↓
🍎 Elle trouve de la nourriture
   ↓
🟡 En rentrant, elle dépose une phéromone sur son chemin
   ↓
🐜🐜 D'autres fourmis suivent cette piste (plus concentrée = plus attractive)
   ↓
💨 La phéromone s'évapore avec le temps (les mauvais chemins disparaissent)
   ↓
✅ Émergence du chemin le plus court, sans chef
```

**Transposé au sauvetage :**

| Concept ACO | Application SAR |
|---|---|
| 🐜 Fourmi | 🚁 Drone autonome |
| 🏠 Nid | 🏥 Base de secours |
| 🍎 Nourriture | 🆘 Victime |
| 🟡 Phéromone positive | Zone explorée avec succès |
| 🔴 Phéromone négative | Obstacle / zone dangereuse |
| 💨 Évaporation | Oubli progressif des vieilles pistes |

### 2. La stigmergie

La **stigmergie** est la communication **indirecte** via l'environnement. Les drones ne se parlent pas pour se coordonner : ils **modifient la grille de phéromones**, que les autres lisent.

```
Drone A explore la case (10,15) → dépose +5 de phéromone
Drone B voit (10,15) très marquée → la suit (chemin prometteur)
Drone C voit (10,15) → évite de la re-explorer inutilement
```

**Avantage :** pas besoin de communication directe permanente, robuste aux pannes.

### 3. Le cycle d'un drone

Chaque drone exécute une boucle périodique (`TickerBehaviour`, toutes les 150 ms) :

```
┌─────────────────────────────────────────────────────────────┐
│                    CYCLE D'UN DRONE                         │
│                                                             │
│  1. Vérifier si toutes les victimes sont trouvées → arrêt   │
│  2. Vérifier la batterie (timeout → retour base)            │
│  3. DÉTECTION (avant déplacement)                           │
│  4. Se déplacer (choix ACO)                                 │
│  5. DÉTECTION (après déplacement)                           │
│  6. Déposer des phéromones                                  │
│  7. Écouter les retours de l'environnement                  │
└─────────────────────────────────────────────────────────────┘
```

**États possibles :**

| État | Signification |
|---|---|
| `EXPLORING` | Explore activement la zone |
| `RETURNING` | Rentrer à la base **après avoir trouvé** une victime |
| `RETURNING_EMPTY` | Rentrer à la base (batterie faible, sans victime) |
| `IDLE` | Au repos à la base |

> ⚠️ **Important :** un drone **continue de détecter même en mode `RETURNING`**. S'il croise une nouvelle victime sur le chemin du retour, il la signale sans interrompre son retour.

---

## 🔍 Que se passe-t-il quand un drone trouve une victime ?

Voici la **séquence complète**, étape par étape :

```
┌──────────────────────────────────────────────────────────────────────┐
│  ÉTAPE 1 — DÉTECTION                                                 │
│                                                                      │
│  Le drone calcule la distance de Manhattan à chaque victime          │
│  non encore trouvée.                                                 │
│                                                                      │
│  Si distance ≤ perceptionRadius (4 cases) → VICTIME DÉTECTÉE         │
│                                                                      │
│  ✅ La détection est testée AVANT et APRÈS le déplacement,           │
│     pour ne rater aucune case traversée.                             │
└──────────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────────┐
│  ÉTAPE 2 — ENREGISTREMENT LOCAL                                      │
│                                                                      │
│  grid.addVictimPath(chemin)   → la victime est marquée "trouvée"     │
│  stats.recordAntPath(...)     → calcul du facteur de renfort ACO     │
│                                                                      │
│  Le chemin complet (base → ... → victime) est mémorisé.              │
└──────────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────────┐
│  ÉTAPE 3 — DÉPÔT DE PHÉROMONES                                       │
│                                                                      │
│  Le drone dépose une phéromone sur TOUT son chemin :                 │
│                                                                      │
│      quantité = 100 / (1 + distance_parcourue)                       │
│                                                                      │
│  → Plus le chemin est court, plus la phéromone est forte.            │
│  → Les autres drones seront attirés vers ce chemin prometteur.       │
└──────────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────────┐
│  ÉTAPE 4 — NOTIFICATION (messages ACL)                               │
│                                                                      │
│  📤 Drone → EnvironmentAgent : "VICTIM_FOUND:factor:signature:x,y"   │
│  📤 Drone → VictimAgent      : "victim_detected:x,y"                 │
│                                                                      │
│  📥 VictimAgent → Drone      : "victim_confirmed:n"  (accusé)        │
└──────────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────────┐
│  ÉTAPE 5 — VALIDATION COLLECTIVE (EnvironmentAgent)                  │
│                                                                      │
│  L'environnement vérifie :                                           │
│   • Cooldown respecté ? (évite le spam)                              │
│   • Signature exacte OU similarité ≥ 80 % avec le meilleur chemin ?  │
│                                                                      │
│  Si validé → renforcement élite + "PATH_ACCEPTED"                    │
│  Sinon     → "PATH_REJECTED" (le drone booste son exploration)       │
└──────────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────────┐
│  ÉTAPE 6 — RETOUR & SECOURS                                          │
│                                                                      │
│  Le drone passe en RETURNING et suit son chemin à l'envers.          │
│  À l'arrivée : Drone → BaseAgent : "VICTIM_RESCUED"                  │
│  La base comptabilise la victime secourue.                           │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 🤝 Collaboration entre agents

### Protocole de communication

Toute la communication passe par des **messages ACL (FIPA)** via JADE. Les constantes sont centralisées dans `MessageProtocol`.

```
                    ┌──────────────────┐
                    │ EnvironmentAgent │  ← orchestrateur
                    │  (grille, phéro) │
                    └────────┬─────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
   ┌────▼─────┐         ┌────▼─────┐         ┌───▼──────┐
   │DroneAgent│◄───────►│DroneAgent│  ...    │DroneAgent│
   │   #1     │         │   #2     │         │   #N     │
   └────┬─────┘         └──────────┘         └──────────┘
        │
        │  ┌──────────────┐        ┌──────────────┐
        ├─►│ VictimAgent  │        │  BaseAgent   │
        │  │ (confirmation)│        │ (logistique) │
        │  └──────────────┘        └──────────────┘
        └──────────────────────────────────────────►
```

**Table des messages :**

| Message | Émetteur → Récepteur | Rôle |
|---|---|---|
| `VICTIM_FOUND:factor:signature:x,y` | Drone → Environment | Proposer une détection |
| `PATH_ACCEPTED:factor:n` | Environment → Drone | Chemin validé (renfort) |
| `PATH_REJECTED:raison` | Environment → Drone | Chemin rejeté (booste l'exploration) |
| `DIVERSIFY` | Environment → Tous | Ordre de diversification (stagnation) |
| `victim_detected:x,y` | Drone → Victim | Notifier la victime |
| `victim_confirmed:n` | Victim → Drone | Accusé de réception |
| `VICTIM_RESCUED` | Drone → Base | Victime ramenée à la base |

### Validation collective

Une victime n'est **confirmée** qu'après plusieurs détections indépendantes :

| Seuil | Constante | Effet |
|---|---|---|
| **1** détection | — | Victime enregistrée, renfort ×30 % |
| **2** détections | `MEDIUM_THRESHOLD` | Renfort ×60 %, évaporation lente activée |
| **3** détections | `FULL_THRESHOLD` | Renfort ×100 % (confirmation complète) |

**Pourquoi ?** Cela **réduit les faux positifs** : si 3 drones indépendants détectent la même victime, la probabilité d'erreur est très faible.

---

## 🏛️ Architecture

### Vue en couches

Le projet suit une **architecture en couches** stricte, où la logique métier est **totalement découplée** de l'infrastructure JADE.

```
┌────────────────────────────────────────────────────────────────┐
│  COUCHE 4 : AGENTS (JADE)          ← Infrastructure            │
│  DroneAgent, EnvironmentAgent, BaseAgent, VictimAgent          │
│  → UNIQUEMENT : messaging ACL, behaviours, cycle de vie        │
└──────────────────────────┬─────────────────────────────────────┘
                           │ délègue
┌──────────────────────────▼─────────────────────────────────────┐
│  COUCHE 3 : PROTOCOLE & ORCHESTRATION                          │
│  MessageProtocol (constantes), Statistics (façade)             │
└──────────────────────────┬─────────────────────────────────────┘
                           │ utilise
┌──────────────────────────▼─────────────────────────────────────┐
│  COUCHE 2 : DOMAINE (POJO 100 % testable)                      │
│  PheromoneField · VictimRegistry · AntColonyOptimizer          │
│  DroneModel · Grid · Position                                  │
│  → AUCUNE dépendance à JADE ni à Swing                         │
└──────────────────────────┬─────────────────────────────────────┘
                           │ configuré par
┌──────────────────────────▼─────────────────────────────────────┐
│  COUCHE 1 : CONFIG & RUNTIME                                   │
│  SimulationConfig · SimulationRuntimeControl · Scenario        │
└────────────────────────────────────────────────────────────────┘
```

**Règle d'or :** le package `domain/` ne contient **jamais** d'import `jade.*` ni `javax.swing.*`. C'est ce qui rend la logique ACO **testable unitairement**.

### Les agents JADE

| Agent | Service DF | Rôle |
|---|---|---|
| **EnvironmentAgent** | `EnvironmentService` | Orchestre : crée les drones/victimes, gère l'évaporation, valide les détections, détecte la stagnation |
| **DroneAgent** | `DroneService` | Explore, détecte, dépose des phéromones, rentre à la base |
| **BaseAgent** | `BaseService` | Logistique : accuse réception des retours, compte les sauvetages |
| **VictimAgent** | `VictimService` | Représente une victime, confirme les détections |

### Structure des fichiers

```
📦 DroneSwarm-SAR
├── 📄 pom.xml
├── 📄 README.md
├── 📄 run.bat                          # Lanceur portable (auto JAVA_HOME)
│
├── 📂 src/main/java/
│   │
│   ├── 📂 domain/                      # 🧠 LOGIQUE MÉTIER PURE (0 dépendance JADE)
│   │   ├── 📂 pheromone/
│   │   │   └── PheromoneField.java     # Matrice + évaporation différenciée + élite
│   │   ├── 📂 victim/
│   │   │   └── VictimRegistry.java     # SOURCE UNIQUE de vérité des victimes
│   │   ├── 📂 aco/
│   │   │   └── AntColonyOptimizer.java # Sélection probabiliste ACO
│   │   └── 📂 drone/
│   │       └── DroneModel.java         # Machine à états + batterie
│   │
│   ├── 📂 agents/                      # 📡 INFRASTRUCTURE JADE
│   │   ├── 📂 protocol/
│   │   │   └── MessageProtocol.java    # Toutes les constantes de communication
│   │   ├── DroneAgent.java
│   │   ├── EnvironmentAgent.java
│   │   ├── BaseAgent.java
│   │   └── VictimAgent.java
│   │
│   ├── 📂 environment/                 # 🗺️ TOPOLOGIE & UI
│   │   ├── Grid.java                   # Grille, obstacles, voisinage
│   │   ├── Position.java               # Coordonnées (x, y)
│   │   ├── GridPanel.java              # Rendu graphique (heatmap)
│   │   └── SimulationFrame.java        # Fenêtre Swing + sliders
│   │
│   ├── 📂 utils/                       # ⚙️ CONFIG & RUNTIME
│   │   ├── SimulationConfig.java
│   │   ├── SimulationRuntimeControl.java
│   │   ├── SimulationScenario.java
│   │   ├── Statistics.java
│   │   └── MetricsExporter.java
│   │
│   └── 📂 main/
│       └── LauncherMain.java           # Point d'entrée
│
├── 📂 src/main/resources/
│   └── logback.xml
│
├── 📂 design/
│   ├── 📂 GAIA/                        # Modèle de rôles
│   └── 📂 AUML/                        # Diagrammes d'états
│
└── 📂 logs/                            # Métriques CSV générées
```

---

## ⚙️ Formule ACO détaillée

À chaque pas, le drone choisit le prochain voisin selon une **probabilité pondérée** :

$$
P(i) \propto \underbrace{(\tau_i + 1)^{\alpha}}_{\text{phéromone}} \cdot \underbrace{\eta_i^{\beta}}_{\text{attraction victime}} \cdot \underbrace{\text{persistance}}_{\text{continuité}} \cdot \underbrace{\text{pénalités}}_{\text{anti-boucle}} \cdot \underbrace{\text{biais}}_{\text{couverture}} \cdot \underbrace{\text{répulsion}}_{\text{anti-redondance}}
$$

| Terme | Formule | Rôle |
|---|---|---|
| **Phéromone** | $(\tau_i + 1)^{\alpha}$ | Suit les pistes prometteuses |
| **Attraction** | $\eta_i = 1 + \frac{50}{d+1}$ | Se dirige vers les victimes proches |
| **Persistance** | `3.0` si même direction, sinon `1.0` | Évite les zigzags (économie batterie) |
| **Pénalité visite** | `0.35` si visité récemment | Anti-boucle |
| **Pénalité retour** | `0.15` si case précédente | Anti-backtracking |
| **Biais directionnel** | $1 + \cos(\theta) \cdot 2$ | Chaque drone couvre sa zone |
| **Répulsion** | $1 - \sum \frac{r-d+1}{r} \cdot 0.3$ | Évite les autres drones |

**Exploration aléatoire :** avec probabilité `explorationRate` (20 %), le drone choisit un voisin **au hasard** → évite les optima locaux.

### Innovations vs ACO classique

| Innovation | ACO classique | DroneSwarm-SAR |
|---|---|---|
| **Évaporation** | Uniforme | **Différenciée** : ρ_confirmé = 0.25×ρ, ρ_bruit = 2×ρ |
| **Validation** | Aucune | **Collective** (seuils 2 et 3) |
| **Diversification** | Aucune | **Automatique** après stagnation (300 cycles) |
| **Pénalités** | Aucune | Visite + backtracking + persistance |
| **Guidage** | Heuristique simple | Gradient de Manhattan + perception locale |
| **Obstacles** | Non gérés | Cases impraticables + zones dangereuses |

---

## 🚀 Installation & Lancement

### Prérequis

| Outil | Version | Notes |
|---|---|---|
| **JDK** | 17+ | `C:\Program Files\Java\jdk-17` |
| **JADE** | 4.6.0 | Récupéré via Maven |
| **Maven** | 3.8+ | Optionnel (ou `run.bat`) |

### Méthode 1 — `run.bat` (recommandé)

```cmd
run.bat
```

Le script **détecte automatiquement** `JAVA_HOME`, **compile si nécessaire**, puis lance la simulation.

**Avec options :**

```cmd
run.bat --scenario=demo --no-sniffer
```

> 💡 **Sous PowerShell**, préfixez par `.\` :
> ```powershell
> .\run.bat --scenario=demo --no-sniffer
> ```

### Méthode 2 — Maven

```bash
mvn clean compile
mvn exec:java
```

### Méthode 3 — Compilation manuelle

```powershell
$m="$env:USERPROFILE\.m2\repository"
$cp="$m\com\tilab\jade\jade\4.6.0\jade-4.6.0.jar;$m\ch\qos\logback\logback-classic\1.5.3\logback-classic-1.5.3.jar;$m\ch\qos\logback\logback-core\1.5.3\logback-core-1.5.3.jar;$m\org\slf4j\slf4j-api\2.0.12\slf4j-api-2.0.12.jar"

$files = Get-ChildItem -Recurse -Path src\main\java -Filter *.java | ForEach-Object { $_.FullName }
& "C:\Program Files\Java\jdk-17\bin\javac.exe" -encoding UTF-8 -cp $cp -d target\classes $files

& "C:\Program Files\Java\jdk-17\bin\java.exe" -cp "$cp;target\classes" main.LauncherMain
```

### Interface graphique

Au lancement, vous verrez :

| Fenêtre | Contenu |
|---|---|
| **JADE RMA** | Gestion de la plateforme |
| **SimulationFrame** | La carte : heatmap phéromones, obstacles, base, victimes, drones |
| **Sniffer** (optionnel) | Visualisation des messages ACL en temps réel |

**Contrôles disponibles dans l'UI :**

- 🎚️ **Sliders** : vitesse, taux d'exploration, α, β, évaporation
- ⏸️ **Bouton Pause / Reprendre**
- 📜 **Zone d'événements** : détections en direct
- 📊 **Barre de progression** : victimes trouvées

---

## 🎛️ Scénarios & Configuration

### Scénarios prédéfinis

```cmd
run.bat --scenario=<nom>
```

| Scénario | Grille | Drones | Victimes | Obstacles | Cas d'usage |
|---|---|---|---|---|---|
| `demo` | 30×30 | 5 | 2 | 10 | Démonstration rapide |
| `standard` | 60×60 | 10 | 5 | 30 | Équilibre par défaut |
| `urban` | 80×80 | 20 | 8 | 80 | Milieu dense |
| `disaster` | 100×100 | 8 | 12 | 50 | Grande zone |
| `nocturnal` | 60×60 | 15 | 6 | 40 | Perception réduite (rayon 2) |

### Arguments CLI

| Argument | Effet |
|---|---|
| `--scenario=<nom>` | Sélectionne un scénario |
| `--no-sniffer` | Désactive le Sniffer JADE |

### Paramètres clés (`SimulationConfig`)

| Paramètre | Défaut | Description |
|---|---|---|
| `gridWidth` / `gridHeight` | 60 | Dimensions de la zone |
| `droneCount` | 10 | Nombre de drones |
| `victimCount` | 5 | Nombre de victimes |
| `obstacleCount` | 30 | Nombre d'obstacles |
| `alpha` (α) | 1.0 | Poids des phéromones |
| `beta` (β) | 1.5 | Poids de l'attraction victime |
| `rho` (ρ) | 0.02 | Taux d'évaporation de base |
| `randomExploration` | 0.20 | Probabilité d'exploration aléatoire |
| `perceptionRadius` | 4 | Rayon de détection (cases) |
| `stagnationThreshold` | 300 | Cycles avant diversification |
| `maxDroneSteps` | 3000 | Autonomie max (pas) |

---

## 📊 Métriques & Observabilité

### Export CSV

Toutes les 10 itérations, l'agent Environnement exporte :

```
logs/metrics_<timestamp>.csv
```

| Colonne | Description |
|---|---|
| `iteration` | Numéro d'itération |
| `shortest_path` | Meilleure distance connue |
| `confirmations` | Confirmations de la victime courante |
| `discoveries` | Nombre total de détections |
| `stagnation_cycles` | Cycles depuis la dernière amélioration |
| `drones_active` | Nombre de drones |
| `victims_found` | Victimes distinctes trouvées |

### Logs

Configurés via `logback.xml`. Niveaux utiles :

```
[INFO ] Drone #7: victime détectée à (29,15) (distance 3) [état: RETURNING]
[INFO ] [SEND] Drone #7 → Environment (victime à (29,15), renfort 30%)
[INFO ] [VALIDATION] Victime confirmée! Renfort x30% (confirmations: 1/3)
[INFO ] [RECV] Drone #7 : victime confirmée par Victim_29_15
[WARN ] [STAGNATION] Itération 320 → diversification
```

---

## 🧪 Tests

La couche `domain/` est **conçue pour être testée sans JADE** :

```java
// Exemple : tester l'optimiseur ACO en isolation
PheromoneField field = new PheromoneField(10, 10, SimulationConfig.defaults());
AntColonyOptimizer aco = new AntColonyOptimizer(new Random(42));
Position next = aco.selectNext(candidates, field, context);
assertNotNull(next);
```

```bash
mvn test
```

---

## 📚 Références

1. **Dorigo, M., & Gambardella, L. M.** (1997). *Ant colony system: A cooperative learning approach to the traveling salesman problem.* IEEE Transactions on Evolutionary Computation, 1(1), 53-66.
2. **Wooldridge, M.** (2009). *An Introduction to MultiAgent Systems.* John Wiley & Sons.
3. **Bellifemine, F., Caire, G., & Greenwood, D.** (2007). *Developing Multi-Agent Systems with JADE.* Wiley.
4. **Pérez-Carabaza, S. et al.** (2022). *A multi-UAV minimum time search planner based on ACOR.* Engineering Applications of AI, 107, 104525.
5. **Alers, S. et al.** (2014). *Bee-inspired foraging in an experimental multi-agent system.* Adaptive and Learning Agents.
6. **INSARAG** (2019). *Guidelines and Methodology.* International Search and Rescue Advisory Group.

---

## 📄 Licence

Ce projet est distribué sous licence **MIT**. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

---

## 👥 Auteur

**Mohamed El Attar** — *Master's in AI and Data Science*

[![GitHub](https://img.shields.io/badge/GitHub-mohamedelattar15-181717?style=flat-square&logo=github)](https://github.com/mohamedelattar15)

---

<div align="center">

**🚁 DroneSwarm-SAR** — *L'intelligence émerge de la collaboration, pas d'un chef.*

</div>
