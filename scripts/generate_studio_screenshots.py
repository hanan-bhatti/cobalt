import subprocess
import os
import sys

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCREENSHOTS_DIR = os.path.join(PROJECT_ROOT, "assets", "screenshots")

THEMES = {
    "sunset": {
        "bg": "radial-gradient(circle at 75% 25%, #ff8c42 0%, #ff3b30 45%, #6a0572 75%, #1a0826 100%)",
        "glow": "rgba(255, 120, 60, 0.35)",
        "accent": "#ff8c42"
    },
    "cobalt": {
        "bg": "radial-gradient(circle at 50% 30%, #0d2847 0%, #061325 50%, #020710 100%)",
        "glow": "rgba(0, 229, 255, 0.28)",
        "accent": "#00e5ff"
    },
    "aurora": {
        "bg": "radial-gradient(circle at 35% 25%, #00b4d8 0%, #0077b6 40%, #023e8a 70%, #03045e 100%)",
        "glow": "rgba(0, 245, 212, 0.3)",
        "accent": "#00f5d4"
    },
    "purple_haze": {
        "bg": "radial-gradient(circle at 65% 25%, #8338ec 0%, #3a0ca3 50%, #10002b 100%)",
        "glow": "rgba(199, 125, 255, 0.3)",
        "accent": "#c77dff"
    },
    "crimson": {
        "bg": "radial-gradient(circle at 70% 30%, #e63946 0%, #9b2226 50%, #1a0507 100%)",
        "glow": "rgba(230, 57, 70, 0.35)",
        "accent": "#e63946"
    },
    "slate": {
        "bg": "radial-gradient(circle at 50% 30%, #1e293b 0%, #0f172a 60%, #020617 100%)",
        "glow": "rgba(148, 163, 184, 0.2)",
        "accent": "#94a3b8"
    }
}

def render_mockup(
    src_image,
    dest_image,
    title="",
    subtitle="",
    theme_key="sunset",
    tilt="none", # "none", "tilt-left", "tilt-right", "isometric"
    width=1080,
    height=1440
):
    theme = THEMES.get(theme_key, THEMES["sunset"])
    bg_css = f"background: {theme['bg']};"
    glow_color = theme["glow"]
    accent_color = theme["accent"]

    # Dynamic 3D perspective transforms
    if tilt == "tilt-left":
        transform_css = "transform: perspective(1400px) rotateY(13deg) rotateX(7deg) rotateZ(-2.5deg) scale(0.96);"
        shadow_css = f"box-shadow: -22px 30px 70px -10px rgba(0,0,0,0.7), -40px 60px 120px -20px {glow_color};"
    elif tilt == "tilt-right":
        transform_css = "transform: perspective(1400px) rotateY(-13deg) rotateX(7deg) rotateZ(2.5deg) scale(0.96);"
        shadow_css = f"box-shadow: 22px 30px 70px -10px rgba(0,0,0,0.7), 40px 60px 120px -20px {glow_color};"
    elif tilt == "isometric":
        transform_css = "transform: perspective(1200px) rotateX(16deg) rotateY(-16deg) rotateZ(5deg) scale(0.92);"
        shadow_css = f"box-shadow: 30px 40px 85px -10px rgba(0,0,0,0.8), 45px 65px 130px -20px {glow_color};"
    else:
        transform_css = "transform: scale(0.98);"
        shadow_css = f"box-shadow: 0 30px 80px -15px rgba(0,0,0,0.75), 0 50px 140px -20px {glow_color};"

    header_html = ""
    if title:
        header_html = f"""
        <div class="header-card">
          <div class="badge-pill">COBALT v2.2.2</div>
          <h1 class="card-title">{title}</h1>
          <p class="card-subtitle">{subtitle}</p>
        </div>
        """

    src_abs = os.path.abspath(src_image)
    dest_abs = os.path.abspath(dest_image)
    os.makedirs(os.path.dirname(dest_abs), exist_ok=True)

    html_content = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    width: {width}px;
    height: {height}px;
    {bg_css}
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Inter", Helvetica, Arial, sans-serif;
    position: relative;
    -webkit-font-smoothing: antialiased;
  }}

  /* Ambient Mesh & Radial Spotlights */
  .ambient-glow {{
    position: absolute;
    width: {int(width * 0.9)}px;
    height: {int(height * 0.9)}px;
    background: radial-gradient(circle, {glow_color} 0%, transparent 70%);
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    pointer-events: none;
    filter: blur(45px);
  }}

  .grid-pattern {{
    position: absolute;
    inset: 0;
    background-image: 
      linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
    background-size: 44px 44px;
    mask-image: radial-gradient(ellipse at center, black 40%, transparent 85%);
    pointer-events: none;
  }}

  .header-card {{
    text-align: center;
    margin-bottom: 24px;
    z-index: 5;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
  }}

  .badge-pill {{
    display: inline-block;
    padding: 6px 14px;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.12);
    backdrop-filter: blur(12px);
    border: 1px solid rgba(255, 255, 255, 0.2);
    color: #ffffff;
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 1.2px;
    text-transform: uppercase;
  }}

  .card-title {{
    color: #ffffff;
    font-size: 32px;
    font-weight: 800;
    letter-spacing: -0.5px;
    text-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
  }}

  .card-subtitle {{
    color: rgba(255, 255, 255, 0.85);
    font-size: 17px;
    font-weight: 500;
    max-width: 600px;
    line-height: 1.4;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }}

  /* Phone Mockup Frame */
  .device-container {{
    position: relative;
    z-index: 2;
    {transform_css}
  }}

  .phone-frame {{
    position: relative;
    width: 530px;
    height: 1180px;
    background: #0f1218;
    border-radius: 56px;
    padding: 13px;
    border: 1.5px solid rgba(255, 255, 255, 0.22);
    {shadow_css}
  }}

  /* Metallic Bezel Layer */
  .phone-frame::before {{
    content: "";
    position: absolute;
    inset: -3px;
    border-radius: 59px;
    border: 2px solid rgba(255, 255, 255, 0.08);
    pointer-events: none;
  }}

  .screen-wrapper {{
    width: 100%;
    height: 100%;
    border-radius: 44px;
    overflow: hidden;
    position: relative;
    background: #000;
  }}

  .screen-img {{
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }}

  /* Centered Punch Hole Camera */
  .camera-punch {{
    position: absolute;
    top: 15px;
    left: 50%;
    transform: translateX(-50%);
    width: 14px;
    height: 14px;
    background: #000000;
    border-radius: 50%;
    box-shadow: inset 0 0 3px rgba(255, 255, 255, 0.35), 0 0 0 1.5px #1a1e28;
    z-index: 10;
  }}

  /* Glass Specular Glare */
  .glare-overlay {{
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.12) 0%, rgba(255, 255, 255, 0.02) 34%, transparent 60%);
    pointer-events: none;
    z-index: 8;
  }}
