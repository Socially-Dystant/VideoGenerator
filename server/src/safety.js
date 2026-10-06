// Content-safety rules enforced server-side. The Android app runs the same text
// check for instant feedback, but the server is the source of truth because the
// client can't be trusted.

// Any reference to minors blocks an NSFW request outright. Deliberately broad:
// a false positive costs a reword, a false negative is unacceptable.
const MINOR_TERMS = [
  'child', 'children', 'childlike', 'kid', 'kids', 'kiddie', 'minor', 'minors',
  'underage', 'under-age', 'under age', 'preteen', 'pre-teen', 'preteens', 'tween', 'tweens',
  'teen', 'teens', 'teenage', 'teenager', 'teenagers', 'adolescent', 'adolescents',
  'juvenile', 'juveniles', 'pubescent', 'prepubescent', 'pre-pubescent', 'infant', 'infants',
  'toddler', 'toddlers', 'babies', 'newborn', 'schoolgirl', 'schoolgirls', 'schoolboy',
  'schoolboys', 'school girl', 'school boy', 'loli', 'lolis', 'lolita', 'shota', 'shotacon',
  'lolicon', 'jailbait', 'young girl', 'young girls', 'young boy', 'young boys',
  'little girl', 'little girls', 'little boy', 'little boys', 'daughter', 'stepdaughter',
  'son', 'stepson', 'niece', 'nephew', 'grade school', 'middle school', 'elementary school',
  'kindergarten', 'high school', 'highschool', 'high schooler', 'junior high', 'barely legal',
  'youthful body', 'flat chested child', 'cub',
];

const MINOR_TERM_RE = new RegExp(
  '\\b(' + MINOR_TERMS.map((t) => t.replace(/[-\s]/g, '[-\\s]?')).join('|') + ')\\b',
  'i',
);

// "15 years old", "aged 12", "age: 16", "16yo", "17 y/o"
const UNDERAGE_NUMBER_RE =
  /\b(?:(?:[1-9]|1[0-7])\s*(?:-|\s)?(?:years?|yrs?|yo|y\/o|y\.o\.)(?:\s*-?\s*old)?|(?:aged?|age:)\s*(?:[1-9]|1[0-7]))\b/i;

function findMinorReference(text) {
  if (!text) return null;
  const m = text.match(MINOR_TERM_RE) || text.match(UNDERAGE_NUMBER_RE);
  return m ? m[0] : null;
}

const SFW_DIRECTIVE =
  'Keep it safe for work: no nudity, sexual content or gore.';

const NSFW_DIRECTIVE =
  'Everyone shown is a consenting adult (18+) with a clearly adult appearance.';

// Asks a vision model whether any image shows someone who may be under 18.
// Fails closed: errors, unparseable answers or uncertainty all block the request.
async function checkImagesForMinors({ images, apiKey, baseUrl, model }) {
  if (images.length === 0) return { ok: true };

  const content = [
    {
      type: 'text',
      text:
        'You are a strict child-safety classifier. For the attached images, decide whether ANY ' +
        'person shown is, or could plausibly be, under 18 years old (including drawings, anime, ' +
        'dolls or stylised characters). Reply with JSON only, no prose: ' +
        '{"minor_present": true|false, "uncertain": true|false, "reason": "<short>"}',
    },
    ...images.map((img) => ({
      type: 'image_url',
      image_url: { url: `data:${img.mimetype};base64,${img.buffer.toString('base64')}` },
    })),
  ];

  let res;
  try {
    res = await fetch(`${baseUrl}/v1/chat/completions`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model,
        temperature: 0,
        max_tokens: 200,
        messages: [{ role: 'user', content }],
      }),
    });
  } catch (err) {
    return { ok: false, reason: `Age check unavailable (${err.message}).` };
  }
  if (!res.ok) {
    return { ok: false, reason: `Age check failed (HTTP ${res.status}).` };
  }

  const body = await res.json().catch(() => null);
  const text = body?.choices?.[0]?.message?.content ?? '';
  const json = text.match(/\{[\s\S]*\}/);
  let verdict;
  try {
    verdict = JSON.parse(json ? json[0] : '');
  } catch {
    return { ok: false, reason: 'Age check returned an unreadable answer.' };
  }

  if (verdict.minor_present === false && verdict.uncertain === false) return { ok: true };
  return {
    ok: false,
    reason: 'An uploaded image may show a person under 18. NSFW generation is not allowed with this image.',
  };
}

module.exports = {
  findMinorReference,
  checkImagesForMinors,
  SFW_DIRECTIVE,
  NSFW_DIRECTIVE,
};
