const fs = require('node:fs');
const crypto = require('node:crypto');
const value = JSON.parse(fs.readFileSync(0, 'utf8'));
const key = crypto.createPublicKey({ key: Buffer.from(value.publicKey, 'base64'), format: 'der', type: 'spki' });
if (key.asymmetricKeyType !== 'ec' || key.asymmetricKeyDetails.namedCurve !== 'prime256v1' ||
    !crypto.verify('sha256', Buffer.from(value.payload, 'base64'), key, Buffer.from(value.signature, 'base64'))) {
  process.exit(1);
}
