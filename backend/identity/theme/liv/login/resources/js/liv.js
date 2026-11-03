// Comportamiento del tema liv: mostrar u ocultar la contraseña (IconButton) y el estado Loading del botón
// principal ("Ingresando…"), que además impide un segundo envío del formulario.
// Contrato con las plantillas (login.ftl): el botón que alterna lleva data-liv-password-toggle y aria-controls; el
// formulario, data-liv-busy; su botón de envío, data-busy-label y un .liv-button__label; el anuncio, data-liv-status.

for (const button of document.querySelectorAll('[data-liv-password-toggle]')) {
  const input = document.getElementById(button.getAttribute('aria-controls'));
  button.addEventListener('click', () => {
    const visible = input.type === 'password';
    input.type = visible ? 'text' : 'password';
    button.toggleAttribute('data-visible', visible);
    button.setAttribute('aria-label', visible ? button.dataset.labelHide : button.dataset.labelShow);
  });
}

for (const form of document.querySelectorAll('form[data-liv-busy]')) {
  form.addEventListener('submit', (event) => {
    const button = form.querySelector('button[type="submit"][data-busy-label]');
    if (!button) {
      return;
    }
    if (button.getAttribute('aria-disabled') === 'true') {
      event.preventDefault();
      return;
    }
    button.setAttribute('aria-disabled', 'true');
    button.querySelector('.liv-button__label').textContent = button.dataset.busyLabel;
    const status = form.querySelector('[data-liv-status]');
    if (status) {
      status.textContent = button.dataset.busyLabel;
    }
  });
}
