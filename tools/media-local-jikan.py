#!/usr/bin/env python3
"""Start a real, isolated Jikan v4 instance for emulator validation.

Uses the official image and MongoDB. Seeds two actual MAL entries, not fixtures.
The host endpoint is http://127.0.0.1:8702/v4/; use adb reverse tcp:8702 tcp:8702.
The small validation catalogue contains Cowboy Bebop and Berserk. A full Jikan
catalogue requires running the upstream indexers separately.
"""
import argparse
import json
import subprocess
import tempfile
import time
import urllib.request
from pathlib import Path

API = "cobalt-jikan-api"
DB = "cobalt-jikan-mongo"
NETWORK = "cobalt-media-validation"
IMAGE = "jikanme/jikan-rest@sha256:505f06101617c337db5b301701258de63a55f6593b0181cf2d84c92ff49cee9d"
MONGO = "mongo@sha256:8bb4442761a63a8dda452744082cdac9644b429807eb977b42324a63cef59ca4"
BASE = "http://127.0.0.1:8702/v4"

def docker(*args, check=True):
    return subprocess.run(["docker", *args], check=check, capture_output=True, text=True)

def exists(kind, name):
    return docker(kind, "inspect", name, check=False).returncode == 0

def get(path):
    with urllib.request.urlopen(BASE + path, timeout=20) as response:
        return json.load(response)

def start():
    if not exists("network", NETWORK):
        docker("network", "create", NETWORK)
    if not exists("container", DB):
        print("Preparing MongoDB…", flush=True)
        docker("pull", MONGO)
        docker("create", "--name", DB, "--network", NETWORK, "--memory", "512m",
               "--cpus", "1", "--label", "io.cobalt.media-validation=true", MONGO,
               "--wiredTigerCacheSizeGB", "0.25")
    docker("start", DB)
    if not exists("container", API):
        print("Preparing the official Jikan image…", flush=True)
        docker("pull", IMAGE)
        docker("create", "--name", API, "--network", NETWORK, "--memory", "512m",
               "--cpus", "2", "--label", "io.cobalt.media-validation=true",
               "-p", "127.0.0.1:8702:8080", IMAGE)
        with tempfile.TemporaryDirectory(prefix="cobalt-jikan-config-") as directory:
            root = Path(directory)
            env = root / "server.env"
            rr = root / "server.rr.yaml"
            docker("cp", API + ":/app/.env.dist", str(env))
            docker("cp", API + ":/app/.rr.yaml", str(rr))
            # Omit SCOUT_DRIVER: the official config defaults to its null engine.
            env.write_text(env.read_text() + "\nDB_HOST=" + DB + "\nLOG_LEVEL=warning\nTHROTTLE=false\n")
            # Fresh workers avoid accumulated Mongo text-query state in this image.
            rr.write_text(rr.read_text().replace("num_workers: 0", "num_workers: 2")
                          .replace("max_jobs: 64", "max_jobs: 1"))
            env.chmod(0o666)
            rr.chmod(0o666)
            docker("cp", str(env), API + ":/app/.env")
            docker("cp", str(rr), API + ":/app/.rr.yaml")
    docker("start", API)
    for attempt in range(20):
        try:
            get("")
            break
        except Exception:
            if attempt == 19:
                raise RuntimeError("Jikan failed to become ready; inspect docker logs " + API)
            time.sleep(1)
    docker("exec", "--user", "0", API, "chown", "10001:10001", "/app/.env", "/app/.rr.yaml")
    print(docker("exec", API, "php", "artisan", "migrate", "--force").stdout.strip())
    for path in ("/anime/1", "/manga/2"):
        data = get(path).get("data")
        if not data:
            raise RuntimeError("Jikan could not fetch " + path)
        print("Seeded", data.get("title"))
    print("Local Jikan ready:", BASE + "/")

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("action", choices=("start", "stop", "status"))
args = parser.parse_args()
if args.action == "start":
    start()
elif args.action == "stop":
    for name in (API, DB):
        if exists("container", name):
            docker("stop", name)
    print("Stopped the validation server; its catalogue remains in its container.")
else:
    print(json.dumps(get(""), indent=2))
