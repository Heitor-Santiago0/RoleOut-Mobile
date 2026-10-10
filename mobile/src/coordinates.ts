export function coordinates(latitude: string, longitude: string): { latitude: number | null; longitude: number | null } | null {
  if (!latitude.trim() && !longitude.trim()) return { latitude: null, longitude: null };
  const parse = (value: string, limit: number) => {
    const text = value.trim().replace(',', '.');
    if (!/^-?\d{1,3}(\.\d{1,6})?$/.test(text)) return null;
    const number = Number(text);
    return Number.isFinite(number) && Math.abs(number) <= limit ? number : null;
  };
  const lat = parse(latitude, 90), lon = parse(longitude, 180);
  return lat === null || lon === null ? null : { latitude: lat, longitude: lon };
}
