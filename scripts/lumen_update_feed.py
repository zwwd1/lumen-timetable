"""Verify the signed app feed against the exact, immutable release artifact."""
import base64
import json
from pathlib import Path
import subprocess


def strict_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Duplicate update field")
            result[key] = value
        return result
    if isinstance(raw, bytes):
        raw = raw.decode("utf-8", errors="strict")
    if len(raw.encode("utf-8")) > 128 * 1024:
        raise ValueError("Update manifest too large")
    return json.loads(raw, object_pairs_hook=unique,
        parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Invalid JSON number")))


def verify_envelope(envelope, keys):
    if isinstance(envelope, (str, bytes)):
        envelope = strict_json(envelope)
    if envelope.get("schemaVersion") != 2 or envelope.get("keyId") not in keys:
        raise ValueError("Untrusted update key or schema")
    payload = base64.b64decode(envelope["payload"], validate=True)
    signature = base64.b64decode(envelope["signature"], validate=True)
    public = base64.b64decode(keys[envelope["keyId"]], validate=True)
    result = subprocess.run(["node", str(Path(__file__).with_name("verify_update_signature.cjs"))],
        input=json.dumps(dict(publicKey=base64.b64encode(public).decode(),
            payload=base64.b64encode(payload).decode(), signature=base64.b64encode(signature).decode())).encode(),
        capture_output=True, timeout=10)
    if result.returncode:
        raise ValueError("Invalid update signature")
    return strict_json(payload)


def validate_release_feed(envelope, keys, metadata):
    payload = verify_envelope(envelope, keys)
    artifact = metadata["artifact"]
    expected = dict(channel="stable", packageName=artifact["packageName"],
        versionCode=artifact["versionCode"], versionName=metadata["version"],
        sourceSha=metadata["sourceCommit"], size=artifact["size"], sha256=artifact["sha256"], minSdk=33)
    if any(type(payload.get(key)) is not type(value) or payload.get(key) != value for key, value in expected.items()):
        raise ValueError("Signed update manifest does not match release")
    url = "https://github.com/" + metadata["repository"] + "/releases/download/v" + metadata["version"] + "/" + artifact["file"]
    if payload.get("mirrors") != [dict(id="github", name="GitHub", url=url)]:
        raise ValueError("Update download must use the verified release")
    if payload.get("artifacts") != [dict(abi=artifact["abi"], size=artifact["size"],
            sha256=artifact["sha256"], mirrors=payload["mirrors"])]:
        raise ValueError("Update ABI artifact does not match release")
    if type(payload.get("revision")) is not int or payload["revision"] < 1:
        raise ValueError("Invalid update revision")
    if payload.get("forceUpdate") is not False or not isinstance(payload.get("releaseNotes"), str):
        raise ValueError("Invalid update display fields")
    return payload


def ensure_forward(previous, incoming):
    if incoming["versionCode"] < previous["versionCode"] or incoming["revision"] < previous["revision"]:
        raise ValueError("Refusing update feed rollback")
    if incoming["versionCode"] == previous["versionCode"] and any(incoming[key] != previous[key] for key in ("sha256", "size", "sourceSha")):
        raise ValueError("Refusing replacement of a published update")
    if incoming["revision"] == previous["revision"] and incoming != previous:
        raise ValueError("Conflicting update revision")


def publish_feed(request, api, envelope, payload, keys):
    current = request(api + "/contents/updates/stable.json?ref=main")
    old = verify_envelope(base64.b64decode(current["content"]), keys)
    ensure_forward(old, payload)
    if old != payload:
        body = json.dumps(dict(message="Publish verified Lumen update feed v" + payload["versionName"],
            content=base64.b64encode((json.dumps(envelope, indent=2) + "\n").encode()).decode(),
            sha=current["sha"], branch="main")).encode()
        request(api + "/contents/updates/stable.json", body, method="PUT")
    stored = request(api + "/contents/updates/stable.json?ref=main")
    if verify_envelope(base64.b64decode(stored["content"]), keys) != payload:
        raise ValueError("Published update feed differs")
