export type NearbyMapProps = { demo: boolean; token?: string; onSelect: (id: number) => void };
export type Point = { id: number; nome: string; categoria: string | null; latitude: number; longitude: number; distanciaKm: number };
