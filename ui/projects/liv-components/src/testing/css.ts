/**
 * Valor declarado de una propiedad CSS para el elemento, según las reglas de las hojas de estilo que lo
 * seleccionan (gana la última, como en los componentes, donde las reglas de estado van después de la
 * base). Sirve para lo que jsdom no calcula con `getComputedStyle`, como `border-color` con `var()` o
 * las reglas de `@media`. No resuelve la especificidad ni los estilos en línea.
 *
 * Sin `media`, solo cuentan las reglas fuera de `@media`; con `media`, solo las que están dentro de un
 * `@media` cuya condición contiene ese texto (p. ej. `forced-colors`).
 */
export function declaredStyle(element: Element, property: string, media?: string): string {
  let value = '';
  const visit = (rules: CSSRuleList, inMedia: boolean) => {
    for (const rule of Array.from(rules)) {
      if (rule instanceof CSSStyleRule) {
        const declared = rule.style.getPropertyValue(property);
        if (declared && inMedia === Boolean(media) && element.matches(rule.selectorText)) {
          value = declared;
        }
      } else if (rule instanceof CSSMediaRule) {
        if (media && rule.conditionText.includes(media)) {
          visit(rule.cssRules, true);
        }
      } else if ('cssRules' in rule) {
        visit((rule as CSSGroupingRule).cssRules, inMedia);
      }
    }
  };
  for (const sheet of Array.from(element.ownerDocument.styleSheets)) {
    visit(sheet.cssRules, false);
  }
  return value;
}
