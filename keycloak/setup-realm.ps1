# Configura el realm `unnoba` en una instancia local de Keycloak (http://localhost:8080)
# para los TP3/TP4 de PDyC 2026. Idempotente: si una entidad ya existe la deja como esta.
#
# Uso:   .\keycloak\setup-realm.ps1
# Reqs:  Keycloak escuchando en localhost:8080 con admin/admin (ver docker-compose.yml).
#
# TP4: crea los realm roles `admin` y `user`, asigna `admin` a tp3-user y crea
# un usuario final tp4-user (rol `user`) para probar los endpoints /me/**.

$ErrorActionPreference = "Stop"

$KcBase       = if ($env:KC_BASE)       { $env:KC_BASE }       else { "http://localhost:8080" }
$KcAdminUser  = if ($env:KC_ADMIN_USER) { $env:KC_ADMIN_USER } else { "admin" }
$KcAdminPass  = if ($env:KC_ADMIN_PASS) { $env:KC_ADMIN_PASS } else { "admin" }
$Realm        = if ($env:REALM)         { $env:REALM }         else { "unnoba" }
$ClientId     = if ($env:CLIENT_ID)     { $env:CLIENT_ID }     else { "pdyc" }
$ClientSecret = if ($env:CLIENT_SECRET) { $env:CLIENT_SECRET } else { "pdyc-secret-dev" }
$TestUser     = if ($env:TEST_USER)     { $env:TEST_USER }     else { "tp3-user" }
$TestPass     = if ($env:TEST_PASS)     { $env:TEST_PASS }     else { "tp3pass" }
$EndUser      = if ($env:END_USER)      { $env:END_USER }      else { "tp4-user" }
$EndPass      = if ($env:END_PASS)      { $env:END_PASS }      else { "tp4pass" }

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

Log "Asignando realm-admin (composite) al service account del client $ClientId..."
$svcUser = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/clients/$PdycId/service-account-user" -Headers $Headers
$rm = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/clients?clientId=realm-management" -Headers $Headers
$rmId = $rm[0].id
# `realm-admin` es un composite role que incluye manage-users, view-users, query-users,
# manage-realm, view-realm, etc. Es el approach estandar para un service account que
# administra el realm. Tambien asignamos los granulares por si el composite se desactiva.
$wanted = @("realm-admin","manage-users","view-users","query-users","manage-realm","view-realm")
$rmRoles = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/clients/$rmId/roles" -Headers $Headers
$rolesToAssign = @($rmRoles | Where-Object { $wanted -contains $_.name } | ForEach-Object { @{ id = $_.id; name = $_.name } })
$body = $rolesToAssign | ConvertTo-Json -AsArray
Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms/$Realm/users/$($svcUser.id)/role-mappings/clients/$rmId" `
    -Headers $Headers -Body $body -ContentType "application/json" | Out-Null

function Ensure-RealmRole($name, $desc) {
    $exists = $true
    try { Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/roles/$name" -Headers $Headers | Out-Null } catch { $exists = $false }
    if (-not $exists) {
        Log "Creando realm role $name..."
        $body = @{ name = $name; description = $desc } | ConvertTo-Json
        Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms/$Realm/roles" -Headers $Headers -Body $body -ContentType "application/json" | Out-Null
    } else { Log "Realm role $name ya existe." }
}

function Assign-RealmRole($userId, $roleName) {
    $role = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/roles/$roleName" -Headers $Headers
    $payload = @(@{ id = $role.id; name = $role.name }) | ConvertTo-Json -AsArray
    Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms/$Realm/users/$userId/role-mappings/realm" `
        -Headers $Headers -Body $payload -ContentType "application/json" | Out-Null
}

Log "Asegurando realm roles TP4 (admin, user)..."
Ensure-RealmRole "admin" "Greater Events backoffice role"
Ensure-RealmRole "user"  "Greater Events end-user role"

Log "Verificando usuario admin $TestUser..."
$users = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/users?username=$TestUser" -Headers $Headers
if ($users.Count -eq 0) {
    Log "Creando usuario $TestUser..."
    $body = @{
        username = $TestUser; enabled = $true; emailVerified = $true
        email = "$TestUser@example.com"; firstName = "TP3"; lastName = "User"
        credentials = @(@{ type = "password"; value = $TestPass; temporary = $false })
    } | ConvertTo-Json -Depth 4
    Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms/$Realm/users" -Headers $Headers -Body $body -ContentType "application/json" | Out-Null
    $users = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/users?username=$TestUser" -Headers $Headers
} else { Log "Usuario $TestUser ya existe." }
Log "Asignando rol admin a $TestUser..."
Assign-RealmRole $users[0].id "admin"

Log "Verificando usuario final $EndUser..."
$endUsers = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/users?username=$EndUser" -Headers $Headers
if ($endUsers.Count -eq 0) {
    Log "Creando usuario $EndUser..."
    $body = @{
        username = $EndUser; enabled = $true; emailVerified = $true
        email = "$EndUser@example.com"; firstName = "TP4"; lastName = "EndUser"
        credentials = @(@{ type = "password"; value = $EndPass; temporary = $false })
    } | ConvertTo-Json -Depth 4
    Invoke-RestMethod -Method Post -Uri "$KcBase/admin/realms/$Realm/users" -Headers $Headers -Body $body -ContentType "application/json" | Out-Null
    $endUsers = Invoke-RestMethod -Uri "$KcBase/admin/realms/$Realm/users?username=$EndUser" -Headers $Headers
} else { Log "Usuario $EndUser ya existe." }
Log "Asignando rol user a $EndUser..."
Assign-RealmRole $endUsers[0].id "user"

Log "Listo. Resumen:"
Write-Host "  Realm:         $Realm"
Write-Host "  Client:        $ClientId  (secret: $ClientSecret)"
Write-Host "  Roles:         admin, user"
Write-Host "  Admin user:    $TestUser  /  $TestPass    (rol admin)"
Write-Host "  End user:      $EndUser  /  $EndPass      (rol user)"
Write-Host "  Issuer:        $KcBase/realms/$Realm"
Write-Host "  Token URL:     $KcBase/realms/$Realm/protocol/openid-connect/token"
Write-Host "  Auth URL:      $KcBase/realms/$Realm/protocol/openid-connect/auth"
Write-Host ""
Write-Host "Antes de arrancar el backend:"
Write-Host "  `$env:KEYCLOAK_CLIENT_SECRET=`"$ClientSecret`""
