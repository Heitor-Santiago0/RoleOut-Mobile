import test from 'node:test';
import assert from 'node:assert/strict';
import { discoveryTiming } from '../src/discoveryTiming.ts';
import { nearbyHighlights } from '../src/recommendations.ts';
const day='2026-10-20';
const event={data:day,horario:'10:00',horarioFim:'12:00'};
const weekly=[{dia:'2',intervalos:[{inicio:'09:00',fim:'12:00'},{inicio:'14:00',fim:'18:00'}]}];

test('events respect exact start/end and do not invent an end for missing schedules',()=>{
  assert.equal(discoveryTiming(event,day,'09:59').status,'later');
  assert.equal(discoveryTiming(event,day,'10:00').status,'now');
  assert.equal(discoveryTiming(event,day,'12:00').status,'unavailable');
  assert.equal(discoveryTiming({data:day,horario:'10:00'},day,'11:00').status,'unknown');
  assert.equal(discoveryTiming({},day,'11:00').status,'unknown');
  assert.equal(discoveryTiming(event,day,'invalid').status,'unknown');
});
test('weekly pauses and date exceptions control recommendations',()=>{
  const place={funcionamento:weekly};
  assert.equal(discoveryTiming(place,day,'11:00').status,'now');
  assert.equal(discoveryTiming(place,day,'12:00').label,'Hoje a partir de 14:00');
  assert.equal(discoveryTiming(place,day,'18:00').status,'unavailable');
  assert.equal(discoveryTiming({funcionamento:[...weekly,{dia:day,intervalos:[]}]},day,'11:00').status,'unavailable');
  assert.equal(discoveryTiming(place,'2026-10-21','11:00').status,'unknown');
  assert.equal(discoveryTiming({...event,horario:'13:00',horarioFim:'14:00',funcionamento:weekly},day,'11:00').status,'unavailable');
});
test('overnight event uses the final date and respects closed exceptions',()=>{
  const overnight={data:'2026-10-19',dataFim:day,horario:'22:00',horarioFim:'02:00'};
  assert.equal(discoveryTiming(overnight,day,'01:00').status,'now');
  assert.equal(discoveryTiming(overnight,day,'02:00').status,'unavailable');
  assert.equal(discoveryTiming({...overnight,funcionamento:[{dia:day,intervalos:[]}]},day,'01:00').status,'unavailable');
  assert.equal(discoveryTiming({...event,cancelado:true},day,'11:00').status,'unavailable');
});
test('ranking prioritizes current schedules then later then unknown without mutating input',()=>{
  const preferences={status:'CONCLUIDO',musica:[],comida:[],atividades:['Museus'],ambientes:[]};
  const base={categoria:'Cultura',distanciaKm:1};
  const items=[{...base,id:1}, {...base,id:2,data:day,horario:'15:00',horarioFim:'16:00'},
    {...base,id:3,...event}, {...base,id:4,data:day,horario:'08:00',horarioFim:'09:00'},
    {...base,id:5,...event,distanciaKm:6}, {...base,id:6,...event,cancelado:true}];
  const before=JSON.stringify(items);
  assert.deepEqual(nearbyHighlights(items,preferences,day,'11:00').map(x=>x.role.id),[3,2,1]);
  assert.equal(JSON.stringify(items),before);
});
