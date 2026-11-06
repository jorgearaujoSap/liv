/**
 * Cómo se llama cada rol de LIV en pantalla (doc. 02 §4.2; los nombres definitivos se confirman en la
 * Fase 1). Un rol que aún no está aquí se muestra legible, no se oculta.
 */
const ROLE_LABELS: Readonly<Record<string, string>> = {
  ADMIN_SISTEMA: 'Administración del sistema',
  CARGADOR_TABLAS: 'Cargador de tablas',
  APROBADOR_TABLAS: 'Aprobador de tablas',
  LIQUIDADOR: 'Liquidador',
  APROBADOR_LIQUIDACION: 'Aprobador de liquidación',
  RECAUDO: 'Recaudo',
  TESORERIA: 'Tesorería',
  APROBADOR_RECAUDO: 'Aprobador de recaudo',
  JURIDICO: 'Jurídico',
  CONSULTA: 'Consulta',
  AUDITOR: 'Auditor',
};

function roleLabel(role: string): string {
  const known = ROLE_LABELS[role];
  if (known) {
    return known;
  }
  const words = role.toLowerCase().replaceAll('_', ' ').trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Los roles de la persona para la cabecera, separados por coma; vacío si no tiene. */
export function roleLabels(roles: readonly string[]): string {
  return roles.map(roleLabel).join(', ');
}
