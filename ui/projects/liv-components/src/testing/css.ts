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

/**
 * Valor declarado de una propiedad CSS para el texto de ejemplo (`::placeholder`) del elemento. Igual que
 * `declaredStyle`, pero solo con las reglas cuyo selector termina en `::placeholder`, que
 * `Element.matches` no reconoce.
 */
export function declaredPlaceholderStyle(element: Element, property: string): string {
  const pseudo = '::placeholder';
  let value = '';
  const visit = (rules: CSSRuleList) => {
    for (const rule of Array.from(rules)) {
      if (rule instanceof CSSStyleRule) {
        const declared = rule.style.getPropertyValue(property);
        const matches = rule.selectorText
          .split(',')
          .map((selector) => selector.trim())
          .some(
            (selector) =>
              selector.endsWith(pseudo) && element.matches(selector.slice(0, -pseudo.length)),
          );
        if (declared && matches) {
          value = declared;
        }
      } else if ('cssRules' in rule && !(rule instanceof CSSMediaRule)) {
        visit((rule as CSSGroupingRule).cssRules);
      }
    }
  };
  for (const sheet of Array.from(element.ownerDocument.styleSheets)) {
    visit(sheet.cssRules);
  }
  return value;
}
