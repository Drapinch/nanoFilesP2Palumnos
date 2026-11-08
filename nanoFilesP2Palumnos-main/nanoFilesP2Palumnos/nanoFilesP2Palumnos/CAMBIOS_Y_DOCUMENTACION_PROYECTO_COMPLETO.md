# Memoria Completa de Cambios y Funcionalidades: NanoFiles P2P
## Asignatura: Redes de Comunicación - Universidad de Murcia (Curso 25-26)

Este documento recoge **todos los cambios implementados en el código con respecto al esqueleto inicial de la práctica**, cubriendo tanto la implementación de los boletines base del proyecto (Convocatorias I y II) como las cuatro funcionalidades ampliadas obligatorias para la Convocatoria III / Extraordinaria.

---

## Índice
1. [Arquitectura General del Sistema NanoFiles](#1-arquitectura-general-del-sistema-nanofiles)
2. [Evolución del Proyecto: De la Base a la Versión Final](#2-evolución-del-proyecto-de-la-base-a-la-versión-final)
3. [Cambios de la Práctica Base (Boletines 1 al 8)](#3-cambios-de-la-práctica-base-boletines-1-al-8)
   - [3.1. Comunicación UDP con el Directorio (Boletines 2, 3 y 4)](#31-comunicación-udp-con-el-directorio-boletines-2-3-y-4)
   - [3.2. Autómata de Estados del Shell (Boletín 5)](#32-autómata-de-estados-del-shell-boletín-5)
   - [3.3. Protocolo Binario P2P sobre TCP (Boletín 6)](#33-protocolo-binario-p2p-sobre-tcp-boletín-6)
   - [3.4. Servidor de Ficheros TCP y Concurrencia (Boletines 7 y 8)](#34-servidor-de-ficheros-tcp-y-concurrencia-boletines-7-y-8)
4. [Ampliaciones Obligatorias de la Convocatoria III / Extraordinaria](#4-ampliaciones-obligatorias-de-la-convocatoria-iii--extraordinaria)
   - [4.1. Requisito 1: Comando `quit` y desregistro garantizado en el directorio](#41-requisito-1-comando-quit-y-desregistro-garantizado-en-el-directorio)
   - [4.2. Requisito 2: Comando `peerdl *` con descarga secuencial round-robin y resumen de acciones](#42-requisito-2-comando-peerdl--con-descarga-secuencial-round-robin-y-resumen-de-acciones)
   - [4.3. Requisito 3: Tamaño de fragmento variable mediante `serve N`](#43-requisito-3-tamaño-de-fragmento-variable-mediante-serve-n)
   - [4.4. Requisito 4: Comando `whohas <filename_substring>` con detección de colisiones](#44-requisito-4-comando-whohas-filename_substring-con-detección-de-colisiones)
5. [Matriz Comparativa de Archivos Modificados](#5-matriz-comparativa-de-archivos-modificados)
6. [Guía de Compilación, Ejecución y Pruebas](#6-guía-de-compilación-ejecución-y-pruebas)

---

## 1. Arquitectura General del Sistema NanoFiles

El sistema NanoFiles es una red híbrida P2P compuesta por:
- **Servidor de Directorio Centralizado (UDP, puerto 6868)**:
  - Gestiona el censo de peers que están sirviendo ficheros activamente (`registeredPeers`).
  - Mantiene un catálogo de ficheros disponibles directamente en el directorio (`directoryFiles`, carpeta `dir-shared`).
  - Protocolo basado en datagramas UDP con mensajes ASCII en formato `campo:valor`.
- **Peers / Clientes NanoFiles (Shell interactivo)**:
  - Actúan como **clientes UDP** frente al Directorio para publicar su presencia, consultar peers y listar ficheros.
  - Actúan como **servidores TCP multihilo** para compartir su carpeta local (`nf-shared`) sirviendo fragmentos de ficheros a otros pares.
  - Actúan como **clientes TCP** para descargar ficheros o fragmentos de ficheros desde otros peers hacia su carpeta local (`nf-descargas`).

---

## 2. Evolución del Proyecto: De la Base a la Versión Final

El proyecto original proporcionado por el departamento constaba únicamente de esqueletos de clases con comentarios `// TODO:`, sin lógica de red, sin parseo de mensajes y sin gestión de sockets.

A lo largo del desarrollo se llevaron a cabo dos grandes etapas:
1. **Fase Base (Convocatoria II)**: Implementación de sockets UDP, protocolo de mensajes ASCII, autómata de estados, servidor concurrente TCP y protocolo binario de transferencia.
2. **Fase de Ampliación (Convocatoria III / Extraordinaria)**:
   - Desregistro en directorio al ejecutar `quit` o terminar el proceso.
   - Descarga secuencial multitarea con `peerdl *` y reporte de métricas por servidor.
   - Parametrización del chunk del servidor mediante `serve N` (en KB).
   - Búsqueda distribuida por nombre con `whohas <substr>`, identificando fuentes y colisiones de hash.

---

## 3. Cambios de la Práctica Base (Boletines 1 al 8)

### 3.1. Comunicación UDP con el Directorio (Boletines 2, 3 y 4)
- **`DirectoryConnector.java`**:
  - Creación del socket UDP (`DatagramSocket`) y resolución del destino (`directoryAddress = new InetSocketAddress(serverAddr, DIRECTORY_PORT)`).
  - Implementación de `sendAndReceiveDatagrams` con temporizador (`setSoTimeout(1000)`), búfer de recepción seguro y bucle de retransmisión hasta `MAX_NUMBER_OF_ATTEMPTS = 5`.
  - Extracción de bytes exactos del datagrama recibido evitando lecturas del búfer completo.
  - Pruebas `testSendAndReceive` ("ping" -> "pingok") y `pingDirectoryRaw` ("ping&PROTOCOL_ID" -> "welcome").
- **`DirMessage.java` y `DirMessageOps.java`**:
  - Diseño del protocolo ASCII en formato `campo:valor\n`.
  - Campos implementados: `operation`, `protocolid`, `status`, `nickname`, `serverport`, `filelist`, `peerlist`.
  - Métodos `fromString(String)` y `toString()` para parsear y serializar datagramas.
- **`NFDirectoryServer.java`**:
  - Servidor UDP en el puerto 6868 capaz de responder a operaciones `ping`, `login`, `register_server`, `unregister_server`, `filelist` y `peerlist`.
  - Carga recursiva de ficheros de la carpeta `dir-shared` mediante `FileInfo.loadFilesFromFolder`.

### 3.2. Autómata de Estados del Shell (Boletín 5)
- **`NFController.java`**:
  - Implementación de los tres estados del cliente:
    - `OFFLINE (0)`: Estado inicial. Solo permite `help`, `myfiles`, `ping` y `quit`.
    - `CONNECTED_TO_DIR (1)`: Tras un `ping` exitoso. Desbloquea `dirfiles`, `peers`, `peerfiles`, `peerdl`, `whohas` y `serve`.
    - `SERVER_RUNNING (2)`: Cuando el peer ha levantado su servidor TCP local y se ha registrado en el directorio.
  - Métodos `canProcessCommandInCurrentState()` y `updateCurrentState(boolean)`.

### 3.3. Protocolo Binario P2P sobre TCP (Boletín 6)
- **`PeerMessage.java` y `PeerMessageOps.java`**:
  - Mensajes codificados en binario mediante `DataInputStream` y `DataOutputStream`.
  - Opcodes:
    - `OPCODE_FILELIST_REQ (1)` / `OPCODE_FILELIST_RESP (11)`
    - `OPCODE_DOWNLOAD_REQ (2)` / `OPCODE_FILE_DATA (12)`
    - `OPCODE_ERR_NOT_FOUND (21)` / `OPCODE_ERR_AMBIGUOUS_HASH (22)`
  - Serialización de campos: `hash` (writeUTF), `name` (writeUTF), `size` (writeLong), datos binarios brutos (write / readFully), `chunkOffset` (writeLong), `chunkSize` (writeInt), y `totalFileSize` (writeLong).

### 3.4. Servidor de Ficheros TCP y Concurrencia (Boletines 7 y 8)
- **`NFServer.java`**:
  - Socket servidor TCP ligado al puerto 10000 (o fallback a puerto dinámico si está ocupado).
  - Bucle de escucha `accept()` concurrente ejecutado en segundo plano en un hilo independiente (`Runnable`).
- **`NFServerThread.java`**:
  - Para cada cliente conectado, crea y arranca un hilo trabajador (`Thread`) que procesa la petición de forma independiente sin bloquear a otros clientes.
- **`NFConnector.java`**:
  - Cliente TCP que conecta con la IP:puerto del peer remoto, enviando solicitudes de ficheros o de listado y leyendo las respuestas binarias.

---

## 4. Ampliaciones Obligatorias de la Convocatoria III / Extraordinaria

A continuación se describen en detalle las modificaciones que dan soporte a los cuatro requisitos añadidos:

### 4.1. Requisito 1: Comando `quit` y desregistro garantizado en el directorio
- **Problema previo**: Un peer registrado que salía con `quit` o con `Ctrl+C` no notificaba su desconexión al directorio. El directorio seguía entregando su IP a otros peers, provocando excepciones de conexión.
- **Solución implementada**:
  - En `NFControllerLogicDir.java`: se introdujo `registeredAsServer` y `isRegisteredAsServer()` para recordar si este nodo tiene un registro activo en el directorio.
  - En `NFController.java` (`COM_QUIT`): se invoca obligatoriamente `controllerPeer.stopFileServer(controllerDir)` y `controllerDir.unregisterFileServer()`.
  - En el constructor de `NFController.java`: se registró un `ShutdownHook` de la JVM:
    ```java
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
            if (controllerPeer != null && controllerPeer.serving()) {
                controllerPeer.stopFileServer(controllerDir);
            }
            if (controllerDir != null && controllerDir.isRegisteredAsServer()) {
                controllerDir.unregisterFileServer();
            }
        } catch (Exception e) {}
    }));
    ```
  - En `NFDirectoryServer.java`: se implementó el desregistro por nickname con mecanismo fallback por dirección IP del paquete UDP.

### 4.2. Requisito 2: Comando `peerdl *` con descarga secuencial round-robin y resumen de acciones
- **Problema previo**: La versión preliminar dividía el archivo en $N$ bloques fijos según el número de servidores (`totalSize / numServers`), lo que fallaba si cada servidor enviaba tamaños de chunk propios y no permitía tolerancia a fallos. Además, el resumen carecía de desglose cuantitativo.
- **Solución implementada**:
  - En `NFControllerLogicP2P.java`: se desarrolló `downloadFileFromServersSequential`:
    1. **Sondeo inicial**: Conexión de metadatos (offset 0, size 0) para obtener el nombre original, tamaño total (`totalSize`) y hash completo SHA-256.
    2. **Bucle secuencial round-robin**: Se descarga fragmento a fragmento (`chunkOffset`, `chunkSize = -1`), avanzando `currentOffset += bytesDescargados` y rotando el turno al siguiente servidor: `serverIndex = (serverIndex + 1) % numServers`.
    3. **Tolerancia a fallos**: Si el servidor en turno falla, se prueban automáticamente los demás servidores disponibles para ese mismo fragmento antes de fallar.
    4. **Integridad**: Comprobación SHA-256 sobre el fichero final guardado sin colisiones de nombre (`FileNameUtil.chooseAvailableName`).
    5. **Resumen de acciones obligatorio**: Se imprime una tabla estructurada con:
       - Fichero, tamaño en bytes y KB, hash verificado y ruta de guardado.
       - Por cada servidor: dirección, bytes descargados, número de fragmentos y porcentaje.

### 4.3. Requisito 3: Tamaño de fragmento variable mediante `serve N`
- **Problema previo**: `serve` no admitía parámetros y el tamaño de transferencia era fijo o completo en un único mensaje.
- **Solución implementada**:
  - En `NFCommands.java`: se amplió la definición y ayuda de `serve` indicando el argumento opcional `[N]`.
  - En `NFShell.java`: se permite pasar `serve [<chunk_size_in_KB>]` y se valida que $N > 0$ sea un entero válido.
  - En `NFController.java`: se procesa el argumento (por defecto 10 KB si se omite) y se transfiere a `startFileServer(controllerDir, serverChunkSizeKB)`.
  - En `NFServer.java`: constructor `NFServer(int chunkSizeKB)` donde `chunkSize = chunkSizeKB * 1024` bytes.
  - En `NFServer.serveFilesToClient`: al atender una petición `OPCODE_DOWNLOAD_REQ`, el servidor calcula:
    ```java
    int effectiveChunkSize = Math.min(requestedChunkSize > 0 ? requestedChunkSize : this.chunkSize, this.chunkSize);
    ```
    Garantizando que nunca se transfieren más de $N$ KB por fragmento. Se utiliza `RandomAccessFile.seek` y `readFully` para leer el fragmento exacto del disco.

### 4.4. Requisito 4: Comando `whohas <filename_substring>` con detección de colisiones
- **Problema previo**: No existía funcionalidad para localizar ficheros por nombre en la red ni para contemplar colisiones entre ficheros con el mismo nombre y diferente contenido.
- **Solución implementada**:
  - En `NFCommands.java`: se definió el código de comando `COM_WHOHAS = 15`.
  - En `NFShell.java`: se parsea y valida el uso obligatorio de exactamente un argumento: `whohas <filename_substring>`.
  - En `NFController.java`: se valida el estado (requiere `ping` previo) y se delega en `controllerPeer.whoHas`.
  - En `NFControllerLogicP2P.java` (`whoHas`):
    1. Consulta ficheros servidos por el directorio (`dirLogic.fetchFileList()`) y registra como fuente `Directorio @ <ip:puerto>`.
    2. Consulta peers registrados en el directorio (`dirLogic.fetchPeerList()`), excluye el nodo propio, conecta vía TCP con cada uno (`getPeerFileListData()`) y registra como fuente `<peerNick> @ <peerAddr>`.
    3. **Indexación por hash SHA-256**: Los resultados se almacenan en un `Map<String, WhoHasMatch>` usando como clave el hash.
    4. **Detección de colisiones**: Si un mismo nombre de archivo aparece con dos o más hashes distintos, se listan como entradas separadas e independientes y se muestra el aviso:
       `AVISO: Existe más de un fichero con este nombre y distinto hash.`

---

## 5. Matriz Comparativa de Archivos Modificados

| Paquete / Archivo | Estado Original (Base) | Estado Final Implementado |
|---|---|---|
| `shell.NFCommands` | Comandos básicos 1 al 14. Sin `whohas`. `serve` sin argumentos. | Añadido `COM_WHOHAS = 15`, `"whohas"`, y textos de ayuda actualizados para `quit`, `serve [N]`, `peerdl *` y `whohas`. |
| `shell.NFShell` | No aceptaba argumentos para `serve`. Desconocía `whohas`. | Soporte y validación estricta de parámetros para `serve [<N>]` (entero > 0) y `whohas <substr>` (1 argumento). |
| `logic.NFController` | Doble registro en directorio al servir. `quit` incompleto. Sin `whohas`. | Gestión de `COM_WHOHAS`, parámetro de fragmento en `COM_SERVE`, desregistro obligado en `COM_QUIT` y `ShutdownHook`. |
| `logic.NFControllerLogicDir` | Sin variable de estado de registro ni consulta de IP del directorio. | Añadido `isRegisteredAsServer()`, `registeredAsServer`, `fetchFileList()` y `getDirectoryAddress()`. |
| `logic.NFControllerLogicP2P` | Descarga de un solo bloque (`totalSize/N`). Sin búsqueda global. | `downloadFileFromServersSequential` (round-robin por chunks con resumen completo), `whoHas` (búsqueda distribuida por hash) y `startFileServer(dir, chunkSizeKB)`. |
| `tcp.server.NFServer` | Puerto fijo, sin tamaño de fragmento configurable, método estático sin contexto. | `NFServer(int chunkSizeKB)`, fragmentación estricta a $N$ KB con `RandomAccessFile`, vinculación con hilos de atención. |
| `tcp.server.NFServerThread` | Llamada estática genérica. | Hilo que conserva la referencia a la instancia de `NFServer` para respetar su tamaño de chunk $N$ KB configurado. |
| `tcp.client.NFConnector` | Descarga de fichero entero en una sola pieza. | Descarga por fragmentos con `downloadFileChunk(hash, offset, size)` y obtención de catálogo con `getPeerFileListData()`. |
| `tcp.message.PeerMessage` | Mensajes binarios básicos sin gestión robusta de nulos. | Serialización binaria segura de `chunkOffset`, `chunkSize`, `totalFileSize`, `hash`, `name` y datos. |
| `udp.server.NFDirectoryServer` | `unregister_server` vulnerable a pequeñas discrepancias de nickname. | Desregistro robusto por nickname y fallback por dirección IP del cliente UDP. |
| `udp.client.DirectoryConnector` | Solo registro básico. | Soporte de `unregisterFileServer()` y método `getDirectoryAddress()`. |

---

## 6. Guía de Compilación, Ejecución y Pruebas

### 6.1. Compilación
El proyecto se compila automáticamente mediante el IDE Eclipse (los archivos `.class` se generan en la carpeta `bin/`), o mediante terminal:
```bash
# Situados en: nanoFilesP2Palumnos/nanoFilesP2Palumnos-main/nanoFilesP2Palumnos/nanoFilesP2Palumnos
javac -d bin -sourcepath src src/es/um/redes/nanoFiles/application/*.java
```

### 6.2. Ejecución del Directorio
```bash
java -cp bin es.um.redes.nanoFiles.application.Directory
```

### 6.3. Ejecución de los Nodos Peers
En distintas ventanas de terminal:
```bash
# Peer 1 (carpeta compartida nf-shared por defecto)
java -cp bin es.um.redes.nanoFiles.application.NanoFiles

# Peer 2 (otra carpeta compartida, ej. David)
java -cp bin es.um.redes.nanoFiles.application.NanoFiles David
```

### 6.4. Verificación de los Comandos Ampliados

1. **Prueba de `serve N` y `whohas`**:
   - En Peer 1:
     ```
     ping
     serve 4
     ```
   - En Peer 2:
     ```
     ping
     whohas fichero
     ```
     *Muestra el fichero `fichero1.txt`, su hash SHA-256 y la lista de fuentes (Directorio y Peer 1).*

2. **Prueba de colisión de nombres en `whohas`**:
   - Crear en Peer 2 un fichero `fichero1.txt` con contenido diferente al de Peer 1.
   - En un Peer 3:
     ```
     ping
     whohas fichero1
     ```
     *Muestra dos entradas independientes para `fichero1.txt` con hashes diferentes y el mensaje de aviso de colisión.*

3. **Prueba de descarga secuencial con `peerdl *`**:
   - En Peer 3:
     ```
     peerdl * <subcadena_hash>
     ```
     *Descarga alternando fragmentos entre los servidores y muestra el resumen completo de la transferencia con métricas individuales.*

4. **Prueba de desregistro en `quit`**:
   - En Peer 1: ejecutar `quit`.
   - En Peer 2: ejecutar `peers`. El nickname de Peer 1 ya no aparece registrado.
