import test from 'node:test';
import assert from 'node:assert/strict';
import { notificationRoute } from '../src/pushPayload.ts';
test('notifications open only a known action with a valid numeric itinerary ID',()=>{
  assert.equal(notificationRoute({tipo:'horarios',roteiroId:12}),12);
  for(const data of [undefined,{}, {url:'https://evil.example'}, {tipo:'other',roteiroId:12},
    ...[0,-1,1.5,NaN,Infinity,2147483648,'12',null].map(roteiroId=>({tipo:'horarios',roteiroId}))])
    assert.equal(notificationRoute(data),null);
});
