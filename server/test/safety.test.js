const test = require('node:test');
const assert = require('node:assert');
const { findMinorReference } = require('../src/safety');
const { buildVideoRequest, validateSettings } = require('../src/ofox');

test('flags minor-related terms', () => {
  for (const text of [
    'a teenager on the beach',
    'she is 16 years old',
    'a 17yo girl',
    'aged 15',
    'school girl uniform',
    'Two young girls',
    'an under-age character',
  ]) {
    assert.ok(findMinorReference(text), `expected a match for: ${text}`);
  }
});

test('does not flag adult descriptions', () => {
  for (const text of [
    'a 25 year old woman in a red dress',
    'two adults dancing at sunset',
    'a person in their thirties',
    'season finale, personal moment',
  ]) {
    assert.strictEqual(findMinorReference(text), null, `unexpected match for: ${text}`);
  }
});

const settings = { duration: 10, resolution: '720p', aspectRatio: '16:9', generateAudio: true, seed: null };

test('start frame only uses frame_images', () => {
  const body = buildVideoRequest({ model: 'm', settings, prompt: 'p', startFrameUrl: 'https://x/s', referenceUrls: [] });
  assert.deepStrictEqual(body.frame_images, [
    { type: 'image_url', image_url: { url: 'https://x/s' }, frame_type: 'first_frame' },
  ]);
  assert.strictEqual(body.input_references, undefined);
});

test('start frame + references puts start frame first in input_references', () => {
  const body = buildVideoRequest({
    model: 'm', settings, prompt: 'p', startFrameUrl: 'https://x/s', referenceUrls: ['https://x/a', 'https://x/b'],
  });
  assert.strictEqual(body.frame_images, undefined);
  assert.deepStrictEqual(body.input_references.map((r) => r.image_url.url), ['https://x/s', 'https://x/a', 'https://x/b']);
});

test('validates settings', () => {
  assert.strictEqual(validateSettings({ resolution: '1080p', duration: 30 }).errors.length, 0);
  assert.ok(validateSettings({ resolution: '4k', duration: 31 }).errors.length === 2);
});
