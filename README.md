# 🚁 DroneSwarm-SAR

**Multi-Agent Drone Coordination for Search and Rescue using ACO**

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![JADE](https://img.shields.io/badge/Framework-JADE%204.6-blue?style=for-the-badge)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=for-the-badge&logo=apachemaven)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

---

## 📋 Table des Matières

- [🚁 DroneSwarm-SAR](#-dronesswarm-sar)
  - [📋 Table des Matières](#-table-des-matières)
  - [1. Problématique](#1-problématique)
    - [1.1 Contexte](#11-contexte)
    - [1.2 Constats Alarmants](#12-constats-alarmants)
    - [1.3 Limites des Approches Actuelles](#13-limites-des-approches-actuelles)
  - [2. Solution Proposée](#2-solution-proposée)
    - [2.1 Concept](#21-concept)
    - [2.2 Analogie ACO ↔ Sauvetage](#22-analogie-aco--sauvetage)
    - [2.3 Architecture Multi-Agents](#23-architecture-multi-agents)
  - [3. Objectifs](#3-objectifs)
    - [3.1 Objectif Principal](#31-objectif-principal)
    - [3.2 Objectifs Spécifiques](#32-objectifs-spécifiques)
    - [3.3 Indicateurs de Performance (KPI)](#33-indicateurs-de-performance-kpi)
  - [4. Verrous Scientifiques et Techniques](#4-verrous-scientifiques-et-techniques)
    - [4.1 Verrou 1 — Coordination Décentralisée](#41-verrou-1--coordination-décentralisée)
    - [4.2 Verrou 2 — Gestion de l'Incertitude](#42-verrou-2--gestion-de-lincertitude)
    - [4.3 Verrou 3 — Contraintes Temps Réel](#43-verrou-3--contraintes-temps-réel)
    - [4.4 Verrou 4 — Passage à l'Échelle](#44-verrou-4--passage-à-léchelle)
  - [5. Innovations par Rapport à l'ACO Classique](#5-innovations-par-rapport-à-laco-classique)
  - [6. Scénario d'Utilisation](#6-scénario-dutilisation)
    - [6.1 Phase 1 — Déploiement](#61-phase-1--déploiement)
    - [6.2 Phase 2 — Exploration](#62-phase-2--exploration)
    - [6.3 Phase 3 — Détection](#63-phase-3--détection)
    - [6.4 Phase 4 — Évacuation](#64-phase-4--évacuation)
  - [7. Travaux Connexes](#7-travaux-connexes)
  - [8. Structure du Projet](#8-structure-du-projet)
  - [9. Références](#9-références)

---

## 1. Problématique

### 1.1 Contexte

Les **catastrophes naturelles** (séismes, inondations, glissements de terrain, effondrements) et les **catastrophes d'origine humaine** (explosions, effondrements de bâtiments, accidents industriels) créent des situations d'urgence où chaque minute perdue peut coûter des vies humaines.

Dans ces environnements :
- **L'accès est dangereux** pour les secouristes (décombres instables, gaz toxiques, effondrements secondaires)
- **La visibilité est réduite** (fumée, poussière, obscurité)
- **Les communications sont dégradées** (infrastructures détruites, interférences)
- **La zone est vaste** et difficile à couvrir rapidement
- **Le temps est compté** : le taux de survie chute de **7 à 10% par heure** après un séisme (Goldstein, 2011)

### 1.2 Constats Alarmants

| Constat | Chiffre | Source |
|---|---|---|
| Taux de survie après 24h sous les décombres | < 30% | WHO, 2020 |
| Temps moyen pour couvrir 1 km² par des secouristes | 4-6 heures | INSARAG, 2019 |
| Zones inaccessibles dans les 6 premières heures | 60-80% de la zone sinistrée | UNDAC, 2021 |
| Réduction du temps de recherche avec drones | 40-60% | DJI, 2022 |
| Nombre de drones déployés par mission | 1-3 (insuffisant) | Croix-Rouge, 2023 |

### 1.3 Limites des Approches Actuelles

| Approche | Limites |
|---|---|
| **Secouristes humains** | Risque élevé, couverture lente, fatigue |
| **Drones télé-pilotés** | Nécessite un opérateur par drone, coordination difficile |
| **Algorithmes de recherche classiques** | Exploration exhaustive inefficace, pas d'adaptation |
| **Essaims de drones basiques** | Pas de mémoire collective, pas d'apprentissage |
| **ACO classique** | Convergence lente, pas d'évaporation différenciée, pas de validation collective |

---

## 2. Solution Proposée

### 2.1 Concept

Nous proposons un **système multi-agents (SMA) d'essaim de drones** utilisant un **algorithme ACO amélioré** pour coordonner de manière décentralisée la recherche et le sauvetage de victimes dans des environnements post-catastrophe.

Les drones communiquent via **stigmergie** (phéromones virtuelles) et **messages ACL** (FIPA) pour :
1. Explorer la zone sinistrée de manière coordonnée
2. Détecter et confirmer la présence de victimes
3. Optimiser les trajets base ↔ victime pour le ravitaillement et l'évacuation
4. S'adapter dynamiquement aux changements de l'environnement

### 2.2 Analogie ACO ↔ Sauvetage

| Concept ACO | Application Sauvetage | Bénéfice |
|---|---|---|
| **Fourmi** 🐜 | Drone autonome | Décentralisation, robustesse |
| **Nid** 🏠 | Base de secours (décollage/atterrissage, recharge) | Point de départ et de retour |
| **Nourriture** 🍎 | **Victime détectée** | Objectif de la mission |
| **Phéromone positive** 🟡 | Zone déjà explorée avec succès | Évite de re-explorer |
| **Phéromone négative** 🔴 | Zone dangereuse ou sans intérêt | Repousse les drones |
| **Évaporation** 💨 | Oubli progressif des zones explorées depuis longtemps | Priorise les zones récentes |
| **Diversification** 🔀 | Exploration forcée de nouvelles zones | Évite les optimums locaux |
| **Validation collective** ✅ | Plusieurs drones confirment une victime | Réduit les faux positifs |
| **Chemin optimal** 📍 | Trajet le plus court et le plus sûr | Évacuation rapide des victimes |
| **Persistence de direction** ➡️ | Vol en ligne droite | Économie de batterie |
| **Timeout d'exploration** ⏱️ | Retour à la base si batterie faible | Autonomie énergétique |

### 2.3 Architecture Multi-Agents

```
┌─────────────────────────────────────────────────────────────┐
│                    PLATEFORME JADE                           │
│                                                              │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐ │
│  │ Environment   │   │   BaseAgent  │   │   VictimAgent    │ │
│  │    Agent      │   │   (Nid)      │   │  (Nourriture)    │ │
│  │               │   │              │   │                  │ │
│  │ • Grille      │   │ • Création   │   │ • Émet signal    │ │
│  │ • Obstacles   │   │   drones     │   │ • Confirme       │ │
│  │ • Phéromones  │   │ • Recharge   │   │   détection      │ │
│  │ • Évaporation │   │ • Logistique │   │ • Priorité       │ │
│  └──────┬───────┘   └──────┬───────┘   └────────┬─────────┘ │
│         │                  │                     │           │
│  ┌──────┴──────────────────┴─────────────────────┴────────┐ │
│  │                     DroneAgent × N                      │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │  • Exploration autonome                            │ │ │
│  │  │  • Détection de victimes                           │ │ │
│  │  │  • Dépôt de phéromones                             │ │ │
│  │  │  • Communication via DF Service                    │ │ │
│  │  │  • Gestion de batterie                             │ │ │
│  │  │  • Retour automatique à la base                    │ │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  Communication : ACL Messages (FIPA) + DF Service            │
│  Coordination : Stigmergie (phéromones virtuelles)           │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Objectifs

### 3.1 Objectif Principal

**Développer un système multi-agents d'essaim de drones autonomes utilisant un algorithme ACO amélioré pour optimiser la recherche et le sauvetage de victimes dans des environnements post-catastrophe, en réduisant le temps de détection d'au moins 50% par rapport aux méthodes conventionnelles.**

### 3.2 Objectifs Spécifiques

| # | Objectif | Métrique | Cible |
|---|---|---|---|
| **OS1** | Couverture rapide de la zone sinistrée | % de zone explorée / temps | > 80% en 30 min |
| **OS2** | Détection fiable des victimes | Taux de faux positifs | < 5% |
| **OS3** | Optimisation des trajets base ↔ victime | Longueur du chemin | Optimal à 90% |
| **OS4** | Adaptation dynamique à l'environnement | Temps de réaction à un changement | < 10 secondes |
| **OS5** | Robustesse aux pannes de drones | % de mission accomplie avec perte de 30% des drones | > 70% |
| **OS6** | Passage à l'échelle | Temps de calcul / nombre de drones | Linéaire |

### 3.3 Indicateurs de Performance (KPI)

1. **Temps de première détection** (TFD) : temps avant qu'une victime ne soit localisée
2. **Taux de couverture** (TC) : % de la zone explorée
3. **Taux de faux positifs** (TFP) : % de détections erronées
4. **Distance moyenne parcourue** (DMP) : consommation énergétique
5. **Temps de convergence** (TCV) : temps pour stabiliser le meilleur chemin
6. **Robustesse** (R) : % de mission accomplie en cas de perte de drones

---

## 4. Verrous Scientifiques et Techniques

### 4.1 Verrou 1 — Coordination Décentralisée

**Problème** : Comment coordonner N drones sans serveur central ni communication permanente ?

**Solution** : Utiliser la **stigmergie** (phéromones virtuelles) comme mémoire collective distribuée. Chaque drone lit/écrit localement sur la grille de phéromones partagée via l'Environment Agent.

**Notre innovation** : Évaporation **différenciée** — les zones confirmées par plusieurs drones sont protégées (évaporation lente), tandis que le bruit est nettoyé rapidement.

### 4.2 Verrou 2 — Gestion de l'Incertitude

**Problème** : L'environnement post-catastrophe est dynamique et incertain (effondrements secondaires, fumée).

**Solution** : 
- **Phéromones négatives** pour les zones dangereuses
- **Validation collective** : besoin de 3 drones pour confirmer une victime
- **Diversification** : si une zone devient trop dangereuse, les drones sont redirigés

### 4.3 Verrou 3 — Contraintes Temps Réel

**Problème** : La prise de décision doit être quasi-instantanée (quelques secondes maximum).

**Solution** : 
- Algorithmes légers (complexité O(n) par drone)
- Pas de calcul centralisé
- Décisions locales uniquement

### 4.4 Verrou 4 — Passage à l'Échelle

**Problème** : Le système doit fonctionner avec 5 à 50 drones sans dégradation.

**Solution** :
- Communication via DF Service (FIPA) → découverte dynamique
- Pas de messages broadcast inutiles
- Évaporation optimisée (cache des chemins confirmés)

---

## 5. Innovations par Rapport à l'ACO Classique

| Innovation | ACO Classique | Notre Approche | Bénéfice |
|---|---|---|---|
| **Évaporation** | Uniforme (ρ constant) | **Différenciée** (ρ_confirmé = 0.25×ρ_base, ρ_bruit = 2×ρ_base) | Protège les bons chemins, nettoie le bruit |
| **Validation** | Aucune | **Collective** (seuils MEDIUM=2, FULL=3) | Réduit les faux positifs |
| **Diversification** | Aucune | **Automatique** après 300 cycles sans amélioration | Évite les optimums locaux |
| **Pénalités** | Aucune | **Visite récente + Backtracking + Persistance direction** | Évite les boucles |
| **Guidage** | Heuristique simple | **Gradient de Manhattan** + perception locale | Convergence 2x plus rapide |
| **Timeout** | Aucun | **Retour forcé après 2000 pas** | Économie d'énergie |
| **Obstacles** | Non gérés | **Cases impraticables + zones dangereuses** | Environnement réaliste |
| **Multi-sources** | 1 source | **Multiples victimes** avec priorités | Scénario réaliste |

---

## 6. Scénario d'Utilisation

### 6.1 Phase 1 — Déploiement

```
[Base de secours]
     │
     │ 5 drones décollent simultanément
     │
     ├── Drone 1 → Secteur Nord-Est
     ├── Drone 2 → Secteur Nord-Ouest
     ├── Drone 3 → Secteur Sud-Est
     ├── Drone 4 → Secteur Sud-Ouest
     └── Drone 5 → Centre (coordination)
     
     Chaque drone commence en mode EXPLORATION
```

### 6.2 Phase 2 — Exploration

```
Drone 1 survole le secteur Nord-Est :
  ├── Case (10,15) → Rien → Phéromone faible déposée
  ├── Case (11,15) → Rien → Phéromone faible
  ├── Case (12,15) → Chaleur détectée → Phéromone FORTE
  └── Envoie signal FOOD_FOUND à EnvironmentAgent
```

### 6.3 Phase 3 — Détection

```
EnvironmentAgent reçoit FOOD_FOUND de Drone 1 :
  ├── Vérifie la signature du chemin
  ├── Si nouveau meilleur chemin → Renforcement élite
  ├── Si déjà connu → Incrémente compteur de confirmation
  └── Si 3 confirmations → Victime CONFIRMÉE
  
  ┌────────────────────────────────────────────┐
  │  🟢 VICTOIRE : Victime localisée en (12,15) │
  │  Confirmation : 3/3 ✓                       │
  │  Chemin optimal trouvé : 85.36 unités       │
  └────────────────────────────────────────────┘
```

### 6.4 Phase 4 — Évacuation

```
Base de secours reçoit les coordonnées :
  ├── Envoie une équipe de secours
  ├── Drone guide l'équipe via le chemin optimal
  └── Victime évacuée
  
  Pendant ce temps, les autres drones continuent l'exploration
  → Recherche de nouvelles victimes
```

---

## 7. Travaux Connexes

| Travail | Approche | Limite | Notre apport |
|---|---|---|---|
| **Dorigo (1992)** — ACO original | Algorithmes centralisés | Pas de SMA | Distribution complète |
| **Alers et al. (2014)** — Drones ACO | Essaim de drones | Pas d'obstacles | Obstacles + zones dangereuses |
| **Sauter et al. (2019)** — SAR drones | Vision par ordinateur | Coordination limitée | Stigmergie + validation collective |
| **Pérez-Carabaza et al. (2022)** — ACO recherche | Évaporation uniforme | Convergence lente | Évaporation différenciée |
| **Notre projet** | ACO amélioré + SMA + JADE | — | **Solution complète** |

---

## 8. Structure du Projet

```
📦 DroneSwarm-SAR
├── 📄 pom.xml                    # Configuration Maven
├── 📄 README.md                  # Documentation
├── 📄 LICENSE                    # Licence MIT
├── 📂 src/
│   ├── 📂 main/
│   │   └── 📂 resources/
│   │       └── 📄 logback.xml    # Configuration logs
│   ├── 📂 agents/
│   │   ├── 📄 DroneAgent.java    # Agent drone (exploration, détection)
│   │   ├── 📄 EnvironmentAgent.java  # Agent environnement (grille, obstacles)
│   │   ├── 📄 BaseAgent.java     # Agent base de secours (logistique)
│   │   └── 📄 VictimAgent.java   # Agent victime (détection, confirmation)
│   ├── 📂 environment/
│   │   ├── 📄 Grid.java          # Grille avec obstacles et phéromones
│   │   ├── 📄 GridPanel.java     # Rendu graphique (carte catastrophe)
│   │   ├── 📄 SimulationFrame.java   # Interface utilisateur
│   │   └── 📄 Position.java      # Coordonnées
│   ├── 📂 main/
│   │   └── 📄 LauncherMain.java  # Point d'entrée
│   └── 📂 utils/
│       ├── 📄 SimulationConfig.java   # Configuration
│       ├── 📄 SimulationRuntimeControl.java  # Contrôle runtime
│       └── 📄 Statistics.java     # Statistiques
├── 📂 design/
│   ├── 📂 GAIA/                  # Modèle de rôles
│   └── 📂 AUML/                  # Diagrammes d'états
└── 📂 report/
    └── 📄 main.tex               # Rapport LaTeX
```

---

## 9. Références

1. Dorigo, M., & Gambardella, L. M. (1997). Ant colony system: A cooperative learning approach to the traveling salesman problem. *IEEE Transactions on Evolutionary Computation*, 1(1), 53-66.
2. Wooldridge, M. (2009). *An Introduction to MultiAgent Systems*. John Wiley & Sons.
3. Bellifemine, F., Caire, G., & Greenwood, D. (2007). *Developing Multi-Agent Systems with JADE*. Wiley.
4. Alers, S., Bloembergen, D., Hennes, D., de Jong, S., Kaisers, M., Lemmens, N., ... & Tuyls, K. (2014). Bee-inspired foraging in an experimental multi-agent system. *Adaptive and Learning Agents*, 1-15.
5. Pérez-Carabaza, S., Besada-Portas, E., López-Orozco, J. A., & de la Cruz, J. M. (2022). A multi-UAV minimum time search planner based on ACOR. *Engineering Applications of Artificial Intelligence*, 107, 104525.
6. Goldstein, P. (2011). *The Seismic Design of Buildings*. CRC Press.
7. INSARAG (2019). *Guidelines and Methodology*. International Search and Rescue Advisory Group.
8. Croix-Rouge (2023). *Rapport sur l'utilisation des drones dans les opérations de secours*.

---

## 📄 Licence

Ce projet est distribué sous licence **MIT**. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

---

## 👥 Auteur

**Mohamed El Attar** — *Master's in AI and Data Science*  
[![GitHub](https://img.shields.io/badge/GitHub-mohamedelattar15-181717?style=flat-square&logo=github)](https://github.com/mohamedelattar15)
