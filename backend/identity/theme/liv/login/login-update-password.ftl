<#--
  Contraseña nueva (acción requerida en el primer ingreso o cuando un administrador obliga el cambio): frame "Acceso ·
  Actualizar contraseña". El aviso general de Keycloak ("tiene que cambiar su contraseña") no se muestra: lo dice el
  subtítulo. Las contraseñas distintas van en su campo; los errores de la política del realm (D-001-7) Keycloak los manda
  como mensaje general, así que van en la alerta y marcan el campo de la contraseña nueva.
  Al guardar se cierran las demás sesiones del usuario (logout-sessions, decisión del responsable): si el cambio lo
  obligó un administrador por seguridad, ninguna sesión anterior sigue viva.
-->
<#import "template.ftl" as layout>
<#import "components.ftl" as c>
<#assign erroresEnCampos = messagesPerField.existsError('password', 'password-confirm')>
<#assign esAvisoDeLaAccion = message?has_content && message.summary == msg("updatePasswordMessage")>
<#-- Error general sin campo: en esta página, los de la política de contraseñas. -->
<#assign errorGeneral = !erroresEnCampos && message?has_content && message.type == 'error'>
<@layout.registrationLayout displayMessage=!erroresEnCampos && !esAvisoDeLaAccion pageTitle=msg("livUpdatePasswordTitle")
    headingIcon="key-round" showAttemptedUser=false securityNote=true; section>
  <#if section = "header">
    ${msg("livUpdatePasswordTitle")}
  <#elseif section = "subtitle">
    <p class="liv-heading__subtitle">${msg("livUpdatePasswordSubtitle")}</p>
  <#elseif section = "form">
    <form id="kc-passwd-update-form" class="liv-form__body" action="${url.loginAction}" method="post" data-liv-busy>
      <div class="liv-fields">
        <@c.textField id="password-new" label=msg("passwordNew") icon="lock" type="password" autocomplete="new-password"
          help=msg("livPasswordPolicyHelp") error=messagesPerField.get('password') invalid=errorGeneral
          describedBy=errorGeneral?then("liv-alert-message", "") autofocus=true/>
        <@c.textField id="password-confirm" label=msg("passwordConfirm") icon="lock" type="password"
          autocomplete="new-password" error=messagesPerField.get('password-confirm')/>
      </div>
      <input type="hidden" name="logout-sessions" value="on">
      <@c.submitButton label=msg("livUpdatePasswordSubmit") busyLabel=msg("livSaving") trailingIcon="arrow-right"/>
      <#if isAppInitiatedAction??>
        <button class="liv-button liv-button--ghost" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
      </#if>
    </form>
  </#if>
</@layout.registrationLayout>
