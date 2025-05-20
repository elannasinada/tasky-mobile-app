# README - Tasky Mobile App

# **Table des matières**
- [Comment installer le projet?](#project-installation)
- [Comment configurer le projet?](#project-configuration)
- [Comment exécuter le projet?](#project-execution)

# Installation du projet <a id="project-installation"></a>

## Prérequis
* Android Studio installé
* JDK 17 (voir `build.gradle`)
* Compte Firebase et Google Cloud Platform
* Accès à Internet

## Étapes
1. Clonez le dépôt :

```bash
git clone https://github.com/elannasinada/tasky-mobile-app.git
cd tasky-mobile-app
```

2. Ouvrez le projet dans Android Studio (`File > Open`)
3. Synchronisez avec Gradle (`Tools > Android > Sync Project with Gradle Files`)

# Configuration du projet <a id="project-configuration"></a>

## Firebase
1. Créez un projet sur console.firebase.google.com
2. Ajoutez votre app Android avec le package `io.tasky.taskyapp`
3. Téléchargez et placez `google-services.json` dans `app/`
4. Activez l'authentification Email/Password et Google
5. Configurez la Realtime Database avec ces règles :

```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null",
    "$uid": {
      ".read": "$uid === auth.uid",
      ".write": "$uid === auth.uid"
    }
  }
}
```

## Cloud Functions (pour Google Calendar)
* Installez Firebase CLI :

```bash
npm install -g firebase-tools
firebase login
firebase init functions
```

* Configurez les variables d'environnement (remplacez par vos infos) :

```bash
firebase functions:config:set google.client_id="VOTRE_CLIENT_ID" \
  google.client_secret="VOTRE_CLIENT_SECRET" \
  google.redirect_uri="VOTRE_REDIRECT_URI" \
  google.service_account_email="VOTRE_SERVICE_ACCOUNT_EMAIL" \
  google.service_account_private_key="VOTRE_SERVICE_ACCOUNT_PRIVATE_KEY"
```

* Déployez les fonctions :

```bash
firebase deploy --only functions
```

## Google Calendar API
1. Activez l'API dans la Console Google Cloud
2. Créez des identifiants OAuth 2.0 :
   * Application Android (package `io.tasky.taskyapp`, SHA-1 de certificat)
   * Application Web (pour les Cloud Functions)
3. Ajoutez l'ID client Web dans `res/values/strings.xml` :

```xml
<string name="default_web_client_id">VOTRE_WEB_CLIENT_ID</string>
```

## Clé API Gemini (optionnelle)
* Obtenez votre clé sur Google AI Studio
* Ajoutez dans `local.properties` à la racine :

```properties
gemini.api.key=VOTRE_CLE_API_GEMINI
```

# Exécution du projet <a id="project-execution"></a>

## Préparation
* Lancez un émulateur ou connectez un appareil physique compatible
* Vérifiez la version minSdkVersion dans `build.gradle`

## Démarrage
* Sélectionnez la configuration `debug` dans Android Studio
* Cliquez sur `Run`

## Astuces debug
* Vérifiez le fichier `google-services.json`
* Contrôlez que les empreintes SHA-1/SHA-256 sont bien configurées dans Firebase
* Surveillez les logs Firebase et Android Studio pour détecter erreurs

# **License**
© 2025 Tasky. Tous droits réservés.

# **Contact**
Pour plus d'informations, veuillez contacter l'équipe de Tasky.
