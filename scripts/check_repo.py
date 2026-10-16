"""Public CI: hygiene, patcher compilation, and standalone health/physics checks."""
import hashlib
import json
import pathlib
import re
import subprocess
import shutil
import urllib.request

root = pathlib.Path(__file__).resolve().parent.parent
files = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
for name in filter(None, files):
    path = root / name
    if path.suffix.lower() in {".jar", ".class", ".dll", ".zip", ".bplus"}:
        raise SystemExit(f"Private input/binary must not be committed: {name}")
    content = path.read_text(encoding="utf-8-sig")
    if re.search(r"(?i)[a-z]:[/\\]users[/\\]", content):
        raise SystemExit(f"Machine-specific user path must not be committed: {name}")
    if re.search(r"\b(?:ghp_|github_pat_)[A-Za-z0-9_]{20,}", content):
        raise SystemExit(f"Possible credential must not be committed: {name}")

manifest = json.loads((root / "dependencies.json").read_text(encoding="utf-8-sig"))
cache = root / ".build" / "ci"
cache.mkdir(parents=True, exist_ok=True)
jars = []
for item in manifest:
    if item["name"] not in {"asm", "asm-tree"}:
        continue
    target = cache / item["file"]
    local_cache = root / ".build" / "deps" / item["file"]
    if not target.exists():
        if local_cache.exists():
            shutil.copyfile(local_cache, target)
        else:
            urllib.request.urlretrieve(item["url"], target)
    if hashlib.sha256(target.read_bytes()).hexdigest() != item["sha256"]:
        raise SystemExit(f"Dependency checksum mismatch: {item['name']}")
    jars.append(str(target))
import os
subprocess.run(["javac", "-source", "8", "-target", "8", "-encoding", "UTF-8", "-cp",
                os.pathsep.join(jars), "-d", str(cache), str(root / "src" / "PatchJar.java")], check=True)
print("PASS repository hygiene and standalone patcher compilation")
standalone = [root / "src" / "streamproof" / (name + ".java")
              for name in ("GameAccess", "TabHealth", "BowPhysics", "MotionHistory", "RodPhysics", "RodUse",
                           "BowGeometry", "BowController", "BowFlight", "BowSolution", "BowForecast", "BowLog", "BowDiagnostics")]
standalone.append(root / "test" / "HealthBowProbe.java")
standalone.append(root / "test" / "MotionPredictionProbe.java")
standalone.append(root / "test" / "RodAimProbe.java")
standalone.append(root / "test" / "BowAccuracyProbe.java")
standalone.append(root / "test" / "BowDiagnosticsProbe.java")
subprocess.run(["javac", "-source", "8", "-target", "8", "-encoding", "UTF-8",
                "-d", str(cache)] + [str(path) for path in standalone], check=True)
subprocess.run(["java", "-Xverify:all", "-cp", str(cache), "streamproof.HealthBowProbe"], check=True)
subprocess.run(["java", "-Xverify:all", "-cp", str(cache), "streamproof.MotionPredictionProbe"], check=True)
subprocess.run(["java", "-Xverify:all", "-cp", str(cache), "streamproof.RodAimProbe"], check=True)
subprocess.run(["java", "-Xverify:all", "-cp", str(cache), "streamproof.BowAccuracyProbe"], check=True)
subprocess.run(["java", "-Xverify:all", "-cp", str(cache), "streamproof.BowDiagnosticsProbe"], check=True)
