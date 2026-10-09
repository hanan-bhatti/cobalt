#!/usr/bin/env python3
"""
Dynamic F-Droid Metadata Auto-Generator & Version Tracker
"""

import os
import re
import sys

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE_FILE = os.path.join(PROJECT_ROOT, "app", "build.gradle.kts")
CHANGELOG_FILE = os.path.join(PROJECT_ROOT, "CHANGELOG.md")
FASTLANE_DIR = os.path.join(PROJECT_ROOT, "fastlane", "metadata", "android", "en-US")
CHANGELOGS_DIR = os.path.join(FASTLANE_DIR, "changelogs")
FDROID_YML_FILE = os.path.join(PROJECT_ROOT, "fdroid", "com.hanan_bhatti.cobalt.yml")

def extract_app_version():
    with open(GRADLE_FILE, "r", encoding="utf-8") as f:
        content = f.read()
    version_code = re.search(r'versionCode\s*=\s*(\d+)', content).group(1)
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', content).group(1)
    return version_code, version_name

def extract_latest_changelog():
    if os.path.exists(CHANGELOG_FILE):
        with open(CHANGELOG_FILE, "r", encoding="utf-8") as f:
            content = f.read()
        sections = re.split(r'\n(?=##\s+)', content)
        for sec in sections:
            if sec.strip().startswith("##"):
                lines = sec.strip().split("\n")[1:]
                changelog_text = " ".join([l.strip("- *").strip() for l in lines if l.strip() and not l.startswith("#")])
                if changelog_text:
                    return changelog_text[:487] + "..." if len(changelog_text) > 490 else changelog_text
    return "Maintenance and performance updates."

def sync_fastlane_changelog(version_code, changelog_text):
    os.makedirs(CHANGELOGS_DIR, exist_ok=True)
    target_file = os.path.join(CHANGELOGS_DIR, f"{version_code}.txt")
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(changelog_text.strip() + "\n")

def sync_fdroid_yml(version_code, version_name):
    if not os.path.exists(FDROID_YML_FILE): return
    with open(FDROID_YML_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    content = re.sub(r'CurrentVersion:\s*.*', f'CurrentVersion: {version_name}', content)
    content = re.sub(r'CurrentVersionCode:\s*.*', f'CurrentVersionCode: {version_code}', content)

    commit_hash = "HEAD"
    try:
        import subprocess
        commit_hash = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    except: pass

    build_pattern = f"versionName: {version_name}"
    if build_pattern not in content:
        new_build = f"  - versionName: {version_name}\n    versionCode: {version_code}\n    commit: {commit_hash}\n    subdir: app\n    gradle:\n      - assembleFossRelease"
        
        # Insert before AutoUpdateMode or at the end of Builds:
        if 'AutoUpdateMode:' in content:
            content = content.replace('AutoUpdateMode:', f"{new_build}\n\nAutoUpdateMode:")
        else:
            content += f"\n{new_build}\n"

    with open(FDROID_YML_FILE, "w", encoding="utf-8") as f:
        f.write(content)

def main():
    version_code, version_name = extract_app_version()
    changelog = extract_latest_changelog()
    sync_fastlane_changelog(version_code, changelog)
    sync_fdroid_yml(version_code, version_name)

if __name__ == "__main__":
    main()
