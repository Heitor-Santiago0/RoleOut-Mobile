import test from 'node:test';
import assert from 'node:assert/strict';
import { registrationFailure, registrationToken } from '../src/pushRegistration.ts';

test('registration obtains the native token once and forwards it to Expo', async () => {
  const native = {type:'android',data:'private-device-token'};
  let calls=0;
  assert.equal(await registrationToken(async()=>{calls++;return native;},async token=>{
    assert.equal(token,native); return {data:'expo-result'};
  }),'expo-result');
  assert.equal(calls,1);
});

test('failed native registration stops the pipeline and can be retried', async () => {
  let attempts=0,expoCalls=0;
  const device=async()=>{if(++attempts===1)throw new Error('SERVICE_NOT_AVAILABLE');return 'native';};
  const expo=async()=>{expoCalls++;return {data:'registered'};};
  await assert.rejects(registrationToken(device,expo),/internet/);
  assert.equal(expoCalls,0);
  assert.equal(await registrationToken(device,expo),'registered');
  assert.equal(expoCalls,1);
});

test('configuration, network and unknown stage failures are distinguished without exposing provider data', async () => {
  assert.match(registrationFailure(new Error('FIS_AUTH_ERROR secret'),'device').message,/configuração/);
  assert.match(registrationFailure({code:'ERR_NOTIFICATIONS_NETWORK_ERROR'},'expo').message,/internet/);
  assert.match(registrationFailure(null,'device').message,/registro Android/);
  await assert.rejects(registrationToken(async()=> 'native',async()=>{throw new Error('secret-token');}),error=>{
    assert.match(error.message,/registro Expo/);assert.ok(!error.message.includes('secret-token'));return true;
  });
});
