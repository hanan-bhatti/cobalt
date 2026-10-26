#!/usr/bin/env python3
"""
Dynamic Issue Template & Security Policy Version Tracker
Extracts all release versions from app/build.gradle.kts, CHANGELOG.md, and git tags,
and automatically synchronizes:
1. .github/ISSUE_TEMPLATE/bug_report.yml (dropdown list of supported/past versions)
2. SECURITY.md (supported versions table)
"""

import os
import re
import subprocess

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE_FILE = os.path.join(PROJECT_ROOT, "app", "build.gradle.kts")
CHANGELOG_FILE = os.path.join(PROJECT_ROOT, "CHANGELOG.md")
BUG_REPORT_FILE = os.path.join(PROJECT_ROOT, ".github", "ISSUE_TEMPLATE", "bug_report.yml")
SECURITY_FILE = os.path.join(PROJECT_ROOT, "SECURITY.md")


def get_current_app_version():
    with open(GRADLE_FILE, "r", encoding="utf-8") as f:
        content = f.read()
    match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if match:
        return match.group(1).strip()
    return "2.2.1"


def version_sort_key(v):
    # Splits 2.2.1-beta01 into (2, 2, 1, 'beta01')
    clean = v.strip().lstrip("v")
    m = re.match(r'^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:[.-](.+))?$', clean)
    if not m:
        return (0, 0, 0, clean)
    major = int(m.group(1) or 0)
    minor = int(m.group(2) or 0)
    patch = int(m.group(3) or 0)
    suffix = m.group(4) or "zzz" # Suffix like "beta" is lower than release
    is_release = 1 if suffix == "zzz" else 0
    return (major, minor, patch, is_release, suffix)


def get_all_versions(current_version):
    versions = set()
    versions.add(current_version)

    # 1. Extract from CHANGELOG.md
    if os.path.exists(CHANGELOG_FILE):
        with open(CHANGELOG_FILE, "r", encoding="utf-8") as f:
            for line in f:
                m = re.match(r'^##\s+\[([^\]]+)\]', line.strip())
                if m:
                    versions.add(m.group(1).strip())

    # 2. Extract from git tags if available
    try:
        tag_output = subprocess.check_output(
            ["git", "tag", "-l", "v*"],
            cwd=PROJECT_ROOT,
            stderr=subprocess.DEVNULL,
            encoding="utf-8"
        )
        for tag in tag_output.splitlines():
            clean_tag = tag.strip().lstrip("v")
            if clean_tag and not clean_tag.endswith("-build"):
                versions.add(clean_tag)
    except Exception:
        pass

    sorted_versions = sorted(list(versions), key=version_sort_key, reverse=True)
    # Ensure current_version is always strictly at index 0
    if current_version in sorted_versions:
        sorted_versions.remove(current_version)
    sorted_versions.insert(0, current_version)
    return sorted_versions


def update_bug_report_template(current_version, all_versions):
    if not os.path.exists(BUG_REPORT_FILE):
        return

    with open(BUG_REPORT_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    # Build options block
    options_lines = [f"        - v{current_version} (Latest Release)"]
    for v in all_versions:
        if v == current_version:
            continue
        options_lines.append(f"        - v{v}")
    options_lines.append("        - Custom Build / Development Branch (main)")
    options_block = "\n".join(options_lines)

    # Replace options block in bug_report.yml under app-version
    pattern = r'(id:\s*app-version[\s\S]*?options:\n)([\s\S]*?)(\n\s*validations:)'
    replacement = r'\1' + options_block + r'\3'
    new_content, count = re.subn(pattern, replacement, content, count=1)

    if count > 0:
        with open(BUG_REPORT_FILE, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"Updated {BUG_REPORT_FILE} with {len(all_versions)} versions.")
    else:
        print(f"Warning: Could not match app-version options block in {BUG_REPORT_FILE}.")


def update_security_md(current_version):
    if not os.path.exists(SECURITY_FILE):
        return

    with open(SECURITY_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    # Extract major.minor.0 as base supported threshold (e.g. 2.2.0)
    parts = current_version.split(".")
    if len(parts) >= 2 and parts[0].isdigit() and parts[1].isdigit():
        base_version = f"{parts[0]}.{parts[1]}.0"
    else:
        base_version = current_version

    table_replacement = f"| >= {base_version} | :white_check_mark: |\n| < {base_version} | :x: |"
    table_pattern = r'(\|\s*Version\s*\|\s*Supported\s*\|\n\|[-|]+\|\n)([\s\S]*?)(\n\n---)'
    new_content = re.sub(table_pattern, r'\1' + table_replacement + r'\3', content, count=1)

    # Update affected version example e.g. (e.g., `v2.2.x`)
    new_content = re.sub(
        r'(\(e\.g\.,\s*`)[^`]+(`\))',
        f'\\1v{current_version}\\2',
        new_content,
        count=1
    )

    with open(SECURITY_FILE, "w", encoding="utf-8") as f:
        f.write(new_content)
    print(f"Updated {SECURITY_FILE} to support >= {base_version} (Latest: v{current_version}).")


def main():
    current_version = get_current_app_version()
    print(f"Current app version detected: v{current_version}")
    all_versions = get_all_versions(current_version)
    update_bug_report_template(current_version, all_versions)
    update_security_md(current_version)
    print("Version synchronization complete!")


if __name__ == "__main__":
    main()
