export function recoveryCode(value: string) { return value.trim(); }
export function recoveryError(code: string, password: string, confirmation: string): string {
  if (!/^[A-Za-z0-9_-]{43}$/.test(recoveryCode(code))) return 'Cole o código completo recebido por e-mail.';
  if (password.length < 6 || password.length > 128) return 'Use uma senha entre 6 e 128 caracteres.';
  if (password !== confirmation) return 'As senhas precisam ser iguais.';
  return '';
}
