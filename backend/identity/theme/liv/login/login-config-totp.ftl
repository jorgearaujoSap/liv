<#--
  Configurar el segundo factor (acción requerida para los roles de 001-06): frame "Acceso · Configurar segundo factor".
  Pasos numerados (Keycloak/Step), el QR que genera Keycloak junto a la clave para escribirla a mano, y el código.
  El aviso general de Keycloak no se muestra: lo dice el subtítulo. El nombre del dispositivo solo se pide cuando
  Keycloak lo exige (si la cuenta ya tiene otro); el frame es el del primer ingreso.
-->
<#import "template.ftl" as layout>
<#import "components.ftl" as c>
<#assign erroresEnCampos = messagesPerField.existsError('totp', 'userLabel')>
<#assign esAviso = message?has_content && message.type == 'warning'>
<@layout.registrationLayout displayMessage=!erroresEnCampos && !esAviso pageTitle=msg("livConfigTotpTitle")
    headingIcon="smartphone" showAttemptedUser=false; section>
  <#if section = "header">
    ${msg("livConfigTotpTitle")}
  <#elseif section = "subtitle">
    <p class="liv-heading__subtitle">${msg("livConfigTotpSubtitle")}</p>
  <#elseif section = "form">
    <ol class="liv-steps">
      <@c.step number=1 title=msg("livTotpStep1Title") text=msg("livTotpStep1Text")/>
      <@c.step number=2 title=msg("livTotpStep2Title") text=msg("livTotpStep2Text")>
        <div class="liv-totp-qr">
          <img class="liv-totp-qr__image" src="data:image/png;base64,${totp.totpSecretQrCode}" alt="${msg("livTotpQrAlt")}">
          <div class="liv-totp-qr__manual">
            <p class="liv-totp-qr__hint">${msg("livTotpManualHint")}</p>
            <code class="liv-totp-qr__secret" id="kc-totp-secret-key">${totp.totpSecretEncoded}</code>
          </div>
        </div>
      </@c.step>
      <@c.step number=3 title=msg("livTotpStep3Title") text=msg("livTotpStep3Text")/>
    </ol>
    <form id="kc-totp-settings-form" class="liv-form__body" action="${url.loginAction}" method="post" data-liv-busy>
      <@c.codeInput id="totp" label=msg("livCodeLabel") help=msg("livCodeHelpSetup") error=messagesPerField.get('totp')/>
      <input type="hidden" id="totpSecret" name="totpSecret" value="${totp.totpSecret}">
      <#if mode??><input type="hidden" id="mode" name="mode" value="${mode}"></#if>
      <#if totp.otpCredentials?size gte 1>
        <@c.textField id="userLabel" label=msg("loginTotpDeviceName") autocomplete="off" error=messagesPerField.get('userLabel')/>
      </#if>
      <@c.submitButton id="saveTOTPBtn" label=msg("livActivateSubmit") busyLabel=msg("livActivating") trailingIcon="arrow-right"/>
      <#if isAppInitiatedAction??>
        <button class="liv-button liv-button--ghost" type="submit" id="cancelTOTPBtn" name="cancel-aia" value="true">${msg("doCancel")}</button>
      </#if>
    </form>
  </#if>
</@layout.registrationLayout>
