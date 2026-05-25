# Configura el realm `unnoba` en una instancia local de Keycloak (http://localhost:8080)
# para el TP3 de PDyC 2026. Idempotente: si una entidad ya existe la deja como esta.
#
# Uso:   .\keycloak\setup-realm.ps1
# Reqs:  Keycloak escuchando en localhost:8080 con admin/admin (ver docker-compose.yml).

$ErrorActionPreference = "Stop"

$KcBase       = if ($env:KC_BASE)       { $env:KC_BASE }       else { "http://localhost:8080" }
$KcAdminUser  = if ($env:KC_ADMIN_USER) { $env:KC_ADMIN_USER } else { "admin" }
$KcAdminPass  = if ($env:KC_ADMIN_PASS) { $env:KC_ADMIN_PASS } else { "admin" }
$Realm        = if ($env:REALM)         { $env:REALM }         else { "unnoba" }
$ClientId     = if ($env:CLIENT_ID)     { $env:CLIENT_ID }     else { "pdyc" }
$ClientSecret = if ($env:CLIENT_SECRET) { $env:CLIENT_SECRET } else { "pdyc-secret-dev" }
$TestUser     = if ($env:TEST_USER)     { $env:TEST_USER }     else { "tp3-user" }
$TestPass     = if ($env:TEST_PASS)     { $env:TEST_PASS }     else { "tp3pass" }

function Log($msg) { Write-Host "[setup-realm] $msg" }

Log "Esperando a Keycloak en $KcBase ..."
for ($i = 0; $i -lt 60; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "$KcBase/realms/master/.well-known/openid-configuration" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { Log "Keycloak listo."; break }
    } catch {}
    Start-Sleep -Seconds 2
    if ($i -eq 59) { throw "Keycloak no respondio en 120s" }
}

Log "Obteniendo token de admin..."
$tokenResp = Invoke-RestMethod -Method Post -Uri "$KcBase/realms/master/protocol/openid-connect/token" `
    -Body @{ username = $KcAdminUser; password = $KcAdminPass; grant_type = "password"; client_id = "admin-cli" } `
    -ContentType "application/x-www-form-urlencoded"
$AdminToken = $tokenResp.access_token
$Headers = @{ Authorization = "Bearer $AdminToken" }

Log "Verificando realm $Realm..."
$realmExists = $true
try { Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm" -Headers $Headers | Out-Null } catch { $realmExists = $false }
if (-not $realmExists) {
    Log "Creando realm $Realm..."
    $body = @{ realm = $Realm; enabled = $true; sslRequired = "external"; registrationAllowed = $false; loginWithEmailAllowed = $true; resetPasswordAllowed = $true; accessTokenLifespan = 3600 } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms" -Headers $Headers -Body $body -ContentType "application/json" | Out-Null
} else { Log "Realm $Realm ya existe." }

Log "Verificando client $ClientId..."
$clientList = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/clients?clientId=$ClientId" -Headers $Headers
if ($clientList.Count -eq 0) {
    Log "Creando client $ClientId..."
    $body = @{
        clientId = $ClientId; enabled = $true; protocol = "openid-connect"; publicClient = $false
        clientAuthenticatorType = "client-secret"; secret = $ClientSecret
        serviceAccountsEnabled = $true; standardFlowEnabled = $true; directAccessGrantsEnabled = $false
        redirectUris = @("https://oauth.pstmn.io/v1/callback","http://localhost:8081/*")
        webOrigins = @("+")
        attributes = @{ "post.logout.redirect.uris" = "+" }
    } | ConvertTo-Json -Depth 4
    Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms/$Realm/clients" -Headers $Headers -Body $body -ContentType "application/json" | Out-Null
    $clientList = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/clients?clientId=$ClientId" -Headers $Headers
} else { Log "Client $ClientId ya existe." }
$PdycId = $clientList[0].id

Log "Asegurando client secret..."
Invoke-RestMethod -Method Put -Uri "$KcBase/admin/realms/$Realm/clients/$PdycId" -Headers $Headers `
    -Body (@{ secret = $ClientSecret } | ConvertTo-Json) -ContentType "application/json" | Out-Null

Log "Asignando manage-users / view-users / query-users al service account..."
$svcUser = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/clients/$PdycId/service-account-user" -Headers $Headers
$rm = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/clients?clientId=realm-management" -Headers $Headers
$rmId = $rm[0].id
$wanted = @("manage-users","view-users","query-users")
$rmRoles = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/clients/$rmId/roles" -Headers $Headers
$rolesToAssign = @($rmRoles | Where-Object { $wanted -contains $_.name })
$body = $rolesToAssign | ConvertTo-Json -AsArray
Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms/$Realm/users/$($svcUser.id)/role-mappings/clients/$rmId" `
    -Headers $Headers -Body $body -ContentType "application/json" | Out-Null

Log "Verificando usuario de prueba $TestUser..."
$users = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/users?username=$TestUser" -Headers $Headers
if ($users.Count -eq 0) {
    Log "Creando usuario $TestUser..."
    $body = @{
        username = $TestUser; enabled = $true; emailVerified = $true
        email = "$TestUser@example.com"; firstName = "TP3"; lastName = "User"
        credentials = @(@{ type = "password"; value = $TestPass; temporary = $false })
    } | ConvertTo-Json -Depth 4
    Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms/$Realm/users" -Headers $Headers -Body $body -ContentType "application/json" | Out-Null
} else { Log "Usuario $TestUser ya existe." }

Log "Listo. Resumen:"
Write-Host "  Realm:         $Realm"
Write-Host "  Client:        $ClientId  (secret: $ClientSecret)"
Write-Host "  Test user:     $TestUser  /  $TestPass"
Write-Host "  Issuer:        $KcBase/realms/$Realm"
Write-Host "  Token URL:     $KcBase/realms/$Realm/protocol/openid-connect/token"
Write-Host "  Auth URL:      $KcBase/realms/$Realm/protocol/openid-connect/auth"
Write-Host ""
Write-Host "Antes de arrancar el backend:"
Write-Host "  `$env:KEYCLOAK_CLIENT_SECRET=`"$ClientSecret`""
