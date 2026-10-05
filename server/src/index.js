const crypto = require('node:crypto');
const express = require('express');
const multer = require('multer');
const {
  findMinorReference,
  checkImagesForMinors,
  SFW_DIRECTIVE,
  NSFW_DIRECTIVE,
} = require('./safety');
const { MAX_REFERENCE_IMAGES, buildVideoRequest, validateSettings, summariseTask } = require('./ofox');
const { SpicyClient, isSpicyId, TERMINAL_STATES, ID_PREFIX } = require('./spicy');

const config = {
  port: Number(process.env.PORT) || 3000,
  ofoxApiKey: process.env.OFOX_API_KEY,
  ofoxBaseUrl: (process.env.OFOX_BASE_URL || 'https://api.ofox.ai').replace(/\/$/, ''),
  videoModel: process.env.OFOX_VIDEO_MODEL || 'alibaba/wan-3.0-prime',
  ageCheckModel: process.env.AGE_CHECK_MODEL || 'openai/gpt-4o-mini',
  appToken: process.env.APP_TOKEN,
  // NSFW requests go to SpicyAPI instead of Ofox.
  spicyApiKey: process.env.SPICY_API_KEY,
  spicyBaseUrl: (process.env.SPICY_BASE_URL || 'https://api.spicyapi.ai').replace(/\/$/, ''),
  spicyModelBase: process.env.SPICY_MODEL_BASE || 'alibaba/wan-3.0-prime',
  // Render sets RENDER_EXTERNAL_URL automatically. Without a public URL the
  // images are sent inline as data URIs instead.
  publicBaseUrl: (process.env.PUBLIC_BASE_URL || process.env.RENDER_EXTERNAL_URL || '').replace(/\/$/, ''),
  imageDelivery: process.env.IMAGE_DELIVERY || 'url',
};

if (!config.ofoxApiKey) {
  console.error('OFOX_API_KEY is not set.');
  process.exit(1);
}
if (!config.appToken) {
  console.error('APP_TOKEN is not set. Refusing to start an open proxy to your Ofox account.');
  process.exit(1);
}

const spicy = config.spicyApiKey
  ? new SpicyClient({ apiKey: config.spicyApiKey, baseUrl: config.spicyBaseUrl, modelBase: config.spicyModelBase })
  : null;
if (!spicy) console.warn('SPICY_API_KEY is not set; NSFW requests will be rejected.');

// Uploaded images are served back to Ofox from short-lived, unguessable URLs.
const IMAGE_TTL_MS = 3 * 60 * 60 * 1000;
const images = new Map();
setInterval(() => {
  const now = Date.now();
  for (const [token, img] of images) if (img.expiresAt < now) images.delete(token);
}, 10 * 60 * 1000).unref();

function publishImage(file) {
  if (config.imageDelivery === 'data_uri' || !config.publicBaseUrl) {
    return `data:${file.mimetype};base64,${file.buffer.toString('base64')}`;
  }
  const token = crypto.randomBytes(24).toString('hex');
  images.set(token, { buffer: file.buffer, mimetype: file.mimetype, expiresAt: Date.now() + IMAGE_TTL_MS });
  return `${config.publicBaseUrl}/files/${token}`;
}

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 20 * 1024 * 1024, files: MAX_REFERENCE_IMAGES + 1 },
  fileFilter: (_req, file, cb) => {
    cb(null, ['image/jpeg', 'image/png', 'image/webp', 'image/bmp'].includes(file.mimetype));
  },
});

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '1mb' }));

app.get('/health', (_req, res) => res.json({ ok: true }));

app.get('/files/:token', (req, res) => {
  const img = images.get(req.params.token);
  if (!img || img.expiresAt < Date.now()) return res.sendStatus(404);
  res.type(img.mimetype).send(img.buffer);
});

function requireAppToken(req, res, next) {
  const header = req.get('authorization') || '';
  const given = Buffer.from(header.replace(/^Bearer\s+/i, ''));
  const expected = Buffer.from(config.appToken);
  if (given.length !== expected.length || !crypto.timingSafeEqual(given, expected)) {
    return res.status(401).json({ error: 'Invalid app token.' });
  }
  next();
}

async function ofox(path, init = {}) {
  const res = await fetch(`${config.ofoxBaseUrl}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${config.ofoxApiKey}`,
      'Content-Type': 'application/json',
      ...init.headers,
    },
  });
  const body = await res.json().catch(() => ({}));
  return { status: res.status, ok: res.ok, body };
}

function ofoxError(body, fallback) {
  return body?.error?.message || body?.error || body?.message || fallback;
}

