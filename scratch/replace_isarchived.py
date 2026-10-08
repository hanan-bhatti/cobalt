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
    # Fix any remaining broken logic where isItemArchived still tries to look at folders
    replacements = [
        ("val isItemArchived: (SavedItem) -> Boolean = { it.isArchived || it.folders.contains(\"Archive\") }", 
         "val isItemArchived: (SavedItem) -> Boolean = { it.isArchived }")
    ]
    
    files = glob.glob('app/src/**/*.kt', recursive=True)
    for f in files:
        replace_in_file(f, replacements)

if __name__ == "__main__":
    main()
