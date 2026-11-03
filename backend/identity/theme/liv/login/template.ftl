<#--
  Estructura común de las páginas de acceso de LIV (frames "001 · Acceso" de LIV-Diseño): Keycloak/BrandPanel a la
  izquierda en escritorio y el formulario a la derecha; en móvil, el logotipo sobre el formulario.
  Las páginas de Keycloak que el tema no redefine también la usan, con las clases de theme.properties.
  Parámetros propios, además de los de la plantilla base:
    pageTitle     título de la pestaña (por defecto, el nombre del producto);
    alertTitle    título de la alerta cuando el mensaje es un error;
    securityNote  muestra "Conexión cifrada · Los accesos quedan registrados" al pie del formulario;
    headingIcon   icono del recuadro sobre el título (pasos posteriores al inicio de sesión);
    showAttemptedUser  muestra la cuenta intentada, como la base; las páginas con frame propio no la muestran.
  Secciones propias: "subtitle", debajo del título.
-->
<#import "components.ftl" as c>
<#import "icons.ftl" as i>
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false pageTitle="" alertTitle="" securityNote=false headingIcon="" showAttemptedUser=true>
<!DOCTYPE html>
<html lang="${lang}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <title><#if pageTitle?has_content>${pageTitle} · </#if>${msg("livProductName")}</title>
  <#if properties.styles?has_content>
    <#list properties.styles?split(' ') as style>
  <link href="${url.resourcesPath}/${style}" rel="stylesheet">
    </#list>
  </#if>
  <#-- Sin scripts en línea, para admitir una CSP con script-src 'self': liv.js lee los datos del body. -->
  <script src="${url.resourcesPath}/js/liv.js" type="module"></script>
</head>
<body data-page-id="login-${pageId}" data-sso-login-in-other-tabs-url="${url.ssoLoginInOtherTabsUrl}"<#if authenticationSession??> data-auth-session-id-hash="${authenticationSession.authSessionIdHash}"</#if>>
<div class="liv-page">
  <aside class="liv-brand">
    <@c.logo tone="inverse"/>
    <div class="liv-brand__message">
      <div>
        <p class="liv-brand__headline">${msg("livBrandHeadline")}</p>
        <p class="liv-brand__lead">${msg("livBrandLead")}</p>
      </div>
      <ul class="liv-brand__features" role="list">
        <@brandFeature icon="shield-check" title=msg("livFeatureSecureTitle") text=msg("livFeatureSecureText")/>
        <@brandFeature icon="calculator" title=msg("livFeatureRulesTitle") text=msg("livFeatureRulesText")/>
        <@brandFeature icon="history" title=msg("livFeatureTraceTitle") text=msg("livFeatureTraceText")/>
      </ul>
    </div>
    <p class="liv-brand__footer">${msg("livBrandFooter")}</p>
  </aside>
  <main class="liv-main">
    <div class="liv-form">
      <div class="liv-form__logo"><@c.logo/></div>
      <header class="liv-heading<#if headingIcon?has_content> liv-heading--with-icon</#if>">
        <#if headingIcon?has_content><span class="liv-icon-tile"><@i.icon name=headingIcon size=24/></span></#if>
        <h1 class="liv-heading__title" id="kc-page-title"><#nested "header"></h1>
        <#nested "subtitle">
      </header>
      <#-- Como en la base: después del primer paso, la cuenta con la que se entra y el enlace para cambiarla. -->
      <#if showAttemptedUser && auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
        <#nested "show-username">
        <p class="liv-attempted-user">
          <@i.icon name="user" size=16/>
          <span id="kc-attempted-username">${auth.attemptedUsername}</span>
          <a class="liv-link" id="reset-login" href="${url.loginRestartFlowUrl}">${msg("restartLoginTooltip")}</a>
        </p>
      </#if>
      <#-- Como en la base: las acciones que pide la aplicación no muestran el aviso de que hay que completarlas. -->
      <#assign mostrarMensaje = displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
      <#if mostrarMensaje>
        <@c.alert status=message.type text=message.summary title=(message.type == 'error')?then(alertTitle, "")/>
      </#if>
      <#nested "form">
      <#if auth?has_content && auth.showTryAnotherWayLink()>
        <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
          <input type="hidden" name="tryAnotherWay" value="on">
          <button type="submit" class="liv-button liv-button--secondary">${msg("doTryAnotherWay")}</button>
        </form>
      </#if>
      <#nested "socialProviders">
      <#if displayInfo><#nested "info"></#if>
      <#if securityNote>
        <p class="liv-security-note"><@i.icon name="lock-keyhole" size=14/><span>${msg("livSecurityNote")}</span></p>
      </#if>
    </div>
  </main>
</div>
</body>
</html>
</#macro>

<#-- Una característica del panel de marca: icono en su recuadro, título y descripción. -->
<#macro brandFeature icon title text>
<li class="liv-brand__feature">
  <span class="liv-brand__feature-icon"><@i.icon name=icon/></span>
  <span class="liv-brand__feature-text">
    <span class="liv-brand__feature-title">${title}</span>
    <span class="liv-brand__feature-description">${text}</span>
  </span>
</li>
</#macro>
