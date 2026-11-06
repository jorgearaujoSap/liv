import type { IconName } from 'liv-components';

import type { Capabilities, Capability } from '../../api/platform/models';

/**
 * Cómo se ve el menú (001-15). El backend decide **qué** opciones ve la persona y cuáles están aún sin
 * construir (`/api/v1/me/capabilities`); aquí solo se dice cómo se llaman, qué icono tienen y a dónde
 * llevan. Los nombres siguen el mapa del menú de Doc. 01 §3 y las tarjetas del diseño de Inicio.
 */

/** Una opción del menú, en cualquier nivel. */
export interface MenuOption {
  /** Número de la opción de Doc. 01 §3, p. ej. `1.5.9`. */
  readonly code: string;
  readonly label: string;
  /** Ruta de la opción; no existe si aún no está construida (`COMING_SOON`): no navega. */
  readonly href: string | undefined;
  /** `false` mientras la opción no esté construida. */
  readonly available: boolean;
  readonly children: readonly MenuOption[];
}

/** Un módulo (nivel 1): además de su nombre, lleva el icono y la descripción de su tarjeta de Inicio. */
export interface MenuModule extends MenuOption {
  readonly icon: IconName;
  readonly description: string;
}

interface ModulePresentation {
  readonly label: string;
  readonly icon: IconName;
  readonly description: string;
}

const MODULES: Readonly<Record<string, ModulePresentation>> = {
  '1': {
    label: 'Administración',
    icon: 'settings',
    description: 'Entidades, usuarios, normas, vigencias y tablas base de liquidación.',
  },
  '2': {
    label: 'Registro automotor y contribuyentes',
    icon: 'car',
    description: 'Vehículos, matrículas, propietarios y homologación con las tablas de avalúo.',
  },
  '3': {
    label: 'Liquidación',
    icon: 'calculator',
    description: 'Liquidación individual y masiva por vigencia, simulador y determinación oficial.',
  },
  '4': {
    label: 'Cuenta corriente y recaudo',
    icon: 'wallet',
    description: 'Obligaciones, recibos y pagos, imputación, saldos a favor y distribución.',
  },
  '5': {
    label: 'Cartera',
    icon: 'hand-coins',
    description: 'Cartera histórica, intereses de mora, sanciones, prescripción y cortes.',
  },
  '6': {
    label: 'Cobro',
    icon: 'gavel',
    description: 'Gestión del cobro persuasivo y coactivo.',
  },
  '7': {
    label: 'Medidas cautelares',
    icon: 'lock',
    description: 'Registro y seguimiento de las medidas cautelares.',
  },
  '8': {
    label: 'Documentos y notificaciones',
    icon: 'folder-open',
    description: 'Documentos generados y notificaciones a los contribuyentes.',
  },
  '9': {
    label: 'Auditoría e integraciones',
    icon: 'shield-check',
    description: 'Bitácora de eventos e integraciones con RUNT y bancos.',
  },
};

/** Nombres de las subopciones del módulo 1, Doc. 01 §3. Las de los módulos 2 a 9 se agregan con su matriz. */
const OPTION_LABELS: Readonly<Record<string, string>> = {
  '1.1': 'Entidades y administraciones tributarias',
  '1.2': 'Usuarios, roles y permisos',
  '1.3': 'Normas y soportes',
  '1.4': 'Vigencias fiscales',
  '1.5': 'Cargue de tablas base por vigencia',
  '1.5.1': 'Asistente de preparación de vigencia',
  '1.5.2': 'Catálogos de avalúos',
  '1.5.3': 'Cargar archivo de avalúos',
  '1.5.4': 'Bandeja de lotes',
  '1.5.5': 'Tarifas por rango',
  '1.5.6': 'Calendario tributario',
  '1.5.7': 'Beneficios y descuentos',
  '1.5.8': 'Reglas de sanción',
  '1.5.9': 'Configuración de jurisdicción',
  '1.6': 'Consultas y exportación de tablas base',
  '1.6.1': 'Consulta de lotes de importación',
  '1.6.2': 'Consulta de filas en staging',
  '1.6.3': 'Consulta de errores y advertencias',
  '1.6.4': 'Consulta de catálogo publicado',
  '1.6.5': 'Comparativo entre vigencias y versiones',
  '1.6.6': 'Consulta de tarifas y calendario',
  '1.6.7': 'Reporte mensual SIBGA',
  '1.7': 'Referencias vehiculares normalizadas',
  '1.8': 'Parámetros generales',
};

/** Icono de un módulo que este catálogo aún no conoce (el backend puede ir adelante del frontend). */
const UNKNOWN_MODULE_ICON: IconName = 'file-text';

/**
 * Ruta de una opción del menú. Lleva el número de Doc. 01 §3 para que la ruta y la documentación
 * hablen de lo mismo. Cada feature que construya una opción registra su ruta antes que esta.
 */
export function optionPath(code: string): string {
  return `/opcion/${code}`;
}

/** El menú de la persona, a partir de sus capacidades: solo lo permitido, en el orden del backend. */
export function buildMenu(capabilities: Capabilities): MenuModule[] {
  return capabilities.items.map(toModule);
}

function toModule(capability: Capability): MenuModule {
  const known = MODULES[capability.code];
  return {
    ...toOption(capability, `Módulo ${capability.code}`),
    label: known?.label ?? `Módulo ${capability.code}`,
    icon: known?.icon ?? UNKNOWN_MODULE_ICON,
    description: known?.description ?? '',
  };
}

function toOption(capability: Capability, fallbackLabel = `Opción ${capability.code}`): MenuOption {
  const available = capability.status === 'AVAILABLE';
  return {
    code: capability.code,
    label: OPTION_LABELS[capability.code] ?? fallbackLabel,
    href: available ? optionPath(capability.code) : undefined,
    available,
    children: capability.children.map((child) => toOption(child)),
  };
}
