/** `#rrggbb` at an alpha, for the many translucent fills. */
export function alpha(hex: string, a: number): string {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

/** `#rrggbb` a fraction [t] of the way from [a] to [b]. */
export function mix(a: string, b: string, t: number): string {
  const x = parseInt(a.slice(1), 16);
  const y = parseInt(b.slice(1), 16);
  let out = '#';
  for (const shift of [16, 8, 0]) {
    const p = (x >> shift) & 255;
    const q = (y >> shift) & 255;
    out += Math.round(p + (q - p) * t).toString(16).padStart(2, '0').toUpperCase();
  }
  return out;
}
