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
