<#--
  Contraseña nueva (acción requerida en el primer ingreso o al vencer la contraseña): frame "Acceso · Actualizar
  contraseña". El aviso general de Keycloak ("tiene que cambiar su contraseña") no se muestra: lo dice el subtítulo.
  Los errores van en su campo; la política de contraseñas es la del realm (D-001-7).
-->
<#import "template.ftl" as layout>
<#import "components.ftl" as c>
<#assign erroresEnCampos = messagesPerField.existsError('password', 'password-confirm')>
<#assign esAviso = message?has_content && message.type == 'warning'>
<@layout.registrationLayout displayMessage=!erroresEnCampos && !esAviso pageTitle=msg("livUpdatePasswordTitle")
    headingIcon="key-round" showAttemptedUser=false securityNote=true; section>
  <#if section = "header">
    ${msg("livUpdatePasswordTitle")}
  <#elseif section = "subtitle">
    <p class="liv-heading__subtitle">${msg("livUpdatePasswordSubtitle")}</p>
  <#elseif section = "form">
    <form id="kc-passwd-update-form" class="liv-form__body" action="${url.loginAction}" method="post" data-liv-busy>
      <div class="liv-fields">
        <@c.textField id="password-new" label=msg("passwordNew") icon="lock" type="password" autocomplete="new-password"
          help=msg("livPasswordPolicyHelp") error=messagesPerField.get('password') extra='autofocus'/>
        <@c.textField id="password-confirm" label=msg("passwordConfirm") icon="lock" type="password"
          autocomplete="new-password" error=messagesPerField.get('password-confirm')/>
      </div>
      <@c.submitButton label=msg("livUpdatePasswordSubmit") busyLabel=msg("livSaving") trailingIcon="arrow-right"/>
      <#if isAppInitiatedAction??>
        <button class="liv-button liv-button--ghost" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
      </#if>
    </form>
  </#if>
</@layout.registrationLayout>
