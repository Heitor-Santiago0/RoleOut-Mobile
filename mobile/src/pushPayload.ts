// Accept only our known action and a positive database ID, never an arbitrary URL.
export function notificationRoute(data: Record<string, unknown> | undefined): number | null {
  return data?.tipo === 'horarios' && typeof data.roteiroId === 'number'
    && Number.isSafeInteger(data.roteiroId) && data.roteiroId > 0 && data.roteiroId <= 2147483647 ? data.roteiroId : null;
}

export function discoveryNotificationRoute(data: Record<string, unknown> | undefined): number | null {
  return data?.tipo === 'descoberta' && typeof data.roleId === 'number' && Number.isSafeInteger(data.roleId)
    && data.roleId > 0 && data.roleId <= 2147483647 ? data.roleId : null;
}
