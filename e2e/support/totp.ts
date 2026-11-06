import { createHmac } from 'node:crypto';

/**
 * Código TOTP de 6 dígitos (RFC 6238, HMAC-SHA1, 30 segundos), el mismo que da una aplicación de autenticación con
 * ese secreto y el que valida Keycloak (política del realm: 6 dígitos cada 30 segundos). Keycloak guarda el secreto
 * tal cual, sin base32.
 */
export function codigoTotp(secreto: string, instante = Date.now()): string {
  const contador = Buffer.alloc(8);
  contador.writeBigUInt64BE(BigInt(Math.floor(instante / 1000 / 30)));
  const mac = createHmac('sha1', Buffer.from(secreto, 'utf8')).update(contador).digest();
  const desplazamiento = mac[mac.length - 1] & 0x0f;
  const numero = (mac.readUInt32BE(desplazamiento) & 0x7fffffff) % 1_000_000;
  return numero.toString().padStart(6, '0');
}
