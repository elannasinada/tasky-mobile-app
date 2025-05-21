# Gestion des Priorités de Tâches dans Tasky

Ce document décrit les fonctionnalités de gestion des priorités de tâches dans Tasky, y compris les priorités suggérées par l'IA et la sélection manuelle des priorités.

## Aperçu

Tasky implémente un système intelligent de priorité des tâches qui combine les suggestions de l'IA avec le contrôle utilisateur. Ce système aide les utilisateurs à prioriser efficacement leurs tâches tout en conservant un contrôle total sur leur gestion des tâches.

## Fonctionnalités Clés

### Priorités Suggérées par l'IA

Lors de la création d'une nouvelle tâche, le système peut automatiquement suggérer un niveau de priorité approprié basé sur :

- Le contenu du titre et de la description de la tâche
- La proximité de l'échéance
- Le type de tâche
- D'autres facteurs contextuels

L'IA analyse ces facteurs et attribue l'un des trois niveaux de priorité :

- **Faible** : Priorité par défaut pour les tâches routinières
- **Moyenne** : Tâches qui méritent une attention particulière
- **Élevée** : Tâches critiques qui nécessitent une attention immédiate

### Sélection Manuelle des Priorités

Les utilisateurs peuvent sélectionner manuellement une priorité pour n'importe quelle tâche :

1. Lors de la création d'une nouvelle tâche
2. Lors de la modification d'une tâche existante
3. Pour remplacer une suggestion de l'IA

Une fois qu'une priorité est sélectionnée manuellement, elle est marquée comme "définie manuellement" et sera conservée même lorsque la tâche est modifiée.

### Persistance des Priorités

Le système implémente une hiérarchie claire des priorités :

1. **Les priorités sélectionnées par l'utilisateur ont toujours préséance**. Lorsqu'un utilisateur définit manuellement une priorité, elle est enregistrée de façon permanente avec la tâche et ne sera jamais remplacée par l'IA.
2. **Les suggestions de l'IA sont appliquées uniquement lorsqu'aucune priorité manuelle n'existe**. Si un utilisateur n'a pas explicitement défini une priorité, l'IA en suggérera une.

## Interface Utilisateur

L'interface de sélection des priorités dans Tasky indique clairement :

- Quand une priorité est suggérée par l'IA vs définie manuellement
- Quelle priorité est actuellement sélectionnée
- Que les sélections manuelles seront enregistrées de façon permanente

### Composants de l'Interface

- **Boutons de Sélection de Priorité** : Trois boutons pour les priorités Faible, Moyenne et Élevée
- **Indicateur de Suggestion de l'IA** : Montre quand une IA a suggéré la priorité actuelle
- **Indicateur de Sélection Manuelle** : Montre quand la priorité a été définie manuellement par l'utilisateur
- **Texte Explicatif** : Explique que les sélections manuelles seront enregistrées de façon permanente

## Implémentation Technique

Le système de priorité des tâches est implémenté avec ces composants clés :

1. **Modèle de Tâche** : Inclut les champs `priority` (0=Faible, 1=Moyenne, 2=Élevée) et `isPriorityManuallySet` (booléen)
2. **TaskDetailsViewModel** : Gère la logique pour déterminer quand utiliser l'IA vs les priorités manuelles
3. **GeminiService** : Utilise l'API Gemini de Google pour analyser les données des tâches et suggérer des priorités appropriées
4. **Composants UI** : Fournissent un retour visuel sur le statut de priorité

## Configuration

Pour utiliser les suggestions de priorité de l'IA, vous avez besoin d'une clé API Google Gemini :

1. Obtenez une clé depuis [Google AI Studio](https://aistudio.google.com/)
2. Ajoutez-la à `local.properties` dans la racine de votre projet :
   ```
   gemini.api.key=VOTRE_CLÉ_API
   ```

Si aucune clé API n'est fournie, l'application fonctionnera toujours mais n'offrira pas de suggestions de priorité par l'IA.

## Bonnes Pratiques

- **Laissez l'IA gérer la priorisation routinière** pour gagner du temps
- **Définissez manuellement les priorités pour les tâches** où vous avez une connaissance spécifique de leur importance
- **Commencez avec les suggestions de l'IA** et remplacez-les uniquement si nécessaire
