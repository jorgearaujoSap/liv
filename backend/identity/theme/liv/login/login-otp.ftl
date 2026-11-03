<#--
  Código del segundo factor: frames "Acceso · Código del segundo factor" y "Código incorrecto" (001-07). El error va
  en el campo y dice que los intentos fallidos cuentan para el bloqueo (messages_es.properties). "Volver a iniciar
  sesión" reinicia el flujo, como el enlace de la plantilla base. Con más de un dispositivo, se elige cuál.
  A diferencia de las acciones requeridas, aquí Keycloak no manda un aviso general: solo se oculta la alerta cuando el
  error ya está en el campo.
-->
<#import "template.ftl" as layout>
<#import "components.ftl" as c>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp') pageTitle=msg("livOtpTitle")
    headingIcon="smartphone" showAttemptedUser=false; section>
  <#if section = "header">
    ${msg("livOtpTitle")}
  <#elseif section = "subtitle">
    <p class="liv-heading__subtitle">${msg("livOtpSubtitle")}</p>
  <#elseif section = "form">
    <form id="kc-otp-login-form" class="liv-form__body" action="${url.loginAction}" method="post" data-liv-busy>
      <#if otpLogin.userOtpCredentials?size gt 1>
        <fieldset class="liv-choices">
          <legend class="liv-label">${msg("livOtpDeviceLegend")}</legend>
          <#list otpLogin.userOtpCredentials as credencial>
            <label class="liv-choice">
              <input type="radio" name="selectedCredentialId" value="${credencial.id}"<#if credencial.id == otpLogin.selectedCredentialId> checked</#if>>
              <span>${credencial.userLabel}</span>
            </label>
          </#list>
        </fieldset>
      </#if>
      <@c.codeInput id="otp" label=msg("livCodeLabel") help=msg("livCodeHelp") error=messagesPerField.get('totp')/>
      <@c.submitButton id="kc-login" name="login" label=msg("livVerifySubmit") busyLabel=msg("livVerifying")/>
      <a class="liv-button liv-button--ghost" id="reset-login" href="${url.loginRestartFlowUrl}">${msg("livBackToLogin")}</a>
    </form>
  </#if>
</@layout.registrationLayout>
