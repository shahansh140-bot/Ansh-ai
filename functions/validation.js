const VIDEO_MODEL = "gemini-omni-1.2-flash";
const MIN_GENERATE_DURATION_SEC = 3;
const MAX_GENERATE_DURATION_SEC = 15;
const DEFAULT_DURATION_SEC = 10;
const MIN_EXTEND_DURATION_SEC = 3;
const MAX_EXTEND_DURATION_SEC = 10;
const MAX_CUMULATIVE_DURATION_SEC = 30;

const ALL_UI_DURATION_SECONDS = Array.from({ length: 15 }, (_, i) => i + 1);
const SUPPORTED_GENERATE_DURATIONS = ALL_UI_DURATION_SECONDS.filter(
  (s) => s >= MIN_GENERATE_DURATION_SEC && s <= MAX_GENERATE_DURATION_SEC
);
const UNSUPPORTED_GENERATE_DURATIONS = ALL_UI_DURATION_SECONDS.filter(
  (s) => s < MIN_GENERATE_DURATION_SEC || s > MAX_GENERATE_DURATION_SEC
);
const SUPPORTED_EXTEND_DURATIONS = ALL_UI_DURATION_SECONDS.filter(
  (s) => s >= MIN_EXTEND_DURATION_SEC && s <= MAX_EXTEND_DURATION_SEC
);

/**
 * Strictly parses requested duration without silently clamping or forcing a fallback.
 * Returns { valid: true, seconds } or { valid: false, message }.
 */
function parseAndValidateGenerateDuration(rawDuration) {
  if (rawDuration === undefined || rawDuration === null || String(rawDuration).trim() === "") {
    return {
      valid: false,
      message: "Duration is required. Choose a supported duration between 3 and 15 seconds (e.g., \"10s\").",
    };
  }
  const normalized = String(rawDuration).trim().toLowerCase();
  const match = normalized.match(/^(\d+)\s*(?:s|sec|secs|second|seconds)?$/);
  if (!match) {
    return {
      valid: false,
      message: `Invalid duration format '${rawDuration}'. Specify an integer number of seconds between 3s and 15s.`,
    };
  }
  const seconds = parseInt(match[1], 10);
  if (!Number.isInteger(seconds) || seconds < MIN_GENERATE_DURATION_SEC || seconds > MAX_GENERATE_DURATION_SEC) {
    return {
      valid: false,
      message: `Duration '${seconds}s' is unsupported by ${VIDEO_MODEL}. Supported clip durations are ${MIN_GENERATE_DURATION_SEC}s–${MAX_GENERATE_DURATION_SEC}s (1s and 2s are not natively supported).`,
    };
  }
  return { valid: true, seconds };
}

/**
 * Strictly validates Extend Video step duration (3s–10s per step) and cumulative ceiling.
 */
function parseAndValidateExtendDuration(rawDuration, currentDurationSec = 0) {
  if (rawDuration === undefined || rawDuration === null || String(rawDuration).trim() === "") {
    return {
      valid: false,
      message: `Extension duration is required (${MIN_EXTEND_DURATION_SEC}s–${MAX_EXTEND_DURATION_SEC}s per extension step).`,
    };
  }
  const normalized = String(rawDuration).trim().toLowerCase();
  const match = normalized.match(/^(\d+)\s*(?:s|sec|secs|second|seconds)?$/);
  if (!match) {
    return {
      valid: false,
      message: `Invalid extension duration '${rawDuration}'. Specify 3s–10s.`,
    };
  }
  const seconds = parseInt(match[1], 10);
  if (!Number.isInteger(seconds) || seconds < MIN_EXTEND_DURATION_SEC || seconds > MAX_EXTEND_DURATION_SEC) {
    return {
      valid: false,
      message: `Extension duration '${seconds}s' is unsupported by ${VIDEO_MODEL}. Each extension segment must be between ${MIN_EXTEND_DURATION_SEC}s and ${MAX_EXTEND_DURATION_SEC}s.`,
    };
  }
  const current = Number.isFinite(Number(currentDurationSec)) ? Math.max(0, Number(currentDurationSec)) : 0;
  if (current + seconds > MAX_CUMULATIVE_DURATION_SEC) {
    return {
      valid: false,
      message: `Extending by ${seconds}s would exceed the ${MAX_CUMULATIVE_DURATION_SEC}s maximum cumulative video length for ${VIDEO_MODEL} (current: ${Math.round(current)}s).`,
    };
  }
  return { valid: true, seconds };
}

function getCapabilitiesPayload() {
  return {
    model: VIDEO_MODEL,
    defaultDurationSeconds: DEFAULT_DURATION_SEC,
    supportedDurationsSeconds: SUPPORTED_GENERATE_DURATIONS,
    unsupportedDurationsSeconds: UNSUPPORTED_GENERATE_DURATIONS,
    unsupportedReason: `${VIDEO_MODEL} requires a minimum native clip duration of 3 seconds (max 15 seconds). 1s and 2s clips are unsupported.`,
    supportsTextToVideo: true,
    supportsImageToVideo: true,
    extend: {
      supported: true,
      supportedStepDurationsSeconds: SUPPORTED_EXTEND_DURATIONS,
      maxCumulativeSeconds: MAX_CUMULATIVE_DURATION_SEC,
      note: "Preserves scene continuity, characters, audio, and visual style via Interactions API previous_interaction_id.",
    },
    aspectRatios: ["16:9", "9:16"],
    resolution: "720p",
  };
}

module.exports = {
  VIDEO_MODEL,
  MIN_GENERATE_DURATION_SEC,
  MAX_GENERATE_DURATION_SEC,
  DEFAULT_DURATION_SEC,
  MIN_EXTEND_DURATION_SEC,
  MAX_EXTEND_DURATION_SEC,
  MAX_CUMULATIVE_DURATION_SEC,
  ALL_UI_DURATION_SECONDS,
  SUPPORTED_GENERATE_DURATIONS,
  UNSUPPORTED_GENERATE_DURATIONS,
  SUPPORTED_EXTEND_DURATIONS,
  parseAndValidateGenerateDuration,
  parseAndValidateExtendDuration,
  getCapabilitiesPayload,
};
