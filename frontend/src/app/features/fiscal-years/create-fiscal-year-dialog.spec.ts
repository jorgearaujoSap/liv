import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import type { CreatableYear, FiscalYear } from '../../api/tax/models';
import { TimeoutError } from '../../core/http/with-timeout';
import { CreateFiscalYearDialog } from './create-fiscal-year-dialog';
import { FiscalYearsService } from './fiscal-years.service';

/** El texto visible de un elemento: cada nodo de texto por separado, para que los bloques no se peguen. */
const text = (element: Element | null | undefined): string => {
  if (!element) {
    return '';
  }
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  const parts: string[] = [];
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const part = node.textContent?.replace(/\s+/g, ' ').trim();
    if (part) {
      parts.push(part);
    }
  }
  return parts.join(' ');
};

const option = (year: number, relation: string): CreatableYear => ({
  year,
  startsOn: `${year}-01-01`,
  endsOn: `${year}-12-31`,
  relation,
});

/** Lo que manda el contrato 1.1.0 en 2026: del año más reciente al más antiguo. */
const FROM_2027_TO_2024 = [
  option(2027, 'NEXT'),
  option(2026, 'CURRENT'),
  option(2025, 'PAST'),
  option(2024, 'PAST'),
];

const CREATED: FiscalYear = {
  year: 2026,
  startsOn: '2026-01-01',
  endsOn: '2026-12-31',
  status: 'PLANEADA',
  assessmentsEnabled: false,
};

const rejected = (status: number, body: Record<string, unknown> | null) =>
  new HttpErrorResponse({ status, error: body });

const v01 = (year: number) => rejected(409, { status: 409, code: 'V-01', year });

