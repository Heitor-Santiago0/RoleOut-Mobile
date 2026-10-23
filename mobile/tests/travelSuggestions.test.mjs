import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluateTravel, usableTravelFix } from '../src/travelSuggestions.ts';
import { discoveryNotificationRoute } from '../src/pushPayload.ts';
const now=1000000;
const fix={timestamp:now,coords:{latitude:-19.7,longitude:-47.9,accuracy:50}};
const suggestion={roleId:1,nome:'Museu',cidade:'Uberaba',distanciaKm:1,motivos:['Ambiente: Tranquilo'],outraCidade:true};
function setup(overrides={}) {
  const calls=[];
  const deps={allowed:async()=>true,permissions:async()=>true,stop:async()=>calls.push('stop'),settings:async()=>({ativo:true,viagens:true,intervaloHoras:24,cidadeBase:'São Paulo'}),city:async()=>{calls.push('city');return 'Uberaba';},evaluate:async()=>{calls.push('evaluate');return suggestion;},notify:async()=>calls.push('notify'),...overrides};
  return {deps,calls};
}
test('travel rejects stale, future, inaccurate and invalid fixes',()=>{
  assert.equal(usableTravelFix(fix,now),true);
  for(const value of [undefined,{...fix,timestamp:now+1},{...fix,timestamp:now-120000},...[null,-1,501,NaN].map(accuracy=>({...fix,coords:{...fix.coords,accuracy}})),{...fix,coords:{...fix.coords,longitude:181}}])assert.equal(usableTravelFix(value,now),false);
});
test('travel requires device consent, permissions and saved account opt-in before geocoding',async()=>{
  for(const overrides of [{allowed:async()=>false},{permissions:async()=>false},{settings:async()=>({ativo:false,viagens:true})},{settings:async()=>({ativo:true,viagens:false})}]) {
    const {deps,calls}=setup(overrides); await evaluateTravel(fix,now,deps); assert.deepEqual(calls,['stop']);
  }
});
test('travel ignores home city and missing city without consuming account frequency',async()=>{
  for(const city of ['sao paulo',' SÃO PAULO ',null,'']) {
    const {deps,calls}=setup({city:async()=>city}); await evaluateTravel(fix,now,deps); assert.deepEqual(calls,[]);
  }
});
test('travel notifies only an eligible away suggestion from server',async()=>{
  const {deps,calls}=setup();await evaluateTravel(fix,now,deps);assert.deepEqual(calls,['city','evaluate','notify']);
  for(const result of [null,{...suggestion,outraCidade:false}]) {
    const state=setup({evaluate:async()=>result});await evaluateTravel(fix,now,state.deps);assert.ok(!state.calls.includes('notify'));
  }
});
test('logout or permission revocation while API request is pending suppresses notification',async()=>{
  for(const guard of ['allowed','permissions']) {
    let valid=true;const state=setup({[guard]:async()=>valid,evaluate:async()=>{valid=false;return suggestion;}});
    await evaluateTravel(fix,now,state.deps);assert.ok(!state.calls.includes('notify'));
  }
});
test('network failures never substitute stale recommendations',async()=>{
  const {deps,calls}=setup({evaluate:async()=>{throw new Error('offline');}});
  await assert.rejects(evaluateTravel(fix,now,deps),/offline/);assert.ok(!calls.includes('notify'));
});
test('discovery notifications accept only a positive role ID and known type',()=>{
  assert.equal(discoveryNotificationRoute({tipo:'descoberta',roleId:12}),12);
  for(const data of [undefined,{}, {tipo:'horarios',roleId:12},...[0,-1,1.5,NaN,2147483648,'12'].map(roleId=>({tipo:'descoberta',roleId}))])assert.equal(discoveryNotificationRoute(data),null);
});
