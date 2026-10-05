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
  constructor({ apiKey, baseUrl, modelBase }) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl;
    this.modelBase = modelBase;
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
    return body.data;
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
    return { id: ID_PREFIX + data.taskId, status: STATE_TO_STATUS[data.state] || data.state, videoUrl: null, error: null, costUsd: null, billedSeconds: null };
  }

  // Asset URLs expire after ~20 minutes, so the app asks for a fresh one before playing or saving.
  async getTask(id) {
    const data = await this.call(`/jobs/recordInfo?taskId=${encodeURIComponent(id.slice(ID_PREFIX.length))}`);
    const video = data.output?.assets?.find((a) => a.mime?.startsWith('video/')) ?? data.output?.assets?.[0];
    return {
      id,
      status: STATE_TO_STATUS[data.state] || data.state,
      videoUrl: video?.url ?? null,
      error: data.errorMessage ?? null,
      costUsd: data.cost != null ? Number(data.cost) : null,
      billedSeconds: null,
    };
  }
}

const isSpicyId = (id) => id.startsWith(ID_PREFIX);

module.exports = { SpicyClient, isSpicyId };
