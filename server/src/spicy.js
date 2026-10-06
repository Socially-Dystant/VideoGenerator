// SpicyAPI client, used for NSFW requests. Docs: https://docs.spicyapi.ai/docs
const crypto = require('node:crypto');

// Job ids returned to the app carry this prefix so status/cancel calls can be
// routed back to SpicyAPI instead of Ofox.
const ID_PREFIX = 'spicy.';

const STATE_TO_STATUS = {
  queued: 'pending',
  running: 'in_progress',
  processing: 'in_progress',
  succeeded: 'completed',
  failed: 'failed',
  expired: 'expired',
};

class SpicyClient {
  constructor({ apiKey, baseUrl, modelBase, imageModelBase = 'alibaba/wan-2.7-pro' }) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.modelBase = modelBase;
    this.imageModelBase = imageModelBase;
  }

  async call(path, init = {}) {
    const res = await fetch(`${this.baseUrl}/api/v1${path}`, {
      ...init,
      headers: {
        Authorization: `Bearer ${this.apiKey}`,
        'Content-Type': 'application/json',
        ...init.headers,
      },
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok || (body.code && body.code !== 200)) {
      const msg = body?.data?.errorMessage || body?.msg || body?.error?.message || `SpicyAPI returned HTTP ${res.status}`;
      const err = new Error(msg);
      err.status = res.ok ? 502 : res.status;
      throw err;
    }
    return body.data ?? body;
  }

  // Ticket → presigned PUT → commit; returns the spicy:// URI for task input.
  async upload(file) {
    const ticket = await this.call('/common/upload-url', {
      method: 'POST',
      body: JSON.stringify({ contentType: file.mimetype, bytes: file.buffer.length }),
    });
    const put = await fetch(ticket.uploadUrl, {
      method: ticket.method || 'PUT',
      headers: ticket.headers,
      body: file.buffer,
    });
    if (!put.ok) {
      const err = new Error(`SpicyAPI image upload failed (HTTP ${put.status})`);
      err.status = 502;
      throw err;
    }
    const committed = await this.call(`/files/${encodeURIComponent(ticket.fileId)}/commit`, { method: 'POST', body: '{}' });
    return committed.uri;
  }

  /**
   * Same mode rules as Ofox: start frame only → image-to-video; any references →
   * reference-to-video with the start frame (if present) as Image 1; otherwise text-to-video.
   */
  buildTask({ settings, prompt, startFrameUri, referenceUris }) {
    const input = {
      prompt,
      duration_seconds: settings.duration,
      resolution: settings.resolution,
      generate_audio: settings.generateAudio,
    };
    if (Number.isInteger(settings.seed)) input.seed = settings.seed;

    let mode;
    if (referenceUris.length > 0) {
      mode = 'reference-to-video';
      input.reference_image_urls = startFrameUri ? [startFrameUri, ...referenceUris] : referenceUris;
      input.aspect_ratio = settings.aspectRatio;
    } else if (startFrameUri) {
      mode = 'image-to-video';
      input.image_url = startFrameUri;
      // Left unset, image-to-video inherits the start frame's shape.
      if (settings.aspectRatio !== 'adaptive') input.aspect_ratio = settings.aspectRatio;
    } else {
      mode = 'text-to-video';
      input.aspect_ratio = settings.aspectRatio;
    }
    return { model: `${this.modelBase}/${mode}`, input };
  }

  async createTask(task) {
    const data = await this.call('/jobs/createTask', {
      method: 'POST',
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: JSON.stringify(task),
    });
    return {
      id: ID_PREFIX + data.taskId,
      provider: 'spicy',
      model: task.model,
      status: STATE_TO_STATUS[data.state] || data.state,
      videoUrl: null,
      error: null,
      costUsd: null,
      billedSeconds: null,
    };
  }

  // Asset URLs expire after ~20 minutes, so the app asks for a fresh one before playing or saving.
  async getTask(id) {
    const data = await this.call(`/jobs/recordInfo?taskId=${encodeURIComponent(id.slice(ID_PREFIX.length))}`);
    const assets = data.output?.assets ?? [];
    const video = assets.find((a) => a.mime?.startsWith('video/')) ?? assets.find((a) => a.mime?.startsWith('image/')) ?? assets[0];
    const videoUrl = video?.url || data.output?.video_url || data.output?.url || null;
    const status = STATE_TO_STATUS[data.state] || data.state;

    // SpicyAPI omits the link while the video is still being copied into its
    // storage, and after it expires or is deleted. Say which, instead of nothing.
    let videoNote = null;
    if (status === 'completed' && !videoUrl) {
      if (data.contentState === 'purged') videoNote = 'This video was deleted from SpicyAPI.';
      else if (data.contentState === 'expired' || video?.unavailable) {
        videoNote = 'This video has expired on SpicyAPI (videos are kept for up to ~14 days).';
      } else if (video?.pending || assets.length === 0) {
        videoNote = 'SpicyAPI is still preparing the video file. Try again in a minute.';
      } else videoNote = 'SpicyAPI returned no link for this video.';
      console.log(
        `No video link for ${id}: contentState=${data.contentState} assets=${JSON.stringify(
          assets.map(({ mime, pending, unavailable, url }) => ({ mime, pending, unavailable, hasUrl: Boolean(url) })),
        )} outputKeys=${Object.keys(data.output ?? {}).join(',')}`,
      );
    }
    return {
      id,
      provider: 'spicy',
      model: data.model ?? null,
      kind: kindOf(data.model),
      status,
      videoUrl,
      videoNote,
      error: data.errorMessage ?? null,
      costUsd: data.cost != null ? Number(data.cost) : null,
      billedSeconds: null,
      createdAt: data.createdAt ?? null,
      completedAt: data.completedAt ?? null,
      purged: data.contentState === 'purged',
    };
  }

  /**
   * Most recent jobs for this model family. The list endpoint has no prompt or
   * output, so each job is re-read with recordInfo to get its video link.
   */
  async listRecent(limit) {
    const page = await this.call('/jobs?limit=100');
    const items = (page.items ?? page ?? [])
      .filter((item) => this.isOurModel(item.model))
      .filter((item) => item.contentState !== 'purged')
      .sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
      .slice(0, limit);
    const tasks = await Promise.all(
      items.map((item) =>
        this.getTask(ID_PREFIX + item.taskId).catch(() => ({
          id: ID_PREFIX + item.taskId,
          provider: 'spicy',
          model: item.model ?? null,
          status: STATE_TO_STATUS[item.state] || item.state,
          videoUrl: null,
          error: null,
          costUsd: item.cost != null ? Number(item.cost) : null,
          billedSeconds: null,
          createdAt: item.createdAt ?? null,
          completedAt: null,
        })),
      ),
    );
    return tasks.filter((t) => !t.purged);
  }
}

