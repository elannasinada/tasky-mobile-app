_# GitHub Actions Setup Guide for Tasky App

This guide explains how to correctly set up the required secrets for GitHub Actions to build the
Tasky app.

## Required Secrets

The following secrets need to be set up in your repository settings.

### How to Add Secrets in GitHub

1.  Navigate to your repository on GitHub.
2.  Click on the **Settings** tab.
3.  In the left sidebar, expand **Secrets and variables**, then click on **Actions**.
4.  Click the **New repository secret** button.
5.  Enter the name of the secret (e.g., `GOOGLE_SERVICES_BASE64`) in the "Name" field.
6.  Paste the secret's value into the "Secret" field.
7.  Click **Add secret**.

### 1. GOOGLE_SERVICES_BASE64 (Recommended)

This secret should contain the Base64-encoded contents of your `google-services.json` file.

#### To create this secret:

1. Get your `google-services.json` file from the Firebase console
2. Encode it to Base64:

   **On macOS/Linux:**
   ```bash
   base64 -i google-services.json
   ```

   **On Windows (PowerShell):**
   ```powershell
   [Convert]::ToBase64String([System.IO.File]::ReadAllBytes("google-services.json"))
   ```

3. Copy the entire output (without line breaks) and add it as the `GOOGLE_SERVICES_BASE64` secret in
   your repository following the "How to Add Secrets in GitHub" steps above.

### 2. GEMINI_API_KEY (Optional)

If you're using Gemini AI features, add your Gemini API key as a secret named `GEMINI_API_KEY`. Follow the "How to Add Secrets in GitHub" steps above.

## Troubleshooting Build Issues

If the build fails, check the following:

### 1. Verify the google-services.json file

The workflow automatically validates the JSON file structure. If validation fails, check that:

- The Base64 encoding is correct and doesn't have line breaks
- The original file from Firebase has the correct structure

### 2. Manual validation

You can manually validate your google-services.json file using this command:

```bash
cat google-services.json | jq empty
```

If it doesn't output anything, the JSON is valid.

### 3. Common Structure Issues

Make sure your google-services.json contains at least:

```json
{
  "project_info": {
    "project_id": "your-project-id",
    "project_number": "your-project-number",
    "firebase_url": "https://your-project.firebaseio.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:your-id:android:your-app-id",
        "android_client_info": {
          "package_name": "io.tasky.taskyapp"
        }
      }
    }
  ]
}
```

### 4. Re-downloading the File

If you suspect corruption, re-download the google-services.json file from the Firebase console:

1. Go to the Firebase console
2. Select your project
3. Click the Android app (io.tasky.taskyapp)
4. Click the gear icon
5. Choose "Download google-services.json"_