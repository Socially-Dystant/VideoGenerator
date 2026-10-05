# Video Generator

Android app (Jetpack Compose) + a small Node.js server on Render that turns a scene description, shot list, reference images and an optional start frame into a **Wan 3.0 Prime** video through the [Ofox video API](https://ofox.ai/docs/api/videos).

```
Android app  ──(HTTPS, APP_TOKEN)──▶  Render server ──(OFOX_API_KEY)──▶  api.ofox.ai/v1/videos          (NSFW off)
                                                    └─(SPICY_API_KEY)─▶  api.spicyapi.ai/api/v1/jobs     (NSFW on)
```

API keys only live on the server, so they can't be pulled out of the APK.

## Features

- Start frame + up to 9 reference images (8 when a start frame is used), each with an `@tag` and an optional note.
- Scene description and an ordered shot list (framing, optional length, description). Shots without a length split the leftover time evenly.
- Characters: after you add images the app offers to create one. Pick which uploads show the person, name them, and describe their clothing piece by piece. The prompt then adds an identity lock (face, hair, body, skin, distinguishing features) and a wardrobe-continuity block, and `@Name` in the scene or shots becomes `Name (Image 1, Image 2)`.
- Live prompt preview showing exactly what is sent, with `@tags` resolved to Wan's image labels (`Image 1`, `Image 2`, …; switchable to `@Image1` in Settings).
- Permanent instructions (Instructions tab) added to the top of every prompt, each with an on/off switch.
- Resolution 480p / 720p / 1080p, duration 5–30 s, aspect ratio, audio on/off, optional seed, cost estimate.
- History tab: polls job status, plays, saves to Movies, or opens the finished video; cancel pending jobs.
- NSFW toggle (see below).

### How the start frame and reference images are combined

Ofox treats `frame_images` (first frame) and `input_references` (reference-to-video) as mutually exclusive. The app therefore:

| You provide | Sent to Ofox as |
| --- | --- |
| Start frame only | `frame_images` with `frame_type: first_frame` |
| References only | `input_references`; `@tags` become `Image 1…N` |
| Both | `input_references` with the start frame as **Image 1**, and the prompt tells Wan to open on Image 1 exactly |

### NSFW and minors

- NSFW is off by default. When off, a "keep it safe for work" rule is added to every prompt.
- When on, the user must confirm everyone depicted is an adult and that real people shown have consented.
- Any text that references minors (e.g. "teen", "schoolgirl", "16 years old") blocks an NSFW request, both in the app and again on the server.
- Every uploaded image in an NSFW request is checked by a vision model (`AGE_CHECK_MODEL`) through Ofox. If it sees a possible minor, is unsure, or the check fails, the request is blocked.
- NSFW requests go to [SpicyAPI](https://docs.spicyapi.ai/docs) using Wan 3.0 Prime (`alibaba/wan-3.0-prime/reference-to-video`, or `/image-to-video` / `/text-to-video` when there are no reference images). Images are uploaded through SpicyAPI's upload flow, and job ids are prefixed `spicy.` so status checks route back to it.
- SpicyAPI video links expire after about 20 minutes. The History tab fetches a fresh link before playing, saving or opening, but save anything you want to keep. SpicyAPI jobs can't be cancelled.

## Deploying the server to Render

1. Push this repo to GitHub.
2. In Render: **New → Blueprint**, pick the repo. `render.yaml` creates the `videogenerator-server` web service, built from the root `Dockerfile` (which packages only `server/`). A service created by hand works too: use the Docker runtime, default Dockerfile path and health check path `/health`.
3. When prompted, set `OFOX_API_KEY`. Render generates `APP_TOKEN` for you; copy it from the service's **Environment** page.
4. Once it's live, open `https://<your-service>.onrender.com/health`. It should return `{"ok":true}`.

| Variable | Default | Purpose |
| --- | --- | --- |
| `OFOX_API_KEY` | — (required) | Your Ofox key |
| `APP_TOKEN` | — (required) | Shared secret the app sends as a Bearer token |
| `SPICY_API_KEY` | — (needed for NSFW) | SpicyAPI key; NSFW requests are rejected without it |
| `SPICY_MODEL_BASE` | `alibaba/wan-3.0-prime` | SpicyAPI model prefix; `/reference-to-video` etc. is appended |
| `OFOX_VIDEO_MODEL` | `alibaba/wan-3.0-prime` | Video model |
| `AGE_CHECK_MODEL` | `openai/gpt-4o-mini` | Vision model used for the NSFW age check |
| `IMAGE_DELIVERY` | `url` | `url` serves uploads from short-lived links on this server; `data_uri` sends them inline |
| `PUBLIC_BASE_URL` | `RENDER_EXTERNAL_URL` | Public base URL for image links |

The blueprint uses the **Starter** plan. Free instances sleep when idle and lose the in-memory image store, which can break a job if Ofox fetches the images late.

Run it locally:

```bash
cd server && npm install && OFOX_API_KEY=sk-... APP_TOKEN=dev npm start
```

## Android app

Open the project in Android Studio and run the `app` configuration (minSdk 29). Then in the app's **Settings** tab, enter your Render URL and the `APP_TOKEN`.

Tests: `./gradlew testDebugUnitTest` (prompt builder and safety) and `cd server && npm test`.
