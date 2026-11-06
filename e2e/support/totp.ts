import { createHmac } from 'node:crypto';

/**
 * Código TOTP de 6 dígitos (RFC 6238, HMAC-SHA1, 30 segundos), el mismo que da una aplicación de autenticación con
 * ese secreto y el que valida Keycloak (política del realm: 6 dígitos cada 30 segundos). Un texto es el secreto tal
 * cual lo guarda Keycloak; los bytes son los de la clave que muestra la página de configurar (`desdeBase32`).
 */
export function codigoTotp(secreto: string | Buffer, instante = Date.now()): string {
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(Math.floor(instante / 1000 / 30)));
  const clave = typeof secreto === 'string' ? Buffer.from(secreto, 'utf8') : secreto;
  const mac = createHmac('sha1', clave).update(contador).digest();
  const desplazamiento = mac[mac.length - 1] & 0x0f;
  const numero = (mac.readUInt32BE(desplazamiento) & 0x7fffffff) % 1_000_000;
  return numero.toString().padStart(6, '0');
}

/** Un código de 6 dígitos que con seguridad no es el que vale ahora: el válido con cada cifra cambiada. */
export function codigoIncorrecto(valido: string): string {
  return [...valido].map((cifra) => String((Number(cifra) + 5) % 10)).join('');
}

/** Bytes de una clave en base32 (RFC 4648), como la muestra Keycloak para escribirla a mano: admite espacios. */
export function desdeBase32(texto: string): Buffer {
  const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const letra of texto.replace(/[\s=]/g, '').toUpperCase()) {
    const valor = alfabeto.indexOf(letra);
    if (valor < 0) {
      throw new Error(`La clave TOTP tiene un carácter que no es base32: ${letra}`);
    }
    bits += valor.toString(2).padStart(5, '0');
  }
  const bytes = bits.match(/.{8}/g) ?? [];
  return Buffer.from(bytes.map((byte) => parseInt(byte, 2)));
}
