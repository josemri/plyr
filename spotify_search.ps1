# Script de PowerShell para buscar en Spotify API
# Solo busca tracks, albums, artists y playlists (sin shows, episodes, audiobooks)

param(
    [Parameter(Mandatory=$true)]
    [string]$Query,
    
    [Parameter(Mandatory=$false)]
    [string]$ClientId = "f1b6dcd12bb14e73b72ea9f67ecf6d63",
    
    [Parameter(Mandatory=$false)]
    [string]$ClientSecret = "f30c65c7c87c478d975c93b8e43b1c82"
)

# Función para obtener token de acceso
function Get-SpotifyAccessToken {
    param($ClientId, $ClientSecret)
    
    $credentials = "$ClientId`:$ClientSecret"
    $encodedCredentials = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($credentials))
    
    $body = @{
        grant_type = "client_credentials"
    }
    
    $headers = @{
        "Authorization" = "Basic $encodedCredentials"
        "Content-Type" = "application/x-www-form-urlencoded"
    }
    
    try {
        $response = Invoke-RestMethod -Uri "https://accounts.spotify.com/api/token" -Method Post -Headers $headers -Body $body
        return $response.access_token
    }
    catch {
        Write-Error "Error obteniendo token: $_"
        return $null
    }
}

# Función para buscar en Spotify
function Search-Spotify {
    param($AccessToken, $Query)
    
    $encodedQuery = [uri]::EscapeDataString($Query)
    $uri = "https://api.spotify.com/v1/search?q=$encodedQuery&type=track,album,artist,playlist&limit=10"
    
    $headers = @{
        "Authorization" = "Bearer $AccessToken"
    }
    
    try {
        $response = Invoke-RestMethod -Uri $uri -Method Get -Headers $headers
        return $response
    }
    catch {
        Write-Error "Error en búsqueda: $_"
        return $null
    }
}

# Función para mostrar resultados en formato árbol
function Show-Results {
    param($Results)
    
    Write-Host "=== RESULTADOS DE BÚSQUEDA ===" -ForegroundColor Yellow
    Write-Host ""
    
    # Tracks
    if ($Results.tracks.items.Count -gt 0) {
        Write-Host "Tracks ($($Results.tracks.items.Count))" -ForegroundColor Green
        for ($i = 0; $i -lt $Results.tracks.items.Count; $i++) {
            $track = $Results.tracks.items[$i]
            $prefix = if ($i -eq $Results.tracks.items.Count - 1) { "└── " } else { "├── " }
            $artists = ($track.artists | ForEach-Object { $_.name }) -join ", "
            Write-Host "$prefix🎵 $($track.name) - $artists" -ForegroundColor White
        }
        Write-Host ""
    }
    
    # Albums
    if ($Results.albums.items.Count -gt 0) {
        Write-Host "Albums ($($Results.albums.items.Count))" -ForegroundColor Green
        for ($i = 0; $i -lt $Results.albums.items.Count; $i++) {
            $album = $Results.albums.items[$i]
            $prefix = if ($i -eq $Results.albums.items.Count - 1) { "└── " } else { "├── " }
            $artists = ($album.artists | ForEach-Object { $_.name }) -join ", "
            Write-Host "$prefix💿 $($album.name) - $artists ($($album.total_tracks) tracks)" -ForegroundColor White
        }
        Write-Host ""
    }
    
    # Artists
    if ($Results.artists.items.Count -gt 0) {
        Write-Host "Artists ($($Results.artists.items.Count))" -ForegroundColor Green
        for ($i = 0; $i -lt $Results.artists.items.Count; $i++) {
            $artist = $Results.artists.items[$i]
            $prefix = if ($i -eq $Results.artists.items.Count - 1) { "└── " } else { "├── " }
            $followers = if ($artist.followers.total -gt 0) { " ($($artist.followers.total) seguidores)" } else { "" }
            Write-Host "$prefix🎤 $($artist.name)$followers" -ForegroundColor White
        }
        Write-Host ""
    }
    
    # Playlists
    if ($Results.playlists.items.Count -gt 0) {
        Write-Host "Playlists ($($Results.playlists.items.Count))" -ForegroundColor Green
        for ($i = 0; $i -lt $Results.playlists.items.Count; $i++) {
            $playlist = $Results.playlists.items[$i]
            $prefix = if ($i -eq $Results.playlists.items.Count - 1) { "└── " } else { "├── " }
            $tracks = if ($playlist.tracks.total -gt 0) { " ($($playlist.tracks.total) songs)" } else { "" }
            Write-Host "$prefix📋 $($playlist.name)$tracks" -ForegroundColor White
        }
        Write-Host ""
    }
}

# Ejecutar búsqueda
Write-Host "Obteniendo token de acceso..." -ForegroundColor Cyan
$accessToken = Get-SpotifyAccessToken -ClientId $ClientId -ClientSecret $ClientSecret

if ($accessToken) {
    Write-Host "Buscando: '$Query'..." -ForegroundColor Cyan
    $results = Search-Spotify -AccessToken $accessToken -Query $Query
    
    if ($results) {
        Show-Results -Results $results
    }
} else {
    Write-Error "No se pudo obtener el token de acceso"
}

# Ejemplo de uso:
# .\spotify_search.ps1 -Query "the beatles"