SpicyClient.prototype.purge = async function purge(id) {
  return this.call('/jobs/purge', {
    method: 'POST',
    body: JSON.stringify({ taskId: id.startsWith(ID_PREFIX) ? id.slice(ID_PREFIX.length) : id }),
  });
};

/**
 * Every job for this model family in the list endpoint's widest window
 * (92 days), following pagination. Used by "erase all".
 */
SpicyClient.prototype.listAll = async function listAll() {
  const day = (offset) => new Date(Date.now() + offset * 86400000).toISOString().slice(0, 10);
  const jobs = [];
  let cursor = null;
  for (let pageNo = 0; pageNo < 50; pageNo++) {
    const query = new URLSearchParams({ limit: '100', from: day(-91), to: day(1) });
    if (cursor) query.set('cursor', cursor);
    const page = await this.call(`/jobs?${query}`);
    const items = page.items ?? [];
    jobs.push(...items.filter((item) => this.isOurModel(item.model)));
    cursor = page.nextCursor ?? page.cursor ?? null;
    if (!cursor || items.length === 0) break;
  }
  return jobs;
};

const TERMINAL_STATES = new Set(['succeeded', 'failed', 'canceled', 'cancelled', 'expired']);

/** Video jobs vs Wan image jobs, from the model id. */
function kindOf(model) {
  return model && /\/(text-to-image|edit)$/.test(model) ? 'image' : 'video';
}

/** Jobs this app makes: Wan video (modelBase) and Wan image (imageModelBase). */
SpicyClient.prototype.isOurModel = function isOurModel(model) {
  return !model || model.startsWith(this.modelBase) || model.startsWith(this.imageModelBase);
};

/**
 * Wan 2.7 image task: reference images → /edit (up to 9 images, 2K canvas),
 * prompt only → /text-to-image (2K or 4K).
 */
SpicyClient.prototype.buildImageTask = function buildImageTask({ prompt, referenceUris, resolution, aspectRatio, seed }) {
  const input = { prompt };
  if (aspectRatio) input.aspect_ratio = aspectRatio;
  if (Number.isInteger(seed)) input.seed = seed;
  if (referenceUris.length > 0) {
    input.image_urls = referenceUris;
    return { model: `${this.imageModelBase}/edit`, input };
  }
  input.resolution = resolution === '4k' ? '4k' : '2k';
  return { model: `${this.imageModelBase}/text-to-image`, input };
};

const isSpicyId = (id) => id.startsWith(ID_PREFIX);

module.exports = { SpicyClient, isSpicyId, TERMINAL_STATES, ID_PREFIX, kindOf };
