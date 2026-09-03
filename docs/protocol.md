# LAN protocol v1

The Bonjour service type is `_todobar-sync._tcp`. Each TCP connection carries one request and one response. A frame is a four-byte unsigned big-endian length followed by UTF-8 JSON, with a maximum body size of 1,048,576 bytes.

The outer envelope fields are `version`, `kind`, `serverID`, nullable `deviceID`, and `sealedPayload`. `sealedPayload` is base64 of `12-byte nonce || ciphertext || 16-byte tag` produced by AES-256-GCM. Its authenticated additional data is the UTF-8 string `version|kind|serverID|deviceID`; a null device ID contributes an empty final component.

Message pairs are `pair`/`pair_response`, `sync`/`sync_response`, and `unbind`/`unbind_response`. Pairing uses the QR provisional key and returns a random session key. Other messages use that session key. A sync carries ordered `{id,text,createdAt}` values and returns `acknowledgedIDs`; Android marks only those IDs delivered after authenticating the response.

Payloads and limits are validated before persistence. Implementations must never log todo text, provisional/session keys, QR contents, or signing secrets.
