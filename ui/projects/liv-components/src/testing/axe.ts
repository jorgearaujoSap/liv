import axe from 'axe-core';

/**
 * Ejecuta axe-core sobre el elemento y falla si hay violaciones. El contraste de color no se puede
 * calcular en jsdom: lo revisa el complemento de accesibilidad de Storybook en un navegador real.
 */
export async function expectNoAxeViolations(element: Element): Promise<void> {
  const results = await axe.run(element, { rules: { 'color-contrast': { enabled: false } } });
  expect(results.violations.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
}
