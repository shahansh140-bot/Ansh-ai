const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");
const {
  VIDEO_MODEL,
  parseAndValidateGenerateDuration,
  parseAndValidateExtendDuration,
  getCapabilitiesPayload,
} = require("./validation");

admin.initializeApp();

const FIRESTORE_DB_ID = "ai-studio-android-anshai-357c4e13-6dda-4e60-9f11-ae04d778fe72";
const db = getFirestore(admin.app(), FIRESTORE_DB_ID);

// Stored strictly on the server in Cloud Secret Manager (never bundled in the Android APK).
const geminiApiKey = defineSecret("GEMINI_API_KEY");

const GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta";

function sendError(res, httpStatus, code, message) {
  return res.status(httpStatus).json({
    error: {
      code,
      message,
    },
  });
}

function classifyGoogleApiError(httpStatus, rawBody) {
  const lower = (rawBody || "").toLowerCase();
  if (
    httpStatus === 402 ||
    lower.includes("billing") ||
    lower.includes("blaze") ||
    lower.includes("payment") ||
    lower.includes("paid") ||
    lower.includes("tier")
  ) {
    return {
      status: 402,
      code: "BILLING_REQUIRED",
      message:
        "Google Cloud / Gemini API billing is required for video generation. Enable paid billing on the project and retry.",
    };
  }
  if (httpStatus === 429 || lower.includes("quota") || lower.includes("resource_exhausted") || lower.includes("rate")) {
    return {
      status: 429,
      code: "QUOTA_EXCEEDED",
      message: "Video generation quota or rate limit exceeded. Please wait and try again later.",
    };
  }
  if (httpStatus === 404 || httpStatus === 503 || lower.includes("not found") || lower.includes("unavailable")) {
    return {
      status: 503,
      code: "SERVICE_UNAVAILABLE",
      message: `Video generation service (${VIDEO_MODEL}) is currently unavailable or not enabled on this API project.`,
    };
  }
  if (httpStatus === 401 || httpStatus === 403 || lower.includes("api key") || lower.includes("permission")) {
    return {
      status: 403,
      code: "AUTH_OR_PERMISSION_ERROR",
      message: "Server API key lacks permission for video generation or billing is not active.",
    };
  }
  return {
    status: httpStatus >= 400 && httpStatus < 600 ? httpStatus : 502,
    code: "GENERATION_FAILED",
    message: `Upstream video API error (${httpStatus}): ${rawBody.slice(0, 300)}`,
  };
}

function extractVideoBlock(interaction) {
  const stepContainers = [];
  if (Array.isArray(interaction?.steps)) {
    stepContainers.push(...interaction.steps);
  }
  if (Array.isArray(interaction?.outputs)) {
    stepContainers.push({ content: interaction.outputs });
  }

  for (let i = stepContainers.length - 1; i >= 0; i--) {
    const step = stepContainers[i];
    const blocks = Array.isArray(step?.content)
      ? step.content
      : Array.isArray(step?.content?.parts)
      ? step.content.parts
      : [];
    for (const block of blocks) {
      if (
        block &&
        (block.type === "video" ||
          (typeof block.mime_type === "string" && block.mime_type.startsWith("video/")) ||
          (typeof block.mimeType === "string" && block.mimeType.startsWith("video/")))
      ) {
        return {
          uri: block.uri || null,
          data: block.data || null,
          mimeType: block.mime_type || block.mimeType || "video/mp4",
        };
      }
    }
  }
  return null;
}

function extractFileNameFromUri(uri) {
  if (!uri) return null;
  const match = uri.match(/(files\/[^/:?]+)/);
  return match ? match[1] : null;
}

