const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname,
  '../../app/src/main/assets/academic-plugin/gecko/environment.js'), 'utf8');

function realm(withFrames = true) {
  const nativeWindow = { document: {}, wrappedJSObject: { pageHelper: () => 'page' } };
  for (const key of ['window', 'self', 'top', 'parent', 'frames']) nativeWindow[key] = nativeWindow;
  nativeWindow.addEventListener = function (name, callback) {
    assert.equal(this, nativeWindow);
    callback(name);
  };
  class Frame {
    constructor(child) { this.child = child; }
    get contentWindow() { return this.child; }
  }
  const sandbox = { window: nativeWindow };
  if (withFrames) sandbox.HTMLIFrameElement = Frame;
  vm.runInNewContext(source, sandbox);
  return { sandbox, nativeWindow, Frame };
}

test('page helpers are available while native DOM and callback receivers stay native', () => {
  const { sandbox, nativeWindow } = realm();
  assert.equal(sandbox.unsafeWindow.pageHelper(), 'page');
  assert.equal(sandbox.unsafeWindow.document, nativeWindow.document);
  let received;
  sandbox.unsafeWindow.addEventListener('load', event => { received = event; });
  assert.equal(received, 'load');
  assert.equal(sandbox.unsafeWindow.self, sandbox.unsafeWindow);
});

test('iframe windows expose page helpers with a stable wrapper and native DOM nodes', () => {
  const { nativeWindow, Frame } = realm();
  const childDocument = {};
  const child = { document: childDocument, wrappedJSObject: { noSubmit: () => 42 }, parent: nativeWindow };
  child.window = child;
  child.self = child;
  const frame = new Frame(child);
  assert.ok(frame instanceof Frame);
  assert.equal(frame.contentWindow.noSubmit(), 42);
  assert.equal(frame.contentWindow.document, childDocument);
  assert.equal(frame.contentWindow, frame.contentWindow);
  assert.equal(frame.contentWindow.self, frame.contentWindow);
});

test('null iframe windows and contexts without iframe support remain usable', () => {
  const { Frame } = realm();
  assert.equal(new Frame(null).contentWindow, null);
  const { sandbox } = realm(false);
  assert.equal(sandbox.unsafeWindow.pageHelper(), 'page');
});

test('cross-origin document and page-global access errors still propagate', () => {
  const { Frame } = realm();
  const denied = Object.assign(new Error('Cross-origin access denied'), { name: 'SecurityError' });
  const child = {};
  Object.defineProperty(child, 'document', { get() { throw denied; } });
  Object.defineProperty(child, 'wrappedJSObject', { get() { throw denied; } });
  const frame = new Frame(child);
  assert.throws(() => frame.contentWindow.document, error => error === denied);
  assert.throws(() => frame.contentWindow.pageHelper, error => error === denied);
});
