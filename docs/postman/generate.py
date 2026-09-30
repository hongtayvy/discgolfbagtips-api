#!/usr/bin/env python3
"""Regenerate the Postman collection from the running API's OpenAPI document.

A hand-maintained collection drifts the moment an endpoint changes, so this reads the live spec
instead. Request bodies are curated here rather than derived from schemas, because a schema stub is
not something you can press Send on.

    SPRING_PROFILES_ACTIVE=local mvn spring-boot:run     # in another shell
    python3 docs/postman/generate.py
"""
import collections
import json
import pathlib
import sys
import urllib.request

SPEC_URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/v3/api-docs"
OUT = pathlib.Path(__file__).parent / "disc-golf-bag-tips.postman_collection.json"

BAG = [
    {"name": "Aviar", "brand": "Innova", "plastic": "DX", "weightGrams": 175, "wear": "BEAT_IN"},
    {"name": "Buzzz", "brand": "Discraft", "plastic": "ESP", "weightGrams": 177, "wear": "SEASONED"},
    {"name": "Teebird", "brand": "Innova", "plastic": "Champion", "weightGrams": 175},
]
PROFILE = {"skillLevel": "INTERMEDIATE", "throwingStyle": "FOREHAND", "courseType": "WOODED"}
ANALYSIS = {"bag": BAG, "profile": PROFILE, "conditions": {"weather": "WINDY"}}
ANALYSIS_FULL = dict(ANALYSIS, filters={"brands": ["Discraft", "Dynamic Discs"], "maxSpeed": 12},
                     carriedBag={"brand": "GRIPeq", "model": "G-Series"})
BODIES = {
    ("post", "/api/v1/recommendations"): ANALYSIS_FULL,
    ("post", "/api/v1/lineup"): ANALYSIS_FULL,
    ("post", "/api/v1/session/bag"): ANALYSIS,
    ("post", "/api/v1/profiles"): {"name": "Wooded / East Coast",
                                   "description": "tight lines, forehand-heavy", "bag": ANALYSIS},
}
EXAMPLE_QUERY = {"q": "buzz", "limit": "10", "capacity": "18", "brand": "Innova", "type": "BACKPACK"}


def build_url(path, params):
    raw = "{{baseUrl}}" + path
    query = [{"key": p["name"], "value": EXAMPLE_QUERY.get(p["name"], ""),
              "description": p.get("description", ""),
              "disabled": not p.get("required", False) and p["name"] not in EXAMPLE_QUERY}
             for p in params if p.get("in") == "query"]
    if query:
        raw += "?" + "&".join(f"{q['key']}={q['value']}" for q in query if not q["disabled"])
    out = {"raw": raw, "host": ["{{baseUrl}}"],
           "path": [s for s in path.strip("/").split("/") if s]}
    if query:
        out["query"] = query
    variables = [{"key": p["name"], "value": "", "description": p.get("description", "")}
                 for p in params if p.get("in") == "path"]
    if variables:
        out["variable"] = variables
    return out


def main():
    with urllib.request.urlopen(SPEC_URL, timeout=30) as response:
        spec = json.load(response)

    folders = collections.OrderedDict()
    for path, methods in sorted(spec.get("paths", {}).items()):
        for method, op in methods.items():
            if method not in ("get", "post", "put", "delete", "patch"):
                continue
            headers = []
            body = BODIES.get((method, path))
            if body is not None:
                headers.append({"key": "Content-Type", "value": "application/json"})
            if path.startswith("/api/v1/admin"):
                headers.append({"key": "X-Admin-Token", "value": "{{adminToken}}"})

            request = {"method": method.upper(), "header": headers,
                       "url": build_url(path, op.get("parameters", [])),
                       "description": op.get("description") or op.get("summary", "")}
            if body is not None:
                request["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2),
                                   "options": {"raw": {"language": "json"}}}
            folders.setdefault((op.get("tags") or ["Other"])[0], []).append(
                {"name": op.get("summary") or f"{method.upper()} {path}",
                 "request": request, "response": []})

    collection = {
        "info": {
            "name": spec["info"]["title"],
            "description": (
                "Generated from the live OpenAPI document at /v3/api-docs, so it cannot drift from "
                "the code. Regenerate with `python3 docs/postman/generate.py`.\n\n"
                "Every request body is runnable as-is rather than a schema stub.\n\n"
                "Session: the analysis and profile endpoints use a `BAGTIPS_SESSION` cookie. Postman "
                "keeps cookies per domain automatically, so saving a bag and listing it back works "
                "without configuration.\n\n"
                "Variables: `baseUrl` and `adminToken` (matches `bagtips.admin-token`; the local "
                "profile uses `local-dev-token`)."),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "item": [{"name": tag, "item": items} for tag, items in folders.items()],
        "variable": [
            {"key": "baseUrl", "value": "http://localhost:8080", "type": "string"},
            {"key": "adminToken", "value": "local-dev-token", "type": "string"},
        ],
    }
    OUT.write_text(json.dumps(collection, indent=2) + "\n")
    print(f"wrote {OUT.name}: {len(folders)} folders, "
          f"{sum(len(v) for v in folders.values())} requests")


if __name__ == "__main__":
    main()
