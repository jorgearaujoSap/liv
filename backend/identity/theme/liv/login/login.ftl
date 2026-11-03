<#--
  Inicio de sesión: frames "Acceso · Iniciar sesión", "Ingresando", "Credenciales incorrectas (incluye bloqueo)" y
  "Iniciar sesión (móvil)". Todo fallo muestra el mismo mensaje genérico (001-03, 001-04, 001-05): lo fija
  messages_es.properties. Sin registro, sin "Recordarme" ni recuperación por correo (el realm los desactiva).
-->
<#import "template.ftl" as layout>
<#import "components.ftl" as c>
<#import "icons.ftl" as i>
<#--
  Keycloak liga a los campos el error de credenciales incorrectas, usuario inexistente y cuenta bloqueada, pero manda
  el de cuenta desactivada como mensaje general. Los dos se pintan igual, con la misma alerta y los mismos atributos en
  los campos, para que el HTML tampoco revele que la cuenta existe (001-05; check.sh compara las páginas). Se reconoce
  por el texto, que es el mismo mensaje único (messages_es.properties); otros errores generales, como el tiempo
  agotado, van por la alerta de la plantilla sin marcar los campos.
-->
<#if messagesPerField.existsError('username', 'password')>
  <#assign errorAcceso = messagesPerField.getFirstError('username', 'password')>
<#elseif message?has_content && message.type == 'error' && message.summary == msg("accountDisabledMessage")>
  <#assign errorAcceso = message.summary>
<#else>
  <#assign errorAcceso = "">
</#if>
<#assign hayError = errorAcceso?has_content>
<@layout.registrationLayout displayMessage=!hayError pageTitle=msg("loginAccountTitle") alertTitle=msg("livLoginErrorTitle") securityNote=true; section>
  <#if section = "header">
    ${msg("loginAccountTitle")}
  <#elseif section = "subtitle">
    <p class="liv-heading__subtitle">${msg("livLoginSubtitle")}</p>
  <#elseif section = "form">
    <#if hayError>
      <@c.alert status="error" title=msg("livLoginErrorTitle") text=errorAcceso/>
    </#if>
    <form id="kc-form-login" class="liv-form__body" action="${url.loginAction}" method="post" data-liv-busy>
      <#assign idDelError = hayError?then("liv-alert-message", "")>
      <div class="liv-fields">
        <#if !usernameHidden??>
          <@c.textField id="username" label=msg("username") icon="user" value=(login.username!'') autocomplete="username"
            invalid=hayError describedBy=idDelError plainText=true autofocus=true/>
        </#if>
        <@c.textField id="password" label=msg("password") icon="lock" type="password" autocomplete="current-password"
          placeholder=msg("livPasswordPlaceholder") invalid=hayError describedBy=idDelError/>
      </div>
      <input type="hidden" id="id-hidden-input" name="credentialId" value="${auth.selectedCredential!''}">
      <@c.submitButton id="kc-login" name="login" label=msg("doLogIn") busyLabel=msg("livLoggingIn") trailingIcon="arrow-right"/>
    </form>
    <p class="liv-help"><@i.icon name="circle-help" size=16/><span>${msg("livForgotPasswordHelp")}</span></p>
  </#if>
</@layout.registrationLayout>
