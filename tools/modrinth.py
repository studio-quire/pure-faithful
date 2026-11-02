"""Pure Faithful's mods on Modrinth (docs/release.md > Modrinth): a release's jars as new versions, checked after upload.

    python tools/modrinth.py release <version> [--type beta|release|alpha]   upload local/release/<version>/'s jars
    python tools/modrinth.py verify <version>                                  compare Modrinth's files with SHA256SUMS

The token is read from local/modrinth-token.txt (git-ignored) and never printed. The projects belong to the Studio
Quire organisation; creating them was a one-off, done by hand through the same API.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent.parent
API = "https://api.modrinth.com"
UA = "studio-quire/pure-faithful (hello@studioquire.org)"
REPO = "https://github.com/studio-quire/pure-faithful"
# mod -> (project slug, environment as Modrinth's v3 versions take it)
MODS = {
    "hardening": ("pure-faithful-hardening", "client_or_server"),
    "saves": ("pure-faithful-saves", "client_or_server"),
    "speed": ("pure-faithful-speed", "client_only"),
    "display": ("pure-faithful-display", "client_only"),
}


def session():
    s = requests.Session()
    s.headers.update({"Authorization": (ROOT / "local" / "modrinth-token.txt").read_text(encoding="utf-8").strip(),
                      "User-Agent": UA})
    return s


def ok(r, what):
    if r.status_code >= 300:
        raise SystemExit(f"{what}: HTTP {r.status_code}: {r.text[:500]}")
    return r.json() if r.content else None


def release(version, kind):
    s = session()
    folder = ROOT / "local" / "release" / version
    for mod, (slug, env) in MODS.items():
        jar = folder / f"purefaithful-{mod}-{version}.jar"
        project = ok(s.get(f"{API}/v2/project/{slug}"), slug)
        if any(v["version_number"] == version for v in ok(s.get(f"{API}/v2/project/{slug}/version"), slug)):
            print(f"{slug} {version}: already there")
            continue
        # b1.7.3 takes the babric loader only: Modrinth refuses "fabric" for game versions before 18w43b.
        data = {"name": f"Pure Faithful {version}", "version_number": version,
                "changelog": f"Part of Pure Faithful {version}. The release's changelog: {REPO}/blob/main/CHANGELOG.md",
                "dependencies": [], "game_versions": ["b1.7.3"], "version_type": kind, "loaders": ["babric"],
                "featured": True, "project_id": project["id"], "file_parts": ["file"], "primary_file": "file"}
        v = ok(s.post(f"{API}/v2/version", files={"data": (None, json.dumps(data), "application/json"),
                                                  "file": (jar.name, jar.read_bytes(), "application/java-archive")}), slug)
        # Sides are per version now (v3's environment); without one, Modrinth shows "unknown".
        ok(s.patch(f"{API}/v3/version/{v['id']}", json={"environment": env}), slug + " environment")
        print(f"{slug} {version}: uploaded ({v['id']})")
    verify(version)


def verify(version):
    s = session()
    sums = {}
    for line in (ROOT / "local" / "release" / version / "SHA256SUMS.txt").read_text().splitlines():
        digest, name = line.split()
        sums[name] = digest
    bad = 0
    for mod, (slug, _) in MODS.items():
        v = next((v for v in ok(s.get(f"{API}/v2/project/{slug}/version"), slug) if v["version_number"] == version), None)
        if v is None:
            print(f"{slug}: no {version}"); bad += 1; continue
        f = next(f for f in v["files"] if f["primary"])
        data = requests.get(f["url"], headers={"User-Agent": UA}).content
        same = hashlib.sha256(data).hexdigest() == sums.get(f["filename"])
        print(f"{slug} {version}: {f['filename']} {'matches SHA256SUMS' if same else 'DIFFERS'}")
        bad += not same
    return bad


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("release"); r.add_argument("version"); r.add_argument("--type", default="beta")
    v = sub.add_parser("verify"); v.add_argument("version")
    a = ap.parse_args()
    if a.cmd == "release":
        release(a.version, a.type)
    else:
        sys.exit(1 if verify(a.version) else 0)


if __name__ == "__main__":
    main()