// 007-04 a 007-08, 007-21 y 007-22: el diálogo de creación con su lista desplegable. Diseño: Figma LIV-Diseño,
// 67:3040 (el diálogo), 82:5802 (un año pasado), 67:3163 (con un rechazo) y 67:3284 (los mensajes).
describe('Crear vigencia (007-04 a 007-08, 007-22)', () => {
  let service: { create: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<CreateFiscalYearDialog>;
  let host: HTMLElement;

  const render = (years: CreatableYear[] = [option(2027, 'NEXT'), option(2026, 'CURRENT')]) => {
    fixture = TestBed.createComponent(CreateFiscalYearDialog);
    fixture.componentRef.setInput('years', years);
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
  };
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const select = () => host.querySelector<HTMLSelectElement>('liv-select select');
  /** El año elegido, o `undefined` si la lista muestra «Elige un año». */
  const chosen = () => select()?.value || undefined;
  const optionLabels = () =>
    Array.from(select()?.options ?? []).map((candidate) => text(candidate));
  const choose = async (year: number) => {
    const control = select() as HTMLSelectElement;
    control.value = String(year);
    control.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
  };
  const button = (label: string) =>
    Array.from(host.querySelectorAll('button')).find((candidate) =>
      text(candidate).endsWith(label),
    );
  const submit = async () => {
    button('Crear vigencia')?.click();
    await settle();
  };
  const alerts = () => Array.from(host.querySelectorAll('liv-alert[role="alert"]'));
  const warnings = () => Array.from(host.querySelectorAll('liv-alert[status="warning"]'));
  /** Lo que hace la pantalla tras un rechazo que pide releer: inhabilita el diálogo, lee y lo habilita. */
  const reread = async (years: CreatableYear[]) => {
    fixture.componentRef.setInput('refreshing', true);
    await settle();
    fixture.componentRef.setInput('years', years);
    fixture.componentRef.setInput('refreshing', false);
    await settle();
  };
  const pressEscape = () => {
    (host.querySelector('[role="dialog"]') ?? document.body).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    );
    fixture.detectChanges();
  };

  beforeEach(() => {
    service = { create: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: FiscalYearsService, useValue: service }],
    });
  });

  describe('lo que dice el diálogo (007-04, 007-22)', () => {
    it('es un diálogo con título, descripción y las dos acciones', () => {
      render();

      const dialog = host.querySelector('[role="dialog"]');
      expect(dialog?.getAttribute('aria-modal')).toBe('true');
      expect(text(host.querySelector('[role="dialog"] h2, [role="dialog"] h1'))).toBe(
        'Crear vigencia',
      );
      expect(text(host)).toContain(
        'Elige el año de la vigencia nueva. Va del 1 de enero al 31 de diciembre de ese año.',
      );
      expect(button('Cancelar')).toBeDefined();
      expect(button('Crear vigencia')).toBeDefined();
    });

    it('el año se elige en una lista desplegable con la etiqueta «Año de la vigencia», sin radios', () => {
      render();

      expect(host.querySelectorAll('liv-select')).toHaveLength(1);
      expect(text(host.querySelector('liv-select label'))).toBe('Año de la vigencia');
      expect(host.querySelector('input[type="radio"], fieldset')).toBeNull();
    });

    it('ofrece los años en el orden del servidor, con «Elige un año» primero y la etiqueta de cada uno', () => {
      render(FROM_2027_TO_2024);

      expect(optionLabels()).toEqual([
        'Elige un año',
        '2027 · Año siguiente al año en curso',
        '2026 · Año en curso',
        '2025',
        '2024',
      ]);
    });

    it('«Elige un año» no se puede elegir', () => {
      render();

      const placeholder = select()?.options[0] as HTMLOptionElement;
      expect(placeholder.value).toBe('');
      expect(placeholder.disabled).toBe(true);
    });

    it('un año pasado solo muestra su número: no hay etiqueta de «año anterior al año en curso» (E4-4)', () => {
      render([option(2026, 'PAST')]);

      expect(optionLabels()).toEqual(['Elige un año', '2026']);
    });

    it('una relación que no conoce, o que coincide con una propiedad de Object, se muestra sin etiqueta', () => {
      render([option(2026, 'OTRA'), option(2025, 'constructor')]);

      expect(optionLabels()).toEqual(['Elige un año', '2026', '2025']);
    });

    it('con 26 años ofrece los 26, sin cortar la lista', () => {
      const years = [option(2027, 'NEXT'), option(2026, 'CURRENT')];
      for (let year = 2025; year >= 2002; year--) {
        years.push(option(year, 'PAST'));
      }
      render(years);

      expect(optionLabels()).toHaveLength(27);
      expect(optionLabels().at(-1)).toBe('2002');
    });

    it('muestra el inicio y el fin del año elegido, y los cambia al elegir otro', async () => {
      render(FROM_2027_TO_2024);

      expect(text(host)).toContain('Inicio 1 de enero de 2026');
      expect(text(host)).toContain('Fin 31 de diciembre de 2026');

      await choose(2027);

      expect(text(host)).toContain('Inicio 1 de enero de 2027');
      expect(text(host)).toContain('Fin 31 de diciembre de 2027');
    });

    it('advierte que una vigencia no se puede borrar, sin hablar de años históricos (67:3040)', () => {
      render();

      expect(warnings()).toHaveLength(1);
      expect(text(warnings()[0])).toBe(
        'Una vigencia no se puede borrar Revisa el año antes de continuar.',
      );
      expect(text(host)).not.toContain('históricos');
    });

    it('con un año pasado elegido, una sola advertencia, «Es un año pasado», que reemplaza a la general (82:5802)', async () => {
      render(FROM_2027_TO_2024);

      await choose(2025);

      expect(warnings()).toHaveLength(1);
      expect(text(warnings()[0])).toBe(
        'Es un año pasado Una vigencia no se puede borrar. Revisa el año antes de continuar. Solo se podrá liquidar cuando estén cargadas las tablas de ese año.',
      );
      expect(warnings()[0].querySelectorAll('p:not(.alert__title)')).toHaveLength(2);
    });

    it('vuelve a la advertencia general al elegir de nuevo un año que no es pasado', async () => {
      render(FROM_2027_TO_2024);
      await choose(2025);

      await choose(2027);

      expect(warnings()).toHaveLength(1);
      expect(text(warnings()[0])).toBe(
        'Una vigencia no se puede borrar Revisa el año antes de continuar.',
      );
    });

    it('«año pasado» lo dice la relación que manda el servidor, no un cálculo de fechas', async () => {
      render([option(2026, 'OTRA'), option(1999, 'NEXT')]);

      await choose(1999);

      expect(text(warnings()[0])).toBe(
        'Una vigencia no se puede borrar Revisa el año antes de continuar.',
      );
    });
  });

  // 007-22 y riesgo 6 de la spec: ningún año pasado viene elegido, y solo el año en curso.
  describe('el año elegido al abrir (007-22, E4-3)', () => {
    it('si el año en curso se puede crear, viene elegido, y con él sus fechas', () => {
      render(FROM_2027_TO_2024);

      expect(chosen()).toBe('2026');
      expect(text(host)).toContain('Inicio 1 de enero de 2026');
      expect(button('Crear vigencia')?.disabled).toBe(false);
    });

    it('no depende del orden de la lista: el orden del contrato 1.0.0 (en curso, siguiente) da lo mismo', () => {
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);

      expect(chosen()).toBe('2026');
    });

    it('sin año en curso, ninguno viene elegido, aunque haya el siguiente', () => {
      render([option(2027, 'NEXT'), option(2025, 'PAST'), option(2024, 'PAST')]);

      expect(chosen()).toBeUndefined();
      expect(text(select()?.selectedOptions[0])).toBe('Elige un año');
    });

    it('con solo años pasados, ninguno viene elegido, ni siquiera el primero de la lista', () => {
      render([option(2025, 'PAST'), option(2024, 'PAST')]);

      expect(chosen()).toBeUndefined();
    });

    it('una relación que no conoce no se elige por omisión', () => {
      render([option(2026, 'OTRA'), option(2027, 'NEXT')]);

      expect(chosen()).toBeUndefined();
    });

    it('sin año elegido, «Crear vigencia» está inhabilitado, no hay fechas y enviar no pide nada', async () => {
      render([option(2027, 'NEXT'), option(2025, 'PAST')]);

      expect(button('Crear vigencia')?.disabled).toBe(true);
      expect(text(host)).not.toContain('Inicio');
      expect(text(host)).not.toContain('Fin 31');
      const dialog = fixture.componentInstance as unknown as { submit(): Promise<void> };

      await dialog.submit();

      expect(service.create).not.toHaveBeenCalled();
    });

    it('sin ningún año que ofrecer, solo queda «Elige un año» y «Crear vigencia» está inhabilitado', () => {
      render([]);

      expect(optionLabels()).toEqual(['Elige un año']);
      expect(button('Crear vigencia')?.disabled).toBe(true);
    });

    it('el foco inicial va a la lista, nunca a «Crear vigencia», con año elegido y sin él', () => {
      render(FROM_2027_TO_2024);
      expect(document.activeElement).toBe(select());
      expect(document.activeElement).not.toBe(button('Crear vigencia'));
      fixture.destroy();

      render([option(2027, 'NEXT')]);
      expect(document.activeElement).toBe(select());
    });

    it('si la persona eligió un año y la lista cambia sin un rechazo, se queda elegido mientras siga disponible', async () => {
      render(FROM_2027_TO_2024);
      await choose(2025);

      fixture.componentRef.setInput('years', [...FROM_2027_TO_2024]);
      await settle();

      expect(chosen()).toBe('2025');
    });

    it('si ese año ya no está, vuelve a la regla de apertura: el año en curso, o ninguno', async () => {
      render(FROM_2027_TO_2024);
      await choose(2025);

      fixture.componentRef.setInput('years', [
        option(2027, 'NEXT'),
        option(2026, 'CURRENT'),
        option(2024, 'PAST'),
      ]);
      await settle();
      expect(chosen()).toBe('2026');

      await choose(2024);
      fixture.componentRef.setInput('years', [option(2027, 'NEXT'), option(2025, 'PAST')]);
      await settle();
      expect(chosen()).toBeUndefined();
    });
  });

  describe('después de un V-01 (007-05, 67:3163)', () => {
    it('al releer la lista sin el año en curso, no queda ningún año elegido: «Elige un año» y «Crear vigencia» inhabilitado', async () => {
      service.create.mockRejectedValue(v01(2026));
      render(FROM_2027_TO_2024);

      await submit();
      await reread([option(2027, 'NEXT'), option(2025, 'PAST'), option(2024, 'PAST')]);

      expect(chosen()).toBeUndefined();
      expect(button('Crear vigencia')?.disabled).toBe(true);
      expect(text(host)).not.toContain('Inicio');
      expect(alerts()).toHaveLength(1);
    });

    it('la regla es la de abrir: aunque se haya elegido un año pasado que sigue disponible, queda el año en curso', async () => {
      service.create.mockRejectedValue(v01(2025));
      render(FROM_2027_TO_2024);
      await choose(2025);

      await submit();
      await reread([option(2027, 'NEXT'), option(2026, 'CURRENT'), option(2025, 'PAST')]);

      expect(chosen()).toBe('2026');
    });

    it('si el año en curso sigue disponible tras un V-01 de otro año, queda el año en curso', async () => {
      service.create.mockRejectedValue(v01(2027));
      render(FROM_2027_TO_2024);
      await choose(2027);

      await submit();
      await reread([option(2026, 'CURRENT'), option(2025, 'PAST')]);

      expect(chosen()).toBe('2026');
    });

    it('el foco vuelve a la lista cuando termina la relectura, también sin año elegido, y no se queda en «Crear vigencia»', async () => {
      service.create.mockRejectedValue(v01(2026));
      render(FROM_2027_TO_2024);

      await submit();
      button('Crear vigencia')?.focus();
      await reread([option(2027, 'NEXT'), option(2025, 'PAST')]);

      expect(document.activeElement).toBe(select());
    });

    it('pide a la pantalla que vuelva a leer la lista, sin cerrar el diálogo', async () => {
      service.create.mockRejectedValue(v01(2026));
      render(FROM_2027_TO_2024);
      const stale = vi.fn();
      const closed = vi.fn();
      fixture.componentInstance.staleList.subscribe(stale);
      fixture.componentInstance.closed.subscribe(closed);

      await submit();

      expect(stale).toHaveBeenCalledTimes(1);
      expect(stale).toHaveBeenCalledWith(expect.objectContaining({ year: 2026, unknown: false }));
      expect(closed).not.toHaveBeenCalled();
    });
  });

  // H2-18: después de un resultado que no se conoce, el diálogo no cambia por su cuenta el año que se intentó.
  describe('después de un resultado que no se conoce (H2-18, 007-22)', () => {
    const unknown = [
      ['un tiempo agotado', () => new TimeoutError(10_000)],
      ['sin respuesta (estado 0)', () => rejected(0, null)],
      ['un error del servidor (503)', () => rejected(503, { status: 503 })],
    ] as const;

    it.each(unknown)(
      '%s: al releer, sigue elegido el año intentado si todavía está en la lista',
      async (_name, error) => {
        service.create.mockRejectedValue(error());
        render(FROM_2027_TO_2024);
        await choose(2025);

        await submit();
        await reread([...FROM_2027_TO_2024]);

        expect(chosen()).toBe('2025');
        expect(text(host)).toContain('Inicio 1 de enero de 2025');
        expect(text(warnings()[0])).toContain('Es un año pasado');
      },
    );

    it.each(unknown)(
      '%s: si el año intentado ya no está, no queda ninguno elegido, ni el año en curso',
      async (_name, error) => {
        service.create.mockRejectedValue(error());
        render(FROM_2027_TO_2024);
        await choose(2025);

        await submit();
        await reread([option(2027, 'NEXT'), option(2026, 'CURRENT'), option(2024, 'PAST')]);

        expect(chosen()).toBeUndefined();
        expect(button('Crear vigencia')?.disabled).toBe(true);
      },
    );

    it('con el año en curso intentado y todavía disponible, sigue elegido', async () => {
      service.create.mockRejectedValue(new TimeoutError(10_000));
      render(FROM_2027_TO_2024);

      await submit();
      await reread([...FROM_2027_TO_2024]);

      expect(chosen()).toBe('2026');
    });

    it('si la relectura falla, se conserva el año intentado y la lista vuelve a estar habilitada, con el foco en ella', async () => {
      service.create.mockRejectedValue(new TimeoutError(10_000));
      render(FROM_2027_TO_2024);
      await choose(2025);

      await submit();
      fixture.componentRef.setInput('refreshing', true);
      await settle();
      fixture.componentRef.setInput('refreshFailed', true);
      fixture.componentRef.setInput('refreshing', false);
      await settle();

      expect(chosen()).toBe('2025');
      expect(select()?.disabled).toBe(false);
      expect(document.activeElement).toBe(select());
    });

    it('un intento siguiente empieza de cero: un V-01 no conserva el año de un intento anterior', async () => {
      service.create.mockRejectedValueOnce(new TimeoutError(10_000));
      service.create.mockRejectedValueOnce(v01(2026));
      render(FROM_2027_TO_2024);
      await choose(2025);

      await submit();
      await reread([...FROM_2027_TO_2024]);
      await submit();
      await reread([...FROM_2027_TO_2024]);

      expect(chosen()).toBe('2026');
    });
  });

  describe('la advertencia se anuncia cuando cambia (82:5802)', () => {
    it('vive en una región viva que no se recrea al elegir otro año', async () => {
      render(FROM_2027_TO_2024);
      const region = host.querySelector('[aria-live="polite"]');
      expect(region?.querySelector('liv-alert[status="warning"]')).not.toBeNull();

      await choose(2025);

      expect(host.querySelector('[aria-live="polite"]')).toBe(region);
      expect(text(region)).toContain('Es un año pasado');
    });
  });

  describe('lo que pidió un rechazo no pisa una elección posterior (H2-18)', () => {
    it('tras un V-01 con la relectura fallida, el año que la persona elige después se conserva cuando la lista cambia', async () => {
      service.create.mockRejectedValue(v01(2026));
      render(FROM_2027_TO_2024);

      await submit();
      fixture.componentRef.setInput('refreshing', true);
      await settle();
      fixture.componentRef.setInput('refreshFailed', true);
      fixture.componentRef.setInput('refreshing', false);
      await settle();
      await choose(2025);
      fixture.componentRef.setInput('years', [...FROM_2027_TO_2024]);
      await settle();

      expect(chosen()).toBe('2025');
    });
  });

  describe('la lista inhabilitada mientras se envía y se relee (007-22)', () => {
    it('mientras se envía, la lista está inhabilitada, y vuelve a estar habilitada al terminar', async () => {
      let fail: (error: unknown) => void = () => undefined;
      service.create.mockReturnValue(
        new Promise<FiscalYear>((_resolve, reject) => (fail = reject)),
      );
      render();

      button('Crear vigencia')?.click();
      fixture.detectChanges();
      expect(select()?.disabled).toBe(true);

      fail(rejected(400, { status: 400, code: 'SYS-400' }));
      await settle();
      expect(select()?.disabled).toBe(false);
    });

    it('mientras la pantalla relee la lista, la lista y la acción están inhabilitadas', async () => {
      render();

      fixture.componentRef.setInput('refreshing', true);
      await settle();
      expect(select()?.disabled).toBe(true);
      expect(button('Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');

      fixture.componentRef.setInput('refreshing', false);
      await settle();
      expect(select()?.disabled).toBe(false);
      expect(button('Crear vigencia')?.getAttribute('aria-disabled')).not.toBe('true');
    });
  });

  describe('crear (007-04)', () => {
    it('pide la creación del año elegido, como número, y avisa con la vigencia creada', async () => {
      service.create.mockResolvedValue(CREATED);
      render();
      const created = vi.fn();
      fixture.componentInstance.created.subscribe(created);

      await submit();

      expect(service.create).toHaveBeenCalledWith(2026);
      expect(created).toHaveBeenCalledWith(CREATED);
    });

    it('crea el año que la persona eligió, también uno pasado', async () => {
      service.create.mockResolvedValue({ ...CREATED, year: 2024 });
      render(FROM_2027_TO_2024);
      await choose(2024);

      await submit();

      expect(service.create).toHaveBeenCalledWith(2024);
    });

    it('mientras se envía, el botón está cargando y Escape no cierra', async () => {
      let finish: (value: FiscalYear) => void = () => undefined;
      service.create.mockReturnValue(new Promise<FiscalYear>((resolve) => (finish = resolve)));
      render();
      const closed = vi.fn();
      fixture.componentInstance.closed.subscribe(closed);

      button('Crear vigencia')?.click();
      fixture.detectChanges();

      expect(button('Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');
      expect(text(host)).toContain('Creando la vigencia…');
      pressEscape();
      expect(closed).not.toHaveBeenCalled();

      finish(CREATED);
      await settle();
    });

    it('mientras se envía, un segundo clic no repite la petición', async () => {
      service.create.mockReturnValue(new Promise<FiscalYear>(() => undefined));
      render();

      button('Crear vigencia')?.click();
      fixture.detectChanges();
      button('Crear vigencia')?.click();
      fixture.detectChanges();

      expect(service.create).toHaveBeenCalledTimes(1);
    });

    it('«Cancelar» y Escape cierran el diálogo cuando no se está enviando', () => {
      render();
      const closed = vi.fn();
      fixture.componentInstance.closed.subscribe(closed);

      button('Cancelar')?.click();
      pressEscape();

      expect(closed).toHaveBeenCalledTimes(2);
    });
  });

  describe('rechazos (007-05, 007-07)', () => {
    it.each([
      [
        'V-01 · 007-05',
        v01(2027),
        'V-01 · La vigencia 2027 ya existe en tu administración: es posible que otra persona la haya creado hace un momento. Actualizamos la lista.',
      ],
      [
        'V-05 PAST_LIMIT · 007-07',
        rejected(422, { status: 422, code: 'V-05', reason: 'PAST_LIMIT', minYear: 2002 }),
        'V-05 · No se puede crear una vigencia anterior a 2002, el primer año que se puede crear.',
      ],
      [
        'V-05 FUTURE_LIMIT · 007-07',
        rejected(422, { status: 422, code: 'V-05', reason: 'FUTURE_LIMIT', maxYear: 2027 }),
        'V-05 · No se puede crear una vigencia posterior al año siguiente al año en curso: el último año que se puede crear es 2027.',
      ],
    ])('%s: una alerta de error con el código y los años', async (_name, error, message) => {
      service.create.mockRejectedValue(error);
      render();

      await submit();

      expect(alerts()).toHaveLength(1);
      expect(text(alerts()[0])).toBe(`No se creó la vigencia ${message}`);
      expect(button('Crear vigencia')?.getAttribute('aria-disabled')).not.toBe('true');
      expect(host.querySelector('[role="dialog"]')).not.toBeNull();
    });

    it('la alerta va arriba de la lista de años, como en Figma (67:3163)', async () => {
      service.create.mockRejectedValue(v01(2027));
      render();

      await submit();

      const alert = alerts()[0];
      expect(
        alert.compareDocumentPosition(select() as HTMLElement) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });

    it('se crea de nuevo en cada intento, para que se anuncie aunque el texto se repita', async () => {
      service.create.mockRejectedValue(v01(2027));
      render();

      await submit();
      const first = alerts()[0];
      await submit();
      const second = alerts()[0];

      expect(alerts()).toHaveLength(1);
      expect(text(second)).toBe(text(first));
      expect(second).not.toBe(first);
    });

    it('un intento que sale bien no deja la alerta de uno anterior', async () => {
      service.create.mockRejectedValueOnce(v01(2027));
      service.create.mockResolvedValueOnce(CREATED);
      render();

      await submit();
      expect(alerts()).toHaveLength(1);
      await submit();

      expect(alerts()).toHaveLength(0);
    });

    it('otros rechazos no piden volver a leer la lista', async () => {
      service.create.mockRejectedValue(
        rejected(422, { status: 422, code: 'V-05', reason: 'PAST_LIMIT', minYear: 2002 }),
      );
      render();
      const stale = vi.fn();
      fixture.componentInstance.staleList.subscribe(stale);

      await submit();

      expect(stale).not.toHaveBeenCalled();
    });

    it('un motivo que el servidor ya no emite (V-02 GAP) dice el mensaje general y deja reintentar', async () => {
      service.create.mockRejectedValue(
        rejected(422, { status: 422, code: 'V-02', reason: 'GAP', requiredYear: 2027 }),
      );
      render();

      await submit();

      expect(text(alerts()[0])).toBe(
        'No se creó la vigencia No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
      );
      expect(button('Crear vigencia')?.getAttribute('aria-disabled')).not.toBe('true');
    });

    it('un error de la petición dice que no se creó y deja reintentar', async () => {
      service.create.mockRejectedValue(rejected(400, { status: 400, code: 'SYS-400' }));
      render();

      await submit();

      expect(text(alerts()[0])).toBe(
        'No se creó la vigencia No pudimos crear la vigencia. Intenta de nuevo; si continúa, avisa al administrador del sistema.',
      );
      expect(button('Crear vigencia')?.getAttribute('aria-disabled')).not.toBe('true');
    });

    it('un 403 de permiso no muestra un mensaje propio: lo atiende el interceptor, que lleva a «Sin permiso» (H2-14)', async () => {
      service.create.mockRejectedValue(rejected(403, { status: 403, code: '001-16' }));
      render();

      await submit();

      expect(alerts()).toHaveLength(0);
    });

    it('un 403 anti-CSRF (001-12) no lo atiende el interceptor: se dice que no se creó', async () => {
      service.create.mockRejectedValue(rejected(403, { status: 403, code: '001-12' }));
      render();

      await submit();

      expect(alerts()).toHaveLength(1);
      expect(text(alerts()[0])).toContain('No se creó la vigencia');
    });

    it('un tiempo agotado dice que no se sabe si se creó y pide releer la lista', async () => {
      service.create.mockRejectedValue(new TimeoutError(10_000));
      render();
      const stale = vi.fn();
      fixture.componentInstance.staleList.subscribe(stale);

      await submit();

      expect(text(alerts()[0])).toBe(
        'No sabemos si se creó la vigencia No pudimos confirmar el resultado. Es posible que la vigencia sí se haya creado: revisa la lista antes de intentarlo de nuevo.',
      );
      expect(stale).toHaveBeenCalledTimes(1);
      expect(stale).toHaveBeenCalledWith(expect.objectContaining({ year: 2026, unknown: true }));
    });
  });

  describe('estado del diálogo', () => {
    it('un segundo envío mientras se envía no repite la petición, aunque el clic llegue', () => {
      service.create.mockReturnValue(new Promise<FiscalYear>(() => undefined));
      render();
      const dialog = fixture.componentInstance as unknown as { submit(): Promise<void> };

      void dialog.submit();
      void dialog.submit();

      expect(service.create).toHaveBeenCalledTimes(1);
    });

    it('creada la vigencia, un segundo envío no repite la petición aunque el diálogo siga abierto', async () => {
      service.create.mockResolvedValue(CREATED);
      render();
      const dialog = fixture.componentInstance as unknown as { submit(): Promise<void> };

      await dialog.submit();
      await dialog.submit();

      expect(service.create).toHaveBeenCalledTimes(1);
    });

    it('un rechazo libera el envío: se puede volver a intentar', async () => {
      service.create.mockRejectedValueOnce(rejected(400, { status: 400, code: 'SYS-400' }));
      service.create.mockResolvedValueOnce(CREATED);
      render();

      await submit();
      await submit();

      expect(service.create).toHaveBeenCalledTimes(2);
    });

    it('con la relectura en curso, enviar no pide nada', async () => {
      render();
      fixture.componentRef.setInput('refreshing', true);
      await settle();
      const dialog = fixture.componentInstance as unknown as { submit(): Promise<void> };

      await dialog.submit();

      expect(service.create).not.toHaveBeenCalled();
    });

    it('si la relectura falló, el diálogo lo dice dentro, donde se lee', async () => {
      render();
      expect(text(host)).not.toContain('No pudimos actualizar la lista');

      fixture.componentRef.setInput('refreshFailed', true);
      await settle();

      expect(text(host.querySelector('[role="dialog"]'))).toContain(
        'No pudimos actualizar la lista de vigencias. Los años que se ofrecen pueden no estar al día.',
      );
    });

    it('«Cancelar» está inhabilitado mientras se envía', () => {
      service.create.mockReturnValue(new Promise<FiscalYear>(() => undefined));
      render();

      button('Crear vigencia')?.click();
      fixture.detectChanges();

      expect(button('Cancelar')?.disabled).toBe(true);
    });
  });
});
