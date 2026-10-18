# Cobros y abonos desde MINA Móvil

## Recorrido habitual

Acuerdo → OT → procesos y costos → finalizar trabajo → ingreso del servicio → cobro o abonos.

Desde el detalle de la OT, abra **Cobros y abonos de la OT**. La pantalla incluye los pasos, cliente, caja asignada, total de cada ingreso, cobrado confirmado, pendiente local y saldo disponible. El ingreso se puede registrar en móvil después de finalizar la OT; una OT cancelada no admite nuevos ingresos ni nuevos cobros. Una OT propia sin cliente no tiene a quién cobrar.

La tarifa del acuerdo, el ingreso y el cobro son registros distintos. Registrar la tarifa o finalizar la OT no genera dinero en caja. El formulario de cobro registra dinero realmente recibido; no es una promesa de pago.

## En Online

1. Confirme que existe el ingreso del servicio.
2. Seleccione moneda, método de pago, fecha y monto aplicado a uno o varios ingresos de la misma OT. Agregue referencia u observaciones si corresponde.
3. Revise la confirmación con cliente, OT, caja y total recibido.
4. La app conserva primero la operación en SQLite y la envía con una clave única. El servidor crea el cobro Finalizado, sus aplicaciones y la entrada de caja en una misma transacción.
5. Si falta respuesta, el pago permanece pendiente: se reenvía con la misma clave. No se captura nuevamente como otro pago.

El servidor calcula el total a partir de las líneas y valida cliente, pertenencia de ingresos, caja asignada activa, moneda, método e importes. Revisa los pagos confirmados actuales antes de aceptar un abono. El historial de la pantalla muestra las aplicaciones de los cobros de esta OT en la caja asignada; el saldo considera pagos confirmados de todas las cajas.

## En Offline

Prepare una **descarga nueva con la versión de API que incluye cobros** antes de salir sin conexión. La descarga incluye condiciones de caja, monedas, métodos y saldos de ingresos accesibles. También se puede registrar un pago de una OT y un ingreso creados localmente: al subir, se resuelven sus identificadores temporales en el orden de la cola.

Ejemplo: ingreso C$6,000, abono recibido C$2,000.

- El teléfono guarda C$2,000 como **pendiente de sincronizar**.
- La pantalla reserva ese importe: muestra C$4,000 disponibles para otro abono local.
- El servidor todavía no confirma ese cobro ni registra la entrada de caja.
- Con conexión, abra **Subir datos**, envíe la cola y vuelva a descargar para consultar el saldo actualizado.
- Al aceptar el pago, el servidor registra únicamente C$2,000 en caja, sin duplicar el ingreso del servicio.

El efectivo recibido físicamente puede existir aunque todavía no esté sincronizado: el estado pendiente describe el registro en el sistema. Las operaciones quedan ligadas al usuario que las guardó y sobreviven al cierre de la app.

## Reintentos y conflictos

- **Nunca enviado:** puede quitar el pendiente local para corregirlo, con confirmación. Si recibió dinero, registre después el dato correcto.
- **Rechazado explícitamente con reversión del lote:** se conserva la cola. Revise el motivo; puede quitar el pendiente para corregir el monto, caja u otros datos y volver a enviarlo. El servidor no deja un cobro nuevo parcialmente creado.
- **Envío sin respuesta confirmada:** no se permite quitarlo ni registrar otro pago de esa OT. Reintente el mismo pendiente; si el servidor ya lo aceptó, devuelve su resultado anterior sin crear otro cobro ni movimiento.
- Un rechazo posterior a un envío incierto no prueba que el primero no haya llegado; el pendiente conserva la condición de respuesta desconocida hasta obtener confirmación.
- Si otro usuario ya cobró el saldo, el pago supera el disponible, cambió el cliente o la caja, o se desactivó el método, el servidor rechaza la operación con una explicación. No cambia silenciosamente de caja ni reduce el importe recibido.
- **Descargar no borra pagos pendientes:** primero deben resolverse y subirse. El modo Online tampoco sustituye una cola Offline sin enviar.

La subida existente es atómica para el lote completo: un error en otra operación también mantiene pendientes los cobros de ese envío. Se deben corregir las operaciones indicadas y reintentar el lote. Las operaciones con resultado ya confirmado conservan su clave para no repetirse.

## Monedas y permisos

El móvil registra cobros en la **misma moneda del ingreso**, con importes positivos de hasta dos decimales. Cada cobro utiliza una sola moneda. Si el ingreso tiene pagos confirmados en otra moneda, se identifica el caso y se gestiona desde web; no se suman dólares y córdobas ni se inventa una conversión histórica.

Para consultar se necesita acceso a OT y `cobros.view` o `cobros.create`. Para registrar se requiere `cobros.create`, cliente en la OT, caja activa asignada y catálogos activos. Para crear el ingreso se usa su permiso propio `ingresos.create`. La edición o anulación de cobros confirmados conserva el recorrido web y sus reversas históricas.

## Contrato API

- `GET /api/v1/orders/{orderId}/collections`: opciones, ingresos, importes confirmados e historial en la caja del usuario.
- `POST /api/v1/orders/{orderId}/collections`: pago Finalizado idempotente con `operation_key`, `client_id`, `cash_register_id`, `date`, `currency_id`, `payment_method_id`, `reference`, `observations`, y `lines` (`income_id`, `amount`).
- `collection.create` se admite en `POST /api/v1/offline/upload`. La cola resuelve referencias negativas de OT e ingresos antes de registrar el pago.
- `orders[].collections` y `catalogs.collection_settings` forman parte del snapshot según permisos.
- `mobile_sync_operations` conserva el resultado y la huella del pago; una misma clave con datos distintos se rechaza. No hace falta una migración nueva para esta ampliación de cobros.
