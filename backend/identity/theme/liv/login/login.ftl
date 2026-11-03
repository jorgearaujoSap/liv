<#--
  Inicio de sesión: frames "Acceso · Iniciar sesión", "Ingresando", "Credenciales incorrectas (incluye bloqueo)" y
  "Iniciar sesión (móvil)". Todo fallo muestra el mismo mensaje genérico (001-03, 001-04, 001-05): lo fija
  messages_es.properties. Sin registro, sin "Recordarme" ni recuperación por correo (el realm los desactiva).
-->
<#import "template.ftl" as layout>
<#import "components.ftl" as c>
<#import "icons.ftl" as i>
<#assign hayError = messagesPerField.existsError('username', 'password')>
<@layout.registrationLayout displayMessage=!hayError pageTitle=msg("loginAccountTitle") alertTitle=msg("livLoginErrorTitle") securityNote=true; section>
  <#if section = "header">
    ${msg("loginAccountTitle")}
  <#elseif section = "subtitle">
    <p class="liv-heading__subtitle">${msg("livLoginSubtitle")}</p>
  <#elseif section = "form">
    <#if hayError>
      <@c.alert status="error" title=msg("livLoginErrorTitle") text=kcSanitize(messagesPerField.getFirstError('username', 'password'))/>
    </#if>
    <form id="kc-form-login" class="liv-form__body" action="${url.loginAction}" method="post" data-liv-busy>
      <div class="liv-fields">
        <#if !usernameHidden??>
        <div class="liv-text-field">
          <label class="liv-text-field__label" for="username">${msg("username")}</label>
          <div class="liv-text-field__control">
            <@i.icon name="user" extraClass="liv-text-field__icon"/>
            <input class="liv-text-field__input" id="username" name="username" type="text" value="${(login.username!'')}"
                   autocomplete="username" autocapitalize="none" spellcheck="false" dir="ltr" autofocus
                   <#if hayError>aria-invalid="true" aria-describedby="liv-alert-message"</#if>>
          </div>
        </div>
        </#if>
        <div class="liv-text-field">
          <label class="liv-text-field__label" for="password">${msg("password")}</label>
          <div class="liv-text-field__control">
            <@i.icon name="lock" extraClass="liv-text-field__icon"/>
            <input class="liv-text-field__input" id="password" name="password" type="password" autocomplete="current-password"
                   placeholder="${msg("livPasswordPlaceholder")}" dir="ltr"
                   <#if hayError>aria-invalid="true" aria-describedby="liv-alert-message"</#if>>
            <button class="liv-icon-button" type="button" aria-controls="password" aria-label="${msg("showPassword")}"
                    data-liv-password-toggle data-label-show="${msg("showPassword")}" data-label-hide="${msg("hidePassword")}">
              <@i.icon name="eye" size=16 extraClass="liv-icon-button__show"/>
              <@i.icon name="eye-off" size=16 extraClass="liv-icon-button__hide"/>
            </button>
          </div>
        </div>
      </div>
      <input type="hidden" id="id-hidden-input" name="credentialId" value="${auth.selectedCredential!''}">
      <button class="liv-button liv-button--primary" id="kc-login" name="login" type="submit" data-busy-label="${msg("livLoggingIn")}">
        <span class="liv-button__spinner"><@c.spinner/></span>
        <span class="liv-button__label">${msg("doLogIn")}</span>
        <@i.icon name="arrow-right" extraClass="liv-button__trailing-icon"/>
      </button>
      <span class="liv-sr-only" role="status" data-liv-status></span>
    </form>
    <p class="liv-help"><@i.icon name="circle-help" size=16/><span>${msg("livForgotPasswordHelp")}</span></p>
  </#if>
</@layout.registrationLayout>