async function authenticateRequest(req, res) {
  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    sendError(res, 401, "AUTH_REQUIRED", "Missing Firebase Auth Bearer token.");
    return null;
  }
  const idToken = authHeader.slice("Bearer ".length).trim();
  let decodedUser;
  try {
    decodedUser = await admin.auth().verifyIdToken(idToken);
  } catch (err) {
    sendError(res, 401, "AUTH_REQUIRED", "Invalid or expired Firebase Auth token.");
    return null;
  }

  const appCheckToken = req.header("X-Firebase-AppCheck");
  if (appCheckToken) {
    try {
      await admin.appCheck().verifyToken(appCheckToken);
    } catch (err) {
      sendError(res, 403, "APP_CHECK_FAILED", "Firebase App Check verification failed.");
      return null;
    }
  }

  // Enforce global kill switch & user suspension status
  const [userSnap, configSnap] = await Promise.all([
    db.collection("users").doc(decodedUser.uid).get(),
    db.collection("app_config").doc("global").get(),
  ]);

  if (userSnap.exists && userSnap.data()?.status === "DISABLED") {
    sendError(res, 403, "ACCOUNT_DISABLED", "Your ANSH AI account is suspended.");
    return null;
  }
  if (configSnap.exists && configSnap.data()?.videoGenEnabled === false) {
    sendError(res, 403, "DISABLED_BY_ADMIN", "Video generation is currently disabled by Super Admin.");
    return null;
  }

  return decodedUser;
}

