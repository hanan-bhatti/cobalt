# Second Brain

A universal capture and personal knowledge archive with minimalist design and Gemini AI OCR region marking.

## Features
- **Minimalist Design**: Clean, editorial-style UI with a warm monochrome palette.
- **Universal Capture**: Seamlessly capture notes, images, links, and documents.
- **Gemini AI OCR**: AI-powered text extraction from images.
- **Widgets**: Quick capture directly from your home screen.
- **Local Persistence**: Offline-first architecture using Room database.
- **Authentication**: Firebase Authentication.

## Build Requirements
- Android Studio / JDK 17
- Firebase configuration (`google-services.json` must be provided and placed in `app/`)
- API keys configured in `.env` (copy `.env.example` to `.env` and fill in the values)

## Setup & Run
1. Clone this repository.
2. Set up Firebase:
   - Create a Firebase project and add an Android app with the package name.
   - Download `google-services.json` and place it in the `app/` directory.
3. Configure API Keys:
   - Copy `.env.example` to `.env`.
   - Add your Gemini API key and other necessary credentials to `.env`.
4. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

## Release Build
To create a signed release build locally or via GitHub Actions, you must provide your keystore and signing credentials via environment variables:
- `KEYSTORE_PATH`: Path to your keystore file.
- `KEYSTORE_PASSWORD`: Keystore password.
- `KEY_ALIAS`: Key alias.
- `KEY_PASSWORD`: Key password.

GitHub Actions is set up to automatically build release APKs and AABs on workflow dispatch.
