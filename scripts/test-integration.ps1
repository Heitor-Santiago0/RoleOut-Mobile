# Creates test accounts and one role only in the isolated integration environment.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8082/RoleOut/api/v1'
$compose = Join-Path $PSScriptRoot '../compose.integration.yml'
function Request($method, $path, $body = $null, $token = $null) {
    $requestOptions = @{ Uri = "$base$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 20 }
    if ($token) { $requestOptions.Headers = @{ Authorization = "Bearer $token" } }
    if ($null -ne $body) {
        $requestOptions.ContentType = 'application/json; charset=utf-8'
        $requestOptions.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8 -Compress))
    }
    Invoke-WebRequest @requestOptions
}
function Assert($condition, $message) { if (-not $condition) { throw $message } }
function ExpectStatus($status, $method, $path, $body = $null, $token = $null) {
    try { $response = Request $method $path $body $token; $actual = [int]$response.StatusCode }
    catch {
        if ($null -eq $_.Exception.Response) { throw }
        $actual = [int]$_.Exception.Response.StatusCode
    }
    Assert ($actual -eq $status) "$method $path expected $status, got $actual"
}
$suffix = [Guid]::NewGuid().ToString('N')
$password = [Guid]::NewGuid().ToString('N')
$tokens = @()
try {
    foreach ($owner in @('a', 'b')) {
        $email = "integration-$suffix-$owner@example.invalid"
        ExpectStatus 201 POST '/auth/cadastro' @{ nome = "Integration $owner"; email = $email; senha = $password }
        $session = (Request POST '/auth/login' @{ email = $email; senha = $password }).Content | ConvertFrom-Json
        $tokens += $session.token
        ExpectStatus 200 GET '/auth/me' $null $session.token
    }
    $sql = "INSERT INTO roles (nome, local, cidade, categoria, valor) VALUES ('Integration $suffix', 'Museu teste', 'Cidade Integracao', 'Cultura', 20.10); SELECT LAST_INSERT_ID();"
    $result = $sql | docker compose -f $compose exec -T mysql mysql -uroleout -pintegration-local-only -D roleout -N
    Assert ($LASTEXITCODE -eq 0) 'Failed to create isolated fixture'
    $roleId = [int]($result | Select-Object -Last 1)
    ExpectStatus 200 GET "/roles/$roleId"
    $accentedName = 'Roteiro de integra' + [char]0x00e7 + [char]0x00e3 + 'o'
    $plan = @{ nome = $accentedName; cidade = 'Cidade Integracao'; data = '2026-10-15'; partida = '14:00'; duracaoMinutos = 120; pessoas = 3; orcamentoCentavos = 3000; modoOrcamento = 'person'; paradas = @(@{ roleId = $roleId; minutos = 60 }) }
    $created = Request POST '/roteiros' $plan $tokens[0]
    Assert ($created.StatusCode -eq 201) 'Save failed'
    $saved = $created.Content | ConvertFrom-Json
    Assert ($saved.roteiro.totalCentavos -eq 6030) 'Server price calculation failed'
    Assert ($saved.roteiro.orcamentoCentavos -eq 9000) 'Per-person budget failed'
    $id = $saved.roteiro.id
    $read = (Request GET "/roteiros/$id" $null $tokens[0]).Content | ConvertFrom-Json
    Assert ($read.roteiro.nome -eq $plan.nome) 'UTF-8 round trip failed'
    $list = (Request GET '/roteiros' $null $tokens[0]).Content | ConvertFrom-Json
    Assert (@($list.itens).Count -eq 1) 'Owner list failed'
    ExpectStatus 404 GET "/roteiros/$id" $null $tokens[1]
    $other = (Request GET '/roteiros' $null $tokens[1]).Content | ConvertFrom-Json
    Assert (@($other.itens).Count -eq 0) 'Cross-account list leak'
    ExpectStatus 401 GET "/roteiros/$id"
    $plan.orcamentoCentavos = 100
    ExpectStatus 409 POST '/roteiros' $plan $tokens[0]
    $after = (Request GET '/roteiros' $null $tokens[0]).Content | ConvertFrom-Json
    Assert (@($after.itens).Count -eq 1) 'Rejected save left partial data'
    foreach ($path in @('/', '/roles', '/sugestoes')) {
        $web = Invoke-WebRequest "http://127.0.0.1:8082/RoleOut$path" -UseBasicParsing -TimeoutSec 20
        Assert ($web.StatusCode -eq 200) "Web page failed: $path"
    }
    Write-Output 'PASS: signup, login, identity, role detail, save/read/list, UTF-8, per-person budget, server prices, account isolation, budget rollback and JSP pages.'
} finally {
    foreach ($token in $tokens) {
        ExpectStatus 204 POST '/auth/logout' $null $token
        ExpectStatus 401 GET '/auth/me' $null $token
    }
}
Write-Output 'PASS: logout revokes sessions. Test records remain only in the isolated integration volume.'
