import base64
import json
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import lumen_update_feed as feed


class FeedTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Ephemeral test key; only the public key and signed envelope leave the helper.
        cls.signer = """
const crypto = require('node:crypto'), fs = require('node:fs');
const pair = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const raw = fs.readFileSync(0);
console.log(JSON.stringify({
  publicKey: pair.publicKey.export({type:'spki',format:'der'}).toString('base64'),
  schemaVersion:2, keyId:'test', payload:raw.toString('base64'),
  signature:crypto.sign('sha256',raw,pair.privateKey).toString('base64')
}));
"""

    def setUp(self):
        self.meta = dict(repository="zwwd1/lumen-timetable", sourceCommit="b" * 40, version="1.2.18",
            artifact=dict(packageName="cn.scvtc.campus.next", versionCode=27, size=100, sha256="a" * 64,
                abi="arm64-v8a", file="Lumen-Timetable-1.2.18.apk"))
        mirrors = [dict(id="github", name="GitHub",
            url="https://github.com/zwwd1/lumen-timetable/releases/download/v1.2.18/Lumen-Timetable-1.2.18.apk")]
        self.payload = dict(channel="stable", revision=200, packageName="cn.scvtc.campus.next", versionCode=27,
            versionName="1.2.18", sourceSha="b" * 40, minSdk=33, size=100, sha256="a" * 64,
            mirrors=mirrors, forceUpdate=False, releaseNotes="更新",
            artifacts=[dict(abi="arm64-v8a", size=100, sha256="a" * 64, mirrors=mirrors)])

    def signed(self, payload=None):
        raw = json.dumps(payload or self.payload, ensure_ascii=False).encode()
        result = subprocess.run(["node", "-e", self.signer], input=raw, check=True, capture_output=True)
        envelope = json.loads(result.stdout)
        self.keys = {"test": envelope.pop("publicKey")}
        return envelope

    def test_exact_release_passes(self):
        self.assertEqual(feed.validate_release_feed(self.signed(), self.keys, self.meta), self.payload)

    def test_stale_or_mismatched_release_is_rejected(self):
        for key, value in [("versionCode", 26), ("sha256", "c" * 64), ("size", 101), ("sourceSha", "c" * 40)]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                feed.validate_release_feed(self.signed(dict(self.payload, **{key: value})), self.keys, self.meta)

    def test_bad_signature_rejected(self):
        envelope = self.signed()
        envelope["payload"] = base64.b64encode(b"{}").decode()
        with self.assertRaises(ValueError):
            feed.validate_release_feed(envelope, self.keys, self.meta)

    def test_wrong_download_or_abi_rejected(self):
        for change in [dict(mirrors=[]), dict(artifacts=[])]:
            with self.assertRaises(ValueError):
                feed.validate_release_feed(self.signed(dict(self.payload, **change)), self.keys, self.meta)

    def test_rollback_and_same_version_replacement_rejected(self):
        for change in [dict(versionCode=26), dict(revision=199), dict(sha256="c" * 64)]:
            with self.assertRaises(ValueError):
                feed.ensure_forward(self.payload, dict(self.payload, **change))

    def test_publish_is_idempotent_and_verifies_stored_feed(self):
        envelope = self.signed()
        content = dict(sha="original", content=base64.b64encode(json.dumps(envelope).encode()).decode())
        from unittest.mock import Mock
        request = Mock(return_value=content)
        feed.publish_feed(request, "https://api.github.com/repos/zwwd1/lumen-timetable", envelope, self.payload, self.keys)
        self.assertEqual(request.call_count, 2)
        self.assertTrue(all(len(call.args) == 1 for call in request.call_args_list))

    def test_duplicate_fields_rejected(self):
        with self.assertRaises(ValueError):
            feed.strict_json('{"versionCode":27,"versionCode":26}')


if __name__ == "__main__":
    unittest.main()
