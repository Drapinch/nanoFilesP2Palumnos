# Documentación de la Funcionalidad Ampliada (Convocatoria III / Extraordinaria)
## Asignatura: Redes de Comunicación - Proyecto NanoFilesP2P

Este documento detalla la comprobación, diseño, implementación y manual de uso de los cuatro requisitos mínimos obligatorios ampliados para la convocatoria actual:

---

### Índice
1. [Requisito 1: Comando `quit` da de baja los peers registrados en el directorio](#requisito-1-comando-quit-da-de-baja-los-peers-registrados-en-el-directorio)
2. [Requisito 2: Comando `peerdl *` con descarga secuencial y resumen de acciones](#requisito-2-comando-peerdl--con-descarga-secuencial-y-resumen-de-acciones)
3. [Requisito 3: Tamaño de chunk variable determinado con `serve N`](#requisito-3-tamaño-de-chunk-variable-determinado-con-serve-n)
4. [Requisito 4: Comando `whohas <filename_substring>` con detección de colisiones de nombre y hash](#requisito-4-comando-whohas-filename_substring)
5. [Resumen de Archivos Modificados](#resumen-de-archivos-modificados)
6. [Guía de Pruebas y Validación](#guía-de-pruebas-y-validación)

---

### Requisito 1: Comando `quit` da de baja los peers registrados en el directorio

#### Descripción y Objetivo
En versiones anteriores, al cerrar o finalizar un nodo (`peer`), este permanecía indefinidamente en la lista de servidores registrados (`registeredPeers`) del servidor de directorio. Como consecuencia, otros peers intentaban conectarse a nodos inexistentes (fantasmas). Con este requisito, la orden `quit` garantiza que el peer detiene su servidor local y solicita al directorio su baja inmediata.

#### Implementación Técnica
1. **Detección de estado registrado (`NFControllerLogicDir`)**:
   - Se añadió la variable booleana `registeredAsServer` y el método `isRegisteredAsServer()`.
   - Cuando `registerFileServer` tiene éxito, `registeredAsServer = true`.
   - Cuando `unregisterFileServer` tiene éxito, `registeredAsServer = false`.
2. **Lógica de salida en `NFController.java` (`COM_QUIT`)**:
   - Si el servidor local está activo (`controllerPeer.serving()`), se detiene con `controllerPeer.stopFileServer(controllerDir)`.
   - Si el peer estaba registrado en el directorio (`controllerDir.isRegisteredAsServer()`), se envía el mensaje `OPERATION_UNREGISTER_SERVER` al directorio mediante `controllerDir.unregisterFileServer()`.
3. **Gancho de apagado robusto (`ShutdownHook`)**:
   - En el constructor de `NFController`, se añade `Runtime.getRuntime().addShutdownHook(...)` para interceptar salidas inesperadas (p. ej. `Ctrl+C` o cierre de terminal), ejecutando automáticamente la baja en el directorio.
4. **Tratamiento en el Directorio (`NFDirectoryServer.java`)**:
   - Al recibir `OPERATION_UNREGISTER_SERVER`, el directorio elimina la entrada de `registeredPeers` por nickname, o por dirección IP/puerto del cliente como mecanismo de respaldo. Responde con `OPERATION_UNREGISTER_SERVER_OK`.

---

### Requisito 2: Comando `peerdl *` con descarga secuencial y resumen de acciones

#### Descripción y Objetivo
Permite descargar un fichero identificado por subcadena de hash consultando a todos los peers que lo comparten mediante el comodín `*`. La descarga debe ser **secuencial** a través de los distintos servidores (alternando fragmentos entre ellos) y debe mostrar obligatoriamente un **resumen detallado** de las acciones llevadas a cabo (a qué servidores se conecta, cuántos fragmentos y bytes se descargan de cada uno, y verificación de integridad).

#### Implementación Técnica
1. **Localización de servidores (`downloadFromPeers`)**:
   - `dirLogic.searchFilesByHash(targetHashSubstring)` localiza los peers que poseen el fichero por su hash.
   - Si la búsqueda es ambigua (múltiples hashes distintos coinciden con la subcadena), se avisa al usuario listando los hashes coincidentes.
   - Con el hash completo unívoco obtenido, se invoca a `downloadFileFromServersSequential(serverList, targetHashSubstring, fullHash)`.
2. **Descarga secuencial por fragmentos (`downloadFileFromServersSequential`)**:
   - Se realiza una primera conexión a un servidor para consultar los metadatos (nombre del fichero original, tamaño total en bytes `totalSize` y hash SHA-256).
   - Se inicializa el búfer de recepción en memoria `byte[] fileBuffer` con tamaño exacto `totalSize`.
   - Se mantiene un índice de desplazamiento (`currentOffset`) y un puntero de rotación de servidores (`serverIndex`).
   - En cada iteración:
     1. Se solicita el siguiente fragmento al servidor en turno `serverAddressList[serverIndex]`.
     2. El servidor responde con los datos del fragmento ajustados a su tamaño configurado de chunk.
     3. Los bytes recibidos se copian en `fileBuffer` en la posición `currentOffset`.
     4. Se actualizan las métricas por servidor: `bytesPerServer` y `chunksPerServer`.
     5. Se avanza el offset: `currentOffset += data.length`.
     6. Se rota al siguiente servidor para el siguiente fragmento: `serverIndex = (serverIndex + 1) % numServers`.
     7. En caso de fallo de red de un servidor, el sistema prueba automáticamente los demás servidores alternativos para el mismo fragmento antes de fallar.
3. **Escritura segura y verificación de integridad**:
   - Se guarda el fichero en el directorio local compartido utilizando `FileNameUtil.chooseAvailableName(...)` para evitar sobreescribir ficheros locales.
   - Se calcula el checksum SHA-256 local (`FileDigest.computeFileChecksumString`) y se compara con el hash esperado. Si no coincide, se elimina el fichero corrupto.
4. **Resumen de acciones obligatorio**:
   - Se imprime por consola una tabla estructurada con:
     - Nombre del fichero descargado y tamaño total (bytes y KB).
     - Hash SHA-256 verificado.
     - Ruta local donde se ha guardado el fichero.
     - Desglose por cada servidor: IP:puerto, bytes descargados, número de fragmentos y porcentaje del total.

---

### Requisito 3: Tamaño de chunk variable determinado con `serve N`

#### Descripción y Objetivo
Cada servidor de ficheros puede definir el tamaño en kilobytes (KB) de los fragmentos en los que divide los ficheros para su transferencia mediante el comando `serve N`, donde `N` es un entero positivo.

#### Implementación Técnica
1. **Ampliación sintáctica del shell (`NFCommands` y `NFShell`)**:
   - `serve` ahora acepta un argumento opcional `[N]`.
   - `NFShell.validateCommandArguments` comprueba que, si se proporciona `N`, sea un número entero mayor que 0.
   - En caso de error de sintaxis, muestra: `Correct use: serve [<chunk_size_in_KB>]`.
   - Si no se especifica `N`, se aplica un valor por defecto estándar (10 KB).
2. **Configuración en el servidor TCP (`NFServer`)**:
   - `NFServer(int chunkSizeKB)` calcula `chunkSize = chunkSizeKB * 1024` (bytes).
   - `serveFilesToClientInternal` utiliza este límite: al procesar `OPCODE_DOWNLOAD_REQ`, el servidor calcula el tamaño efectivo a enviar como `Math.min(requestedChunkSize, this.chunkSize)`.
   - Nunca envía más bytes de los permitidos por su tamaño de fragmento configurado `N`.
   - Lee el fragmento exacto usando `RandomAccessFile.seek(chunkOffset)` y `RandomAccessFile.readFully(fileData)`.
   - Comunica al cliente el `chunkOffset` y la longitud real enviada en `PeerMessage` con `OPCODE_FILE_DATA`.

---

### Requisito 4: Comando `whohas <filename_substring>`

#### Descripción y Objetivo
Permite buscar ficheros compartidos por otros nodos y por el directorio a partir de una subcadena del nombre del fichero. La consulta muestra:
- Nombre y tamaño del fichero.
- Hash SHA-256 de cada fichero coincidente.
- Lista de nodos que lo comparten (incluyendo el directorio en caso de servirlo).
- **Detección explícita de colisiones de nombre con distinto hash**: Si dos o más nodos comparten un fichero con el mismo nombre pero diferente contenido (distinto hash), el sistema los trata y muestra como entradas independientes y añade un aviso específico al usuario.

#### Implementación Técnica
1. **Comando en shell (`NFCommands` y `NFShell`)**:
   - Código de comando `COM_WHOHAS = 15`.
   - Nombre de comando: `"whohas"`.
   - Requiere exactamente 1 argumento: `<filename_substring>`.
   - Solo permitido tras hacer `ping` al directorio (`currentState != OFFLINE`).
2. **Estructura de agrupación (`WhoHasMatch`)**:
   - Los resultados se indexan en un `Map<String, WhoHasMatch>` usando como **clave el hash SHA-256**.
   - Cada `WhoHasMatch` contiene el hash, el conjunto de nombres asociados (`Set<String> fileNames`), el tamaño y la lista de fuentes (`List<String> sharers`).
   - De esta forma, si dos ficheros tienen el mismo nombre pero diferente hash, constituyen dos entradas separadas e identificables unívocamente.
3. **Recolección de datos (`whoHas` en `NFControllerLogicP2P`)**:
   - **Directorio**: Invoca `dirLogic.fetchFileList()`. Para cada fichero cuyo nombre contenga la subcadena, se añade a la entrada correspondiente a su hash y se registra como fuente `"Directorio @ <ip:puerto>"`.
   - **Peers**: Invoca `dirLogic.fetchPeerList()`. Para cada peer registrado (excluyendo el nodo local):
     - Se conecta vía TCP usando `NFConnector`.
     - Solicita la lista de ficheros con `getPeerFileListData()`.
     - Para cada fichero coincidente, se añade al mapa por hash y se registra como fuente `"<nickname> @ <ip:puerto>"`.
4. **Formato de salida**:
   - Se detecta si algún nombre aparece asociado a más de un hash diferente.
   - En tal caso, se imprime un aviso: `AVISO: Existe más de un fichero con este nombre y distinto hash.`
   - Se muestra la información detallada y limpia de cada fichero encontrado.

---

### Resumen de Archivos Modificados

| Archivo | Ruta | Cambios Principales |
|---|---|---|
| `NFCommands.java` | `.../shell/NFCommands.java` | Añadido `COM_WHOHAS = 15`, `"whohas"`, ayudas de comandos actualizadas (`serve [N]`, `peerdl *`, `whohas`, `quit`). |
| `NFShell.java` | `.../shell/NFShell.java` | Soporte de argumentos para `serve [N]` y `whohas <substr>`. Validación de parámetros. |
| `NFController.java` | `.../logic/NFController.java` | Manejo de `COM_WHOHAS`, `COM_SERVE` con argumento de chunk, `COM_QUIT` con baja obligatoria y `ShutdownHook`. |
| `NFControllerLogicDir.java` | `.../logic/NFControllerLogicDir.java` | Control de estado `isRegisteredAsServer()`, métodos `fetchFileList()` y `getDirectoryAddress()`. |
| `NFControllerLogicP2P.java` | `.../logic/NFControllerLogicP2P.java` | Implementación de `whoHas(...)`, descarga secuencial `downloadFileFromServersSequential(...)` con resumen de acciones, y soporte de chunk N en `startFileServer`. |
| `NFConnector.java` | `.../tcp/client/NFConnector.java` | Gestión de descargas de chunks con `downloadFileChunk`, obtención estructurada de listas con `getPeerFileListData()`. |
| `PeerMessage.java` | `.../tcp/message/PeerMessage.java` | Gestión segura de campos binarios (`hash`, `name`, `chunkOffset`, `chunkSize`, `totalFileSize`). |
| `NFServer.java` | `.../tcp/server/NFServer.java` | Tamaño de chunk variable en constructor `NFServer(int chunkSizeKB)`, fraccionamiento estricto a `N` KB en `serveFilesToClient`. |
| `NFServerThread.java` | `.../tcp/server/NFServerThread.java` | Asociación del hilo con la instancia de `NFServer` para heredar su tamaño de chunk configurado. |
| `NFDirectoryServer.java` | `.../udp/server/NFDirectoryServer.java` | Baja de servidores en `OPERATION_UNREGISTER_SERVER` con eliminación en `registeredPeers` y fallback por IP. |
| `DirectoryConnector.java` | `.../udp/client/DirectoryConnector.java` | Mensaje de baja `unregisterFileServer`, obtención de dirección del directorio. |

---

### Guía de Pruebas y Validación

#### Prueba 1: `quit` da de baja el peer en el directorio
1. Iniciar el directorio:
   ```bash
   java -cp bin es.um.redes.nanoFiles.application.Directory
   ```
2. En una terminal (Peer A), arrancar NanoFiles y hacer ping:
   ```
   ping
   serve 10
   ```
3. En otra terminal (Peer B), arrancar NanoFiles y consultar peers:
   ```
   ping
   peers
   ```
   *Resultado esperado*: Aparece el nickname de Peer A en la lista de peers registrados.
4. En Peer A, ejecutar:
   ```
   quit
   ```
   *Resultado esperado*: Muestra mensaje de confirmación de desregistro en el directorio.
5. En Peer B, volver a ejecutar:
   ```
   peers
   ```
   *Resultado esperado*: Peer A ya NO aparece en la lista (`(none)`).

---

#### Prueba 2: Tamaño de chunk variable con `serve N`
1. Arrancar un peer con un tamaño de chunk de 4 KB:
   ```
   serve 4
   ```
   *Resultado esperado*: Mensaje de confirmación:
   `* Servidor de ficheros arrancado en segundo plano, escuchando en el puerto ... (tamaño de fragmento configurado: 4 KB)`
2. Al solicitar un fichero de 12 KB, el servidor enviará 3 fragmentos de 4 KB (4096 bytes) cada uno.

---

#### Prueba 3: Descarga secuencial con `peerdl *` y resumen de acciones
1. Configurar dos peers (Peer A con `serve 4` y Peer B con `serve 8`), ambos compartiendo un mismo fichero (p. ej. `fichero1.txt`).
2. En Peer C, ejecutar:
   ```
   ping
   peerdl * <subcadena_hash>
   ```
3. *Resultado esperado*:
   - Muestra la descarga fragmento a fragmento alternando entre Peer A y Peer B.
   - Al finalizar, muestra el resumen estructurado:
     ```
     ======================================================================
                        RESUMEN DE DESCARGA SECUENCIAL                     
     ======================================================================
     Fichero descargado:    fichero1.txt
     Tamaño total:          ... bytes (... KB)
     Hash SHA-256:          ...
     Ruta local:            nf-descargas/fichero1.txt
     Integridad SHA-256:    VERIFICADA (OK)

     Detalle de acciones por servidor:
      - Servidor 127.0.0.1:10000: ... bytes en ... fragmento(s)
      - Servidor 127.0.0.1:10001: ... bytes en ... fragmento(s)
     ======================================================================
     ```

---

#### Prueba 4: Búsqueda con `whohas <filename_substring>`
1. Con ficheros en la carpeta `dir-shared` del directorio y en las carpetas de los peers registrados:
   - Crear un fichero `test.txt` en el directorio.
   - Crear un fichero `test.txt` con el **mismo contenido** en Peer A.
   - Crear un fichero `test.txt` con **distinto contenido** en Peer B.
2. Ejecutar en otro peer:
   ```
   ping
   whohas test
   ```
3. *Resultado esperado*:
   - Muestra 2 entradas separadas para `test.txt` (una para cada hash SHA-256 distinto).
   - La primera entrada muestra que es compartida por el Directorio y por Peer A.
   - La segunda entrada muestra el aviso:
     `AVISO: Existe más de un fichero con este nombre y distinto hash.`
     y muestra que es compartida únicamente por Peer B.
