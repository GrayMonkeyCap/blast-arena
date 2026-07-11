// Small 2D math + collision helpers shared by sim, bots and renderer.
// The playfield is the XZ plane; Y is height. Facing angles use
// atan2(dx, dz) so that THREE's rotation.y matches directly (forward = +Z).

export const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
export const lerp = (a, b, t) => a + (b - a) * t;

export function angleLerp(a, b, t) {
  let d = (b - a) % (Math.PI * 2);
  if (d > Math.PI) d -= Math.PI * 2;
  if (d < -Math.PI) d += Math.PI * 2;
  return a + d * t;
}

export const dist2 = (x0, z0, x1, z1) => Math.hypot(x1 - x0, z1 - z0);

export function norm2(x, z) {
  const len = Math.hypot(x, z);
  return len > 1e-6 ? { x: x / len, z: z / len, len } : { x: 0, z: 0, len: 0 };
}

// Solid boxes are {x, z, w, d, h} centered at (x, z), sitting on the floor.
// Returns a push-out vector + contact normal if a circle at (px,pz) with
// radius r overlaps the box footprint, else null.
export function circlePushOut(px, pz, r, box) {
  const hw = box.w / 2;
  const hd = box.d / 2;
  const cx = clamp(px, box.x - hw, box.x + hw);
  const cz = clamp(pz, box.z - hd, box.z + hd);
  const dx = px - cx;
  const dz = pz - cz;
  const d2 = dx * dx + dz * dz;
  if (d2 >= r * r) return null;
  if (d2 > 1e-9) {
    const d = Math.sqrt(d2);
    return { x: (dx / d) * (r - d), z: (dz / d) * (r - d), nx: dx / d, nz: dz / d };
  }
  // Circle center is inside the box: escape along the shallowest axis.
  const ox = hw + r - Math.abs(px - box.x);
  const oz = hd + r - Math.abs(pz - box.z);
  if (ox < oz) {
    const s = px >= box.x ? 1 : -1;
    return { x: s * ox, z: 0, nx: s, nz: 0 };
  }
  const s = pz >= box.z ? 1 : -1;
  return { x: 0, z: s * oz, nx: 0, nz: s };
}