</style>
</head>
<body>
  <div class="ambient-glow"></div>
  <div class="grid-pattern"></div>
  {header_html}
  <div class="device-container">
    <div class="phone-frame">
      <div class="camera-punch"></div>
      <div class="screen-wrapper">
        <div class="glare-overlay"></div>
        <img class="screen-img" src="file://{src_abs}" />
      </div>
    </div>
  </div>
</body>
</html>
"""
    tmp_html = f"/tmp/studio_mockups/mock_{os.path.basename(dest_image)}.html"
    with open(tmp_html, "w", encoding="utf-8") as f:
        f.write(html_content)

    cmd = [
        "chromium",
        "--headless=new",
        "--disable-gpu",
        f"--screenshot={dest_abs}",
        f"--window-size={width},{height}",
        f"file://{tmp_html}"
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(f"✅ Created studio mockup: {dest_image}")

def main():
    os.makedirs(SCREENSHOTS_DIR, exist_ok=True)
    
    mockups = [
        {
            "src": "screenshots/seeded_notes_home2.png",
            "dest": "assets/screenshots/dashboard_home.png",
            "theme": "sunset",
            "tilt": "tilt-right"
        },
        {
            "src": "assets/screenshots/floating_edge_panel.png",
            "dest": "assets/screenshots/floating_edge_panel.png",
            "theme": "cobalt",
            "tilt": "tilt-left"
        },
        {
            "src": "/tmp/video_all_frames/sec_12.png",
            "dest": "assets/screenshots/ocr_target_screenshot.png",
            "theme": "cobalt",
            "tilt": "tilt-right"
        },
        {
            "src": "/tmp/video_all_frames/sec_18.png",
            "dest": "assets/screenshots/ocr_extracted_links.png",
            "theme": "aurora",
            "tilt": "tilt-left"
        },
        {
            "src": "screenshots/media_search_sheet.png",
            "dest": "assets/screenshots/folders_screen.png",
            "theme": "aurora",
            "tilt": "straight"
        },
        {
            "src": "screenshots/edit_mode_capture.png",
            "dest": "assets/screenshots/capture_link.png",
            "theme": "purple_haze",
            "tilt": "straight"
        },
        {
            "src": "screenshots/settings_tab2.png",
            "dest": "assets/screenshots/memory_detail.png",
            "theme": "sunset",
            "tilt": "tilt-right"
        },
        {
            "src": "screenshots/deleted_undo_toast.png",
            "dest": "assets/screenshots/voice_memo_transcription.png",
            "theme": "purple_haze",
            "tilt": "straight"
        },
        {
            "src": "assets/screenshots/voice_memo_recording.png",
            "dest": "assets/screenshots/voice_memo_recording.png",
            "theme": "crimson",
            "tilt": "tilt-left"
        },
        {
            "src": "assets/screenshots/settings_screen.png",
            "dest": "assets/screenshots/settings_screen.png",
            "theme": "slate",
            "tilt": "straight"
        }
    ]

    for m in mockups:
        print(f"Rendering {m['dest']}...")
        render_mockup(
            src_image=m["src"],
            dest_image=m["dest"],
            theme_key=m["theme"],
            tilt=m["tilt"]
        )

if __name__ == "__main__":
    main()
