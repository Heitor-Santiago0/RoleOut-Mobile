import { openingProblem } from './openingHours.ts';
import { eventStartOn, eventEndMinute, happensOn } from './eventPeriod.ts';
import type { Stop } from './itinerary';
type Point = { latitude: number; longitude: number };
const clock = (time: string) => { const [h, m] = time.split(':').map(Number); return h * 60 + m; };
function position(stop: Stop): Point {
  const { latitude, longitude } = stop.role;
  if (typeof latitude !== 'number' || !Number.isFinite(latitude) || Math.abs(latitude) > 90 || typeof longitude !== 'number' || !Number.isFinite(longitude) || Math.abs(longitude) > 180) throw new Error(`Faltam coordenadas de ${stop.role.nome}. A ordem atual foi mantida.`);
  return { latitude, longitude };
}
function distance(a: Point, b: Point) {
  const rad = Math.PI / 180;
  return Math.sin((a.latitude - b.latitude) * rad / 2) ** 2 + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin((a.longitude - b.longitude) * rad / 2) ** 2;
}
function fixed(stop: Stop, day?: string | null) {
  const values = [stop.horario, eventStartOn(stop.role, day)].filter((time): time is string => !!time);
  if (values.some(time => !/^([01]\d|2[0-3]):[0-5]\d$/.test(time))) throw new Error('Complete os horários das paradas no formato HH:MM.');
  return values.length ? Math.min(...values.map(clock)) : Infinity;
}
export function smartOrder(stops: Stop[], start: string, origin?: Point, day?: string | null): Stop[] {
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(start)) throw new Error('Informe um horário de partida válido.');
  if (!stops.length) return [];
  if (stops.some(stop => stop.role.cancelado)) throw new Error('Há um evento cancelado. Troque ou remova essa parada antes de organizar.');
  if (day && stops.some(stop => !happensOn(stop.role, day))) throw new Error('Há uma parada fora do período do evento. Revise a data.');
  stops.forEach(position);
  const remaining = [...stops]; const result: Stop[] = [];
  let here = origin ?? position(stops[0]); let cursor = clock(start);
  while (remaining.length) {
    const timed = remaining.filter(stop => Number.isFinite(fixed(stop, day))).sort((a, b) => fixed(a, day) - fixed(b, day));
    const flexible = remaining.filter(stop => !Number.isFinite(fixed(stop, day))).sort((a, b) => distance(here, position(a)) - distance(here, position(b)));
    const urgent = timed[0]; const nearby = flexible[0];
    // Reserve 30 minutes before a commitment; this is a buffer, not a travel ETA.
    const chosen = urgent && (!nearby || cursor + nearby.minutes + 30 >= fixed(urgent, day)) ? urgent : nearby ?? urgent;
    if (Number.isFinite(fixed(chosen, day))) {
      if (cursor > fixed(chosen, day)) throw new Error(`O horário de ${chosen.role.nome} conflita com a partida ou outra visita. Revise os horários; a ordem atual foi mantida.`);
      cursor = fixed(chosen, day);
    }
    const hoursIssue = openingProblem(chosen.role.funcionamento, day, cursor, cursor + chosen.minutes);
    if (hoursIssue) throw new Error(`${chosen.role.nome}: ${hoursIssue} A ordem atual foi mantida.`);
    cursor += chosen.minutes;
    if (cursor > eventEndMinute(chosen.role, day)) throw new Error(`A visita a ${chosen.role.nome} ultrapassa o término do evento. Reduza a duração; a ordem atual foi mantida.`);
    here = position(chosen);
    result.push(chosen); remaining.splice(remaining.indexOf(chosen), 1);
  }
  return result;
}
