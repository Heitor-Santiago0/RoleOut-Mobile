# Creates test accounts and one role only in the isolated integration environment.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8082/RoleOut/api/v1'
$compose = Join-Path $PSScriptRoot '../compose.integration.yml'
function Request($method, $path, $body = $null, $token = $null) {
    $requestOptions = @{ Uri = "$base$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 20; Headers = @{ Origin = 'http://localhost:8081' } }
    if ($token) { $requestOptions.Headers.Authorization = "Bearer $token" }
    if ($null -ne $body) {
        $requestOptions.ContentType = 'application/json; charset=utf-8'
        $requestOptions.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8 -Compress))
    }
    $response = Invoke-WebRequest @requestOptions
    if ($response.Headers['Access-Control-Allow-Origin'] -ne 'http://localhost:8081') { throw 'Missing explicit CORS origin' }
    $response
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
$adminId = $null
function PendingReview($orgId, $venueId, $token) {
    for ($offset = 0; $offset -le 100000; $offset += 20) {
        $page = (Request GET "/revisoes?deslocamento=$offset" $null $token).Content | ConvertFrom-Json
        $match = @($page.itens | Where-Object { $_.organizacaoId -eq $orgId -and $_.localId -eq $venueId })
        if ($match.Count -gt 0) { return $match[0] }
        if (@($page.itens).Count -lt 20) { break }
    }
    throw 'Review fixture not found'
}
$preflight = Invoke-WebRequest "$base/roteiros" -Method OPTIONS -UseBasicParsing -Headers @{
    Origin = 'http://localhost:8081'
    'Access-Control-Request-Method' = 'POST'
    'Access-Control-Request-Headers' = 'authorization,content-type'
}
Assert ($preflight.StatusCode -eq 204) 'Browser preflight failed'
Assert ($preflight.Headers['Access-Control-Allow-Origin'] -eq 'http://localhost:8081') 'Preflight origin failed'
try {
    foreach ($owner in @('a', 'b', 'reviewer')) {
        $email = "integration-$suffix-$owner@example.invalid"
        ExpectStatus 201 POST '/auth/cadastro' @{ nome = "Integration $owner"; email = $email; senha = $password }
        $session = (Request POST '/auth/login' @{ email = $email; senha = $password }).Content | ConvertFrom-Json
        $tokens += $session.token
        if ($owner -eq 'reviewer') { $adminId = [int]$session.conta.id }
        ExpectStatus 200 GET '/auth/me' $null $session.token
    }
    $preferences = (Request GET '/preferencias' $null $tokens[0]).Content | ConvertFrom-Json
    Assert ($preferences.perfil.status -eq 'PENDENTE') 'New preferences must be pending'
    $choices = @{ pular = $false; musica = @('Rock'); comida = @(); atividades = @('Museus'); ambientes = @('Tranquilo') }
    ExpectStatus 200 POST '/preferencias' $choices $tokens[0]
    ExpectStatus 200 POST '/preferencias' $choices $tokens[0]
    $loaded = (Request GET '/preferencias' $null $tokens[0]).Content | ConvertFrom-Json
    Assert ($loaded.perfil.musica[0] -eq 'Rock') 'Preferences did not persist'
    $otherPreferences = (Request GET '/preferencias' $null $tokens[1]).Content | ConvertFrom-Json
    Assert ($otherPreferences.perfil.status -eq 'PENDENTE') 'Preferences leaked between accounts'
    $choices.musica = @('not-in-catalog')
    ExpectStatus 400 POST '/preferencias' $choices $tokens[0]
    $skip = @{ pular = $true; musica = @(); comida = @(); atividades = @(); ambientes = @() }
    ExpectStatus 200 POST '/preferencias' $skip $tokens[1]
    $skipped = (Request GET '/preferencias' $null $tokens[1]).Content | ConvertFrom-Json
    Assert ($skipped.perfil.status -eq 'PULADO') 'Skip was not persisted'
    ExpectStatus 401 GET '/preferencias'
    Write-Output 'PASS: preference persistence, repeated saves, catalog validation, skip and account isolation.'
    $orgInput = @{ nome = "Organization $suffix"; tipo = 'MUSEU'; contatoEmail = "org-$suffix@example.invalid"; descricao = 'Integration fixture'; contatoTelefone = '' }
    $orgResponse = Request POST '/organizacoes' $orgInput $tokens[0]
    Assert ($orgResponse.StatusCode -eq 201) 'Organization creation failed'
    $org = $orgResponse.Content | ConvertFrom-Json
    Assert ($org.status -eq 'PENDENTE') 'Organization must require approval'
    $orgInput.nome = "Updated $suffix"
    ExpectStatus 200 PUT "/organizacoes/$($org.id)" $orgInput $tokens[0]
    ExpectStatus 404 GET "/organizacoes/$($org.id)" $null $tokens[1]
    ExpectStatus 404 PUT "/organizacoes/$($org.id)" $orgInput $tokens[1]
    $venueInput = @{ nome = 'Integration venue'; categoria = 'Cultura'; endereco = 'Test street 1'; cidade = 'Cidade Integracao'; uf = 'MG'; cep = '38000-000'; descricao = ''; acessibilidade = '' }
    $venue = (Request POST "/organizacoes/$($org.id)/locais" $venueInput $tokens[0]).Content | ConvertFrom-Json
    Assert ($venue.status -eq 'RASCUNHO') 'Venue must remain a draft'
    $venueInput.nome = 'Updated venue'
    ExpectStatus 200 PUT "/organizacoes/$($org.id)/locais/$($venue.id)" $venueInput $tokens[0]
    $venueRead = (Request GET "/organizacoes/$($org.id)/locais/$($venue.id)" $null $tokens[0]).Content | ConvertFrom-Json
    Assert ($venueRead.nome -eq 'Updated venue') 'Venue edit was not saved'
    ExpectStatus 404 GET "/organizacoes/$($org.id)/locais" $null $tokens[1]
    $venueInput.status = 'PUBLICADO'
    ExpectStatus 400 PUT "/organizacoes/$($org.id)/locais/$($venue.id)" $venueInput $tokens[0]
    Write-Output 'PASS: organization and venue create/edit/read, private ownership and draft status.'
    ExpectStatus 403 GET '/revisoes' $null $tokens[0]
    ExpectStatus 404 POST "/organizacoes/$($org.id)/locais/$($venue.id)/enviar" $null $tokens[1]
    ExpectStatus 200 POST "/organizacoes/$($org.id)/locais/$($venue.id)/enviar" $null $tokens[0]
    # Provision only the fresh, uniquely named test account in the isolated compose project.
    "UPDATE contas SET administrador=true WHERE id=$adminId AND email='integration-$suffix-reviewer@example.invalid';" | docker compose -f $compose exec -T mysql mysql -uroleout -pintegration-local-only -D roleout
    Assert ($LASTEXITCODE -eq 0) 'Test admin provisioning failed'
    $pending = PendingReview $org.id $venue.id $tokens[2]
    $decision = @{ organizacaoId = $org.id; localId = $venue.id; versao = $pending.versao; decisao = 'APROVAR'; justificativa = 'Integration review' }
    ExpectStatus 409 POST '/revisoes' $decision $tokens[2]
    $pending = PendingReview $org.id $null $tokens[2]
    $decision.localId = $null; $decision.versao = $pending.versao
    ExpectStatus 403 POST '/revisoes' $decision $tokens[0]
    ExpectStatus 200 POST '/revisoes' $decision $tokens[2]
    ExpectStatus 409 POST '/revisoes' $decision $tokens[2]
    $pending = PendingReview $org.id $venue.id $tokens[2]
    $decision.localId = $venue.id; $decision.versao = $pending.versao
    ExpectStatus 200 POST '/revisoes' $decision $tokens[2]
    $published = (Request GET "/organizacoes/$($org.id)/locais/$($venue.id)" $null $tokens[0]).Content | ConvertFrom-Json
    Assert ($published.status -eq 'PUBLICADO') 'Venue approval failed'
    $history = (Request GET "/organizacoes/$($org.id)/revisoes" $null $tokens[0]).Content | ConvertFrom-Json
    Assert (@($history.itens).Count -eq 2) 'Review audit history failed'
    ExpectStatus 404 GET "/organizacoes/$($org.id)/revisoes" $null $tokens[1]
    Write-Output 'PASS: submit, admin authorization, organization-before-venue approval, duplicate decision conflict and owner history.'
    $draftPath = "/rascunhos-roles/$($venue.id)"
    $draftInput = @{ nome = "Draft $suffix"; descricao = 'Private draft'; categoria = 'Cultura'; data = '2027-02-28'; valor = 12.35 }
    ExpectStatus 401 GET $draftPath
    ExpectStatus 404 POST $draftPath $draftInput $tokens[1]
    $draft = (Request POST $draftPath $draftInput $tokens[0]).Content | ConvertFrom-Json
    Assert ($draft.versao -eq 1 -and $draft.valor -eq 12.35) 'Draft creation failed'
    ExpectStatus 404 GET $draftPath $null $tokens[1]
    $draftInput.versao = 1; $draftInput.valor = 0; $draftInput.data = $null
    ExpectStatus 200 PUT "$draftPath/$($draft.id)" $draftInput $tokens[0]
    ExpectStatus 409 PUT "$draftPath/$($draft.id)" $draftInput $tokens[0]
    $drafts = (Request GET $draftPath $null $tokens[0]).Content | ConvertFrom-Json
    Assert ($drafts.itens[0].versao -eq 2 -and $drafts.itens[0].valor -eq 0 -and $null -eq $drafts.itens[0].data) 'Draft edit failed'
    $draftInput.versao = 2; $draftInput.status = 'PUBLICADO'
    ExpectStatus 400 PUT "$draftPath/$($draft.id)" $draftInput $tokens[0]
    $publicCount = "SELECT COUNT(*) FROM roles WHERE nome='Draft $suffix';" | docker compose -f $compose exec -T mysql mysql -uroleout -pintegration-local-only -D roleout -N
    Assert ($LASTEXITCODE -eq 0 -and [int]($publicCount | Select-Object -Last 1) -eq 0) 'Draft leaked into public roles'
    Write-Output 'PASS: private role drafts, price per person, optional date, stale edits, strict JSON and account isolation.'
    ExpectStatus 403 GET '/revisoes-roles' $null $tokens[0]
    ExpectStatus 404 POST "$draftPath/$($draft.id)/enviar" @{ versao = 2 } $tokens[1]
    ExpectStatus 200 POST "$draftPath/$($draft.id)/enviar" @{ versao = 2 } $tokens[0]
    ExpectStatus 409 POST "$draftPath/$($draft.id)/enviar" @{ versao = 2 } $tokens[0]
    $draftInput.Remove('status')
    $draftInput.versao = 3
    ExpectStatus 409 PUT "$draftPath/$($draft.id)" $draftInput $tokens[0]
    $roleDecision = @{ rascunhoId = $draft.id; versao = 3; decisao = 'REJEITAR'; justificativa = 'Please clarify the activity' }
    ExpectStatus 403 POST '/revisoes-roles' $roleDecision $tokens[0]
    ExpectStatus 200 POST '/revisoes-roles' $roleDecision $tokens[2]
    $draftInput.versao = 4; $draftInput.descricao = 'Clarified activity'
    ExpectStatus 200 PUT "$draftPath/$($draft.id)" $draftInput $tokens[0]
    ExpectStatus 200 POST "$draftPath/$($draft.id)/enviar" @{ versao = 5 } $tokens[0]
    ExpectStatus 409 POST '/revisoes-roles' $roleDecision $tokens[2]
    $roleDecision.versao = 6; $roleDecision.decisao = 'APROVAR'; $roleDecision.justificativa = 'Activity verified'
    $approvedRole = (Request POST '/revisoes-roles' $roleDecision $tokens[2]).Content | ConvertFrom-Json
    ExpectStatus 409 POST '/revisoes-roles' $roleDecision $tokens[2]
    $publicRole = (Request GET "/roles/$($approvedRole.role.roleId)").Content | ConvertFrom-Json
    Assert ($publicRole.nome -eq $draftInput.nome -and $publicRole.valor -eq 0) 'Approved role not public'
    $roleHistory = (Request GET "$draftPath/$($draft.id)/revisoes" $null $tokens[0]).Content | ConvertFrom-Json
    Assert (@($roleHistory.itens).Count -eq 2) 'Role review audit failed'
    ExpectStatus 404 GET "$draftPath/$($draft.id)/revisoes" $null $tokens[1]
    $legacy = Invoke-WebRequest "http://127.0.0.1:8082/RoleOut/roles?acao=excluir&id=$($approvedRole.role.roleId)" -UseBasicParsing
    Assert ($legacy.StatusCode -eq 200) 'Legacy page unavailable'
    ExpectStatus 200 GET "/roles/$($approvedRole.role.roleId)"
    Write-Output 'PASS: role submission, rejection/correction, audited approval, public detail and protection from legacy deletion.'
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
    if ($null -ne $adminId) {
        "UPDATE contas SET administrador=false WHERE id=$adminId AND email='integration-$suffix-reviewer@example.invalid';" | docker compose -f $compose exec -T mysql mysql -uroleout -pintegration-local-only -D roleout
        Assert ($LASTEXITCODE -eq 0) 'Failed to revoke test admin rights'
    }
    foreach ($token in $tokens) {
        ExpectStatus 204 POST '/auth/logout' $null $token
        ExpectStatus 401 GET '/auth/me' $null $token
    }
}
Write-Output 'PASS: logout revokes sessions. Test records remain only in the isolated integration volume.'
