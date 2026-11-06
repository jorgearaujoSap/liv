import type { Capabilities, Capability } from '../../api/platform/models';
import { buildMenu, optionPath } from './menu-presentation';

const leaf = (code: string, status: Capability['status'] = 'COMING_SOON'): Capability => ({
  code,
  status,
  children: [],
});
const branch = (code: string, children: Capability[]): Capability => ({
  code,
  status: children.some((child) => child.status === 'AVAILABLE') ? 'AVAILABLE' : 'COMING_SOON',
  children,
});
const capabilities = (...items: Capability[]): Capabilities => ({ items });

const MODULES = ['1', '2', '3', '4', '5', '6', '7', '8', '9'];

describe('menú: catálogo de presentación (001-15)', () => {
  it('la ruta de una opción lleva su número, como en el menú de Doc. 01 §3', () => {
    expect(optionPath('1.5.9')).toBe('/opcion/1.5.9');
    expect(optionPath('3')).toBe('/opcion/3');
  });

  it('presenta cada módulo con el nombre, la descripción y el icono del diseño aprobado', () => {
    const menu = buildMenu(capabilities(...MODULES.map((code) => leaf(code))));

    expect(menu.map((module) => [module.label, module.icon])).toEqual([
      ['Administración', 'settings'],
      ['Registro automotor y contribuyentes', 'car'],
      ['Liquidación', 'calculator'],
      ['Cuenta corriente y recaudo', 'wallet'],
      ['Cartera', 'hand-coins'],
      ['Cobro', 'gavel'],
      ['Medidas cautelares', 'lock'],
      ['Documentos y notificaciones', 'folder-open'],
      ['Auditoría e integraciones', 'shield-check'],
    ]);
    expect(menu[0].description).toBe(
      'Entidades, usuarios, normas, vigencias y tablas base de liquidación.',
    );
    expect(menu[5].description).toBe('Gestión del cobro persuasivo y coactivo.');
  });

  it('muestra solo lo que el backend permite y en el orden que él da', () => {
    const menu = buildMenu(capabilities(leaf('6'), leaf('3')));

    expect(menu.map((module) => module.code)).toEqual(['6', '3']);
  });

  it('sin capacidades no hay módulos', () => {
    expect(buildMenu(capabilities())).toEqual([]);
  });

  it('una opción COMING_SOON queda deshabilitada y sin destino: no navega', () => {
    const [module] = buildMenu(capabilities(leaf('3', 'COMING_SOON')));

    expect(module.available).toBe(false);
    expect(module.href).toBeUndefined();
  });

  it('una opción AVAILABLE navega a la ruta de su número', () => {
    const [module] = buildMenu(capabilities(leaf('3', 'AVAILABLE')));

    expect(module.available).toBe(true);
    expect(module.href).toBe('/opcion/3');
  });

  it('las subopciones del módulo salen con su nombre y solo las permitidas', () => {
    const [module] = buildMenu(
      capabilities(branch('1', [leaf('1.2', 'AVAILABLE'), leaf('1.4'), leaf('1.7')])),
    );

    expect(module.children.map((child) => [child.code, child.label, child.available])).toEqual([
      ['1.2', 'Usuarios, roles y permisos', true],
      ['1.4', 'Vigencias fiscales', false],
      ['1.7', 'Referencias vehiculares normalizadas', false],
    ]);
    expect(module.children[0].href).toBe('/opcion/1.2');
    expect(module.children[1].href).toBeUndefined();
  });

  it('conserva el tercer nivel (1.5.x) para quien lo pinte', () => {
    const [module] = buildMenu(
      capabilities(branch('1', [branch('1.5', [leaf('1.5.9'), leaf('1.5.3')])])),
    );

    const [cargue] = module.children;
    expect(cargue.label).toBe('Cargue de tablas base por vigencia');
    expect(cargue.children.map((child) => [child.code, child.label])).toEqual([
      ['1.5.9', 'Configuración de jurisdicción'],
      ['1.5.3', 'Cargar archivo de avalúos'],
    ]);
  });

  it('un código que el catálogo no conoce se muestra con su número, no se oculta', () => {
    const menu = buildMenu(capabilities(leaf('10'), branch('1', [leaf('1.9')])));

    expect(menu[0].label).toBe('Módulo 10');
    expect(menu[0].icon).toBe('file-text');
    expect(menu[1].children[0].label).toBe('Opción 1.9');
  });
});
