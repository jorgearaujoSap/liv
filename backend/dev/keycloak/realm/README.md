# Realm de desarrollo

Punto de montaje del *realm* de Keycloak en el entorno local (`/opt/keycloak/data/import`). Lo llena la tarea **T07** de la feature 001 con la configuración base de `backend/identity/` más la capa solo de desarrollo (usuarios ficticios y secretos TOTP de prueba).

Keycloak arranca con `--import-realm`: al iniciar importa los archivos `*.json` de esta carpeta. Mientras no haya ninguno, arranca sin *realm* de LIV (solo la consola de administración) y `check.sh` marca la verificación del *realm* como pendiente.
