// Builds Ofox /v1/videos requests and normalises task responses for the app.

const RESOLUTIONS = ['480p', '720p', '1080p'];
const ASPECT_RATIOS = ['16:9', '9:16', '1:1', '4:3', '3:4', 'adaptive'];
const MIN_DURATION = 2;
const MAX_DURATION = 30;
// Ofox caps input_references at 9 images. When a start frame is combined with
// references it rides along as reference #1, so it counts toward this limit.
const MAX_REFERENCE_IMAGES = 9;

function imageEntry(url) {
  return { type: 'image_url', image_url: { url } };
}

/**
 * Ofox treats frame_images (first frame) and input_references (reference-to-video)
 * as mutually exclusive. To honour both, a start frame sent together with
 * references becomes Image 1 of the references and the prompt (built by the app)
 * tells Wan to open on it.
 */
function buildVideoRequest({ model, settings, prompt, startFrameUrl, referenceUrls }) {
  const body = {
    model,
    prompt,
    duration: settings.duration,
    resolution: settings.resolution,
    aspect_ratio: settings.aspectRatio,
    generate_audio: settings.generateAudio,
  };
  if (Number.isInteger(settings.seed)) body.seed = settings.seed;

  if (startFrameUrl && referenceUrls.length === 0) {
    body.frame_images = [{ ...imageEntry(startFrameUrl), frame_type: 'first_frame' }];
  } else if (referenceUrls.length > 0) {
    const refs = startFrameUrl ? [startFrameUrl, ...referenceUrls] : referenceUrls;
    body.input_references = refs.map(imageEntry);
  }
  return body;
}

function validateSettings(raw) {
  const errors = [];
  const settings = {
    resolution: raw.resolution,
    duration: Number(raw.duration),
    aspectRatio: raw.aspectRatio ?? 'adaptive',
    generateAudio: raw.generateAudio !== false,
    seed: raw.seed == null || raw.seed === '' ? null : Number(raw.seed),
  };
  if (!RESOLUTIONS.includes(settings.resolution)) errors.push(`resolution must be one of ${RESOLUTIONS.join(', ')}`);
  if (!Number.isInteger(settings.duration) || settings.duration < MIN_DURATION || settings.duration > MAX_DURATION) {
    errors.push(`duration must be an integer between ${MIN_DURATION} and ${MAX_DURATION}`);
  }
  if (!ASPECT_RATIOS.includes(settings.aspectRatio)) errors.push(`aspectRatio must be one of ${ASPECT_RATIOS.join(', ')}`);
  if (settings.seed !== null && !Number.isInteger(settings.seed)) errors.push('seed must be an integer');
  return { settings, errors };
}

// Collapses the Ofox task object into the small shape the app consumes.
function summariseTask(task) {
  const videoUrl = task.mirror_urls?.[0] ?? task.unsigned_urls?.[0] ?? task.video_url ?? null;
  return {
    id: task.id,
    status: task.status,
    videoUrl,
    error: task.error?.message ?? task.error ?? null,
    costUsd: task.usage?.video_cost ?? null,
    billedSeconds: task.usage?.video_seconds ?? null,
  };
}

module.exports = {
  RESOLUTIONS,
  ASPECT_RATIOS,
  MAX_REFERENCE_IMAGES,
  buildVideoRequest,
  validateSettings,
  summariseTask,
};
