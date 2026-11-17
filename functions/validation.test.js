const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  parseAndValidateGenerateDuration,
  parseAndValidateExtendDuration,
  getCapabilitiesPayload,
} = require("./validation");

test("Unsupported 1s and 2s durations are explicitly rejected (never clamped silently)", () => {
  for (const invalid of ["1s", "2s", "0s", "16s", "1 second", "2 seconds", "", "abc"]) {
    const res = parseAndValidateGenerateDuration(invalid);
    assert.equal(res.valid, false, `Expected '${invalid}' to be rejected`);
    assert.ok(res.message.length > 10);
  }
});

test("All supported durations from 3s to 15s pass exact requested seconds", () => {
  for (let sec = 3; sec <= 15; sec++) {
    const res = parseAndValidateGenerateDuration(`${sec}s`);
    assert.equal(res.valid, true, `Expected ${sec}s to be valid`);
    assert.equal(res.seconds, sec, `Expected exact duration ${sec}, never forced to 10s`);
  }
});

test("Extend duration strictly accepts 3s..10s and enforces 30s cumulative ceiling", () => {
  for (const bad of ["1s", "2s", "11s", "15s"]) {
    assert.equal(parseAndValidateExtendDuration(bad, 10).valid, false);
  }
  for (let sec = 3; sec <= 10; sec++) {
    const res = parseAndValidateExtendDuration(`${sec}s`, 10);
    assert.equal(res.valid, true);
    assert.equal(res.seconds, sec);
  }
  // Exceeding 30s cumulative cap is rejected
  const overCap = parseAndValidateExtendDuration("10s", 25);
  assert.equal(overCap.valid, false);
});

test("Capabilities payload defaults to 10s and identifies 1s & 2s unsupported", () => {
  const caps = getCapabilitiesPayload();
  assert.equal(caps.defaultDurationSeconds, 10);
  assert.deepEqual(caps.unsupportedDurationsSeconds, [1, 2]);
  assert.equal(caps.supportedDurationsSeconds.length, 13);
  assert.equal(caps.extend.supported, true);
});