app.post(
  '/api/videos',
  requireAppToken,
  upload.fields([
    { name: 'start_frame', maxCount: 1 },
    { name: 'references', maxCount: MAX_REFERENCE_IMAGES },
  ]),
  async (req, res) => {
    let payload;
    try {
      payload = JSON.parse(req.body.payload || '{}');
    } catch {
      return res.status(400).json({ error: 'payload must be JSON.' });
    }

    const { settings, errors } = validateSettings(payload);
    let prompt = typeof payload.prompt === 'string' ? payload.prompt.trim() : '';
    if (!prompt) errors.push('prompt is required');

    const startFrame = req.files?.start_frame?.[0] ?? null;
    const references = req.files?.references ?? [];
    if (references.length + (startFrame && references.length ? 1 : 0) > MAX_REFERENCE_IMAGES) {
      errors.push(`at most ${MAX_REFERENCE_IMAGES} images (start frame + references) are allowed`);
    }
    if (errors.length) return res.status(400).json({ error: errors.join('; ') });

    const nsfw = payload.nsfw === true;
    if (nsfw) {
      if (!spicy) return res.status(503).json({ error: 'NSFW generation is not configured on the server (SPICY_API_KEY missing).' });
      if (payload.adultsConfirmed !== true) {
        return res.status(422).json({
          error: 'NSFW mode requires confirming that everyone depicted is an adult (18+) who consented.',
        });
      }
      const hit = findMinorReference(prompt);
      if (hit) {
        return res.status(422).json({
          error: `NSFW requests may not reference minors (matched "${hit}"). Remove it or turn NSFW off.`,
        });
      }
      const allImages = [startFrame, ...references].filter(Boolean);
      const verdict = await checkImagesForMinors({
        images: allImages,
        apiKey: config.ofoxApiKey,
        baseUrl: config.ofoxBaseUrl,
        model: config.ageCheckModel,
      });
      if (!verdict.ok) return res.status(422).json({ error: verdict.reason });
      if (!prompt.includes(NSFW_DIRECTIVE)) prompt += `\n\n${NSFW_DIRECTIVE}`;

      try {
        const task = spicy.buildTask({
          settings,
          prompt,
          startFrameUri: startFrame ? await spicy.upload(startFrame) : null,
          referenceUris: await Promise.all(references.map((f) => spicy.upload(f))),
        });
        const created = await spicy.createTask(task);
        console.log(`Created ${created.id} (${task.model}, ${settings.resolution}, ${settings.duration}s)`);
        return res.status(202).json(created);
      } catch (err) {
        return res.status(err.status || 502).json({ error: `SpicyAPI: ${err.message}` });
      }
    } else if (!prompt.includes(SFW_DIRECTIVE)) {
      prompt += `\n\n${SFW_DIRECTIVE}`;
    }

    const body = buildVideoRequest({
      model: config.videoModel,
      settings,
      prompt,
      startFrameUrl: startFrame ? publishImage(startFrame) : null,
      referenceUrls: references.map(publishImage),
    });

    try {
      const result = await ofox('/v1/videos', { method: 'POST', body: JSON.stringify(body) });
      if (!result.ok) {
        return res.status(result.status).json({ error: ofoxError(result.body, `Ofox returned HTTP ${result.status}`) });
      }
      const created = summariseTask(result.body);
      console.log(`Created ofox ${created.id} (${settings.resolution}, ${settings.duration}s)`);
      res.status(202).json(created);
    } catch (err) {
      res.status(502).json({ error: `Could not reach Ofox: ${err.message}` });
    }
  },
);

/**
 * Erases the given jobs. SpicyAPI jobs are purged (video and prompt destroyed,
 * finished jobs only, no refund). Ofox can't delete finished videos, so Ofox
 * jobs are cancelled if still running and otherwise only reported back for the
 * app to drop from its History.
 */
async function eraseOne(id) {
  if (isSpicyId(id)) {
    if (!spicy) return { id, erased: false, message: 'SPICY_API_KEY missing on the server.' };
    try {
      await spicy.purge(id);
      console.log(`Purged ${id}`);
      return { id, erased: true, message: 'Deleted from SpicyAPI.' };
    } catch (err) {
      return { id, erased: false, message: `SpicyAPI: ${err.message}` };
    }
  }
  try {
    const result = await ofox(`/v1/videos/${encodeURIComponent(id)}`, { method: 'DELETE' });
    if (result.ok) return { id, erased: true, message: 'Cancelled on Ofox.' };
    // 400 cancel_failed = already finished; Ofox keeps finished videos.
    return { id, erased: false, localOnly: true, message: 'Ofox does not allow deleting finished videos.' };
  } catch (err) {
    return { id, erased: false, message: `Ofox: ${err.message}` };
  }
}

