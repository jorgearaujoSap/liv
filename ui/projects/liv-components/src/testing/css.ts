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
  return lastDeclared(element, property, media, (selector) => element.matches(selector));
}

/**
 * Como `declaredStyle` sin `media`, pero para el texto de ejemplo (`::placeholder`) del elemento:
 * cuentan las reglas con un selector que termina en `::placeholder` y cuya parte anterior selecciona el
 * elemento (`Element.matches` no admite pseudoelementos).
 */
export function declaredPlaceholderStyle(element: Element, property: string): string {
  const pseudo = '::placeholder';
  return lastDeclared(element, property, undefined, (selectorList) =>
    splitSelectorList(selectorList).some(
      (selector) =>
        selector.endsWith(pseudo) && element.matches(selector.slice(0, -pseudo.length) || '*'),
    ),
  );
}

/**
 * Sombra que Tailwind declara para el elemento (`--tw-shadow`, la que aplica `shadow-*`), con los colores
 * ya sacados de `var(--tw-shadow-color, …)`: así se compara con el token de `theme.css`. Ignora la regla
 * universal (`*, ::before, …`) con la que Tailwind da el valor inicial: va después de las de los
 * componentes, pero dentro de `@layer properties`, así que en el navegador pierde siempre.
 */
export function declaredShadow(element: Element): string {
  return lastDeclared(
    element,
    '--tw-shadow',
    undefined,
    (selector) => !selector.trim().startsWith('*') && element.matches(selector),
  )
    .replace(/var\(--tw-shadow-color,\s*([^)]*\)?)\)/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Si el elemento pinta ahora la sombra `shadow` de Tailwind: la declara en `--tw-shadow` y su
 * `box-shadow` usa esa variable (una regla posterior con `box-shadow: none` la anularía).
 */
export function paintsShadow(element: Element, shadow: string): boolean {
  return (
    declaredShadow(element) === shadow &&
    declaredStyle(element, 'box-shadow').includes('var(--tw-shadow)')
  );
}

/**
 * Valor de una variable CSS declarada en el texto de una hoja de estilos (p. ej. `theme.css`). Ignora los
 * comentarios y falla si la variable no existe o está declarada más de una vez: la prueba no debe pasar
 * en silencio con otro valor.
 */
export function cssVariable(css: string, name: string): string {
  const code = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const matches = [...code.matchAll(new RegExp(`(?:^|[\\s{;])${escaped}\\s*:\\s*([^;]+);`, 'g'))];
  if (matches.length !== 1) {
    throw new Error(`La variable ${name} aparece ${matches.length} veces; se esperaba una`);
  }
  return matches[0][1].trim().replace(/\s+/g, ' ');
}

/** Recorre las hojas de estilo y devuelve el último valor de `property` en las reglas aceptadas. */
function lastDeclared(
  element: Element,
  property: string,
  media: string | undefined,
  selects: (selectorText: string) => boolean,
): string {
  let value = '';
  const visit = (rules: CSSRuleList, inMedia: boolean) => {
    for (const rule of Array.from(rules)) {
      if (rule instanceof CSSStyleRule) {
        const declared = rule.style.getPropertyValue(property);
        if (declared && inMedia === Boolean(media) && selects(rule.selectorText)) {
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
 * Separa una lista de selectores por las comas de primer nivel, sin cortar `:is(a, b)`, `[x="a,b"]` ni
 * los caracteres escapados (`.w-1\,5`).
 */
function splitSelectorList(selectorList: string): string[] {
  const selectors: string[] = [];
  let depth = 0;
  let quote = '';
  let current = '';
  let escaped = false;
  for (const char of selectorList) {
    if (escaped) {
      escaped = false;
    } else if (char === '\\') {
      escaped = true;
    } else if (quote) {
      quote = char === quote ? '' : quote;
    } else if (char === '"' || char === "'") {
      quote = char;
    } else if (char === '(' || char === '[') {
      depth++;
    } else if (char === ')' || char === ']') {
      depth = Math.max(0, depth - 1);
    } else if (char === ',' && depth === 0) {
      selectors.push(current.trim());
      current = '';
      continue;
    }
    current += char;
  }
  selectors.push(current.trim());
  return selectors;
}

/**
 * Si el valor calculado de un color usa exactamente la variable `token` (`--color-text-muted`), con o sin
 * valor de reserva. Distingue `--color-action-primary` de `--color-action-primary-subtle`, que un simple
 * `toContain('var(--color-action-primary')` confundiría.
 */
export function usesToken(computedValue: string, token: string): boolean {
  const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`var\\(${escaped}[,)]`).test(computedValue);
}
