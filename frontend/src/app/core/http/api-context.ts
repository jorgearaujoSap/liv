import { HttpContextToken } from '@angular/common/http';

/** La petición ya se reintentó tras un 401: no se reintenta otra vez. */
export const RETRIED_AFTER_UNAUTHORIZED = new HttpContextToken<boolean>(() => false);

/** La petición no dispara la recarga de capacidades ante un 403 (es la propia recarga). */
export const SKIP_FORBIDDEN_HANDLING = new HttpContextToken<boolean>(() => false);
