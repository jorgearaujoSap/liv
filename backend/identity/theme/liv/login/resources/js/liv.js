// Comportamiento del tema liv: seguimiento de la sesión como en la plantilla base, mostrar u ocultar la contraseña
// (IconButton) y el estado Loading del botón principal ("Ingresando…"), que además impide un segundo envío.
// Contrato con las plantillas: el body lleva data-sso-login-in-other-tabs-url y, si hay sesión de autenticación,
// data-auth-session-id-hash (template.ftl); el botón que alterna lleva data-liv-password-toggle y aria-controls; el
// formulario, data-liv-busy; su botón de envío, data-busy-label y un .liv-button__label; el anuncio, data-liv-status;
// el CodeInput, data-liv-code (components.ftl).

// authChecker.js es de la plantilla base de Keycloak; el tema liv la hereda (parent=base). Es un archivo interno de
// Keycloak: se importa aparte para que, si cambiara en otra versión, el resto de este módulo siga funcionando.
const { ssoLoginInOtherTabsUrl, authSessionIdHash } = document.body.dataset;
import('./authChecker.js')
  .then(({ startSessionPolling, checkAuthSession }) => {
    if (ssoLoginInOtherTabsUrl) {
      startSessionPolling(ssoLoginInOtherTabsUrl);
    }
    if (authSessionIdHash) {
      checkAuthSession(authSessionIdHash);
    }
  })
  .catch((error) => console.error('Tema liv: no se pudo cargar authChecker.js', error));

for (const button of document.querySelectorAll('[data-liv-password-toggle]')) {
  const input = document.getElementById(button.getAttribute('aria-controls'));
  button.addEventListener('click', () => {
    const mostrar = input.type === 'password';
    input.type = mostrar ? 'text' : 'password';
    button.toggleAttribute('data-visible', mostrar);
    button.setAttribute('aria-label', mostrar ? button.dataset.labelHide : button.dataset.labelShow);
  });
}

// CodeInput (data-liv-code): solo dígitos, y el código pegado con espacios o guiones ("123 456") entra completo
// aunque el campo tenga maxlength.
for (const input of document.querySelectorAll('input[data-liv-code]')) {
  // Respaldo por si una plantilla omite maxlength: los 6 dígitos de la política TOTP del realm.
  const max = input.maxLength > 0 ? input.maxLength : 6;
  const digits = (text) => text.replace(/\D/g, '').slice(0, max);
  input.addEventListener('paste', (event) => {
    event.preventDefault();
    input.value = digits(event.clipboardData.getData('text'));
  });
  input.addEventListener('input', () => {
    input.value = digits(input.value);
  });
}

const busyForms = document.querySelectorAll('form[data-liv-busy]');
const submitButton = (form) => form.querySelector('button[type="submit"][data-busy-label]');

for (const form of busyForms) {
  form.addEventListener('submit', (event) => {
    const button = submitButton(form);
    if (!button) {
      return;
    }
    if (button.getAttribute('aria-disabled') === 'true') {
      event.preventDefault();
      return;
    }
    // Otro botón del formulario (Cancelar, cancel-aia) envía sin pasar el principal a Loading.
    if (event.submitter && event.submitter !== button) {
      return;
    }
    const label = button.querySelector('.liv-button__label');
    button.dataset.label = label.textContent;
    button.setAttribute('aria-disabled', 'true');
    label.textContent = button.dataset.busyLabel;
    const status = form.querySelector('[data-liv-status]');
    if (status) {
      status.textContent = button.dataset.busyLabel;
    }
  });
}

// Al volver con "atrás", el navegador puede restaurar la página tal como quedó: el botón vuelve a su estado normal.
window.addEventListener('pageshow', (event) => {
  if (!event.persisted) {
    return;
  }
  for (const form of busyForms) {
    const button = submitButton(form);
    if (button?.dataset.label) {
      button.removeAttribute('aria-disabled');
      button.querySelector('.liv-button__label').textContent = button.dataset.label;
      const status = form.querySelector('[data-liv-status]');
      if (status) {
        status.textContent = '';
      }
    }
  }
});
