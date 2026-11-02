import axe from 'axe-core';

/**
 * Ejecuta axe-core sobre el elemento y falla si hay violaciones. El contraste de color no se puede
 * calcular en jsdom, así que esta prueba no lo cubre: se revisa a mano con el panel de accesibilidad de
 * Storybook hasta que las pruebas de navegador (regresión visual, Q14) lo automaticen.
 */
export async function expectNoAxeViolations(element: Element): Promise<void> {
  const results = await axe.run(element, { rules: { 'color-contrast': { enabled: false } } });
  expect(results.violations.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
}
