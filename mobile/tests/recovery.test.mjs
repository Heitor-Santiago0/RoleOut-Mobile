import test from 'node:test';
import assert from 'node:assert/strict';
import { recoveryCode, recoveryError } from '../src/recovery.ts';
const code='A'.repeat(43);
test('recovery accepts pasted code with exterior whitespace and matching valid passwords',()=>{
 assert.equal(recoveryCode(` ${code}\n`),code);assert.equal(recoveryError(` ${code}\n`,'abc123','abc123'),'');
});
test('recovery rejects malformed codes, short passwords and confirmation mismatch',()=>{
 for(const invalid of ['',code.slice(1),code+'A','!'.repeat(43)])assert.ok(recoveryError(invalid,'abc123','abc123'));
 assert.ok(recoveryError(code,'abc12','abc12'));assert.ok(recoveryError(code,'A'.repeat(129),'A'.repeat(129)));assert.ok(recoveryError(code,'abc123','abc124'));
});
