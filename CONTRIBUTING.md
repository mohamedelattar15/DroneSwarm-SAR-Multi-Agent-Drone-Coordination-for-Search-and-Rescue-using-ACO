# 🤝 Contributing — DroneSwarm-SAR

Merci de votre intérêt pour ce projet ! Ce guide décrit les conventions à respecter pour contribuer efficacement.

---

## 📋 Table des matières

- [Prérequis](#-prérequis)
- [Installation](#-installation)
- [Architecture à respecter](#-architecture-à-respecter)
- [Conventions de code](#-conventions-de-code)
- [Workflow Git](#-workflow-git)
- [Tests](#-tests)
- [Checklist avant commit](#-checklist-avant-commit)

---

## 🔧 Prérequis

| Outil | Version minimale |
|---|---|
| **JDK** | 17 |
| **Maven** | 3.8 (optionnel) |
| **Git** | 2.30 |

---

## 🚀 Installation

```bash
# Cloner le dépôt
git clone https://github.com/mohamedelattar15/DroneSwarm-SAR.git
cd DroneSwarm-SAR

# Compiler
mvn clean compile

# Lancer
mvn exec:java
# ou
./run.bat --scenario=demo --no-sniffer
```

---

## 🏛️ Architecture à respecter

Le projet suit une **architecture en couches stricte**. Avant de contribuer, lisez [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

### ⚠️ Règle d'or

> **Le package `domain/` ne doit JAMAIS importer `jade.*` ni `javax.swing.*`.**

```
domain/          → logique métier pure (POJO testables)
agents/          → infrastructure JADE (behaviours, messagerie)
environment/     → topologie & UI
utils/           → config & runtime
```

### Où placer mon code ?

| Type de code | Emplacement |
|---|---|
| Algorithme / logique métier | `domain/` |
| Comportement d'agent | `agents/` |
| Nouvelle constante de protocole | `agents/protocol/MessageProtocol.java` |
| Nouveau paramètre | `utils/SimulationConfig.java` |
| Nouveau scénario | `utils/SimulationScenario.java` |
| Nouvelle métrique | `utils/MetricsExporter.java` |

---

## 📐 Conventions de code

### Nommage

| Élément | Convention | Exemple |
|---|---|---|
| Classe | `PascalCase` | `AntColonyOptimizer` |
| Méthode | `camelCase` | `selectNext()` |
| Constante | `UPPER_SNAKE_CASE` | `MEDIUM_THRESHOLD` |
| Package | `lowercase` | `domain.pheromone` |

### Style

- **Indentation :** 4 espaces (pas de tabulations)
- **Longueur de ligne :** 100 caractères max
- **Accolades :** style K&R (accolade ouvrante sur la même ligne)
- **Imports :** pas d'import wildcard (`import java.util.*` interdit)

### Documentation

Toute classe publique doit avoir un **JavaDoc** décrivant sa responsabilité :

```java
/**
 * Responsabilité unique de la classe.
 * <p>
 * Détails supplémentaires si nécessaire.
 */
public class MyClass {
    // ...
}
```

### Éviter les valeurs magiques

❌ **Mauvais :**
```java
if (content.startsWith("PATH_ACCEPTED")) { ... }
```

✅ **Bon :**
```java
if (content.startsWith(MessageProtocol.PATH_ACCEPTED)) { ... }
```

### Source unique de vérité

Ne **jamais** dupliquer un état. Si vous avez besoin du nombre de victimes :

❌ **Mauvais :**
```java
private int myOwnVictimCount = 0;  // duplication !
```

✅ **Bon :**
```java
int count = grid.getVictimsFound();  // délégation
```

---

## 🔀 Workflow Git

### Branches

| Branche | Usage |
|---|---|
| `main` | Stable, prête à livrer |
| `feature/<nom>` | Nouvelle fonctionnalité |
| `fix/<nom>` | Correction de bug |
| `refactor/<nom>` | Refactoring |

### Convention de commit

Format : `<type>(<scope>): <description>`

| Type | Usage |
|---|---|
| `feat` | Nouvelle fonctionnalité |
| `fix` | Correction de bug |
| `refactor` | Refactoring sans changement de comportement |
| `docs` | Documentation |
| `test` | Tests |
| `chore` | Maintenance |

**Exemples :**

```
feat(aco): ajouter un biais directionnel par drone
fix(detection): détecter les victimes pendant le retour
refactor(domain): extraire VictimRegistry de Grid
docs(readme): ajouter les diagrammes Mermaid
```

### Processus

```bash
# 1. Créer une branche
git checkout -b feature/ma-fonctionnalite

# 2. Développer + compiler
mvn clean compile

# 3. Commit
git add .
git commit -m "feat(scope): description"

# 4. Push
git push origin feature/ma-fonctionnalite

# 5. Ouvrir une Pull Request
```

---

## 🧪 Tests

La couche `domain/` est **conçue pour être testée sans JADE**.

### Écrire un test

```java
class AntColonyOptimizerTest {

    @Test
    void shouldSelectValidNeighbor() {
        // Arrange
        PheromoneField field = new PheromoneField(10, 10, SimulationConfig.defaults());
        AntColonyOptimizer aco = new AntColonyOptimizer(new Random(42));
        List<Position> candidates = List.of(new Position(1, 0), new Position(0, 1));

        // Act
        Position next = aco.selectNext(candidates, field, buildContext());

        // Assert
        assertNotNull(next);
        assertTrue(candidates.contains(next));
    }
}
```

### Lancer les tests

```bash
mvn test
```

### Règles de test

- ✅ Un test = une assertion logique
- ✅ Nommer `shouldXxx_whenYyy()`
- ✅ Utiliser un `Random` avec **seed fixe** (déterminisme)
- ❌ Ne pas tester les classes JADE (infrastructure)

---

## ✅ Checklist avant commit

- [ ] Le projet **compile** (`mvn clean compile`)
- [ ] Aucun import `jade.*` dans `domain/`
- [ ] Aucune chaîne magique de protocole (utiliser `MessageProtocol`)
- [ ] Aucun état dupliqué (source unique de vérité)
- [ ] JavaDoc sur les nouvelles classes publiques
- [ ] Commit au format conventionnel
- [ ] Les tests passent (`mvn test`)

---

## 📚 Ressources

- [Architecture détaillée](docs/ARCHITECTURE.md)
- [README principal](README.md)
- [JADE Documentation](https://jade.tilab.com/documentation/)

---

<div align="center">

Merci de contribuer à **DroneSwarm-SAR** ! 🚁

</div>
