import test from 'node:test';
import assert from 'node:assert/strict';
import { profileError, profileInput } from '../src/socialProfile.ts';
test('public profile payload never copies private account fields or accepts owner override',()=>{
  const source={id:'public-id',nome:'  Ana  ',apresentacao:'  Museus  ',publico:true,versao:3,contaId:19,email:'private@example.com',roteiros:[1,2]};
  assert.deepEqual(profileInput(source),{nome:'Ana',apresentacao:'Museus',publico:true,versao:3});
  assert.equal(profileInput(source,false).publico,false);assert.equal(source.publico,true);
});
test('profile validates name and bio boundaries before saving',()=>{
  assert.ok(profileError({nome:' ',apresentacao:''}));
  assert.ok(profileError({nome:'a'.repeat(81),apresentacao:''}));
  assert.ok(profileError({nome:'Ana',apresentacao:'a'.repeat(501)}));
  assert.equal(profileError({nome:'a'.repeat(80),apresentacao:'a'.repeat(500)}),'');
});
