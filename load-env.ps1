# .env dosyasindaki degerleri, docker-compose.yml'nin yaptigi isim eslemesiyle
# birebir ayni sekilde, mevcut PowerShell oturumuna yukler.
# Kullanim (proje kok dizininde): . .\load-env.ps1
# Bastaki nokta+bosluk onemli ("dot sourcing") - yoksa degiskenler bu oturuma islenmez.

$envPath = ".env"
$lines = Get-Content $envPath
$map = @{}
foreach ($line in $lines) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
        $map[$matches[1]] = $matches[2]
    }
}

[System.Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", $map["POSTGRES_PASSWORD"], "Process")
[System.Environment]::SetEnvironmentVariable("JWT_SECRET", $map["JWT_SECRET"], "Process")
[System.Environment]::SetEnvironmentVariable("ENCRYPTION_SECRET_KEY", $map["ENCRYPTION_SECRET_KEY"], "Process")
[System.Environment]::SetEnvironmentVariable("BOOTSTRAP_ADMIN_USERNAME", $map["BOOTSTRAP_ADMIN_USERNAME"], "Process")
[System.Environment]::SetEnvironmentVariable("BOOTSTRAP_ADMIN_PASSWORD", $map["BOOTSTRAP_ADMIN_PASSWORD"], "Process")
[System.Environment]::SetEnvironmentVariable("BOOTSTRAP_ADMIN_EMAIL", $map["BOOTSTRAP_ADMIN_EMAIL"], "Process")

Write-Host "Ortam degiskenleri .env dosyasindan bu terminal oturumuna yuklendi."