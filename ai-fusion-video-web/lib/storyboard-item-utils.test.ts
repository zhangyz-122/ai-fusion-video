import assert from "node:assert/strict";
import test from "node:test";

import { compareStoryboardItemsAsc, parseIds } from "./storyboard-item-utils";

test("parseIds returns empty array for empty input", () => {
  assert.deepEqual(parseIds(null), []);
  assert.deepEqual(parseIds(undefined), []);
  assert.deepEqual(parseIds(""), []);
});

test("parseIds passes through native arrays", () => {
  assert.deepEqual(parseIds([1, 2, 3]), [1, 2, 3]);
});

test("parseIds parses JSON string arrays", () => {
  assert.deepEqual(parseIds("[1,2,3]"), [1, 2, 3]);
});

test("parseIds returns empty array for invalid JSON or non-array JSON", () => {
  assert.deepEqual(parseIds("not-json"), []);
  assert.deepEqual(parseIds('{"a":1}'), []);
});

test("compareStoryboardItemsAsc sorts by sortOrder first", () => {
  const a = { id: 2, sortOrder: 0, shotNumber: "9", autoShotNumber: null };
  const b = { id: 1, sortOrder: 1, shotNumber: "1", autoShotNumber: null };
  assert.equal(compareStoryboardItemsAsc(a, b), -1);
  assert.equal(compareStoryboardItemsAsc(b, a), 1);
});

test("compareStoryboardItemsAsc falls back to shot number then id", () => {
  const a = { id: 2, sortOrder: 1, shotNumber: null, autoShotNumber: "2" };
  const b = { id: 1, sortOrder: 1, shotNumber: null, autoShotNumber: "10" };
  assert.equal(compareStoryboardItemsAsc(a, b), -1);
  assert.equal(compareStoryboardItemsAsc(b, a), 1);

  const c = { id: 3, sortOrder: 1, shotNumber: null, autoShotNumber: "2" };
  assert.equal(compareStoryboardItemsAsc(a, c), -1);
});
