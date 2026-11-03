<#--
  Componentes de liv-components que reproduce el tema (excepción del ADR 0024), con su mismo nombre, variantes y
  marcado. Los estilos están en styles/liv.css.
-->
<#import "icons.ftl" as i>

<#-- Logo. tone: default (fondo claro) o inverse (fondo oscuro). -->
<#macro logo tone="default">
<span class="liv-logo liv-logo--${tone}">
  <svg class="liv-logo__mark" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 36" width="36" height="36" aria-hidden="true" focusable="false">
    <rect class="liv-logo__tile" width="36" height="36" rx="9"/>
    <polyline class="liv-logo__letter" points="12.5,10 12.5,26 25,26" fill="none" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/>
    <circle class="liv-logo__dot" cx="24.5" cy="11.5" r="2.75"/>
  </svg>
  <span class="liv-logo__wordmark">
    <span class="liv-logo__name">${msg("livProductName")}</span>
    <span class="liv-logo__tagline">${msg("livProductTagline")}</span>
  </span>
</span>
</#macro>

<#--
  Alert. status: error, warning, info o success (los tipos de mensaje de Keycloak). Error usa role="alert" y los
  demás role="status". El mensaje lleva id="liv-alert-message" para que los campos lo enlacen con aria-describedby.
  text llega sin tratar: el macro lo sanea con kcSanitize, como la plantilla base, porque puede traer HTML de Keycloak.
-->
<#macro alert status text title="">
<#assign iconos = {"error": "circle-alert", "warning": "triangle-alert", "info": "info", "success": "circle-check"}>
<div class="liv-alert liv-alert--${status}" role="<#if status == "error">alert<#else>status</#if>">
  <@i.icon name=(iconos[status]!"circle-alert") extraClass="liv-alert__icon"/>
  <div class="liv-alert__body">
    <#if title?has_content><p class="liv-alert__title">${title}</p></#if>
    <p class="liv-alert__message" id="liv-alert-message">${kcSanitize(text)?no_esc}</p>
  </div>
</div>
</#macro>

<#-- Spinner con tono Inverse, dentro del botón principal. El texto que dice qué se carga lo anuncia el botón. -->
<#macro spinner>
<svg class="liv-spinner" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" width="20" height="20" fill="none" stroke-width="2.5" aria-hidden="true" focusable="false">
  <circle class="liv-spinner__track" cx="10" cy="10" r="8.75"/>
  <path class="liv-spinner__arc" d="M10 1.25A8.75 8.75 0 0 1 16.187 16.187"/>
</svg>
</#macro>

<#--
  Button Primary, tamaño Lg, que envía el formulario. Con data-liv-busy en el formulario, liv.js lo pasa a Loading
  (spinner y busyLabel) y evita un segundo envío; el anuncio va en el role="status" de al lado.
-->
<#macro submitButton label busyLabel id="" name="" trailingIcon="">
<button class="liv-button liv-button--primary"<#if id?has_content> id="${id}"</#if><#if name?has_content> name="${name}"</#if> type="submit" data-busy-label="${busyLabel}">
  <span class="liv-button__spinner"><@spinner/></span>
  <span class="liv-button__label">${label}</span>
  <#if trailingIcon?has_content><@i.icon name=trailingIcon extraClass="liv-button__trailing-icon"/></#if>
</button>
<span class="liv-sr-only" role="status" data-liv-status></span>
</#macro>

<#--
  TextField (alto 44): etiqueta siempre visible, icono opcional al inicio y, si es de contraseña, el IconButton para
  mostrarla. Con error (State=Error), el borde y la ayuda pasan a status/danger con icono, el campo queda aria-invalid y
  la ayuda dice qué corregir. invalid y describedBy marcan el campo cuando el error lo explica otro elemento (la alerta
  de la página). plainText: texto que no se corrige ni se capitaliza (el usuario).
-->
<#macro textField id label icon="" name="" type="text" value="" autocomplete="" placeholder="" help="" error="" invalid=false describedBy="" autofocus=false plainText=false>
<#local ayuda = error?has_content?then(error, help)>
<#local ayudaId = id + "-help">
<div class="liv-text-field<#if error?has_content> liv-text-field--error</#if>">
  <label class="liv-text-field__label" for="${id}">${label}</label>
  <div class="liv-text-field__control">
    <#if icon?has_content><@i.icon name=icon extraClass="liv-text-field__icon"/></#if>
    <input class="liv-text-field__input" id="${id}" name="${name?has_content?then(name, id)}" type="${type}" value="${value}"<#if autocomplete?has_content> autocomplete="${autocomplete}"</#if><#if placeholder?has_content> placeholder="${placeholder}"</#if> dir="ltr"<#if plainText> autocapitalize="none" spellcheck="false"</#if><#if autofocus> autofocus</#if><#if invalid || error?has_content> aria-invalid="true"</#if><#if describedBy?has_content> aria-describedby="${describedBy}"<#elseif ayuda?has_content> aria-describedby="${ayudaId}"</#if>>
    <#if type == "password">
    <button class="liv-icon-button" type="button" aria-controls="${id}" aria-label="${msg("showPassword")}"
            data-liv-password-toggle data-label-show="${msg("showPassword")}" data-label-hide="${msg("hidePassword")}">
      <@i.icon name="eye" size=16 extraClass="liv-icon-button__show"/>
      <@i.icon name="eye-off" size=16 extraClass="liv-icon-button__hide"/>
    </button>
    </#if>
  </div>
  <#if ayuda?has_content>
  <p class="liv-text-field__help" id="${ayudaId}"><#if error?has_content><@i.icon name="circle-alert" size=16/></#if><span>${kcSanitize(ayuda)?no_esc}</span></p>
  </#if>
</div>
</#macro>

<#--
  CodeInput: un solo campo para el código del segundo factor (inputmode numérico, one-time-code), con dígitos grandes y
  espaciados; liv.js deja pegar el código con espacios. Con error, como el TextField. digits es el de la política TOTP
  del realm (6); el placeholder son tantos ceros como dígitos. autofocus: en la página de pedir el código; no al
  configurar, donde antes hay que leer los pasos.
-->
<#macro codeInput id label help name="" error="" digits=6 autofocus=false>
<#local ayuda = error?has_content?then(error, help)>
<div class="liv-code-input<#if error?has_content> liv-code-input--error</#if>">
  <label class="liv-code-input__label" for="${id}">${label}</label>
  <div class="liv-code-input__control">
    <input class="liv-code-input__input" id="${id}" name="${name?has_content?then(name, id)}" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="${digits?c}" placeholder="${""?left_pad(digits, "0")}" spellcheck="false" dir="ltr"<#if autofocus> autofocus</#if> data-liv-code aria-describedby="${id}-help"<#if error?has_content> aria-invalid="true"</#if>>
  </div>
  <p class="liv-code-input__help" id="${id}-help"><#if error?has_content><@i.icon name="circle-alert" size=16/></#if><span>${kcSanitize(ayuda)?no_esc}</span></p>
</div>
</#macro>

<#--
  Keycloak/Step (solo del tema): paso numerado de la configuración del segundo factor, dentro de un <ol role="list">
  (sin viñetas, WebKit deja de anunciarlo como lista); el número visible lo repite la lista, por eso es aria-hidden.
-->
<#macro step number title text>
<li class="liv-step">
  <span class="liv-step__number" aria-hidden="true">${number}</span>
  <span class="liv-step__text">
    <span class="liv-step__title">${title}</span>
    <span class="liv-step__description">${text}</span>
  </span>
  <#nested>
</li>
</#macro>
