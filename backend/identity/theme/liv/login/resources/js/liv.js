// Comportamiento del tema liv: seguimiento de la sesión como en la plantilla base, mostrar u ocultar la contraseña
// (IconButton) y el estado Loading del botón principal ("Ingresando…"), que además impide un segundo envío.
// Contrato con las plantillas: el body lleva data-sso-login-in-other-tabs-url y, si hay sesión de autenticación,
// data-auth-session-id-hash (template.ftl); el botón que alterna lleva data-liv-password-toggle y aria-controls; el
// formulario, data-liv-busy; su botón de envío, data-busy-label y un .liv-button__label; el anuncio, data-liv-status.

// authChecker.js es de la plantilla base de Keycloak; el tema liv la hereda (parent=base).
import { startSessionPolling, checkAuthSession } from './authChecker.js';

const { ssoLoginInOtherTabsUrl, authSessionIdHash } = document.body.dataset;
if (ssoLoginInOtherTabsUrl) {
  startSessionPolling(ssoLoginInOtherTabsUrl);
}
if (authSessionIdHash) {
  checkAuthSession(authSessionIdHash);
}

for (const button of document.querySelectorAll('[data-liv-password-toggle]')) {
  const input = document.getElementById(button.getAttribute('aria-controls'));
  button.addEventListener('click', () => {
    const visible = input.type === 'password';
    input.type = visible ? 'text' : 'password';
    button.toggleAttribute('data-visible', visible);
    button.setAttribute('aria-label', visible ? button.dataset.labelHide : button.dataset.labelShow);
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
