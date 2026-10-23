export type ActivationStep = { key: string; success: string; pending: string; run: () => Promise<boolean> };
export type ActivationResult = { key: string; enabled: boolean; message: string };
// A partial failure must not hide successful features or report an unconfirmed activation.
export async function activateFeatures(steps: ActivationStep[], current: () => Promise<boolean>, progress: (results: ActivationResult[]) => void) {
  const results: ActivationResult[] = [];
  for (const step of steps) {
    if (!await current()) return results;
    let result: ActivationResult;
    try {
      const enabled = await step.run();
      result = { key: step.key, enabled, message: enabled ? step.success : step.pending };
    } catch (error) {
      result = { key: step.key, enabled: false, message: error instanceof Error ? error.message : step.pending };
    }
    if (!await current()) return results;
    results.push(result); progress([...results]);
  }
  return results;
}
export async function boundedPosition<T>(operation: Promise<T>, timeoutMs = 20000): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([operation, new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error('A localização demorou. Informe sua cidade em Sugestões automáticas; os outros avisos podem ser configurados agora.')), timeoutMs);
    })]);
  } finally { clearTimeout(timer); }
}
