import test from 'node:test';
import assert from 'node:assert/strict';
import { activateFeatures, boundedPosition } from '../src/permissionActivation.ts';
const step=(key,run)=>({key,run,success:key+' ativado',pending:key+' pendente'});
test('partial activation reports only confirmed successes and continues after failure',async()=>{
  const updates=[];
  const results=await activateFeatures([step('location',async()=>false),step('suggestions',async()=>{throw new Error('offline');}),step('push',async()=>true),step('travel',async()=>false)],async()=>true,v=>updates.push(v));
  assert.deepEqual(results.map(r=>r.enabled),[false,false,true,false]);
  assert.equal(results[1].message,'offline');assert.equal(updates.length,4);assert.equal(updates[0].length,1);
});
test('leaving screen or changing session while pending stops following permissions and success messages',async()=>{
  let current=true, calls=0;
  const results=await activateFeatures([step('first',async()=>{current=false;return true;}),step('second',async()=>{calls++;return true;})],async()=>current,()=>assert.fail('stale update'));
  assert.deepEqual(results,[]);assert.equal(calls,0);
});
test('inactive flow never invokes system prompts',async()=>{
  await activateFeatures([step('first',async()=>assert.fail('unexpected prompt'))],async()=>false,()=>assert.fail('unexpected UI update'));
});
test('GPS timeout allows activation sequence to continue without using late result',async()=>{
  let resolvePosition;
  const pending=new Promise(resolve=>{resolvePosition=resolve;});
  const results=await activateFeatures([step('gps',async()=>{await boundedPosition(pending,10);return true;}),step('push',async()=>true)],async()=>true,()=>{});
  assert.deepEqual(results.map(r=>r.enabled),[false,true]);assert.match(results[0].message,/localização demorou/);
  resolvePosition({city:'Late city'});await Promise.resolve();assert.equal(results[0].enabled,false);
});
test('bounded position returns timely value and preserves immediate errors',async()=>{
  assert.equal(await boundedPosition(Promise.resolve('Uberaba'),100),'Uberaba');
  await assert.rejects(boundedPosition(Promise.reject(new Error('GPS desligado')),100),/GPS desligado/);
});
