"""Publish a previously built APK; never accept or use an Android signing private key."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from lumen_update_feed import validate_release_feed, publish_feed

ROOT = Path(__file__).resolve().parents[1]
META = json.loads((ROOT / 'releases/release.json').read_text())
REPO = os.environ['GITHUB_REPOSITORY']
API = 'https://api.github.com/repos/' + REPO
assert REPO == META['repository']
assert re.fullmatch(r'[0-9a-f]{40}', META['sourceCommit'])
assert re.fullmatch(r'\d+\.\d+\.\d+', META['version'])
update_keys = json.loads((ROOT / 'app/src/main/assets/app-update-keys.json').read_text())
update_envelope = json.loads((ROOT / 'releases/update-manifest.json').read_text())
update_payload = validate_release_feed(update_envelope, update_keys, META)
HEADERS = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2026-03-10'}

def request(url, data=None, mime='application/json', method=None):
    headers = dict(HEADERS)
    if data is not None: headers['Content-Type'] = mime
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=120) as response:
        return json.load(response)

def digest(data):
    return hashlib.sha256(data).hexdigest()

artifacts = [(META['artifact'], META['parts'])] + [(entry['artifact'], entry['parts']) for entry in META.get('additionalArtifacts', [])]
verified_files = []
release_notes = (ROOT / 'releases' / (META['version'] + '.md')).read_text()
for artifact, parts in artifacts:
    assert Path(artifact['file']).name == artifact['file'] and artifact['file'].endswith('.apk')
    expected = {
        'zwwd1/scvtc-kejian': ('cn.scvtc.campus.preview', '5cab58b4d47d14e66143be594d3a4eebb5624be7e8f6a15354126bdab47343b3'),
        'zwwd1/scvtc-zhixue': ('cn.scvtc.campus.next', 'f4eb44ef0231232918e6bfe2bae00506d9bb77c49b6f516869fadaf2dcff7aed'),
        'zwwd1/gnomon-timetable': ('cn.scvtc.campus.preview', '5cab58b4d47d14e66143be594d3a4eebb5624be7e8f6a15354126bdab47343b3'),
        'zwwd1/lumen-timetable': ('cn.scvtc.campus.next', 'f4eb44ef0231232918e6bfe2bae00506d9bb77c49b6f516869fadaf2dcff7aed'),
    }[REPO]
    assert (artifact['packageName'], artifact['certificateSha256']) == expected
    apk = bytearray()
    for part in parts:
        assert re.fullmatch(r'[0-9a-f]{40}', part['blob'])
        blob = request(API + '/git/blobs/' + part['blob'])
        assert blob['encoding'] == 'base64'
        data = base64.b64decode(''.join(blob['content'].split()), validate=True)
        assert len(data) == part['size'] and digest(data) == part['sha256']
        apk.extend(data)
    assert len(apk) == artifact['size'] and digest(apk) == artifact['sha256']
    with tempfile.TemporaryDirectory() as temporary:
        path = Path(temporary) / artifact['file']
        path.write_bytes(apk)
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None
            assert META['sourceCommit'].encode() in archive.read('META-INF/version-control-info.textproto')
            history = json.loads(archive.read('assets/release-history.json'))
            current = next(entry for entry in history if entry['version'] == META['version'])
            assert current['body'] in release_notes, 'APK history and GitHub release notes differ'
            abis = {name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')}
            if artifact.get('abi') == 'arm64-v8a': assert abis == {'arm64-v8a'}
            if artifact.get('abi') == 'universal' and expected[0] == 'cn.scvtc.campus.next': assert abis == {'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'}
        sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools'
        tools = sorted((p for p in sdk.iterdir() if (p / 'apksigner').is_file()), key=lambda p: tuple(map(int, re.findall(r'\d+', p.name))))[-1]
        cert = subprocess.check_output([str(tools/'apksigner'), 'verify', '--verbose', '--print-certs', str(path)], text=True)
        certificate_digests = re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]{64})', cert)
        assert {value.lower() for value in certificate_digests} == {expected[1]}, cert
        manifest = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(path)], text=True)
        assert "package: name='" + expected[0] + "' versionCode='" + str(artifact['versionCode']) + "' versionName='" + META['version'] + "'" in manifest
        assert 'application-debuggable' not in manifest
        subprocess.run([str(tools/'zipalign'), '-c', '-P', '16', '4', str(path)], check=True)
    verified_files.append((artifact, apk))

# All checks complete before creating a draft or a public tag.
tag = 'v' + META['version']
try:
    release = request(API + '/releases/tags/' + tag)
except urllib.error.HTTPError as error:
    if error.code != 404: raise
    drafts = request(API + '/releases?per_page=100')
    release = next((r for r in drafts if r['tag_name'] == tag and r['draft']), None)
    if release is None:
        release = request(API + '/releases', json.dumps({'tag_name': tag, 'target_commitish': META['sourceCommit'], 'name': META['name'] + ' ' + META['version'], 'body': release_notes, 'draft': True, 'prerelease': False}, ensure_ascii=False).encode())
assets = {a['name']: a for a in release['assets']}
provenance = (ROOT / 'BUILD_PROVENANCE.json').read_bytes()
upload_files = [('BUILD_PROVENANCE.json', provenance, 'application/json')]
for artifact, apk in verified_files:
    checksum = (artifact['sha256'] + '  ' + artifact['file'] + '\n').encode()
    upload_files.extend([(artifact['file'], apk, 'application/vnd.android.package-archive'), (artifact['file'] + '.sha256', checksum, 'text/plain')])
for filename, data, mime in upload_files:
    if filename in assets:
        assert assets[filename].get('digest') == 'sha256:' + digest(data), 'Existing asset differs; it was not overwritten'
        continue
    upload = release['upload_url'].split('{')[0] + '?name=' + urllib.parse.quote(filename)
    result = request(upload, bytes(data), mime)
    assert result['size'] == len(data) and result.get('digest') == 'sha256:' + digest(data)
if release['draft']:
    release = request(API + '/releases/' + str(release['id']), json.dumps({'draft': False, 'make_latest': 'true'}).encode(), method='PATCH')
assert not release['draft'] and not release['prerelease']
publish_feed(request, API, update_envelope, update_payload, update_keys)
print('Published verified release: ' + release['html_url'])
print('Verified signed app update feed: v' + update_payload['versionName'])
