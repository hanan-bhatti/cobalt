import os
import glob

def replace_in_file(filepath, replacements):
    with open(filepath, 'r') as f:
        content = f.read()
    
    original_content = content
    for old, new in replacements:
        content = content.replace(old, new)
        
    if content != original_content:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Updated {filepath}")

def main():
    replacements = [
        ("it.isArchived || it.folders.contains(\"Archive\")", "it.isArchived"),
        ("!it.folders.contains(\"Archive\")", "!it.isArchived"),
        ("item.folders.contains(\"Archive\")", "item.isArchived"),
        ("!item.folders.contains(\"Archive\")", "!item.isArchived")
    ]
    
    # We also need to fix `val isItemArchived: (SavedItem) -> Boolean = { it.isArchived || it.folders.contains("Archive") }`
    # It will become `val isItemArchived: (SavedItem) -> Boolean = { it.isArchived }` via the first replacement.

    files = glob.glob('app/src/**/*.kt', recursive=True)
    for f in files:
        replace_in_file(f, replacements)

if __name__ == "__main__":
    main()
