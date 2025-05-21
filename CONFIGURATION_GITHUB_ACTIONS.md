# Guide de Configuration GitHub Actions pour l'Application Tasky

Ce guide explique comment configurer correctement les secrets nécessaires pour que GitHub Actions puisse compiler l'application Tasky.

## Secrets Requis

Les secrets suivants doivent être configurés dans les paramètres de votre dépôt.

### Comment Ajouter des Secrets dans GitHub

1. Accédez à votre dépôt sur GitHub.
2. Cliquez sur l'onglet **Settings** (Paramètres).
3. Dans la barre latérale gauche, développez **Secrets and variables** (Secrets et variables), puis cliquez sur **Actions**.
4. Cliquez sur le bouton **New repository secret** (Nouveau secret de dépôt).
5. Saisissez le nom du secret (par exemple, `GOOGLE_SERVICES_BASE64`) dans le champ "Name" (Nom).
6. Collez la valeur du secret dans le champ "Secret".
7. Cliquez sur **Add secret** (Ajouter le secret).

### 1. GOOGLE_SERVICES_BASE64 (Recommandé)

Ce secret doit contenir le contenu encodé en Base64 de votre fichier `google-services.json`.

#### Pour créer ce secret :

1. Obtenez votre fichier `google-services.json` depuis la console Firebase
2. Encodez-le en Base64 :

   **Sur macOS/Linux :**
   ```bash
   base64 -i google-services.json
   ```

   **Sur Windows (PowerShell) :**
   ```powershell
   [Convert]::ToBase64String([System.IO.File]::ReadAllBytes("google-services.json"))
   ```

3. Copiez la sortie complète (sans sauts de ligne) et ajoutez-la comme secret `GOOGLE_SERVICES_BASE64` dans votre dépôt en suivant les étapes "Comment Ajouter des Secrets dans GitHub" ci-dessus.

### 2. GEMINI_API_KEY (Optionnel)

Si vous utilisez les fonctionnalités de Gemini AI, ajoutez votre clé API Gemini comme secret nommé `GEMINI_API_KEY`. Suivez les étapes "Comment Ajouter des Secrets dans GitHub" ci-dessus.

## Résolution des Problèmes de Compilation

Si la compilation échoue, vérifiez les points suivants :

### 1. Vérifiez le fichier google-services.json

Le workflow valide automatiquement la structure du fichier JSON. Si la validation échoue, vérifiez que :

- L'encodage Base64 est correct et ne comporte pas de sauts de ligne
- Le fichier original de Firebase a la structure correcte

### 2. Validation manuelle

Vous pouvez valider manuellement votre fichier google-services.json en utilisant cette commande :

```bash
cat google-services.json | jq empty
```

Si elle ne produit aucune sortie, le JSON est valide.

### 3. Problèmes de Structure Courants

Assurez-vous que votre google-services.json contient au moins :

```json
{
  "project_info": {
    "project_id": "votre-id-projet",
    "project_number": "votre-numéro-projet",
    "firebase_url": "https://votre-projet.firebaseio.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:votre-id:android:votre-app-id",
        "android_client_info": {
          "package_name": "io.tasky.taskyapp"
        }
      }
    }
  ]
}
```

### 4. Téléchargement du Fichier à Nouveau

Si vous soupçonnez une corruption, téléchargez à nouveau le fichier google-services.json depuis la console Firebase :

1. Accédez à la console Firebase
2. Sélectionnez votre projet
3. Cliquez sur l'application Android (io.tasky.taskyapp)
4. Cliquez sur l'icône d'engrenage
5. Choisissez "Télécharger google-services.json"
