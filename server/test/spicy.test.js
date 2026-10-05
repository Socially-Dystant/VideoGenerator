const test = require('node:test');
const assert = require('node:assert');
const { SpicyClient, isSpicyId } = require('../src/spicy');

const client = new SpicyClient({ apiKey: 'k', baseUrl: 'https://x', modelBase: 'alibaba/wan-3.0-prime' });
const settings = { duration: 10, resolution: '1080p', aspectRatio: 'adaptive', generateAudio: true, seed: 7 };

test('start frame + references uses reference-to-video with the start frame first', () => {
  const task = client.buildTask({ settings, prompt: 'p', startFrameUri: 'spicy://f/s', referenceUris: ['spicy://f/a'] });
  assert.strictEqual(task.model, 'alibaba/wan-3.0-prime/reference-to-video');
  assert.deepStrictEqual(task.input.reference_image_urls, ['spicy://f/s', 'spicy://f/a']);
  assert.strictEqual(task.input.duration_seconds, 10);
  assert.strictEqual(task.input.seed, 7);
});

test('start frame only uses image-to-video and lets adaptive inherit the frame shape', () => {
  const task = client.buildTask({ settings, prompt: 'p', startFrameUri: 'spicy://f/s', referenceUris: [] });
  assert.strictEqual(task.model, 'alibaba/wan-3.0-prime/image-to-video');
  assert.strictEqual(task.input.image_url, 'spicy://f/s');
  assert.strictEqual(task.input.aspect_ratio, undefined);
});

test('no images uses text-to-video', () => {
  const task = client.buildTask({ settings, prompt: 'p', startFrameUri: null, referenceUris: [] });
  assert.strictEqual(task.model, 'alibaba/wan-3.0-prime/text-to-video');
});

test('recognises spicy job ids', () => {
  assert.ok(isSpicyId('spicy.job_123'));
  assert.ok(!isSpicyId('9bcf3c60-7db2-4e1a-a1b2-c3d4e5f60718'));
});
