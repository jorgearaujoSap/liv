import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { cssVariable, declaredStyle, paintsShadow } from '../../testing/css';
import optionCss from './radio-option.component.css' with { loader: 'text' };
import { RadioGroupComponent } from './radio-group.component';
import { RadioOptionComponent } from './radio-option.component';

interface Option {
  value: string;
  label: string;
  description?: string;
  disabled?: boolean;
}

const CURRENT: Option = { value: '2026', label: '2026', description: 'Año en curso' };
const NEXT: Option = {
  value: '2027',
  label: '2027',
  description: 'Año siguiente al año en curso',
};

@Component({
  imports: [RadioGroupComponent, RadioOptionComponent],
  template: `
    <liv-radio-group
      legend="Año de la vigencia"
      [(value)]="value"
      [disabled]="disabled"
      [name]="name"
    >
      @for (option of options; track option.value) {
        <liv-radio-option
          [value]="option.value"
          [label]="option.label"
          [description]="option.description"
          [disabled]="option.disabled ?? false"
        />
      }
    </liv-radio-group>
  `,
})
class HostComponent {
  value: string | null = null;
  options: Option[] = [CURRENT, NEXT];
  disabled = false;
  name: string | undefined;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const host = fixture.nativeElement.querySelector('liv-radio-group') as HTMLElement;
  const radios = Array.from(host.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
  const options = Array.from(host.querySelectorAll<HTMLElement>('liv-radio-option'));
  const cards = Array.from(host.querySelectorAll<HTMLElement>('.radio-option'));
  return { fixture, host, radios, options, cards, page: fixture.componentInstance };
}

/** Teclado: `:focus-visible` solo se activa tras una tecla, como en el navegador. */
function focusWithKeyboard(element: HTMLElement): void {
  document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
  element.focus();
}

describe('RadioGroup', () => {
  it('es un fieldset cuyo legend, visible, le da el nombre accesible', () => {
    const { host } = render();
    const fieldset = host.querySelector('fieldset') as HTMLFieldSetElement;
    const legend = fieldset.querySelector('legend') as HTMLLegendElement;
    expect(fieldset.firstElementChild).toBe(legend);
    expect(legend.textContent?.trim()).toBe('Año de la vigencia');
    expect(fieldset.contains(host.querySelector('liv-radio-option'))).toBe(true);
  });

  it('cada opción es un radio nativo y todos comparten el mismo name', () => {
    const { radios } = render();
    expect(radios).toHaveLength(2);
    expect(radios[0].name).not.toBe('');
    expect(radios[1].name).toBe(radios[0].name);
    expect(radios.map((radio) => radio.value)).toEqual(['2026', '2027']);
  });

  it('dos grupos no comparten name, para que las flechas no salten de uno a otro', () => {
    expect(render().radios[0].name).not.toBe(render().radios[0].name);
  });

  it('usa el name que se le da', () => {
    expect(render({ name: 'vigencia' }).radios.every((radio) => radio.name === 'vigencia')).toBe(
      true,
    );
  });

  it('sin valor no hay ninguna opción marcada', () => {
    const { radios, options } = render();
    expect(radios.some((radio) => radio.checked)).toBe(false);
    expect(options.map((option) => option.dataset['selected'])).toEqual(['false', 'false']);
  });

  it('marca la opción cuyo value es el del grupo', () => {
    const { radios, options } = render({ value: '2026' });
    expect(radios.map((radio) => radio.checked)).toEqual([true, false]);
    expect(options.map((option) => option.dataset['selected'])).toEqual(['true', 'false']);
  });

  it('enlaza el valor en los dos sentidos', () => {
    const { fixture, radios, page } = render({ value: '2026' });
    radios[1].click();
    fixture.detectChanges();
    expect(page.value).toBe('2027');
    expect(radios.map((radio) => radio.checked)).toEqual([false, true]);

    page.value = '2026';
    fixture.detectChanges();
    expect(radios.map((radio) => radio.checked)).toEqual([true, false]);
  });

  it('elegir una opción cuando no había ninguna marcada también cambia el valor', () => {
    const { fixture, radios, page } = render();
    radios[0].click();
    fixture.detectChanges();
    expect(page.value).toBe('2026');
  });

  describe('Disabled', () => {
    it('un grupo deshabilitado deshabilita el fieldset y todos sus radios', () => {
      const { host, radios, options } = render({ value: '2026', disabled: true });
      expect((host.querySelector('fieldset') as HTMLFieldSetElement).disabled).toBe(true);
      expect(radios.every((radio) => radio.disabled)).toBe(true);
      expect(options.every((option) => option.dataset['state'] === 'disabled')).toBe(true);
    });

    it('una opción deshabilitada no se puede elegir y las demás siguen activas', () => {
      const { fixture, radios, options, page } = render({
        value: '2026',
        options: [CURRENT, { ...NEXT, disabled: true }],
      });
      expect(radios.map((radio) => radio.disabled)).toEqual([false, true]);
      expect(options.map((option) => option.dataset['state'])).toEqual(['default', 'disabled']);
      radios[1].click();
      fixture.detectChanges();
      expect(page.value).toBe('2026');
    });

    it('un grupo deshabilitado no cambia de valor', () => {
      const { fixture, radios, page } = render({ value: '2026', disabled: true });
      radios[1].click();
      fixture.detectChanges();
      expect(page.value).toBe('2026');
    });
  });

  it('una opción fuera de un RadioGroup falla: no tendría name ni valor al que unirse', () => {
    @Component({
      imports: [RadioOptionComponent],
      template: `<liv-radio-option value="a" label="A" />`,
    })
    class Orphan {}
    expect(() => TestBed.createComponent(Orphan).detectChanges()).toThrow();
  });

  it('cumple axe (007-21) en cada variante y estado', async () => {
    const withGroup = [
      {},
      { value: '2026' },
      { value: '2027' },
      { options: [CURRENT] },
      { options: [CURRENT], value: '2026' },
      { options: [{ value: '2026', label: '2026' }] },
      { value: '2026', disabled: true },
      { value: '2026', options: [CURRENT, { ...NEXT, disabled: true }] },
    ];
    for (const inputs of withGroup) {
      await expectNoAxeViolations(render(inputs).host);
    }
  });
});

describe('RadioOption', () => {
  it('el nombre accesible del radio es su etiqueta, sin la descripción', () => {
    const { radios, host } = render();
    const labelledBy = radios[0].getAttribute('aria-labelledby') as string;
    expect(host.querySelector(`#${labelledBy}`)?.textContent?.trim()).toBe('2026');
  });

  it('la descripción se enlaza con aria-describedby; sin descripción no hay enlace', () => {
    const { radios, host } = render({
      options: [CURRENT, { value: '2027', label: '2027' }],
    });
    const describedBy = radios[0].getAttribute('aria-describedby') as string;
    expect(host.querySelector(`#${describedBy}`)?.textContent?.trim()).toBe('Año en curso');
    expect(radios[1].hasAttribute('aria-describedby')).toBe(false);
    expect(host.querySelectorAll('.radio-option__description')).toHaveLength(1);
  });

  it('una descripción vacía cuenta como ausente', () => {
    const { host, radios } = render({ options: [{ ...CURRENT, description: '  ' }] });
    expect(radios[0].hasAttribute('aria-describedby')).toBe(false);
    expect(host.querySelector('.radio-option__description')).toBeNull();
  });

  it('cada opción tiene identificadores únicos', () => {
    const { radios } = render();
    const ids = radios.flatMap((radio) => [
      radio.id,
      radio.getAttribute('aria-labelledby'),
      radio.getAttribute('aria-describedby'),
    ]);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('toda la tarjeta es la etiqueta del radio: un clic en ella lo elige', () => {
    const { fixture, cards, page } = render();
    expect(cards[0].tagName).toBe('LABEL');
    cards[1].click();
    fixture.detectChanges();
    expect(page.value).toBe('2027');
  });

  it('el radio nativo se oculta a la vista pero conserva el foco', () => {
    const { radios } = render();
    const radio = radios[0];
    expect(declaredStyle(radio, 'position')).toBe('absolute');
    expect(declaredStyle(radio, 'width')).toBe('1px');
    radio.focus();
    expect(document.activeElement).toBe(radio);
  });

  it('el círculo es decorativo', () => {
    const { host } = render();
    expect(host.querySelector('.radio-option__circle')?.getAttribute('aria-hidden')).toBe('true');
  });

  describe('Selected × State', () => {
    it('Default sin elegir: fondo bg/surface y borde border/default de 1 px', () => {
      const { cards } = render({ value: '2026' });
      expect(getComputedStyle(cards[1]).backgroundColor).toContain('var(--color-bg-surface');
      expect(declaredStyle(cards[1], 'border-color')).toContain('var(--color-border-default,');
      expect(declaredStyle(cards[1], 'border-width')).toBe('1px');
    });

    it('Default sin elegir: el círculo lleva borde border/strong de 2 px y sin punto', () => {
      const { cards } = render({ value: '2026' });
      const circle = cards[1].querySelector('.radio-option__circle') as HTMLElement;
      expect(declaredStyle(circle, 'border-color')).toContain('var(--color-border-strong,');
      expect(declaredStyle(circle, 'border-width')).toBe('2px');
      expect(declaredStyle(circle, 'width')).toContain('20px');
      expect(optionCss).toContain("[data-selected='true'] .radio-option__circle::after");
    });

    it('Selected: fondo action/primary-subtle, borde action/primary de 2 px y punto interior', () => {
      const { cards } = render({ value: '2026' });
      expect(getComputedStyle(cards[0]).backgroundColor).toContain(
        'var(--color-action-primary-subtle',
      );
      expect(declaredStyle(cards[0], 'border-color')).toContain('var(--color-action-primary,');
      // 1 px de borde más 1 px de anillo interior: 2 px sin mover el contenido.
      expect(declaredStyle(cards[0], 'border-width')).toBe('1px');
      expect(declaredStyle(cards[0], '--tw-inset-ring-shadow')).toContain('1px');
      expect(declaredStyle(cards[0], '--tw-inset-ring-shadow')).toContain(
        'var(--color-action-primary,',
      );
      const circle = cards[0].querySelector('.radio-option__circle') as HTMLElement;
      expect(declaredStyle(circle, 'border-color')).toContain('var(--color-action-primary,');
      expect(optionCss).toMatch(
        /\[data-selected='true'\] \.radio-option__circle::after\s*\{[^}]*bg-action-primary/,
      );
    });

    it('la elegida no depende solo del color: el radio nativo queda marcado', () => {
      const { radios } = render({ value: '2027' });
      expect(radios.map((radio) => radio.checked)).toEqual([false, true]);
    });

    it('Hover sin elegir: fondo bg/subtle; elegida o deshabilitada, no cambia', () => {
      // jsdom no evalúa :hover: se comprueba el texto de las reglas y se revisa en Storybook.
      const hover = optionCss.match(/([^{}]*\.radio-option:hover[^{}]*)\{([^}]*)\}/g) ?? [];
      expect(hover).toHaveLength(1);
      expect(hover[0]).toContain("[data-state='default']");
      expect(hover[0]).toContain("[data-selected='false']");
      expect(hover[0]).toContain('bg-bg-subtle');
    });

    it('Disabled sin elegir: fondo bg/subtle, borde border/default y texto text/disabled', () => {
      const { cards, host } = render({
        value: '2026',
        options: [CURRENT, { ...NEXT, disabled: true }],
      });
      expect(getComputedStyle(cards[1]).backgroundColor).toContain('var(--color-bg-subtle');
      expect(declaredStyle(cards[1], 'border-color')).toContain('var(--color-border-default,');
      const circle = cards[1].querySelector('.radio-option__circle') as HTMLElement;
      expect(declaredStyle(circle, 'border-color')).toContain('var(--color-border-default,');
      const label = host.querySelectorAll('.radio-option__label')[1] as HTMLElement;
      const description = host.querySelectorAll('.radio-option__description')[1] as HTMLElement;
      expect(getComputedStyle(label).color).toContain('var(--color-text-disabled');
      expect(getComputedStyle(description).color).toContain('var(--color-text-disabled');
      expect(declaredStyle(cards[1], 'cursor')).toBe('not-allowed');
    });

    it('Disabled elegida: borde border/default y punto text/disabled, sin azul', () => {
      const { cards } = render({ value: '2026', disabled: true });
      expect(declaredStyle(cards[0], 'border-color')).toContain('var(--color-border-default,');
      expect(declaredStyle(cards[0], '--tw-inset-ring-shadow')).not.toContain(
        'var(--color-action-primary,',
      );
      const circle = cards[0].querySelector('.radio-option__circle') as HTMLElement;
      expect(declaredStyle(circle, 'border-color')).toContain('var(--color-border-default,');
      expect(optionCss).toMatch(
        /\[data-state='disabled'\]\[data-selected='true'\] \.radio-option__circle::after\s*\{[^}]*bg-text-disabled/,
      );
    });

    it('Default: etiqueta text/primary y descripción text/secondary', () => {
      const { host } = render();
      expect(
        getComputedStyle(host.querySelector('.radio-option__label') as Element).color,
      ).toContain('var(--color-text-primary');
      expect(
        getComputedStyle(host.querySelector('.radio-option__description') as Element).color,
      ).toContain('var(--color-text-secondary');
    });

    it('Focus: pinta el anillo focus/ring de theme.css solo con el foco del teclado', () => {
      const { cards, radios } = render({ value: '2026' });
      const ring = cssVariable(theme, '--shadow-focus-ring');
      for (const index of [0, 1]) {
        expect(paintsShadow(cards[index], ring)).toBe(false);
      }
      focusWithKeyboard(radios[1]);
      expect(paintsShadow(cards[1], ring)).toBe(true);
      expect(paintsShadow(cards[0], ring)).toBe(false);
      radios[1].blur();
      expect(paintsShadow(cards[1], ring)).toBe(false);
      focusWithKeyboard(radios[0]);
      expect(paintsShadow(cards[0], ring)).toBe(true);
    });

    it('el foco sigue visible en alto contraste (forced-colors)', () => {
      const { cards, radios } = render({ value: '2026' });
      focusWithKeyboard(radios[0]);
      expect(declaredStyle(cards[0], 'outline', 'forced-colors')).toContain('solid');
      radios[0].blur();
      expect(declaredStyle(cards[0], 'outline', 'forced-colors')).toBe('');
    });
  });

  it('cumple axe (007-21) con y sin descripción, y con textos largos', async () => {
    await expectNoAxeViolations(render({ options: [{ value: 'a', label: 'A' }] }).host);
    await expectNoAxeViolations(
      render({
        value: 'a',
        options: [
          {
            value: 'a',
            label: 'Del 1 de enero al 31 de diciembre de 2027',
            description: 'x '.repeat(80),
          },
        ],
      }).host,
    );
  });
});
