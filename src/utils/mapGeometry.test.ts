import test from 'node:test';
import assert from 'node:assert/strict';
import {
  TRIANGLE_VERTICES,
  isPointInModeTriangle,
  trianglePointToBalance,
} from './mapGeometry';

test('isPointInModeTriangle accepts interior and vertex taps while rejecting bounding-box corners', () => {
  // Centroid & near vertices
  assert.equal(isPointInModeTriangle(0.5, 0.61), true);
  assert.equal(isPointInModeTriangle(TRIANGLE_VERTICES.tram.x, TRIANGLE_VERTICES.tram.y), true);
  assert.equal(isPointInModeTriangle(TRIANGLE_VERTICES.transit.x, TRIANGLE_VERTICES.transit.y), true);
  assert.equal(isPointInModeTriangle(TRIANGLE_VERTICES.walk.x, TRIANGLE_VERTICES.walk.y), true);

  // Outer corners of the bounding square outside the triangle
  assert.equal(isPointInModeTriangle(0.05, 0.05), false);
  assert.equal(isPointInModeTriangle(0.95, 0.05), false);
  assert.equal(isPointInModeTriangle(0.12, 0.25), false);
  assert.equal(isPointInModeTriangle(0.88, 0.25), false);
  assert.equal(isPointInModeTriangle(0.5, 0.98), false);

  // Dragging outside still projects onto simplex once active
  const clamped = trianglePointToBalance(0.5, 0.02);
  assert.ok(clamped.tram > 0.9);
});
