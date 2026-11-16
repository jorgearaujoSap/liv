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

const CREATED: FiscalYear = {
  year: 2027,
  startsOn: '2027-01-01',
  endsOn: '2027-12-31',
  status: 'PLANEADA',
  assessmentsEnabled: false,
};

const rejected = (status: number, body: Record<string, unknown> | null) =>
  new HttpErrorResponse({ status, error: body });

// 007-04 a 007-08 y 007-21: el diálogo de creación. Diseño: Figma LIV-Diseño, 67:3040 (el diálogo), 67:3163
// (con un rechazo), 67:3284 (los mensajes) y 75:4479 (la etiqueta de un año pasado).
describe('Crear vigencia (007-04 a 007-08)', () => {
  let service: { create: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<CreateFiscalYearDialog>;
  let host: HTMLElement;

  const render = (years: CreatableYear[] = [option(2027, 'NEXT')]) => {
    fixture = TestBed.createComponent(CreateFiscalYearDialog);
    fixture.componentRef.setInput('years', years);
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
  };
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const radios = () => Array.from(host.querySelectorAll<HTMLInputElement>('input[type="radio"]'));
  const checked = () => radios().find((radio) => radio.checked);
  const button = (label: string) =>
    Array.from(host.querySelectorAll('button')).find((candidate) =>
      text(candidate).endsWith(label),
    );
  const submit = async () => {
    button('Crear vigencia')?.click();
    await settle();
  };
  const alerts = () => Array.from(host.querySelectorAll('liv-alert[role="alert"]'));
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

  describe('lo que dice el diálogo', () => {
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

    it('las opciones salen de los años que manda el backend, con su etiqueta (007-04)', () => {
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);

      expect(text(host.querySelector('fieldset legend'))).toBe('Año de la vigencia');
      const labels = Array.from(host.querySelectorAll('liv-radio-option')).map((item) =>
        text(item),
      );
      expect(labels).toEqual(['2026 Año en curso', '2027 Año siguiente al año en curso']);
    });

    it('la etiqueta de un año pasado dice que es anterior al año en curso (75:4479)', () => {
      render([option(2026, 'PAST')]);

      expect(text(host.querySelector('liv-radio-option'))).toBe(
        '2026 Año anterior al año en curso',
      );
    });

    it('un año con una relación que no conoce se muestra sin etiqueta', () => {
      render([option(2026, 'OTRA')]);

      expect(text(host.querySelector('liv-radio-option'))).toBe('2026');
    });

    it('marca el primer año y muestra sus fechas, y las cambia al elegir otro', async () => {
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);

      expect(checked()?.value).toBe('2026');
      expect(text(host)).toContain('Inicio 1 de enero de 2026');
      expect(text(host)).toContain('Fin 31 de diciembre de 2026');

      radios()[1].click();
      await settle();

      expect(checked()?.value).toBe('2027');
      expect(text(host)).toContain('Inicio 1 de enero de 2027');
      expect(text(host)).toContain('Fin 31 de diciembre de 2027');
    });

    it('advierte que una vigencia no se puede borrar y que los años anteriores quedan históricos (007-04)', () => {
      render();

      expect(text(host)).toContain('Una vigencia no se puede borrar');
      expect(text(host)).toContain(
        'Una vez creada, los años anteriores a la más antigua quedan como históricos y no se podrán crear. Revisa el año antes de continuar.',
      );
    });

    it('el grupo de años siempre tiene una opción marcada mientras está abierto', async () => {
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);
      expect(checked()).toBeDefined();

      fixture.componentRef.setInput('years', [option(2028, 'NEXT')]);
      await settle();

      expect(checked()?.value).toBe('2028');
    });

    it('si el año marcado sigue disponible, se queda marcado cuando cambia la lista', async () => {
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);
      radios()[1].click();
      await settle();

      fixture.componentRef.setInput('years', [option(2026, 'CURRENT'), option(2027, 'NEXT')]);
      await settle();

      expect(checked()?.value).toBe('2027');
    });
  });

  // Riesgo 6 de la spec: con el contrato 1.1.0 la lista llega del año más reciente al más antiguo y el diálogo
  // nunca debe marcar por omisión un año `PAST`.
  describe('el año marcado nunca es un año pasado (riesgo 6)', () => {
    it('con el orden del contrato 1.1.0 (siguiente, en curso, pasados) queda marcado el año en curso', () => {
      render([
        option(2027, 'NEXT'),
        option(2026, 'CURRENT'),
        option(2025, 'PAST'),
        option(2024, 'PAST'),
      ]);

      expect(checked()?.value).toBe('2026');
      expect(text(host)).toContain('Inicio 1 de enero de 2026');
    });

    it('con el orden del contrato 1.0.0 (en curso, siguiente) queda marcado el año en curso', () => {
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);

      expect(checked()?.value).toBe('2026');
    });

    it('sin año en curso, con el siguiente y años pasados, queda marcado el siguiente', () => {
      render([option(2027, 'NEXT'), option(2025, 'PAST'), option(2024, 'PAST')]);

      expect(checked()?.value).toBe('2027');
    });

    it('no marca un año pasado por estar primero en la lista', () => {
      render([option(2025, 'PAST'), option(2027, 'NEXT')]);

      expect(checked()?.value).toBe('2027');
    });

    it('con solo años pasados no marca ninguno: «Crear vigencia» queda inhabilitado y no se muestran fechas', () => {
      render([option(2025, 'PAST'), option(2024, 'PAST')]);

      expect(checked()).toBeUndefined();
      expect(button('Crear vigencia')?.disabled).toBe(true);
      expect(text(host)).not.toContain('Inicio 1 de enero');
      expect(text(host)).not.toContain('Fin 31 de diciembre');
    });

    it('con solo años pasados, el foco inicial va al grupo y no a «Crear vigencia»', () => {
      render([option(2025, 'PAST'), option(2024, 'PAST')]);

      expect(document.activeElement).toBe(radios()[0]);
      expect(document.activeElement).not.toBe(button('Crear vigencia'));
    });

    it('con solo años pasados, enviar no pide nada', async () => {
      render([option(2025, 'PAST')]);

      await submit();

      expect(service.create).not.toHaveBeenCalled();
    });

    it('una relación que no conoce no se marca por omisión', () => {
      render([option(2026, 'OTRA'), option(2027, 'NEXT')]);

      expect(checked()?.value).toBe('2027');
    });

    it('si la persona marcó un año pasado y sigue disponible, se queda marcado', async () => {
      render([option(2026, 'CURRENT'), option(2025, 'PAST')]);
      radios()[1].click();
      await settle();

      fixture.componentRef.setInput('years', [option(2026, 'CURRENT'), option(2025, 'PAST')]);
      await settle();

      expect(checked()?.value).toBe('2025');
    });

    it('si el año marcado ya no está, pasa al año en curso y no a un año pasado', async () => {
      render([option(2027, 'NEXT'), option(2026, 'CURRENT'), option(2025, 'PAST')]);
      radios()
        .find((radio) => radio.value === '2025')
        ?.click();
      await settle();
      expect(checked()?.value).toBe('2025');

      fixture.componentRef.setInput('years', [
        option(2027, 'NEXT'),
        option(2024, 'PAST'),
        option(2026, 'CURRENT'),
      ]);
      await settle();

      expect(checked()?.value).toBe('2026');
    });

    it('si el año marcado ya no está y solo quedan el siguiente y años pasados, pasa al siguiente', async () => {
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);

      fixture.componentRef.setInput('years', [option(2025, 'PAST'), option(2027, 'NEXT')]);
      await settle();

      expect(checked()?.value).toBe('2027');
    });

    it('si el año marcado ya no está y solo quedan años pasados, no queda ninguno marcado', async () => {
      render([option(2026, 'CURRENT'), option(2025, 'PAST')]);

      fixture.componentRef.setInput('years', [option(2025, 'PAST'), option(2024, 'PAST')]);
      await settle();

      expect(checked()).toBeUndefined();
      expect(button('Crear vigencia')?.disabled).toBe(true);
    });

    it('V-01 · 007-05: al releer la lista, solo con el siguiente, queda marcado el siguiente', async () => {
      service.create.mockRejectedValue(rejected(409, { status: 409, code: 'V-01', year: 2026 }));
      render([option(2026, 'CURRENT'), option(2027, 'NEXT'), option(2025, 'PAST')]);

      await submit();
      fixture.componentRef.setInput('years', [option(2027, 'NEXT')]);
      await settle();

      expect(checked()?.value).toBe('2027');
      expect(document.activeElement).toBe(checked());
    });

    it('V-01 · 007-05: al releer la lista, con el siguiente y años pasados, queda marcado el siguiente', async () => {
      service.create.mockRejectedValue(rejected(409, { status: 409, code: 'V-01', year: 2026 }));
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);

      await submit();
      fixture.componentRef.setInput('years', [option(2025, 'PAST'), option(2027, 'NEXT')]);
      await settle();

      expect(checked()?.value).toBe('2027');
    });
  });

  describe('el foco al releer sin ningún año marcado (riesgo 6 y V-01 · 007-05)', () => {
    it('el foco va al primer radio del grupo y no se queda en «Crear vigencia»', async () => {
      service.create.mockRejectedValue(rejected(409, { status: 409, code: 'V-01', year: 2026 }));
      render([option(2026, 'CURRENT'), option(2025, 'PAST')]);

      await submit();
      // Tras el rechazo, el foco queda en «Crear vigencia»: es de donde la relectura lo tiene que sacar.
      button('Crear vigencia')?.focus();
      // La lista nueva ya no trae el año en curso ni el siguiente: no hay año que marcar.
      fixture.componentRef.setInput('years', [option(2025, 'PAST'), option(2024, 'PAST')]);
      await settle();

      expect(checked()).toBeUndefined();
      expect(button('Crear vigencia')?.disabled).toBe(true);
      expect(document.activeElement).toBe(radios()[0]);
    });
  });

  describe('crear (007-04)', () => {
    it('pide la creación del año marcado, como número, y avisa con la vigencia creada', async () => {
      service.create.mockResolvedValue(CREATED);
      render([option(2027, 'NEXT')]);
      const created = vi.fn();
      fixture.componentInstance.created.subscribe(created);

      await submit();

      expect(service.create).toHaveBeenCalledWith(2027);
      expect(created).toHaveBeenCalledWith(CREATED);
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

  describe('rechazos (007-05 a 007-08)', () => {
    it.each([
      [
        'V-01 · 007-05',
        rejected(409, { status: 409, code: 'V-01', year: 2027 }),
        'V-01 · La vigencia 2027 ya existe en tu administración: es posible que otra persona la haya creado hace un momento. Actualizamos la lista.',
      ],
      [
        'V-02 GAP · 007-06',
        rejected(422, { status: 422, code: 'V-02', reason: 'GAP', requiredYear: 2027 }),
        'V-02 · No se puede saltar un año: primero debe existir la vigencia 2027.',
      ],
      [
        'V-02 HISTORICAL · 007-07',
        rejected(422, { status: 422, code: 'V-02', reason: 'HISTORICAL', oldestYear: 2026 }),
        'V-02 · No se pueden crear vigencias anteriores a la más antigua (2026): la carga de vigencias históricas todavía no está autorizada.',
      ],
      [
        'V-05 FIRST_YEAR · 007-08',
        rejected(422, {
          status: 422,
          code: 'V-05',
          reason: 'FIRST_YEAR',
          currentYear: 2026,
          nextYear: 2027,
        }),
        'V-05 · La primera vigencia de la administración debe ser la del año en curso (2026) o la del año siguiente (2027).',
      ],
      [
        'V-05 FUTURE_LIMIT',
        rejected(422, { status: 422, code: 'V-05', reason: 'FUTURE_LIMIT', maxYear: 2027 }),
        'V-05 · No se puede crear una vigencia posterior al año siguiente al año en curso (2027).',
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

    it('la alerta va arriba del grupo de años, como en Figma (67:3163)', async () => {
      service.create.mockRejectedValue(rejected(409, { status: 409, code: 'V-01', year: 2027 }));
      render();

      await submit();

      const alert = alerts()[0];
      const group = host.querySelector('fieldset') as HTMLElement;
      expect(alert.compareDocumentPosition(group) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('se crea de nuevo en cada intento, para que se anuncie aunque el texto se repita', async () => {
      service.create.mockRejectedValue(rejected(409, { status: 409, code: 'V-01', year: 2027 }));
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
      service.create.mockRejectedValueOnce(
        rejected(409, { status: 409, code: 'V-01', year: 2027 }),
      );
      service.create.mockResolvedValueOnce(CREATED);
      render();

      await submit();
      expect(alerts()).toHaveLength(1);
      await submit();

      expect(alerts()).toHaveLength(0);
    });

    it('un V-01 pide a la pantalla que vuelva a leer la lista, sin cerrar el diálogo', async () => {
      service.create.mockRejectedValue(rejected(409, { status: 409, code: 'V-01', year: 2027 }));
      render([option(2027, 'NEXT'), option(2028, 'NEXT')]);
      const stale = vi.fn();
      const closed = vi.fn();
      fixture.componentInstance.staleList.subscribe(stale);
      fixture.componentInstance.closed.subscribe(closed);

      await submit();

      expect(stale).toHaveBeenCalledTimes(1);
      expect(stale).toHaveBeenCalledWith(expect.objectContaining({ year: 2027, unknown: false }));
      expect(closed).not.toHaveBeenCalled();
    });

    it('tras un V-01 y la lista nueva, queda marcado el primer año que siga disponible', async () => {
      service.create.mockRejectedValue(rejected(409, { status: 409, code: 'V-01', year: 2027 }));
      render([option(2027, 'NEXT'), option(2028, 'NEXT')]);
      expect(checked()?.value).toBe('2027');

      await submit();
      fixture.componentRef.setInput('years', [option(2028, 'NEXT')]);
      await settle();

      expect(checked()?.value).toBe('2028');
      expect(alerts()).toHaveLength(1);
    });

    it('otros rechazos no piden volver a leer la lista', async () => {
      service.create.mockRejectedValue(
        rejected(422, { status: 422, code: 'V-02', reason: 'GAP', requiredYear: 2027 }),
      );
      render();
      const stale = vi.fn();
      fixture.componentInstance.staleList.subscribe(stale);

      await submit();

      expect(stale).not.toHaveBeenCalled();
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
      expect(stale).toHaveBeenCalledWith(expect.objectContaining({ year: 2027, unknown: true }));
    });
  });

  describe('estado del diálogo', () => {
    it('al abrir, el foco va al año marcado y no a la acción que no se puede deshacer', () => {
      render([option(2026, 'CURRENT'), option(2027, 'NEXT')]);

      expect(document.activeElement).toBe(checked());
    });

    it('un segundo envío mientras se envía no repite la petición, aunque el clic llegue', async () => {
      service.create.mockReturnValue(new Promise<FiscalYear>(() => undefined));
      render();
      const dialog = fixture.componentInstance as unknown as { submit(): Promise<void> };

      void dialog.submit();
      void dialog.submit();

      expect(service.create).toHaveBeenCalledTimes(1);
    });

    it('sin ningún año que elegir, la acción está inhabilitada y no pide nada', async () => {
      render([]);
      const dialog = fixture.componentInstance as unknown as { submit(): Promise<void> };

      await dialog.submit();

      expect(button('Crear vigencia')?.disabled).toBe(true);
      expect(service.create).not.toHaveBeenCalled();
    });

    it('mientras la pantalla relee la lista, la acción está inhabilitada', async () => {
      render([option(2027, 'NEXT')]);

      fixture.componentRef.setInput('refreshing', true);
      await settle();
      expect(button('Crear vigencia')?.getAttribute('aria-disabled')).toBe('true');

      fixture.componentRef.setInput('refreshing', false);
      await settle();
      expect(button('Crear vigencia')?.getAttribute('aria-disabled')).not.toBe('true');
    });

    it('una relación que coincide con una propiedad de Object se muestra sin etiqueta', () => {
      render([option(2026, 'constructor')]);

      expect(text(host.querySelector('liv-radio-option'))).toBe('2026');
    });

    it('tras un V-01 y la lista nueva, el foco va al año marcado y no queda en la acción de crear', async () => {
      service.create.mockRejectedValue(rejected(409, { status: 409, code: 'V-01', year: 2027 }));
      render([option(2027, 'NEXT'), option(2028, 'NEXT')]);

      await submit();
      fixture.componentRef.setInput('years', [option(2028, 'NEXT')]);
      await settle();

      expect(checked()?.value).toBe('2028');
      expect(document.activeElement).toBe(checked());
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

    it('«Cancelar» está inhabilitado mientras se envía', async () => {
      service.create.mockReturnValue(new Promise<FiscalYear>(() => undefined));
      render();

      button('Crear vigencia')?.click();
      fixture.detectChanges();

      expect(button('Cancelar')?.disabled).toBe(true);
    });
  });
});
