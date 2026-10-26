#!/usr/bin/env python3
"""
Dynamic F-Droid Metadata Auto-Generator & Version Tracker
Maintains strict compatibility with Fastlane (500 char limit) and F-Droid canonical metadata.
"""

import os
import re
import shutil
import subprocess
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

def extract_latest_changelog(version_code):
    # Check if a custom-curated changelog already exists for this version code
    target_file = os.path.join(CHANGELOGS_DIR, f"{version_code}.txt")
    if os.path.exists(target_file):
        with open(target_file, "r", encoding="utf-8") as f:
            existing_text = f.read().strip()
            if existing_text and len(existing_text) <= 500:
                return existing_text

    if not os.path.exists(CHANGELOG_FILE):
        return "- Maintenance and stability improvements."

    with open(CHANGELOG_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    # Split into version sections
    sections = re.split(r'\n(?=##\s+\[)', content)
    target_section = None
    for sec in sections:
        if sec.strip().startswith("##"):
            target_section = sec
            break

    if not target_section:
        return "- Maintenance and stability improvements."

    lines = target_section.strip().split("\n")
    bullet_points = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("- ") or stripped.startswith("* "):
            clean_item = stripped[2:].strip()
            # Clean markdown bold, backticks, and extra markup
            clean_item = re.sub(r'\*\*([^*]+)\*\*', r'\1', clean_item)
            clean_item = clean_item.replace('`', '')
            if clean_item:
                bullet_points.append(clean_item)

    if not bullet_points:
        return "- Maintenance and stability improvements."

    # Build concise bulleted list fitting within 495 characters (Play / F-Droid limit is 500)
    selected_bullets = []
    current_length = 0

    for bp in bullet_points:
        candidate = f"- {bp}"
        added_len = len(candidate) + (1 if selected_bullets else 0)
        if current_length + added_len <= 495:
            selected_bullets.append(candidate)
            current_length += added_len
        else:
            # If nothing fit yet, truncate the single item cleanly
            if not selected_bullets:
                trimmed = candidate[:490].rstrip() + "..."
                selected_bullets.append(trimmed)
            break

    return "\n".join(selected_bullets)

def sync_fastlane_changelog(version_code, changelog_text):
    os.makedirs(CHANGELOGS_DIR, exist_ok=True)
    target_file = os.path.join(CHANGELOGS_DIR, f"{version_code}.txt")
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(changelog_text.strip() + "\n")
    print(f"✅ Synced Fastlane changelog: {target_file} ({len(changelog_text.strip())} chars)")

def sync_fdroid_yml(version_code, version_name):
    if not os.path.exists(FDROID_YML_FILE):
        print(f"⚠️ F-Droid YAML file not found: {FDROID_YML_FILE}")
        return

    with open(FDROID_YML_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    content = re.sub(r'CurrentVersion:\s*.*', f'CurrentVersion: {version_name}', content)
    content = re.sub(r'CurrentVersionCode:\s*.*', f'CurrentVersionCode: {version_code}', content)

    # Determine commit reference
    commit_ref = f"v{version_name}"
    try:
        tag_check = subprocess.run(["git", "tag", "-l", f"v{version_name}"], capture_output=True, text=True).stdout.strip()
        if not tag_check:
            # If tag not yet placed, use HEAD commit SHA
            commit_ref = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        pass

    build_pattern = f"versionName: {version_name}"
    if build_pattern not in content:
        new_build = (
            f"  - versionName: {version_name}\n"
            f"    versionCode: {version_code}\n"
            f"    commit: {commit_ref}\n"
            f"    subdir: app\n"
            f"    gradle:\n"
            f"      - foss\n"
            f"    prebuild: sed -i -e '/playImplementation/d' build.gradle.kts"
        )
        if 'AllowedAPKSigningKeys:' in content:
            content = content.replace('AllowedAPKSigningKeys:', f"{new_build}\n\nAllowedAPKSigningKeys:")
        elif 'AutoUpdateMode:' in content:
            content = content.replace('AutoUpdateMode:', f"{new_build}\n\nAutoUpdateMode:")
        else:
            content += f"\n{new_build}\n"
    else:
        # Update existing build entry with current version code and commit
        content = re.sub(
            rf'(versionName:\s*{re.escape(version_name)}\s*\n\s*versionCode:\s*)\d+',
            rf'\g<1>{version_code}',
            content
        )

    with open(FDROID_YML_FILE, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"✅ Synced F-Droid metadata: {FDROID_YML_FILE} (v{version_name} #{version_code})")

    # Format canonically with fdroid rewritemeta if available
    if shutil.which("fdroid"):
        try:
            subprocess.run(["fdroid", "rewritemeta", "com.hanan_bhatti.cobalt"], cwd=PROJECT_ROOT, capture_output=True, check=False)
            print("✅ Formatted F-Droid YAML canonically using 'fdroid rewritemeta'")
        except Exception:
            pass

def main():
    version_code, version_name = extract_app_version()
    print(f"Processing Cobalt v{version_name} (versionCode: {version_code})...")
    changelog = extract_latest_changelog(version_code)
    sync_fastlane_changelog(version_code, changelog)
    sync_fdroid_yml(version_code, version_name)

    # Sync issue templates and security policy
    try:
        sync_script = os.path.join(PROJECT_ROOT, "scripts", "sync_issue_versions.py")
        if os.path.exists(sync_script):
            subprocess.run([sys.executable, sync_script], cwd=PROJECT_ROOT, check=False)
    except Exception as e:
        print(f"⚠️ Could not sync issue versions: {e}")

if __name__ == "__main__":
    main()
