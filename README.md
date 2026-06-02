OpenOrder Connection Core

Biblioteca interna do OpenOrder para seguranca e conexao LAN offline-first.

Escopo:

- contratos centrais de dispositivo, sessao, permissao e eventos
- pareamento por QR Code com sessionKey efemera
- token efemero criptografado com AES/GCM
- HTTP local para solicitacao de pareamento
- WebSocket autenticado
- discovery LAN por JmDNS
- persistencia local minima em SQLite

Fora do escopo:

- UI Compose
- dominio de restaurante/comandas/pedidos
- cloud/login online
- sincronizacao externa

Validacao local:

```bash
androidjava
./gradlew build
```
