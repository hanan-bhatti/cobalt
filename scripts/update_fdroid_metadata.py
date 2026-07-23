#!/usr/bin/env python3
"""
Dynamic F-Droid Metadata Auto-Generator & Version Tracker
Automatically reads app/build.gradle.kts, extracts versionCode and versionName,
generates fastlane/metadata/android/en-US/changelogs/<versionCode>.txt from CHANGELOG.md,
and syncs fdroid/com.hanan_bhatti.second_brain.yml so you never have to manually track versions.
"""

import os
import re
import sys

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE_FILE = os.path.join(PROJECT_ROOT, "app", "build.gradle.kts")
CHANGELOG_FILE = os.path.join(PROJECT_ROOT, "CHANGELOG.md")
FASTLANE_DIR = os.path.join(PROJECT_ROOT, "fastlane", "metadata", "android", "en-US")
CHANGELOGS_DIR = os.path.join(FASTLANE_DIR, "changelogs")
FDROID_YML_FILE = os.path.join(PROJECT_ROOT, "fdroid", "com.hanan_bhatti.second_brain.yml")

def extract_app_version():
    """Extracts versionCode and versionName from app/build.gradle.kts."""
    if not os.path.exists(GRADLE_FILE):
        print(f"Error: {GRADLE_FILE} not found.")
        sys.exit(1)
        
    with open(GRADLE_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    version_code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    version_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)

    if not version_code_match or not version_name_match:
        print("Error: Could not parse versionCode or versionName from build.gradle.kts")
        sys.exit(1)

    version_code = version_code_match.group(1)
    version_name = version_name_match.group(1)
    return version_code, version_name

def extract_latest_changelog():
    """Extracts the latest release entry from CHANGELOG.md or falls back to a clean default."""
    if os.path.exists(CHANGELOG_FILE):
        with open(CHANGELOG_FILE, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Look for section like ## [1.0.0] or ## v1.0.0 or first heading section
        sections = re.split(r'\n(?=##\s+)', content)
        for sec in sections:
            if sec.strip().startswith("##"):
                lines = sec.strip().split("\n")[1:]
                changelog_text = " ".join([l.strip("- *").strip() for l in lines if l.strip() and not l.startswith("#")])
                if changelog_text:
                    if len(changelog_text) > 490:
                        changelog_text = changelog_text[:487] + "..."
                    return changelog_text

    return "Maintenance and performance updates, offline-first Room database improvements, and UI enhancements."

def sync_fastlane_changelog(version_code, changelog_text):
    """Creates fastlane/metadata/android/en-US/changelogs/<versionCode>.txt."""
    os.makedirs(CHANGELOGS_DIR, exist_ok=True)
    target_file = os.path.join(CHANGELOGS_DIR, f"{version_code}.txt")
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(changelog_text.strip() + "\n")
    print(f"✅ Generated Fastlane changelog: {target_file}")

def sync_fdroid_yml(version_code, version_name):
    """Updates fdroid/com.hanan_bhatti.second_brain.yml with current version info."""
    if not os.path.exists(FDROID_YML_FILE):
        print(f"Warning: {FDROID_YML_FILE} does not exist.")
        return

    with open(FDROID_YML_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    # Update CurrentVersion & CurrentVersionCode
    content = re.sub(r'CurrentVersion:\s*.*', f'CurrentVersion: {version_name}', content)
    content = re.sub(r'CurrentVersionCode:\s*.*', f'CurrentVersionCode: {version_code}', content)

    # Check if this build entry is already in Builds block
    build_pattern = f"versionName: {version_name}"
    if build_pattern not in content:
        new_build_entry = f"""  - versionName: {version_name}
    versionCode: {version_code}
    commit: v{version_name}
    subdir: app
    gradle:
      - assembleFossRelease
"""
        content += f"\n{new_build_entry}"

    with open(FDROID_YML_FILE, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"✅ Synced F-Droid recipe file: {FDROID_YML_FILE}")

def main():
    print("🚀 Auto-syncing F-Droid metadata & version tracking...")
    version_code, version_name = extract_app_version()
    print(f"📱 Detected App Version: {version_name} (Code: {version_code})")
    
    changelog = extract_latest_changelog()
    sync_fastlane_changelog(version_code, changelog)
    sync_fdroid_yml(version_code, version_name)
    print("🎉 All F-Droid submission metadata files are up-to-date!")

if __name__ == "__main__":
    main()
