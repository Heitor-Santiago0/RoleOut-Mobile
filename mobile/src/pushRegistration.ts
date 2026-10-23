// Provider messages can contain tokens. Never display or log their raw contents.
export function registrationFailure(error: unknown, stage: 'device' | 'expo'): Error {
  const value = error && typeof error === 'object' ? error as { code?: unknown; message?: unknown } : {};
  const text = `${typeof value.code === 'string' ? value.code : ''} ${typeof value.message === 'string' ? value.message : ''}`;
  if (/API_KEY_SERVICE_BLOCKED|FIS_AUTH_ERROR|Default FirebaseApp|FirebaseApp.*not initialized/i.test(text)) {
    return new Error('O serviço de notificações precisa de um ajuste de configuração. Informe ao suporte: registro Firebase. Seus rolês continuam disponíveis.');
  }
  if (/ERR_NOTIFICATIONS_NETWORK_ERROR|SERVICE_NOT_AVAILABLE|NETWORK|TIMEOUT|timed out/i.test(text)) {
    return new Error('Não foi possível conectar ao serviço de notificações agora. Confira sua internet e tente ativar os avisos novamente.');
  }
  return new Error(stage === 'device'
    ? 'Não foi possível registrar o celular no serviço de notificações do Android. Tente novamente; se persistir, informe ao suporte: registro Android.'
    : 'Não foi possível concluir a ativação no serviço de envio. Tente novamente; se persistir, informe ao suporte: registro Expo.');
}

export async function registrationToken<T>(device: () => Promise<T>, expo: (token: T) => Promise<{ data: string }>): Promise<string> {
  let token: T;
  try { token = await device(); } catch (error) { throw registrationFailure(error, 'device'); }
  try { return (await expo(token)).data; } catch (error) { throw registrationFailure(error, 'expo'); }
}
