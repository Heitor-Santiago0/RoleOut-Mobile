import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const configure = require('../app.config.js');
test('independent APK rejects missing, insecure and notebook API URLs before building', () => {
  const previous = { ...process.env };
  try {
    process.env.ROLEOUT_STANDALONE = '1';
    delete process.env.EAS_BUILD_PLATFORM;
    for (const value of ['', 'http://api.example.com/RoleOut/api/v1', 'https://127.0.0.1:8082', 'https://192.168.0.176', 'https://localhost', 'https://notebook.local', 'https://[::1]', 'https://user:password@api.example.com', 'https://api.example.com?token=secret']) {
      process.env.EXPO_PUBLIC_API_URL = value;
      assert.throws(() => configure({ config: {} }), /APK independente/);
    }
    process.env.EXPO_PUBLIC_API_URL = 'https://roleout.example.com/RoleOut/api/v1';
    assert.doesNotThrow(() => configure({ config: {} }));
    delete process.env.ROLEOUT_STANDALONE;
    delete process.env.EAS_BUILD_PROFILE;
    process.env.EXPO_PUBLIC_API_URL = 'http://127.0.0.1:8082/RoleOut/api/v1';
    assert.doesNotThrow(() => configure({ config: {} }));
  } finally {
    for (const key of Object.keys(process.env)) if (!(key in previous)) delete process.env[key];
    Object.assign(process.env, previous);
  }
});
