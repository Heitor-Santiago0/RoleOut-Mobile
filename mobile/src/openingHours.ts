export type HoursRule = { dia: string; intervalos: { inicio: string; fim: string }[] };
export type HoursRow = { dia: string; value: string };
export const weekDays = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
const minute = (s: string) => Number(s.slice(0, 2)) * 60 + Number(s.slice(3));
export function hoursRows(rules?: HoursRule[] | null): HoursRow[] {
  return (rules ?? []).map(rule => ({ dia: rule.dia, value: rule.intervalos.length ? rule.intervalos.map(w => `${w.inicio}-${w.fim}`).join(', ') : 'fechado' }));
}
export function parseHours(rows: HoursRow[]): HoursRule[] {
  if (rows.length > 67 || rows.filter(r => r.dia.length !== 1).length > 60) throw new Error('Use até 60 exceções por data.');
  const seen = new Set<string>();
  return rows.map(row => {
    if (seen.has(row.dia)) throw new Error('Há dias repetidos na programação.');
    seen.add(row.dia);
    if (!/^[1-7]$/.test(row.dia) && (!/^20\d{2}-\d{2}-\d{2}$|^2100-\d{2}-\d{2}$/.test(row.dia) || !Number.isFinite(Date.parse(row.dia)) || new Date(row.dia).toISOString().slice(0, 10) !== row.dia)) throw new Error('Confira a data da exceção.');
    if (row.value === 'fechado') return { dia: row.dia, intervalos: [] };
    const intervalos = row.value.split(',').map(part => {
      const match = part.trim().match(/^(([01]\d|2[0-3]):[0-5]\d)-(([01]\d|2[0-3]):[0-5]\d|24:00)$/);
      if (!match || minute(match[1]) >= minute(match[3])) throw new Error('Use intervalos como 09:00-12:00, 14:00-18:00. Para passar da meia-noite, divida entre os dois dias.');
      return { inicio: match[1], fim: match[3] };
    }).sort((a, b) => a.inicio.localeCompare(b.inicio));
    if (intervalos.length > 4 || intervalos.some((w, i) => i > 0 && w.inicio < intervalos[i - 1].fim)) throw new Error('Use até quatro intervalos por dia, sem sobreposição.');
    return { dia: row.dia, intervalos };
  });
}
export function hoursOn(rules: HoursRule[] | null | undefined, day: string) {
  const weekday = String(new Date(day + 'T00:00:00Z').getUTCDay() || 7);
  return (rules?.find(r => r.dia === day) ?? rules?.find(r => r.dia === weekday))?.intervalos;
}
export function openingProblem(rules: HoursRule[] | null | undefined, day: string | null | undefined, start: number, end: number): string {
  if (!rules?.length) return '';
  if (!day) return 'Escolha uma data para conferir o funcionamento.';
  for (let cursor = start; cursor < end;) {
    const offset = Math.floor(cursor / 1440), from = cursor % 1440, to = Math.min(end - offset * 1440, 1440);
    const date = new Date(day + 'T00:00:00Z'); date.setUTCDate(date.getUTCDate() + offset);
    const windows = hoursOn(rules, date.toISOString().slice(0, 10));
    if (windows) {
      let covered = from;
      for (const w of [...windows].sort((a,b) => a.inicio.localeCompare(b.inicio))) if (minute(w.inicio) <= covered && minute(w.fim) > covered) covered = minute(w.fim);
      if (covered < to) return 'A visita fica fora dos horários de funcionamento informados. Revise o horário ou a duração.';
    }
    cursor = offset * 1440 + to;
  }
  return '';
}
export function describeHours(rules?: HoursRule[] | null): string {
  if (!rules?.length) return 'Funcionamento não informado. Confirme com o responsável.';
  return hoursRows(rules).map(r => `${r.dia.length === 1 ? weekDays[Number(r.dia) - 1] : r.dia.split('-').reverse().join('/')}: ${r.value}`).join('\n');
}
