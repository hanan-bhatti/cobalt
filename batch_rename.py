import os
import glob
import re

def process_file(filepath):
    if not os.path.isfile(filepath): return
    if filepath.endswith('.png') or filepath.endswith('.jpg') or filepath.endswith('.dex') or filepath.endswith('.class'): return
    if '/build/' in filepath or '/.gradle/' in filepath or '/.git/' in filepath: return
    
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()

    original = content
    
    # Text replacements
    content = content.replace("Cobalt", "Cobalt")
    content = content.replace("cobalt", "cobalt")
    content = content.replace("Cobalt", "Cobalt")
    content = content.replace("cobalt", "cobalt")
    content = content.replace("cobalt", "cobalt")
    content = content.replace("cobalt", "cobalt")
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, _, files in os.walk('.'):
    for file in files:
        process_file(os.path.join(root, file))

