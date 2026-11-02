import { contrastRatio, shadowColors, solidColor, visibleColor } from './contrast';

// Valores de referencia conocidos, para que un error en la fórmula no ponga en verde las pruebas de theme.
describe('contrast (ayudante de pruebas)', () => {
  const white = solidColor('#ffffff');

  it('da 21:1 entre negro y blanco, y 1:1 entre colores iguales', () => {
    expect(contrastRatio(solidColor('#000000'), white)).toBeCloseTo(21, 5);
    expect(contrastRatio(white, white)).toBe(1);
  });

  it('da el mismo valor en los dos órdenes', () => {
    const gray = solidColor('#767676');
    expect(contrastRatio(gray, white)).toBeCloseTo(4.54, 2);
    expect(contrastRatio(white, gray)).toBeCloseTo(contrastRatio(gray, white), 10);
  });

  it('mezcla la transparencia con el fondo', () => {
    expect(visibleColor('rgb(37 99 235 / 0.4)', white).map(Math.round)).toEqual([168, 193, 247]);
    expect(visibleColor('rgb(37 99 235)', white)).toEqual([37, 99, 235]);
  });

  it('rechaza los formatos que no sabe leer en lugar de adivinar', () => {
    expect(() => solidColor('#fff')).toThrow();
    expect(() => visibleColor('rgba(0, 0, 0, 0.5)', white)).toThrow();
    expect(() => shadowColors('0 0 0 3px var(--x)')).toThrow();
  });

  it('separa las capas de una sombra sin cortar rgb()', () => {
    expect(shadowColors('0 0 0 2px #ffffff, 0 0 0 4px rgb(37 99 235 / 0.4)')).toEqual([
      '#ffffff',
      'rgb(37 99 235 / 0.4)',
    ]);
  });
});
