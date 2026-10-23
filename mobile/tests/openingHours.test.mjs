import test from 'node:test';
import assert from 'node:assert/strict';
import { parseHours, hoursRows, openingProblem, describeHours } from '../src/openingHours.ts';
import { organizeSchedule, timingCheck } from '../src/itinerary.ts';
import { smartOrder } from '../src/smartOrder.ts';
const friday = '2026-10-16';
const weekly = parseHours([{ dia:'5',value:'09:00-12:00, 14:00-18:00' }]);
test('unknown, closed and exceptional hours have different meanings', () => {
  assert.equal(openingProblem(null,friday,600,660),'');
  assert.equal(openingProblem(weekly,'2026-10-17',600,660),'');
  const closed = parseHours([...hoursRows(weekly),{dia:friday,value:'fechado'}]);
  assert.match(openingProblem(closed,friday,600,660),/fora/);
  const special = parseHours([{dia:'5',value:'fechado'},{dia:friday,value:'10:00-12:00'}]);
  assert.equal(openingProblem(special,friday,600,720),'');
  assert.match(describeHours(special),/16\/10\/2026/);
});
test('lunch gaps, exact boundaries and cross-midnight closures are checked', () => {
  assert.equal(openingProblem(weekly,friday,660,720),'');
  assert.match(openingProblem(weekly,friday,690,750),/fora/);
  const night = parseHours([{dia:'5',value:'22:00-24:00'},{dia:'6',value:'00:00-02:00'}]);
  assert.equal(openingProblem(night,friday,1380,1560),'');
  assert.match(openingProblem(night,friday,1380,1561),/fora/);
  assert.match(openingProblem([...night,{dia:'2026-10-17',intervalos:[]}],friday,1380,1500),/fora/);
});
test('invalid dates, duplicate rules and invalid or overlapping windows cannot be saved', () => {
  for (const value of ['','24:00-24:00','22:00-02:00','09:60-12:00','10:00-12:00, 11:00-13:00']) assert.throws(() => parseHours([{dia:'1',value}]));
  assert.throws(() => parseHours([{dia:'2027-02-29',value:'fechado'}]));
  assert.throws(() => parseHours([{dia:'1',value:'fechado'},{dia:'1',value:'10:00-12:00'}]));
  assert.deepEqual(parseHours(hoursRows(weekly)),weekly);
});
test('save and automatic ordering reject visits outside approved windows', () => {
  const role = { id:1,nome:'Museu',cidade:'Uberaba',data:null,valor:0,funcionamento:weekly,latitude:-19.7,longitude:-47.9 };
  const stops = [{role,minutes:60}];
  assert.equal(timingCheck(stops,'11:00',friday).problem,'');
  assert.match(timingCheck(stops,'11:30',friday).problem,/funcionamento/);
  assert.throws(() => organizeSchedule(stops,'16/10/2026','11:30',new Date(2026,9,1),true),/funcionamento/);
  assert.throws(() => smartOrder(stops,'11:30',undefined,friday),/funcionamento/);
  assert.equal(organizeSchedule(stops,'16/10/2026','14:00',new Date(2026,9,1),true).duracaoMinutos,60);
});
