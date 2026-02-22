# data/remote - Online Metadata APIs

## OVERVIEW
Multi-source metadata fetching layer with 4 API integrations.

## APIs
| Provider | Purpose | Key Files |
|----------|---------|-----------|
| Wangy | Chinese music search + lyrics | `wangy/WangyApi.kt`, `wangy/WangyCrypto.kt` |
| Tengx | Chinese music search | `tengx/TengxApi.kt` |
| MusicBrainz | Western music metadata | `musicbrainz/MusicBrainzApi.kt` |
| iTunes | Apple Music metadata | `itunes/ITunesApi.kt` |


## PATTERNS
- Each API has: `*Api.kt` (Retrofit interface) + `*Repository.kt` (business logic)
- Response models in `*/model/` subdirectories
- Wangy uses custom crypto (`wangy/crypto/WangyCrypto.kt`)

## CONVENTIONS
- Use `NetworkConstants.kt` for base URLs
- Repository returns `Result<T>` for error handling
- Rate limiting: MusicBrainz requires 1 req/sec

## ANTI-PATTERNS
- NEVER hardcode API keys in source code
- NEVER expose sensitive crypto logic in public APIs
