# EDA con Redis Streams

Demostración de una arquitectura orientada por eventos usando Redis Streams como broker. El sistema simula transferencias bancarias donde múltiples servicios reaccionan al mismo evento sin acoplarse entre sí.

## ¿Cómo funciona?

Cuando ocurre una transferencia, el productor publica un evento `TransferenciaCreada` en un stream de Redis. Tres grupos de consumidores independientes (antifraude, notificaciones y auditoría) leen ese mismo evento sin competir entre sí. Si un consumidor se cae antes de confirmar el procesamiento, el evento queda pendiente y otro consumidor puede reclamarlo.

```
Productor → XADD → banco.transferencias
                          ↓
              fraude-group    → consumidor-1 → ACK 
              notif-group     → consumidor-1 → ACK
              auditoria-group → auditor-1 → CAIDA
                                  → XPENDING detecta huérfanos
                                  → auditor-2 → XCLAIM + ACK
```

## Requisitos

- Docker Desktop
- Java 21
- Maven

## Cómo correrlo

**1. Levantar Redis**

```bash
docker run --name redis-eda -p 6379:6379 -d redis:7
docker exec -it redis-eda redis-cli ping
```

Debe responder `PONG`.

![Redis corriendo](docs/redis-ping.png)

**2. Crear los grupos de consumidores**

```bash
docker exec -it redis-eda redis-cli
```

```
XGROUP CREATE banco.transferencias fraude-group $ MKSTREAM
XGROUP CREATE banco.transferencias notif-group $
XGROUP CREATE banco.transferencias auditoria-group $
```

**3. Correr el Productor**

Ejecutar `Productor.java` desde IntelliJ. Publica 5 eventos `TransferenciaCreada` al stream con un segundo de delay entre cada uno.

![Productor publicando eventos](docs/productor.png)

**4. Correr el Consumidor**

Ejecutar `Consumidor.java` desde IntelliJ. Lee y procesa los eventos del grupo `fraude-group` y envía ACK por cada uno. Cuando no hay más eventos, termina solo.

![Consumidor procesando eventos](docs/consumidor.png)

## Simulación de caída

Para simular que un consumidor se cae antes de confirmar el procesamiento, se usa `XPENDING` para ver los eventos huérfanos y `XCLAIM` para que otro consumidor los reclame:

```
# Ver eventos pendientes
XPENDING banco.transferencias auditoria-group - + 10

# Otro consumidor reclama los eventos huérfanos
XCLAIM banco.transferencias auditoria-group auditor-2 0 <ID>
XACK banco.transferencias auditoria-group <ID>

# Verificar que no quedó nada pendiente
XPENDING banco.transferencias auditoria-group - + 10
```

## Estructura del proyecto

```
eda-redis-streams/
├── src/main/java/edu/eci/arsw/
│   ├── Productor.java
│   └── Consumidor.java
├── docs/
│   ├── redis-ping.png
│   ├── productor.png
│   └── consumidor.png
└── pom.xml
```

## Conceptos clave

- **Stream**: canal persistente donde viven los eventos. A diferencia de Pub/Sub, los eventos no se pierden si el consumidor no está conectado.
- **Consumer Group**: permite que varios servicios lean el mismo stream de forma independiente sin competir entre sí.
- **ACK**: confirmación de que el evento fue procesado. Sin ACK el evento queda pendiente.
- **XCLAIM**: mecanismo de recuperación ante fallos — permite que otro consumidor se apropie de un evento huérfano.