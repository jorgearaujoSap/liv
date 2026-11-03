<#--
  Error de Keycloak (enlace vencido, cliente desconocido…). Sin frame propio: reutiliza la estructura de
  "Acceso · Credenciales incorrectas" (título, alerta y acción), excepción de AGENTS.md §6.2.
-->
<#import "template.ftl" as layout>
<#import "components.ftl" as c>
<@layout.registrationLayout displayMessage=false pageTitle=msg("errorTitle"); section>
  <#if section = "header">
    ${msg("errorTitle")}
  <#elseif section = "form">
    <@c.alert status="error" text=kcSanitize(message.summary)/>
    <#if traceId??>
      <p class="liv-heading__subtitle" id="traceId">${msg("traceIdSupportMessage", traceId)}</p>
    </#if>
    <#if !skipLink?? && client?? && client.baseUrl?has_content>
      <p class="liv-help"><a class="liv-link" id="backToApplication" href="${client.baseUrl}">${msg("backToApplication")}</a></p>
    </#if>
  </#if>
</@layout.registrationLayout>