app.post('/api/videos/erase', requireAppToken, async (req, res) => {
  const ids = Array.isArray(req.body?.ids) ? req.body.ids.filter((id) => typeof id === 'string').slice(0, 200) : [];
  if (ids.length === 0) return res.status(400).json({ error: 'ids is required.' });
  const results = [];
  for (const id of ids) results.push(await eraseOne(id)); // sequential to stay inside rate limits
  res.json({ results });
});

// Purges every finished SpicyAPI job for this model from the last 92 days,
// including ones the app never recorded. Running jobs are skipped.
app.post('/api/videos/erase-all-spicy', requireAppToken, async (_req, res) => {
  if (!spicy) return res.status(503).json({ error: 'SPICY_API_KEY missing on the server.' });
  let jobs;
  try {
    jobs = await spicy.listAll();
  } catch (err) {
    return res.status(err.status || 502).json({ error: `SpicyAPI: ${err.message}` });
  }
  const purged = [];
  const skipped = [];
  const failed = [];
  for (const job of jobs) {
    const id = ID_PREFIX + job.taskId;
    if (job.contentState === 'purged') continue;
    if (!TERMINAL_STATES.has(job.state)) {
      skipped.push(id);
      continue;
    }
    const result = await eraseOne(id);
    (result.erased ? purged : failed).push(id);
  }
  res.json({ purged, skipped, failed });
});

// The latest jobs from each provider, newest first. Registered before /:id so
// "recent" isn't treated as a job id.
app.get('/api/videos/recent', requireAppToken, async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit) || 5, 1), 20);
  const notes = [];

  const spicyJobs = spicy
    ? spicy.listRecent(limit).catch((err) => {
        notes.push(`SpicyAPI: ${err.message}`);
        return [];
      })
    : Promise.resolve([]);

  // Ofox doesn't document a list endpoint; this tries the OpenAI-style one and
  // reports when it isn't available so the app can fall back to its own job ids.
  const ofoxJobs = ofox(`/v1/videos?limit=${limit}&order=desc`)
    .then((result) => {
      const data = Array.isArray(result.body?.data) ? result.body.data : null;
      if (!result.ok || !data) {
        notes.push('Ofox has no job list; only videos made from this app are refreshed.');
        return { listed: false, jobs: [] };
      }
      return { listed: true, jobs: data.slice(0, limit).map(summariseTask) };
    })
    .catch((err) => {
      notes.push(`Ofox: ${err.message}`);
      return { listed: false, jobs: [] };
    });

  const [spicyList, ofoxList] = await Promise.all([spicyJobs, ofoxJobs]);
  res.json({ spicy: spicyList, ofox: ofoxList.jobs, ofoxListed: ofoxList.listed, notes });
});

app.get('/api/videos/:id', requireAppToken, async (req, res) => {
  if (isSpicyId(req.params.id)) {
    if (!spicy) return res.status(503).json({ error: 'SPICY_API_KEY missing on the server.' });
    try {
      return res.json(await spicy.getTask(req.params.id));
    } catch (err) {
      return res.status(err.status || 502).json({ error: `SpicyAPI: ${err.message}` });
    }
  }
  try {
    const result = await ofox(`/v1/videos/${encodeURIComponent(req.params.id)}`);
    if (!result.ok) {
      return res.status(result.status).json({ error: ofoxError(result.body, `Ofox returned HTTP ${result.status}`) });
    }
    res.json(summariseTask(result.body));
  } catch (err) {
    res.status(502).json({ error: `Could not reach Ofox: ${err.message}` });
  }
});

app.delete('/api/videos/:id', requireAppToken, async (req, res) => {
  if (isSpicyId(req.params.id)) {
    return res.status(501).json({ error: 'SpicyAPI jobs cannot be cancelled.' });
  }
  try {
    const result = await ofox(`/v1/videos/${encodeURIComponent(req.params.id)}`, { method: 'DELETE' });
    res.status(result.ok ? 200 : result.status).json(result.ok ? { cancelled: true } : { error: ofoxError(result.body, 'Cancel failed') });
  } catch (err) {
    res.status(502).json({ error: `Could not reach Ofox: ${err.message}` });
  }
});

// Multer limit / filter errors.
app.use((err, _req, res, _next) => {
  res.status(400).json({ error: err.message || 'Bad request' });
});

app.listen(config.port, () => {
  console.log(`Video generator server listening on :${config.port} (model ${config.videoModel})`);
});