exports.anshVideoApi = onRequest(
  {
    region: "us-central1",
    cors: true,
    timeoutSeconds: 300,
    memory: "512MiB",
    secrets: [geminiApiKey],
  },
  async (req, res) => {
    const path = (req.path || "/").replace(/\/+$/, "") || "/";

    // Informational endpoint for model capabilities (does not expose secrets)
    if (req.method === "GET" && path === "/capabilities") {
      return res.status(200).json(getCapabilitiesPayload());
    }

    const apiKey = (geminiApiKey.value() || "").trim();
    if (!apiKey) {
      return sendError(
        res,
        503,
        "MISSING_API_CONFIG",
        "Server-side GEMINI_API_KEY secret is not configured. Set it in Firebase Secret Manager before generating videos."
      );
    }

    const user = await authenticateRequest(req, res);
    if (!user) return;

    try {
      // 1. POST /generate — Start asynchronous text-to-video or image-to-video generation
      if (req.method === "POST" && (path === "/generate" || path === "/")) {
        const body = typeof req.body === "object" && req.body ? req.body : {};
        const prompt = String(body.prompt || "").trim();
        const mode = String(body.mode || "TEXT_TO_VIDEO");
        const style = String(body.style || "Cinematic").trim();
        const aspectRatio = body.aspectRatio === "9:16" ? "9:16" : "16:9";
        const imageBase64 = typeof body.imageBase64 === "string" ? body.imageBase64.trim() : "";
        const imageMimeType = typeof body.imageMimeType === "string" ? body.imageMimeType.trim() : "image/jpeg";

        if (!prompt) {
          return sendError(res, 400, "VALIDATION_ERROR", "Video prompt cannot be empty.");
        }
        if (mode === "IMAGE_TO_VIDEO" && !imageBase64) {
          return sendError(res, 400, "VALIDATION_ERROR", "A source image is required for Image-to-Video.");
        }

        const durationCheck = parseAndValidateGenerateDuration(body.duration);
        if (!durationCheck.valid) {
          return sendError(res, 400, "VALIDATION_ERROR", durationCheck.message);
        }
        const durationSec = durationCheck.seconds;

        const styledPrompt = style ? `${style} visual style. ${prompt}` : prompt;
        const inputItems = [{ type: "text", text: styledPrompt }];
        if (mode === "IMAGE_TO_VIDEO" && imageBase64) {
          inputItems.push({
            type: "image",
            data: imageBase64,
            mime_type: imageMimeType,
          });
        }

        const upstreamResp = await fetch(`${GEMINI_API_BASE}/interactions`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": apiKey,
          },
          body: JSON.stringify({
            model: VIDEO_MODEL,
            input: inputItems,
            background: true,
            store: true,
            response_format: {
              type: "video",
              delivery: "uri",
              aspect_ratio: aspectRatio,
              duration: `${durationSec}s`,
              resolution: "720p",
            },
          }),
        });

        const rawText = await upstreamResp.text();
        if (!upstreamResp.ok) {
          const classified = classifyGoogleApiError(upstreamResp.status, rawText);
          return sendError(res, classified.status, classified.code, classified.message);
        }

        const parsed = JSON.parse(rawText);
        const videoBlock = extractVideoBlock(parsed);
        return res.status(200).json({
          interactionId: parsed.id,
          status: parsed.status || "in_progress",
          model: VIDEO_MODEL,
          requestedDurationSeconds: durationSec,
          downloadReady: parsed.status === "completed" && Boolean(videoBlock),
        });
      }

      // 2. POST /extend — Extend an existing video interaction preserving scene, characters, and style
      if (req.method === "POST" && path === "/extend") {
        const body = typeof req.body === "object" && req.body ? req.body : {};
        const previousInteractionId = String(body.previousInteractionId || "").trim();
        const prompt = String(body.prompt || "").trim();
        const style = String(body.style || "Cinematic").trim();
        const aspectRatio = body.aspectRatio === "9:16" ? "9:16" : "16:9";
        const currentDurationSec = Number(body.currentDurationSeconds || 0);

        if (!previousInteractionId || !/^[a-zA-Z0-9_\-]+$/.test(previousInteractionId)) {
          return sendError(
            res,
            400,
            "VALIDATION_ERROR",
            "A valid previousInteractionId from a completed video is required to extend it."
          );
        }
        if (!prompt) {
          return sendError(res, 400, "VALIDATION_ERROR", "Please describe how the video should continue.");
        }

        const extDurationCheck = parseAndValidateExtendDuration(body.duration, currentDurationSec);
        if (!extDurationCheck.valid) {
          return sendError(res, 400, "VALIDATION_ERROR", extDurationCheck.message);
        }
        const extSec = extDurationCheck.seconds;

        const continuityInstruction =
          `Seamlessly extend the existing video by ${extSec} seconds while preserving exact scene continuity, characters, lighting, camera motion, and ${style} visual style. Continuation: ${prompt}`;

        const upstreamResp = await fetch(`${GEMINI_API_BASE}/interactions`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": apiKey,
          },
          body: JSON.stringify({
            model: VIDEO_MODEL,
            previous_interaction_id: previousInteractionId,
            input: [{ type: "text", text: continuityInstruction }],
            background: true,
            store: true,
            response_format: {
              type: "video",
              delivery: "uri",
              aspect_ratio: aspectRatio,
              duration: `${extSec}s`,
              resolution: "720p",
            },
          }),
        });

        const rawText = await upstreamResp.text();
        if (!upstreamResp.ok) {
          const classified = classifyGoogleApiError(upstreamResp.status, rawText);
          return sendError(res, classified.status, classified.code, classified.message);
        }

        const parsed = JSON.parse(rawText);
        const videoBlock = extractVideoBlock(parsed);
        return res.status(200).json({
          interactionId: parsed.id,
          parentInteractionId: previousInteractionId,
          status: parsed.status || "in_progress",
          model: VIDEO_MODEL,
          requestedExtensionSeconds: extSec,
          downloadReady: parsed.status === "completed" && Boolean(videoBlock),
        });
      }

      // 3. GET /status?id=<interactionId> — Poll asynchronous interaction & file compilation status
      if (req.method === "GET" && path === "/status") {
        const interactionId = String(req.query.id || "").trim();
        if (!interactionId || !/^[a-zA-Z0-9_\-]+$/.test(interactionId)) {
          return sendError(res, 400, "VALIDATION_ERROR", "Valid interaction id is required.");
        }

        const upstreamResp = await fetch(`${GEMINI_API_BASE}/interactions/${encodeURIComponent(interactionId)}`, {
          method: "GET",
          headers: { "x-goog-api-key": apiKey },
        });

        const rawText = await upstreamResp.text();
        if (!upstreamResp.ok) {
          const classified = classifyGoogleApiError(upstreamResp.status, rawText);
          return sendError(res, classified.status, classified.code, classified.message);
        }

        const parsed = JSON.parse(rawText);
        const rawStatus = String(parsed.status || "in_progress");

        if (rawStatus === "failed" || rawStatus === "cancelled" || rawStatus === "incomplete") {
          const reason = parsed.error?.message || `Video generation ended with status: ${rawStatus}.`;
          return res.status(200).json({
            interactionId,
            status: rawStatus,
            downloadReady: false,
            errorMessage: reason,
          });
        }

        if (rawStatus !== "completed") {
          return res.status(200).json({
            interactionId,
            status: rawStatus,
            downloadReady: false,
          });
        }

        const videoBlock = extractVideoBlock(parsed);
        if (!videoBlock) {
          return res.status(200).json({
            interactionId,
            status: "failed",
            downloadReady: false,
            errorMessage: "Model completed without returning a video output.",
          });
        }

        if (videoBlock.data) {
          return res.status(200).json({
            interactionId,
            status: "completed",
            downloadReady: true,
            mimeType: videoBlock.mimeType,
          });
        }

        const fileName = extractFileNameFromUri(videoBlock.uri);
        if (fileName) {
          const fileResp = await fetch(`${GEMINI_API_BASE}/${fileName}`, {
            method: "GET",
            headers: { "x-goog-api-key": apiKey },
          });
          if (fileResp.ok) {
            const fileMeta = await fileResp.json();
            if (fileMeta.state === "FAILED") {
              return res.status(200).json({
                interactionId,
                status: "failed",
                downloadReady: false,
                errorMessage: "Video file processing failed on server.",
              });
            }
            if (fileMeta.state && fileMeta.state !== "ACTIVE") {
              return res.status(200).json({
                interactionId,
                status: "in_progress",
                downloadReady: false,
              });
            }
          }
        }

        return res.status(200).json({
          interactionId,
          status: "completed",
          downloadReady: true,
          mimeType: videoBlock.mimeType,
        });
      }

      // 4. GET /download?id=<interactionId> — Proxy MP4 bytes securely without exposing GEMINI_API_KEY
      if (req.method === "GET" && path === "/download") {
        const interactionId = String(req.query.id || "").trim();
        if (!interactionId || !/^[a-zA-Z0-9_\-]+$/.test(interactionId)) {
          return sendError(res, 400, "VALIDATION_ERROR", "Valid interaction id is required.");
        }

        const upstreamResp = await fetch(`${GEMINI_API_BASE}/interactions/${encodeURIComponent(interactionId)}`, {
          method: "GET",
          headers: { "x-goog-api-key": apiKey },
        });

        const rawText = await upstreamResp.text();
        if (!upstreamResp.ok) {
          const classified = classifyGoogleApiError(upstreamResp.status, rawText);
          return sendError(res, classified.status, classified.code, classified.message);
        }

        const parsed = JSON.parse(rawText);
        const videoBlock = extractVideoBlock(parsed);
        if (!videoBlock) {
          return sendError(res, 404, "GENERATION_FAILED", "No generated video available for this interaction.");
        }

        if (videoBlock.data) {
          const buf = Buffer.from(videoBlock.data, "base64");
          res.setHeader("Content-Type", videoBlock.mimeType || "video/mp4");
          res.setHeader("Content-Length", String(buf.length));
          return res.status(200).send(buf);
        }

        if (!videoBlock.uri) {
          return sendError(res, 404, "GENERATION_FAILED", "Video output URI was missing.");
        }

        const downloadUrl = videoBlock.uri.includes("alt=media")
          ? videoBlock.uri
          : `${videoBlock.uri}${videoBlock.uri.includes("?") ? "&" : "?"}alt=media`;

        const mediaResp = await fetch(downloadUrl, {
          method: "GET",
          headers: { "x-goog-api-key": apiKey },
        });
        if (!mediaResp.ok) {
          const errText = await mediaResp.text();
          const classified = classifyGoogleApiError(mediaResp.status, errText);
          return sendError(res, classified.status, classified.code, classified.message);
        }

        const arrayBuffer = await mediaResp.arrayBuffer();
        const mp4Buffer = Buffer.from(arrayBuffer);
        res.setHeader("Content-Type", "video/mp4");
        res.setHeader("Content-Length", String(mp4Buffer.length));
        return res.status(200).send(mp4Buffer);
      }

      return sendError(res, 404, "NOT_FOUND", "Unknown video proxy route.");
    } catch (err) {
      return sendError(res, 502, "SERVICE_UNAVAILABLE", err?.message || "Unexpected video proxy failure.");
    }
  }
);
