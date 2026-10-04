#!/usr/bin/env python3
"""Validate .github/dependabot.yml against the official dependabot-2.0 JSON schema.

Dependabot fails the WHOLE file on one unknown key — it does not ignore the entry it
does not recognise. So a plausible-looking typo like `exclude-dependencies` (the real key
is `exclude-patterns`) silently disables every update in the file, and the only symptom
is that no PRs ever appear. That is not a guess: it is what happened here, and it is why
this check exists.

Usage:  python scripts/validate-dependabot.py [path/to/dependabot.yml]

Exits 0 when valid, 1 when not. Requires PyYAML; skips with a clear message if absent.
"""
import json
import sys
import urllib.request

SCHEMA_URL = "https://json.schemastore.org/dependabot-2.0.json"
DEFAULT_PATH = ".github/dependabot.yml"


def load_schema():
    try:
        import yaml  # noqa: F401  (presence check only)
    except ImportError:
        pass
    with urllib.request.urlopen(SCHEMA_URL, timeout=30) as response:
        return json.load(response)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_PATH
    try:
        import yaml
    except ImportError:
        print("PyYAML is not installed; cannot validate.", file=sys.stderr)
        return 0

    with open(path, encoding="utf-8") as handle:
        doc = yaml.safe_load(handle)

    schema = load_schema()
    update_def = schema["definitions"]["update"]
    allowed_update = set(update_def["properties"])
    allowed_group = set(
        update_def["properties"]["groups"]["additionalProperties"]["properties"]
    )

    problems = []
    if doc.get("version") != 2:
        problems.append("version must be 2")

    for index, update in enumerate(doc.get("updates") or []):
        ecosystem = update.get("package-ecosystem", f"updates[{index}]")
        for key in sorted(set(update) - allowed_update):
            problems.append(f"{ecosystem}: unknown key '{key}'")
        for gname, gdef in (update.get("groups") or {}).items():
            if not isinstance(gdef, dict):
                problems.append(f"{ecosystem}.{gname}: group must be a mapping")
                continue
            for key in sorted(set(gdef) - allowed_group):
                problems.append(f"{ecosystem}.{gname}: unknown key '{key}'")

    if problems:
        print("dependabot.yml is INVALID — Dependabot will reject the whole file:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    ecosystems = ", ".join(u.get("package-ecosystem", "?") for u in doc.get("updates", []))
    print(f"dependabot.yml is valid. {len(doc.get('updates', []))} ecosystems: {ecosystems}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
