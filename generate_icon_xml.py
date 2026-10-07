import os

# Delete old PNGs
os.system('find app/src/main/res -name "ic_launcher*.png" -type f -delete')

bg_xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#121212"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""

fg_xml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    
    <!-- Bookmark Ribbon -->
    <path
        android:fillColor="#FF6D00"
        android:pathData="M34,22 C34,17.58 37.58,14 42,14 L66,14 C70.42,14 74,17.58 74,22 L74,94 L54,78 L34,94 Z" />

    <!-- S -->
    <path
        android:strokeColor="#121212"
        android:strokeWidth="5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M 68 30 L 44 30 A 6 6 0 0 0 38 36 L 38 36 A 6 6 0 0 0 44 42 L 64 42 A 6 6 0 0 1 70 48 L 70 48 A 6 6 0 0 1 64 54 L 38 54" />

    <!-- B -->
    <path
        android:strokeColor="#121212"
        android:strokeWidth="5"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M 38 58 L 38 82 M 38 58 L 62 58 A 6 6 0 0 1 68 64 A 6 6 0 0 1 62 70 L 38 70 M 38 70 L 64 70 A 6 6 0 0 1 70 76 A 6 6 0 0 1 64 82 L 38 82" />
        
</vector>
"""

ic_launcher = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""

os.makedirs("app/src/main/res/drawable", exist_ok=True)
os.makedirs("app/src/main/res/mipmap-anydpi-v26", exist_ok=True)

with open("app/src/main/res/drawable/ic_launcher_background.xml", "w") as f:
    f.write(bg_xml)

with open("app/src/main/res/drawable/ic_launcher_foreground.xml", "w") as f:
    f.write(fg_xml)

with open("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", "w") as f:
    f.write(ic_launcher)

with open("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", "w") as f:
    f.write(ic_launcher)

print("Icons generated successfully.")
