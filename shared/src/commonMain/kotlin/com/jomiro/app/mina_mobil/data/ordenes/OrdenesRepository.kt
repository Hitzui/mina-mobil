package com.jomiro.app.mina_mobil.data.ordenes

import com.jomiro.app.mina_mobil.data.ApiError
import com.jomiro.app.mina_mobil.data.OfflineSyncRepository
import com.jomiro.app.mina_mobil.data.SessionRepository
import com.jomiro.app.mina_mobil.network.ApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.datetime.daysUntil
import kotlin.math.roundToInt

class OrdenesRepository(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessions: SessionRepository,
    private val offlineSync: OfflineSyncRepository,
) {
    suspend fun opcionesMaterialProceso(ordenId: Long, procesoId: Long): OpcionesMaterialProceso {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            val catalogs = snapshot["catalogs"]?.jsonObject
                ?: throw OrdenesException("La descarga local no incluye el catálogo de materiales.")
            val products = runCatching {
                ApiJson.instance.decodeFromString<List<ProductoMateriaDto>>(catalogs["products"]?.toString() ?: "[]")
            }.getOrElse { throw OrdenesException("El catálogo local de materiales está dañado. Descargue los datos nuevamente.") }
                .map { it.toModel() }
            return OpcionesMaterialProceso(
                products.map { it.copy(existencia = existenciaLocal(session.userId, it)) },
                kotlin.time.Clock.System.now().toString().take(10),
            )
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/material-options") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los materiales disponibles.")
        return runCatching { ApiJson.instance.decodeFromString<OpcionesMaterialesResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar los materiales disponibles.") }
    }

    suspend fun listarMaterialesProceso(ordenId: Long, procesoId: Long): List<MaterialProcesoTrabajo> {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            return detalleLocal(session.userId, ordenId).procesos.firstOrNull { it.id == procesoId }?.materiales.orEmpty()
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/materials") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los consumos de materia prima.")
        return runCatching { ApiJson.instance.decodeFromString<MaterialesProcesoResponseDto>(body).data.materials.map { it.toModel() } }
            .getOrElse { throw OrdenesException("No se pudieron interpretar los consumos del proceso.") }
    }

    suspend fun registrarMaterialProceso(
        ordenId: Long,
        procesoId: Long,
        productoId: Long,
        fecha: String,
        cantidad: Double,
        observaciones: String?,
    ): MaterialProcesoTrabajo {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val payload = buildJsonObject {
            put("order_id", ordenId); put("process_id", procesoId); put("producto_id", productoId)
            put("fecha", fecha); put("cantidad", cantidad)
            observaciones?.trim()?.takeIf(String::isNotBlank)?.let { put("observaciones", it) }
        }
        if (offlineSync.isOfflineMode(session.userId)) {
            val product = opcionesMaterialProceso(ordenId, procesoId).productos.firstOrNull { it.id == productoId }
                ?: throw OrdenesException("El material no está disponible en la descarga local.")
            val available = product.existencia
            if (cantidad > available + 0.0005) {
                throw OrdenesException("No hay suficiente material en el almacén: se piden ${cantidad.displayMaterial()} y hay ${available.displayMaterial()} ${product.unidad}.")
            }
            val movement = MaterialProcesoTrabajo(
                id = 0L, productoId = product.id, producto = product.nombre, unidad = product.unidad,
                fecha = fecha, cantidad = cantidad, costoUnitario = product.costoPromedio,
                totalNio = (cantidad * product.costoPromedio).roundMoney(), observaciones = observaciones,
            )
            val localId = offlineSync.enqueueOperation(session.userId, "material.consume", payload, "process_material") { id ->
                movement.copy(id = id).toOfflineJson(procesoId).let { localPayload ->
                    kotlinx.serialization.json.buildJsonObject {
                        localPayload.forEach { (key, value) -> put(key, value) }
                        put("order_id", ordenId)
                    }
                }
            }
            return movement.copy(id = localId)
        }
        val response = client.post("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/materials") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json); setBody(payload)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo registrar el consumo de materia prima.")
        return runCatching { ApiJson.instance.decodeFromString<MaterialProcesoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar el consumo guardado.") }
    }

    suspend fun eliminarMaterialProceso(ordenId: Long, procesoId: Long, movement: MaterialProcesoTrabajo) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val payload = buildJsonObject {
                put("order_id", ordenId); put("process_id", procesoId); put("movement_id", movement.id)
            }
            offlineSync.enqueueOperation(session.userId, "material.delete", payload, "process_material_deleted") { localId ->
                buildJsonObject {
                    put("id", movement.id); put("process_id", procesoId); put("order_id", ordenId)
                    put("product_id", movement.productoId); put("quantity", movement.cantidad)
                    put("local_delete_id", localId); put("is_local", movement.id < 0)
                }
            }
            return
        }
        val response = client.delete("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/materials/${movement.id}") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo eliminar el consumo.")
    }

    private fun existenciaLocal(ownerId: Long, product: ProductoMateriaPrima): Double {
        val entities = offlineSync.localEntities(ownerId)
        val deletedIds = entities.filter { it.type == "process_material_deleted" }
            .mapNotNull { it.payload["id"]?.jsonPrimitive?.content?.toLongOrNull() }.toSet()
        val consumed = entities.filter { it.type == "process_material" }
            .filter { it.payload["product_id"]?.jsonPrimitive?.content?.toLongOrNull() == product.id }
            .filter { it.id !in deletedIds }
            .sumOf { it.payload["quantity"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0 }
        val returned = entities.filter { it.type == "process_material_deleted" }
            .filter { it.payload["product_id"]?.jsonPrimitive?.content?.toLongOrNull() == product.id }
            .filter { it.payload["is_local"]?.jsonPrimitive?.content != "true" }
            .sumOf { it.payload["quantity"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0 }
        return (product.existencia - consumed + returned).coerceAtLeast(0.0)
    }

    suspend fun opcionesCostosProceso(ordenId: Long, procesoId: Long): OpcionesCostoProceso {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            val catalogs = snapshot["catalogs"]?.jsonObject
                ?: throw OrdenesException("La descarga local no incluye las opciones de costos.")
            val categories = runCatching {
                ApiJson.instance.decodeFromString<List<CategoriaCostoDto>>(catalogs["cost_categories"]?.toString() ?: "[]")
            }.getOrElse { throw OrdenesException("Las categorías locales están dañadas. Descargue los datos nuevamente.") }
                .map { CategoriaCostoOpcion(it.id, it.name) }
            val currencies = runCatching {
                ApiJson.instance.decodeFromString<List<MonedaCostoDto>>(catalogs["currencies"]?.toString() ?: "[]")
            }.getOrElse { throw OrdenesException("Las monedas locales están dañadas. Descargue los datos nuevamente.") }
                .map { MonedaCostoOpcion(it.id, it.code, it.name, it.isBaseCurrency) }
            if (categories.isEmpty() || currencies.isEmpty()) {
                throw OrdenesException("La descarga local no incluye categorías o monedas para costos. Descargue los datos nuevamente.")
            }
            return OpcionesCostoProceso(categories, currencies, kotlin.time.Clock.System.now().toString().take(10))
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/cost-options") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar las opciones de costos.")
        return runCatching { ApiJson.instance.decodeFromString<OpcionesCostoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las opciones de costos.") }
    }

    suspend fun listarCostosProceso(ordenId: Long, procesoId: Long): List<CostoProcesoTrabajo> {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            return detalleLocal(session.userId, ordenId).procesos.firstOrNull { it.id == procesoId }?.costos.orEmpty()
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/costs") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los costos del proceso.")
        return runCatching { ApiJson.instance.decodeFromString<CostosProcesoResponseDto>(body).data.costs.map { it.toModel() } }
            .getOrElse { throw OrdenesException("No se pudieron interpretar los costos del proceso.") }
    }

    suspend fun guardarCostoProceso(
        ordenId: Long,
        procesoId: Long,
        costoId: Long?,
        categoriaId: Long,
        fecha: String,
        descripcion: String,
        cantidad: Double,
        costoUnitario: Double,
        monedaId: Long,
        observaciones: String?,
    ): CostoProcesoTrabajo {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val body = buildJsonObject {
            put("order_id", ordenId); put("process_id", procesoId)
            costoId?.let { put("cost_id", it) }
            put("categoria_costo_id", categoriaId); put("fecha", fecha); put("descripcion", descripcion.trim())
            put("cantidad", cantidad); put("costo_unitario", costoUnitario); put("moneda_id", monedaId)
            observaciones?.trim()?.takeIf(String::isNotBlank)?.let { put("observaciones", it) }
        }
        if (offlineSync.isOfflineMode(session.userId)) {
            val options = opcionesCostosProceso(ordenId, procesoId)
            val category = options.categorias.firstOrNull { it.id == categoriaId }?.nombre.orEmpty()
            val selectedCurrency = options.monedas.firstOrNull { it.id == monedaId }
            val currency = selectedCurrency?.codigo.orEmpty()
            val isBaseCurrency = selectedCurrency?.monedaBase == true
            val rate = if (isBaseCurrency) 1.0 else offlineExchangeRate(session.userId, monedaId, fecha)
            val total = (cantidad * costoUnitario).roundMoney()
            val result = CostoProcesoTrabajo(
                id = costoId ?: 0L, categoriaId = categoriaId, categoria = category, fecha = fecha,
                descripcion = descripcion, cantidad = cantidad, costoUnitario = costoUnitario, total = total,
                monedaId = monedaId, moneda = currency, totalNio = rate?.let { (total * it).roundMoney() },
                observaciones = observaciones,
            )
            val operationType = if (costoId == null) "cost.create" else "cost.update"
            if (!isBaseCurrency && rate == null) {
                throw OrdenesException("No se descargó el tipo de cambio necesario para la fecha y moneda seleccionadas.")
            }
            val localId = offlineSync.enqueueOperation(session.userId, operationType, body, "process_cost") { tempId ->
                result.copy(id = costoId ?: tempId).toOfflineJson(procesoId)
            }
            return result.copy(id = costoId ?: localId)
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/costs" + (costoId?.let { "/$it" } ?: "")
        val actualResponse = if (costoId == null) client.post(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json); setBody(body)
        } else client.put(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json); setBody(body)
        }
        val responseBody = actualResponse.bodyAsText()
        if (actualResponse.status.value !in 200..299) throw apiException(responseBody, "No se pudo guardar el costo del proceso.")
        return runCatching { ApiJson.instance.decodeFromString<ProcessCostResponseDto>(responseBody).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar el costo guardado.") }
    }

    suspend fun eliminarCostoProceso(ordenId: Long, procesoId: Long, costoId: Long) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val payload = buildJsonObject { put("order_id", ordenId); put("process_id", procesoId); put("cost_id", costoId) }
        if (offlineSync.isOfflineMode(session.userId)) {
            offlineSync.enqueueOperation(session.userId, "cost.delete", payload, "process_cost_deleted") { id ->
                buildJsonObject { put("id", costoId); put("process_id", procesoId); put("local_id", id) }
            }
            return
        }
        val response = client.delete("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/costs/$costoId") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo eliminar el costo.")
    }

    suspend fun opcionesCostosOrden(ordenId: Long): OpcionesCostoOrden {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            val catalogs = snapshot["catalogs"]?.jsonObject
                ?: throw OrdenesException("La descarga local no incluye las opciones de costos.")
            val categories = ApiJson.instance.decodeFromString<List<CategoriaCostoDto>>(catalogs["cost_categories"]?.toString() ?: "[]")
                .map { CategoriaCostoOpcion(it.id, it.name) }
            val currencies = ApiJson.instance.decodeFromString<List<MonedaCostoDto>>(catalogs["currencies"]?.toString() ?: "[]")
                .map { MonedaCostoOpcion(it.id, it.code, it.name, it.isBaseCurrency) }
            if (categories.isEmpty() || currencies.isEmpty()) {
                throw OrdenesException("La descarga local no incluye categorías o monedas para costos. Descargue los datos nuevamente.")
            }
            return OpcionesCostoOrden(categories, currencies, kotlin.time.Clock.System.now().toString().take(10))
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/cost-options") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar las opciones de costos generales.")
        return runCatching { ApiJson.instance.decodeFromString<OpcionesCostoResponseDto>(body).data.toOrderModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las opciones de costos generales.") }
    }

    suspend fun listarCostosOrden(ordenId: Long): List<CostoOrdenTrabajo> {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            return detalleLocal(session.userId, ordenId).costos.filter { it.proceso.isNullOrBlank() }
                .sortedByDescending { it.fecha }
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/costs") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los costos generales de la orden.")
        return runCatching { ApiJson.instance.decodeFromString<CostosOrdenResponseDto>(body).data.costs.map { it.toOrderModel() } }
            .getOrElse { throw OrdenesException("No se pudieron interpretar los costos generales de la orden.") }
    }

    suspend fun guardarCostoOrden(
        ordenId: Long,
        costo: CostoOrdenTrabajo?,
        categoriaId: Long,
        fecha: String,
        descripcion: String,
        cantidad: Double,
        costoUnitario: Double,
        monedaId: Long,
        observaciones: String,
        opciones: OpcionesCostoOrden,
    ): CostoOrdenTrabajo {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val body = buildJsonObject {
            put("order_id", ordenId); costo?.let { put("cost_id", it.id) }
            put("categoria_costo_id", categoriaId); put("fecha", fecha); put("descripcion", descripcion.trim())
            put("cantidad", cantidad); put("costo_unitario", costoUnitario); put("moneda_id", monedaId)
            if (observaciones.isNotBlank()) put("observaciones", observaciones.trim())
        }
        if (offlineSync.isOfflineMode(session.userId)) {
            val category = opciones.categorias.firstOrNull { it.id == categoriaId }?.nombre.orEmpty()
            val currency = opciones.monedas.firstOrNull { it.id == monedaId }
            val rate = if (currency?.monedaBase == true) 1.0 else offlineExchangeRate(session.userId, monedaId, fecha)
            if (rate == null) throw OrdenesException("No se descargó el tipo de cambio necesario para la fecha y moneda seleccionadas.")
            val total = (cantidad * costoUnitario).roundMoney()
            val result = CostoOrdenTrabajo(
                id = costo?.id ?: 0,
                fecha = fecha,
                proceso = null,
                categoria = category,
                descripcion = descripcion,
                cantidad = cantidad.toString(),
                moneda = currency?.codigo.orEmpty(),
                total = total.toString(),
                totalNio = (total * rate).roundMoney().toString(),
                categoriaId = categoriaId,
                cantidadNumero = cantidad,
                costoUnitario = costoUnitario,
                monedaId = monedaId,
                observaciones = observaciones,
            )
            val operationType = if (costo == null) "order_cost.create" else "order_cost.update"
            val localId = offlineSync.enqueueOperation(session.userId, operationType, body, "order_cost") { tempId ->
                result.copy(id = costo?.id ?: tempId).toOfflineJson(ordenId)
            }
            return result.copy(id = costo?.id ?: localId)
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/costs" + (costo?.let { "/${it.id}" } ?: "")
        val response = if (costo == null) client.post(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json); setBody(body)
        } else client.put(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json); setBody(body)
        }
        val responseBody = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(responseBody, "No se pudo guardar el costo general.")
        return runCatching { ApiJson.instance.decodeFromString<OrderCostResponseDto>(responseBody).data.toOrderModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar el costo guardado.") }
    }

    suspend fun eliminarCostoOrden(ordenId: Long, costo: CostoOrdenTrabajo) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val payload = buildJsonObject { put("order_id", ordenId); put("cost_id", costo.id) }
        if (offlineSync.isOfflineMode(session.userId)) {
            offlineSync.enqueueOperation(session.userId, "order_cost.delete", payload, "order_cost_deleted") { localId ->
                buildJsonObject { put("id", costo.id); put("order_id", ordenId); put("local_id", localId) }
            }
            return
        }
        val response = client.delete("${baseUrl.trimEnd('/')}/orders/$ordenId/costs/${costo.id}") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo eliminar el costo general.")
    }

    suspend fun opcionesFormulario(): OpcionesFormularioOrden {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            val clients = ApiJson.instance.decodeFromString<List<OpcionClienteOrdenDto>>(snapshot.getValue("clients").toString())
            val units = ApiJson.instance.decodeFromString<List<OpcionUnidadPesoDto>>(snapshot.getValue("catalogs").jsonObject.getValue("units").toString())
            return OpcionesOrdenDto(clients, units).toModel()
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/form-options"
        val response = client.get(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudieron cargar clientes y unidades para la orden.")
        }
        return runCatching { ApiJson.instance.decodeFromString<OpcionesOrdenResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar los datos del formulario de orden.") }
    }

    suspend fun crear(
        clienteId: Long,
        fecha: String,
        pesoMineral: Double,
        unidadPesoId: Long,
        descripcion: String,
    ): OrdenTrabajo {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val operation = buildJsonObject {
                put("cliente_id", clienteId)
                put("fecha", fecha)
                put("peso_mineral", pesoMineral)
                put("unidad_peso_id", unidadPesoId)
                put("descripcion", descripcion.trim())
            }
            var order = OrdenTrabajo(0, "", fecha, null, descripcion, pesoMineral, null, "Pendiente", 1)
            val localId = offlineSync.enqueueOperation(
                session.userId, "order.create", operation, "order",
                localEntityPayload = { id ->
                    val selectedClient = ApiJson.instance.decodeFromString<List<OpcionClienteOrdenDto>>(
                        offlineSync.snapshot(session.userId)?.getValue("clients").toString(),
                    ).firstOrNull { it.id == clienteId }
                    order = order.copy(id = id, codigo = "LOCAL-${-id}", cliente = selectedClient?.name)
                    order.toOfflineJson(clienteId, unidadPesoId)
                },
            )
            return order.copy(id = localId, codigo = "LOCAL-${-localId}")
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders"
        val response = client.post(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(CrearOrdenRequestDto(
                clientId = clienteId,
                date = fecha,
                description = descripcion.trim().ifBlank { null },
                mineralWeight = pesoMineral,
                weightUnitId = unidadPesoId,
            )))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudo crear la orden (HTTP ${response.status.value}).")
        }
        return runCatching { ApiJson.instance.decodeFromString<OrdenCreadaResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("La orden se guardó, pero no se pudo leer la respuesta del servidor.") }
    }

    suspend fun opcionesEdicion(id: Long): FormularioEdicionOrden {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val order = offlineOrder(session.userId, id) ?: throw OrdenesException("No se encontró la orden en la descarga local.")
            val options = opcionesFormulario()
            val detail = detalleLocal(session.userId, id)
            return FormularioEdicionOrden(order, offlineClientId(session.userId, id) ?: 0L,
                offlineUnitId(session.userId, id), detail.procesos.isNotEmpty(), options.clientes, options.unidades)
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$id/edit-form-options") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los datos para editar la orden.")
        return runCatching { ApiJson.instance.decodeFromString<EdicionOrdenResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar la información editable de la orden.") }
    }

    suspend fun actualizar(
        id: Long,
        clienteId: Long,
        fecha: String,
        pesoMineral: Double,
        unidadPesoId: Long,
        descripcion: String,
        tieneProcesos: Boolean,
    ): OrdenTrabajo {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val previous = offlineOrder(session.userId, id) ?: throw OrdenesException("No se encontró la orden en la descarga local.")
            val clientName = opcionesFormulario().clientes.firstOrNull { it.id == clienteId }?.nombre
            val updated = previous.copy(cliente = clientName, fecha = if (tieneProcesos) previous.fecha else fecha,
                pesoMineral = if (tieneProcesos) previous.pesoMineral else pesoMineral, descripcion = descripcion)
            val body = buildJsonObject {
                put("order_id", id); put("cliente_id", clienteId); put("fecha", fecha)
                put("descripcion", descripcion); put("peso_mineral", pesoMineral); put("unidad_peso_id", unidadPesoId)
            }
            offlineSync.enqueueOperation(session.userId, "order.update", body, "order", id) {
                updated.toOfflineJson(clienteId, if (tieneProcesos) offlineUnitId(session.userId, id) else unidadPesoId)
            }
            return updated
        }
        val response = client.put("${baseUrl.trimEnd('/')}/orders/$id") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(ActualizarOrdenRequestDto(
                clientId = clienteId,
                date = if (tieneProcesos) null else fecha,
                description = descripcion.trim().ifBlank { null },
                mineralWeight = if (tieneProcesos) null else pesoMineral,
                weightUnitId = if (tieneProcesos) null else unidadPesoId,
            )))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo actualizar la orden.")
        return runCatching { ApiJson.instance.decodeFromString<OrdenCreadaResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("La orden se actualizó, pero no se pudo interpretar la respuesta del servidor.") }
    }

    suspend fun listar(
        pagina: Int,
        codigo: String? = null,
        clienteId: Long? = null,
        fechaDesde: String? = null,
        fechaHasta: String? = null,
    ): PaginaOrdenes {
        val owner = sessions.current()?.userId
        if (owner != null && offlineSync.isOfflineMode(owner)) {
            val orders = offlineOrders(owner).filter { order ->
                (codigo.isNullOrBlank() || order.codigo.contains(codigo, ignoreCase = true)) &&
                    (clienteId == null || offlineClientId(owner, order.id) == clienteId) &&
                    (fechaDesde == null || (order.fecha ?: "") >= fechaDesde) &&
                    (fechaHasta == null || (order.fecha ?: "") <= fechaHasta)
            }
            val start = (pagina - 1) * LIST_PAGE_SIZE
            return PaginaOrdenes(orders.drop(start).take(LIST_PAGE_SIZE), pagina,
                ((orders.size + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE).coerceAtLeast(1), orders.size)
        }
        return consultar(pagina, LIST_PAGE_SIZE, codigo, clienteId, fechaDesde, fechaHasta)
    }

    suspend fun calendario(fechaDesde: String, fechaHasta: String): List<OrdenTrabajo> {
        val owner = sessions.current()?.userId
        if (owner != null && offlineSync.isOfflineMode(owner)) {
            return offlineOrders(owner).filter { (it.fecha ?: "") in fechaDesde..fechaHasta }
        }
        val firstPage = consultar(
            pagina = 1,
            perPage = CALENDAR_PAGE_SIZE,
            fechaDesde = fechaDesde,
            fechaHasta = fechaHasta,
        )
        val result = firstPage.ordenes.toMutableList()
        for (pagina in 2..firstPage.ultimaPagina) {
            result += consultar(
                pagina = pagina,
                perPage = CALENDAR_PAGE_SIZE,
                fechaDesde = fechaDesde,
                fechaHasta = fechaHasta,
            ).ordenes
        }
        return result
    }

    suspend fun detalle(id: Long): DetalleOrdenTrabajo {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) return detalleLocal(session.userId, id)
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$id"
        val response = client.get(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudo cargar el detalle de la orden (HTTP ${response.status.value}).")
        }
        return parseOrderDetail(body)
    }

    suspend fun opcionesIngreso(ordenId: Long): OpcionesIngresoOrden {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val snapshot = offlineSync.offlineSnapshot(session.userId)
        if (snapshot != null) {
            val catalogs = snapshot["catalogs"]?.jsonObject ?: throw OrdenesException("La descarga no incluye los catálogos de ingresos.")
            val incomeTypes = catalogs["income_types"]
                ?: throw OrdenesException("La descarga Offline no incluye los tipos de ingreso. Conéctate Online y vuelve a descargar los datos.")
            val types = ApiJson.instance.decodeFromString<List<IncomeTypeDto>>(incomeTypes.toString())
            val units = ApiJson.instance.decodeFromString<List<CategoriaCostoDto>>(catalogs["units"]?.toString() ?: "[]")
            val currencies = ApiJson.instance.decodeFromString<List<MonedaCostoDto>>(catalogs["currencies"]?.toString() ?: "[]")
            return OpcionesIngresoOrden(
                fecha = kotlin.time.Clock.System.now().toString().take(10),
                tipos = types.map { TipoIngresoOpcion(it.id, it.name) },
                unidades = units.map { UnidadPesoOpcion(it.id, it.name) },
                monedas = currencies.map { MonedaCostoOpcion(it.id, it.code, it.name, it.isBaseCurrency) },
            )
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/income-options") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar las opciones de ingresos.")
        return runCatching { ApiJson.instance.decodeFromString<IncomeOptionsResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las opciones de ingresos.") }
    }

    suspend fun guardarIngreso(
        ordenId: Long,
        ingreso: IngresoOrdenTrabajo?,
        tipoId: Long,
        fecha: String,
        descripcion: String,
        cantidad: Double,
        unidadId: Long?,
        monedaId: Long,
        precioUnitario: Double,
        observaciones: String,
        opciones: OpcionesIngresoOrden,
    ) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val payload = buildJsonObject {
            put("order_id", ordenId)
            ingreso?.let { put("income_id", it.id) }
            put("type_id", tipoId); put("date", fecha)
            if (descripcion.isNotBlank()) put("description", descripcion)
            put("quantity", cantidad); unidadId?.let { put("unit_id", it) }
            put("unit_price", precioUnitario); put("currency_id", monedaId)
            if (observaciones.isNotBlank()) put("observations", observaciones)
        }
        if (offlineSync.isOfflineMode(session.userId)) {
            val catalogs = offlineSync.offlineSnapshot(session.userId)?.get("catalogs")?.jsonObject
            val rates = runCatching {
                ApiJson.instance.decodeFromString<List<OfflineExchangeRateDto>>(catalogs?.get("exchange_rates")?.toString() ?: "[]")
            }.getOrDefault(emptyList())
            val currency = opciones.monedas.firstOrNull { it.id == monedaId }
            val exchangeRate = if (currency?.monedaBase == true) 1.0 else rates
                .filter { it.currencyId == monedaId && it.fecha <= fecha }
                .maxWithOrNull(compareBy<OfflineExchangeRateDto> { it.fecha }.thenBy { it.id })?.rate
            val total = (cantidad * precioUnitario).roundMoney()
            val typeName = opciones.tipos.firstOrNull { it.id == tipoId }?.nombre.orEmpty()
            val currencyCode = currency?.codigo.orEmpty()
            val unitName = opciones.unidades.firstOrNull { it.id == unidadId }?.nombre
            val localType = if (ingreso == null) "income.create" else "income.update"
            offlineSync.enqueueOperation(session.userId, localType, payload, "income", ingreso?.id) { localId ->
                buildJsonObject {
                    put("id", ingreso?.id ?: localId); put("order_id", ordenId)
                    put("type_id", tipoId); put("type", typeName); put("date", fecha)
                    put("description", descripcion); put("quantity", cantidad); unidadId?.let { put("unit_id", it) }
                    put("unit", unitName.orEmpty()); put("unit_price", precioUnitario)
                    put("currency_id", monedaId); put("currency", currencyCode); put("total", total)
                    exchangeRate?.let { put("total_nio", (total * it).roundMoney()) }
                    put("observations", observaciones)
                }
            }
            return
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/incomes" + (ingreso?.let { "/${it.id}" } ?: "")
        val response = if (ingreso == null) client.post(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json); setBody(payload)
        } else client.put(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json); setBody(payload)
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo guardar el ingreso.")
    }

    suspend fun eliminarIngreso(ordenId: Long, ingreso: IngresoOrdenTrabajo) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val payload = buildJsonObject { put("order_id", ordenId); put("income_id", ingreso.id) }
        if (offlineSync.isOfflineMode(session.userId)) {
            offlineSync.enqueueOperation(session.userId, "income.delete", payload, "income_deleted", ingreso.id) {
                buildJsonObject { put("id", ingreso.id); put("order_id", ordenId) }
            }
            return
        }
        val response = client.delete("${baseUrl.trimEnd('/')}/orders/$ordenId/incomes/${ingreso.id}") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo eliminar el ingreso.")
    }

    suspend fun opcionesAcuerdo(): OpcionesAcuerdoOrden {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val catalogs = offlineSync.offlineSnapshot(session.userId)?.get("catalogs")?.jsonObject
        if (catalogs != null) {
            val types = ApiJson.instance.decodeFromString<List<ParticipationTypeDto>>(
                catalogs["participation_types"]?.toString() ?: "[]",
            ).map { TipoParticipacionOpcion(it.id, it.name) }
            val currencies = ApiJson.instance.decodeFromString<List<AgreementCurrencyDto>>(
                catalogs["agreement_currencies"]?.toString() ?: "[]",
            ).map { MonedaAcuerdoOpcion(it.id, it.code, it.name, it.isBaseCurrency) }
            return OpcionesAcuerdoOrden(types, currencies, currencies.firstOrNull(MonedaAcuerdoOpcion::monedaBase)?.id)
        }
        val response = client.get("${baseUrl.trimEnd('/')}/agreement-options") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar las opciones del acuerdo.")
        return runCatching { ApiJson.instance.decodeFromString<AgreementOptionsResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las opciones del acuerdo.") }
    }

    suspend fun guardarAcuerdo(
        ordenId: Long,
        acuerdoId: Long?,
        tipoId: Long,
        porcentajeCliente: Double?,
        porcentajeEmpresa: Double?,
        tarifa: Double?,
        monedaId: Long,
        observaciones: String,
        activo: Boolean,
    ) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val payload = buildJsonObject {
            put("order_id", ordenId)
            acuerdoId?.let { put("agreement_id", it) }
            put("participation_type_id", tipoId)
            porcentajeCliente?.let { put("client_percentage", it) }
            porcentajeEmpresa?.let { put("company_percentage", it) }
            tarifa?.let { put("service_fee", it) }
            put("currency_id", monedaId)
            put("observations", observaciones)
            put("active", activo)
        }
        if (offlineSync.isOfflineMode(session.userId)) {
            val agreementOptions = opcionesAcuerdo()
            val opType = if (acuerdoId == null) "agreement.create" else "agreement.update"
            offlineSync.enqueueOperation(
                session.userId, opType, payload, "agreement", acuerdoId,
            ) { localId ->
                buildJsonObject {
                    put("id", acuerdoId ?: localId)
                    put("order_id", ordenId)
                    put("participation_type_id", tipoId)
                    put("participation_type", agreementOptions.tipos.firstOrNull { it.id == tipoId }?.nombre.orEmpty())
                    put("modality", if (tarifa != null) "tarifa" else "porcentaje")
                    porcentajeCliente?.let { put("client_percentage", it) }
                    porcentajeEmpresa?.let { put("company_percentage", it) }
                    tarifa?.let { put("service_fee", it) }
                    put("currency_id", monedaId)
                    put("currency", agreementOptions.monedas.firstOrNull { it.id == monedaId }?.codigo.orEmpty())
                    put("observations", observaciones)
                    put("active", activo)
                }
            }
            return
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/agreements" + (acuerdoId?.let { "/$it" } ?: "")
        val response = if (acuerdoId == null) client.post(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json)
            setBody(payload)
        } else client.put(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo guardar el acuerdo.")
    }

    suspend fun eliminarAcuerdo(ordenId: Long, acuerdo: AcuerdoOrdenTrabajo) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val payload = buildJsonObject { put("order_id", ordenId); put("agreement_id", acuerdo.id) }
            offlineSync.enqueueOperation(session.userId, "agreement.delete", payload, "agreement_deleted", acuerdo.id) {
                buildJsonObject { put("id", acuerdo.id); put("order_id", ordenId) }
            }
            return
        }
        val response = client.delete("${baseUrl.trimEnd('/')}/orders/$ordenId/agreements/${acuerdo.id}") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo eliminar el acuerdo.")
    }

    suspend fun opcionesLiquidacion(ordenId: Long): OpcionesLiquidacionOrden {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val snapshot = offlineSync.offlineSnapshot(session.userId)
        if (snapshot != null) {
            val order = snapshot["orders"]?.let { raw ->
                runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull()
            }.orEmpty().firstOrNull { it.id == ordenId }
                ?: throw OrdenesException("No se encontró la orden en la descarga local.")
            return OpcionesLiquidacionOrden(
                fecha = kotlin.time.Clock.System.now().toString().take(10),
                acuerdos = order.liquidationAgreements.map { it.toModel() },
            )
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/liquidation-options") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los acuerdos aplicables.")
        return runCatching { ApiJson.instance.decodeFromString<LiquidationOptionsResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las opciones de liquidación.") }
    }

    suspend fun guardarLiquidacion(
        ordenId: Long,
        liquidacion: LiquidacionOrdenTrabajo?,
        fecha: String,
        gramos: Double,
        acuerdo: AcuerdoLiquidacionOpcion?,
        observaciones: String,
    ) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (liquidacion == null && acuerdo == null) throw OrdenesException("Seleccione un acuerdo porcentual para distribuir los gramos.")
        val payload = buildJsonObject {
            put("order_id", ordenId)
            liquidacion?.let { put("liquidation_id", it.id) }
            acuerdo?.let { put("agreement_id", it.id) }
            put("date", fecha)
            put("recovered_grams", gramos)
            put("observations", observaciones)
        }
        if (offlineSync.isOfflineMode(session.userId)) {
            if (liquidacion == null && acuerdo != null) {
                offlineSync.enqueueOperation(session.userId, "liquidation.create", payload, "liquidation") { localId ->
                    val roundedGrams = roundFour(gramos)
                    val clientGrams = roundFour(roundedGrams * acuerdo.porcentajeCliente / 100.0)
                    buildJsonObject {
                        put("id", localId); put("order_id", ordenId); put("date", fecha)
                        put("recovered_grams", roundedGrams); put("client_grams", clientGrams)
                        put("company_grams", roundFour(roundedGrams - clientGrams))
                        put("client_percentage", acuerdo.porcentajeCliente); put("company_percentage", acuerdo.porcentajeEmpresa)
                        put("participation_type", acuerdo.tipoParticipacion.orEmpty())
                        put("observations", observaciones)
                    }
                }
            } else if (liquidacion != null) {
                offlineSync.enqueueOperation(session.userId, "liquidation.update", payload, "liquidation", liquidacion.id) {
                    val roundedGrams = roundFour(gramos)
                    val clientGrams = roundFour(roundedGrams * (liquidacion.porcentajeCliente?.toDoubleOrNull() ?: 0.0) / 100.0)
                    buildJsonObject {
                        put("id", liquidacion.id); put("order_id", ordenId); put("date", fecha)
                        put("recovered_grams", roundedGrams); put("client_grams", clientGrams)
                        put("company_grams", roundFour(roundedGrams - clientGrams))
                        put("client_percentage", liquidacion.porcentajeCliente?.toDoubleOrNull() ?: 0.0)
                        put("company_percentage", liquidacion.porcentajeEmpresa?.toDoubleOrNull() ?: 0.0)
                        put("observations", observaciones)
                    }
                }
            }
            return
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/liquidations" +
            (liquidacion?.let { "/${it.id}" } ?: "")
        val response = if (liquidacion == null) client.post(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json)
            setBody(payload)
        } else client.put(endpoint) {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo guardar la liquidación.")
    }

    suspend fun eliminarLiquidacion(ordenId: Long, liquidacion: LiquidacionOrdenTrabajo) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val payload = buildJsonObject { put("order_id", ordenId); put("liquidation_id", liquidacion.id) }
            offlineSync.enqueueOperation(session.userId, "liquidation.delete", payload, "liquidation_deleted", liquidacion.id) {
                buildJsonObject { put("id", liquidacion.id); put("order_id", ordenId) }
            }
            return
        }
        val response = client.delete("${baseUrl.trimEnd('/')}/orders/$ordenId/liquidations/${liquidacion.id}") {
            headers { set(HttpHeaders.Authorization, "Bearer ${session.token}") }
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo eliminar la liquidación.")
    }

    suspend fun finalizar(id: Long) {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val order = offlineOrder(session.userId, id) ?: throw OrdenesException("No se encontró la orden en la descarga local.")
            offlineSync.enqueueOperation(
                session.userId, "order.finalize", buildJsonObject { put("order_id", id) }, "order", id,
            ) { order.copy(estado = "Finalizada", codigoEstado = 3).toOfflineJson(offlineClientId(session.userId, id)) }
            return
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$id/finalize"
        val response = client.post(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudo finalizar la orden (HTTP ${response.status.value}).")
        }
    }

    suspend fun opcionesProceso(id: Long): List<EtapaOpcionProceso> {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            return ApiJson.instance.decodeFromString<List<EtapaProcesoDto>>(
                snapshot.getValue("catalogs").jsonObject.getValue("stages").toString(),
            ).map { it.toModel() }
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$id/process-form-options"
        val response = client.get(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudieron cargar las etapas disponibles.")
        }
        return runCatching { ApiJson.instance.decodeFromString<EtapasProcesoResponseDto>(body).data.stages.map { it.toModel() } }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las etapas disponibles.") }
    }

    suspend fun crearProceso(
        ordenId: Long,
        etapaId: Long,
        fechaInicio: String?,
        fechaFin: String?,
        pesoEntrada: Double?,
        pesoSalida: Double?,
        observaciones: String,
    ) {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val processOptions = opcionesProceso(ordenId)
            val stage = processOptions.firstOrNull { it.id == etapaId }?.nombre ?: "Etapa"
            val payload = buildJsonObject {
                put("order_id", ordenId); put("etapa_id", etapaId)
                fechaInicio?.let { put("fecha_inicio", it) }; fechaFin?.let { put("fecha_fin", it) }
                pesoEntrada?.let { put("peso_entrada", it) }; pesoSalida?.let { put("peso_salida", it) }
                put("observaciones", observaciones)
            }
            offlineSync.enqueueOperation(session.userId, "process.create", payload, "process") { localId ->
                buildJsonObject {
                    put("id", localId); put("order_id", ordenId); put("code", "LOCAL-${-localId}")
                    put("stage", stage); put("status", "Pendiente")
                    fechaInicio?.let { put("start_date", it) }; fechaFin?.let { put("end_date", it) }
                    pesoEntrada?.let { put("input_weight", it) }; pesoSalida?.let { put("output_weight", it) }
                    put("observations", observaciones)
                }
            }
            return
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes"
        val response = client.post(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(CrearProcesoRequestDto(
                etapaId = etapaId,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                pesoEntrada = pesoEntrada,
                pesoSalida = pesoSalida,
                observaciones = observaciones.trim().ifBlank { null },
            )))
        }
        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudo agregar el proceso (HTTP ${response.status.value}).")
        }
    }

    suspend fun opcionesTrabajo(ordenId: Long, procesoId: Long): OpcionesTrabajoEmpleado {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            val catalogs = snapshot.getValue("catalogs").jsonObject
            return OpcionesTrabajoDto(
                ApiJson.instance.decodeFromString(catalogs.getValue("employees").toString()),
                ApiJson.instance.decodeFromString(catalogs.getValue("payment_types").toString()),
                ApiJson.instance.decodeFromString(catalogs.getValue("units").toString()),
            ).toModel()
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/employee-work-options"
        val response = client.get(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar empleados, tipos de pago y unidades.")
        return runCatching { ApiJson.instance.decodeFromString<OpcionesTrabajoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudieron interpretar las opciones del trabajo.") }
    }

    suspend fun tarifaTrabajo(
        ordenId: Long,
        procesoId: Long,
        empleadoId: Long,
        tipoPagoId: Long,
        fecha: String,
    ): TarifaTrabajoEmpleado {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            val catalogs = snapshot.getValue("catalogs").jsonObject
            val rates = ApiJson.instance.decodeFromString<List<OfflineEmployeeRateDto>>(catalogs.getValue("employee_rates").toString())
            val rate = rates.filter { it.employeeId == empleadoId && it.paymentTypeId == tipoPagoId && it.active }
                .filter { (it.validFrom == null || fecha >= it.validFrom) && (it.validUntil == null || fecha <= it.validUntil) }
                .maxByOrNull { it.validFrom.orEmpty() }
                ?: throw OrdenesException("No hay una tarifa descargada vigente para este empleado, tipo de pago y fecha.")
            val exchange = if (rate.isBaseCurrency) 1.0 else {
                val exchangeRates = ApiJson.instance.decodeFromString<List<OfflineExchangeRateDto>>(catalogs.getValue("exchange_rates").toString())
                exchangeRates.firstOrNull { it.currencyId == rate.currencyId && it.fecha == fecha }?.rate
                    ?: throw OrdenesException("No se descargó el tipo de cambio necesario para la fecha seleccionada.")
            }
            return TarifaTrabajoEmpleado(rate.rate, rate.currency ?: "—", exchange, rate.rate * exchange,
                rate.calculationMethod ?: "cantidad_x_tarifa", rate.validFrom, rate.validUntil)
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/employee-work-rate"
        val response = client.get(endpoint) {
            url {
                parameters.append("empleado_id", empleadoId.toString())
                parameters.append("tipo_pago_id", tipoPagoId.toString())
                parameters.append("fecha", fecha)
            }
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo consultar la tarifa vigente.")
        return runCatching { ApiJson.instance.decodeFromString<TarifaTrabajoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar la tarifa del trabajo.") }
    }

    suspend fun crearTrabajo(
        ordenId: Long,
        procesoId: Long,
        empleadoId: Long,
        tipoPagoId: Long,
        fecha: String,
        horaInicio: String?,
        horaFin: String?,
        cantidad: Double,
        unidadId: Long,
        descripcion: String,
        observaciones: String,
    ) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val options = opcionesTrabajo(ordenId, procesoId)
            val employee = options.empleados.firstOrNull { it.id == empleadoId }?.nombre ?: "Empleado"
            val payment = options.tiposPago.firstOrNull { it.id == tipoPagoId }?.nombre ?: "Tipo de pago"
            val unit = options.unidades.firstOrNull { it.id == unidadId }?.nombre ?: ""
            val processCode = detalleLocal(session.userId, ordenId).procesos.firstOrNull { it.id == procesoId }?.codigo
                ?: "LOCAL-${-procesoId}"
            val rate = tarifaTrabajo(ordenId, procesoId, empleadoId, tipoPagoId, fecha)
            val total = (if (rate.metodoCalculo == "TARIFA") rate.tarifa else cantidad * rate.tarifa).roundMoney()
            val totalNio = (if (rate.metodoCalculo == "TARIFA") rate.tarifaNio else cantidad * rate.tarifaNio).roundMoney()
            val payload = buildJsonObject {
                put("order_id", ordenId); put("process_id", procesoId); put("empleado_id", empleadoId)
                put("tipo_pago_id", tipoPagoId); put("fecha", fecha)
                horaInicio?.let { put("hora_inicio", it) }; horaFin?.let { put("hora_fin", it) }
                put("cantidad", cantidad); put("unidad_id", unidadId); put("descripcion", descripcion); put("observaciones", observaciones)
            }
            offlineSync.enqueueOperation(session.userId, "employee_work.create", payload, "employee_work") { localId ->
                buildJsonObject {
                    put("id", localId); put("order_id", ordenId); put("process_id", procesoId)
                    put("employee", employee); put("payment_type", payment); put("date", fecha)
                    put("process", processCode); put("description", descripcion)
                    put("quantity", cantidad); put("unit", unit); put("currency", rate.moneda)
                    put("total", total); put("total_nio", totalNio)
                }
            }
            return
        }
        val endpoint = "${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/employee-works"
        val response = client.post(endpoint) {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(CrearTrabajoEmpleadoRequestDto(
                employeeId = empleadoId,
                paymentTypeId = tipoPagoId,
                date = fecha,
                startTime = horaInicio,
                endTime = horaFin,
                quantity = cantidad,
                unitId = unidadId,
                description = descripcion.trim().ifBlank { null },
                observations = observaciones.trim().ifBlank { null },
            )))
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo registrar el trabajo del empleado.")
    }

    suspend fun opcionesEquipo(ordenId: Long, procesoId: Long): OpcionesEquipoProceso {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            val equipment = ApiJson.instance.decodeFromString<List<EquipoOpcionDto>>(
                snapshot.getValue("catalogs").jsonObject.getValue("equipment").toString(),
            )
            val process = detalleLocal(session.userId, ordenId).procesos.firstOrNull { it.id == procesoId }
            return OpcionesEquipoDto(equipment, process?.fechaInicio?.take(16).orEmpty()).toModel()
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/equipment-options") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudieron cargar los equipos disponibles.")
        return runCatching { ApiJson.instance.decodeFromString<OpcionesEquipoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar la lista de equipos.") }
    }

    suspend fun depreciacionEquipo(
        ordenId: Long,
        procesoId: Long,
        equipoId: Long,
        fechaInicio: String,
        fechaFin: String?,
    ): DepreciacionEquipoProceso {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        offlineSync.offlineSnapshot(session.userId)?.let { snapshot ->
            val equipment = ApiJson.instance.decodeFromString<List<EquipoOpcionDto>>(
                snapshot.getValue("catalogs").jsonObject.getValue("equipment").toString(),
            ).firstOrNull { it.id == equipoId } ?: throw OrdenesException("El equipo no está en la descarga local.")
            val start = kotlinx.datetime.LocalDate.parse(fechaInicio.take(10))
            val end = kotlinx.datetime.LocalDate.parse((fechaFin ?: fechaInicio).take(10))
            val days = start.daysUntil(end).coerceAtLeast(0).toDouble()
            val daily = if (equipment.usefulLifeMonths > 0) (((equipment.acquisitionValue - equipment.residualValue).coerceAtLeast(0.0) /
                (equipment.usefulLifeMonths * 30.0)) * 10_000).roundToInt() / 10_000.0 else 0.0
            val overlap = offlineEquipmentAssignments(session.userId, equipoId).firstOrNull { (priorStart, priorEnd) ->
                val startDate = fechaInicio.take(10)
                val endDate = fechaFin?.take(10)
                (priorEnd == null || startDate <= priorEnd) && (endDate == null || priorStart <= endDate)
            }?.let { "El equipo ya tiene una asignación que se cruza con este periodo." }
            val remaining = equipment.remainingValue.takeIf { it > 0.0 } ?: (equipment.acquisitionValue - equipment.residualValue).coerceAtLeast(0.0)
            return DepreciacionEquipoProceso(equipment.code, equipment.acquisitionValue, equipment.residualValue,
                equipment.usefulLifeMonths, days, daily, (daily * days).roundMoney().coerceAtMost(remaining.roundMoney()), remaining > 0.0, overlap)
        }
        val response = client.get("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/equipment-depreciation") {
            url {
                parameters.append("equipo_id", equipoId.toString())
                parameters.append("fecha_inicio", fechaInicio)
                fechaFin?.let { parameters.append("fecha_fin", it) }
            }
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) throw apiException(body, "No se pudo calcular la depreciación del equipo.")
        return runCatching { ApiJson.instance.decodeFromString<DepreciacionEquipoResponseDto>(body).data.toModel() }
            .getOrElse { throw OrdenesException("No se pudo interpretar el cálculo de depreciación.") }
    }

    suspend fun asignarEquipo(
        ordenId: Long,
        procesoId: Long,
        equipoId: Long,
        fechaInicio: String,
        fechaFin: String?,
        observaciones: String,
    ) {
        val session = sessions.current() ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        if (offlineSync.isOfflineMode(session.userId)) {
            val equipment = opcionesEquipo(ordenId, procesoId).equipos.firstOrNull { it.id == equipoId }
                ?: throw OrdenesException("El equipo no está en la descarga local.")
            val estimate = depreciacionEquipo(ordenId, procesoId, equipoId, fechaInicio, fechaFin)
            if (!estimate.disponible || estimate.solapamiento != null) {
                throw OrdenesException(estimate.solapamiento ?: "El equipo no tiene valor disponible para depreciar.")
            }
            val payload = buildJsonObject {
                put("order_id", ordenId); put("process_id", procesoId); put("equipo_id", equipoId)
                put("fecha_inicio", fechaInicio); fechaFin?.let { put("fecha_fin", it) }; put("observaciones", observaciones)
            }
            val processCode = detalleLocal(session.userId, ordenId).procesos.firstOrNull { it.id == procesoId }?.codigo
                ?: "LOCAL-${-procesoId}"
            offlineSync.enqueueOperation(session.userId, "equipment.assign", payload, "equipment") { localId ->
                buildJsonObject {
                    put("id", localId); put("order_id", ordenId); put("process_id", procesoId); put("process", processCode)
                    put("equipment_id", equipoId); put("equipment_code", equipment.codigo); put("equipment_name", equipment.nombre)
                    put("start_date", fechaInicio); fechaFin?.let { put("end_date", it) }; put("observations", observaciones)
                    put("days", estimate.dias); put("daily_depreciation", estimate.depreciacionDiaria)
                    put("total_depreciation", estimate.depreciacionTotal)
                }
            }
            return
        }
        val response = client.post("${baseUrl.trimEnd('/')}/orders/$ordenId/processes/$procesoId/equipment") {
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            contentType(ContentType.Application.Json)
            setBody(ApiJson.instance.encodeToString(AsignarEquipoRequestDto(
                equipoId = equipoId,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                observaciones = observaciones.trim().ifBlank { null },
            )))
        }
        if (response.status.value !in 200..299) throw apiException(response.bodyAsText(), "No se pudo asignar el equipo al proceso.")
    }

    private suspend fun consultar(
        pagina: Int,
        perPage: Int,
        codigo: String? = null,
        clienteId: Long? = null,
        fechaDesde: String? = null,
        fechaHasta: String? = null,
    ): PaginaOrdenes {
        val session = sessions.current()
            ?: throw OrdenesException("La sesión expiró. Inicia sesión nuevamente.")
        val endpoint = "${baseUrl.trimEnd('/')}/orders"
        val response = client.get(endpoint) {
            url {
                parameters.append("page", pagina.toString())
                parameters.append("per_page", perPage.toString())
                codigo?.takeIf(String::isNotBlank)?.let { parameters.append("q", it) }
                clienteId?.let { parameters.append("cliente_id", it.toString()) }
                fechaDesde?.let { parameters.append("fecha_desde", it) }
                fechaHasta?.let { parameters.append("fecha_hasta", it) }
            }
            headers {
                set(HttpHeaders.Authorization, "Bearer ${session.token}")
                set(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
            throw OrdenesException(message ?: "No se pudieron cargar las órdenes de trabajo (HTTP ${response.status.value}).")
        }

        return parseOrdersPage(body)
    }

    private fun offlineOrder(ownerId: Long, id: Long): OrdenTrabajo? = offlineOrders(ownerId).firstOrNull { it.id == id }

    private fun offlineClientId(ownerId: Long, id: Long): Long? {
        val cached = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().firstOrNull { it.id == id }?.client?.id
        if (cached != null) return cached
        return offlineSync.localEntities(ownerId).firstOrNull { it.type == "order" && it.id == id }
            ?.payload?.get("client")?.jsonObject?.get("id")?.jsonPrimitive?.content?.toLongOrNull()
    }

    private fun offlineExchangeRate(ownerId: Long, currencyId: Long, date: String): Double? =
        offlineSync.snapshot(ownerId)?.get("catalogs")?.jsonObject?.get("exchange_rates")?.let { raw ->
            runCatching { ApiJson.instance.decodeFromString<List<OfflineExchangeRateDto>>(raw.toString()) }
                .getOrNull()?.filter { it.currencyId == currencyId && it.fecha == date }
                ?.maxByOrNull(OfflineExchangeRateDto::id)?.rate
        }

    private fun offlineUnitId(ownerId: Long, id: Long): Long? {
        val snapshotUnit = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().firstOrNull { it.id == id }?.weightUnitId
        if (snapshotUnit != null) return snapshotUnit
        return offlineSync.localEntities(ownerId).firstOrNull { it.type == "order" && it.id == id }
            ?.payload?.get("weight_unit_id")?.jsonPrimitive?.content?.toLongOrNull()
    }

    private fun offlineEquipmentAssignments(ownerId: Long, equipmentId: Long): List<Pair<String, String?>> {
        val fromCatalog = offlineSync.snapshot(ownerId)?.get("catalogs")?.jsonObject?.get("equipment_assignments")
            ?.let { raw ->
                runCatching { raw.jsonArray }.getOrNull().orEmpty().mapNotNull { entry ->
                    val assignment = entry.jsonObject
                    if (assignment["equipment_id"]?.jsonPrimitive?.content?.toLongOrNull() != equipmentId) null
                    else assignment["start_date"]?.jsonPrimitive?.content?.take(10)?.let {
                        it to assignment["end_date"]?.jsonPrimitive?.content?.take(10)
                    }
                }
            }.orEmpty()
        val fromOrderDetails = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().flatMap { it.equipment }.filter { it.equipmentId == equipmentId }
            .mapNotNull { item -> item.startDate?.take(10)?.let { it to item.endDate?.take(10) } }
        val local = offlineSync.localEntities(ownerId).filter { it.type == "equipment" }
            .mapNotNull { entity ->
                val payload = entity.payload
                if (payload["equipment_id"]?.jsonPrimitive?.content?.toLongOrNull() != equipmentId) null
                else payload["start_date"]?.jsonPrimitive?.content?.take(10)?.let {
                    it to payload["end_date"]?.jsonPrimitive?.content?.take(10)
                }
            }
        return fromCatalog + fromOrderDetails + local
    }

    private fun offlineOrders(ownerId: Long): List<OrdenTrabajo> {
        val snapshotOrders = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().map { dto -> OrdenTrabajoDto(
                dto.id, dto.code, dto.date, dto.client, dto.description, dto.mineralWeight, dto.weightUnit, dto.status, dto.statusCode,
            ).toModel() }
        val localOrders = offlineSync.localEntities(ownerId).filter { it.type == "order" }.mapNotNull { entity ->
            runCatching { ApiJson.instance.decodeFromString<OrdenTrabajoDto>(entity.payload.toString()).toModel() }.getOrNull()
        }
        return (snapshotOrders + localOrders).associateBy { it.id }.values.sortedWith(
            compareByDescending<OrdenTrabajo> { it.fecha }.thenByDescending { it.id },
        )
    }

    private fun detalleLocal(ownerId: Long, id: Long): DetalleOrdenTrabajo {
        val snapshotOrder = offlineSync.snapshot(ownerId)?.get("orders")
            ?.let { raw -> runCatching { ApiJson.instance.decodeFromString<List<OrdenTrabajoDetalleDto>>(raw.toString()) }.getOrNull() }
            .orEmpty().firstOrNull { it.id == id }
        val localOrders = offlineSync.localEntities(ownerId).filter { it.type == "order" && it.id == id }
        val localOrder = localOrders.lastOrNull()?.let { runCatching { ApiJson.instance.decodeFromString<OrdenTrabajoDetalleDto>(it.payload.toString()) }.getOrNull() }
        val orderDto = when {
            localOrder != null && snapshotOrder != null -> snapshotOrder.copy(
                code = localOrder.code, date = localOrder.date, client = localOrder.client,
                description = localOrder.description, mineralWeight = localOrder.mineralWeight,
                weightUnit = localOrder.weightUnit, status = localOrder.status, statusCode = localOrder.statusCode,
            )
            localOrder != null -> localOrder
            snapshotOrder != null -> snapshotOrder
            else -> throw OrdenesException("No se encontró la orden en la descarga local.")
        }
        val local = offlineSync.localEntities(ownerId)
        val localProcesses = local.filter { it.type == "process" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<ProcesoOrdenDto>(it.payload.toString()) }.getOrNull() }
        val localWorks = local.filter { it.type == "employee_work" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<TrabajoEmpleadoDto>(it.payload.toString()) }.getOrNull() }
        val localEquipment = local.filter { it.type == "equipment" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<EquipoProcesoDto>(it.payload.toString()) }.getOrNull() }
        val localLiquidations = local.filter { it.type == "liquidation" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<LiquidacionOrdenDto>(it.payload.toString()) }.getOrNull() }
        val localIncomes = local.filter { it.type == "income" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<IngresoOrdenDto>(it.payload.toString()) }.getOrNull() }
        val deletedIncomeIds = local.filter { it.type == "income_deleted" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { it.payload["id"]?.jsonPrimitive?.content?.toLongOrNull() }.toSet()
        val deletedLiquidationIds = local.filter { it.type == "liquidation_deleted" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { it.payload["id"]?.jsonPrimitive?.content?.toLongOrNull() }.toSet()
        val localAgreements = local.filter { it.type == "agreement" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { runCatching { ApiJson.instance.decodeFromString<AcuerdoOrdenDto>(it.payload.toString()) }.getOrNull() }
        val deletedAgreementIds = local.filter { it.type == "agreement_deleted" && it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
            .mapNotNull { it.payload["id"]?.jsonPrimitive?.content?.toLongOrNull() }.toSet()
        val localCostRows = local.filter { it.type == "process_cost" || it.type == "process_cost_deleted" }
            .filter { it.payload["process_id"]?.jsonPrimitive?.content?.toLongOrNull() in
                (orderDto.processes + localProcesses).map { process -> process.id } }
        val costOverrides = localCostRows.filter { it.type == "process_cost" }.mapNotNull { row ->
            runCatching { ApiJson.instance.decodeFromString<ProcessCostDto>(row.payload.toString()) }.getOrNull()
        }
        val deletedCostIds = localCostRows.filter { it.type == "process_cost_deleted" }
            .mapNotNull { it.payload["id"]?.jsonPrimitive?.content?.toLongOrNull() }.toSet()
        val localMaterialRows = local.filter { it.type == "process_material" || it.type == "process_material_deleted" }
            .filter { it.payload["process_id"]?.jsonPrimitive?.content?.toLongOrNull() in
                (orderDto.processes + localProcesses).map { process -> process.id } }
        val materialOverrides = localMaterialRows.filter { it.type == "process_material" }.mapNotNull { row ->
            runCatching { ApiJson.instance.decodeFromString<ProcessMaterialDto>(row.payload.toString()) }.getOrNull()
        }
        val deletedMaterialIds = localMaterialRows.filter { it.type == "process_material_deleted" }
            .mapNotNull { it.payload["id"]?.jsonPrimitive?.content?.toLongOrNull() }.toSet()
        val localOrderCostRows = local.filter { it.type == "order_cost" || it.type == "order_cost_deleted" }
            .filter { it.payload["order_id"]?.jsonPrimitive?.content?.toLongOrNull() == id }
        val orderCostOverrides = localOrderCostRows.filter { it.type == "order_cost" }.mapNotNull { row ->
            runCatching { ApiJson.instance.decodeFromString<CostoOrdenDto>(row.payload.toString()) }.getOrNull()
        }
        val deletedOrderCostIds = localOrderCostRows.filter { it.type == "order_cost_deleted" }
            .mapNotNull { it.payload["id"]?.jsonPrimitive?.content?.toLongOrNull() }.toSet()
        val mergedProcesses = (orderDto.processes + localProcesses).distinctBy { it.id }.map { process ->
            process.copy(costs = (process.costs.filterNot { it.id in deletedCostIds || costOverrides.any { cost -> cost.id == it.id } } +
                costOverrides.filter { it.processId == process.id && it.id !in deletedCostIds }).distinctBy { it.id },
                materials = (process.materials.filterNot { it.id in deletedMaterialIds || materialOverrides.any { material -> material.id == it.id } } +
                    materialOverrides.filter { it.processId == process.id && it.id !in deletedMaterialIds }).distinctBy { it.id })
        }
        return orderDto.copy(
            processes = mergedProcesses,
            costs = (orderDto.costs.filterNot { it.process.isNullOrBlank() &&
                (it.id in deletedOrderCostIds || orderCostOverrides.any { localCost -> localCost.id == it.id }) } +
                orderCostOverrides.filterNot { it.id in deletedOrderCostIds }).distinctBy { it.id },
            employeeWork = (orderDto.employeeWork + localWorks).distinctBy { it.id },
            equipment = (orderDto.equipment + localEquipment).distinctBy { it.id },
            agreements = (orderDto.agreements.filterNot { it.id in deletedAgreementIds || localAgreements.any { localAgreement -> localAgreement.id == it.id } } +
                localAgreements.filterNot { it.id in deletedAgreementIds }).distinctBy { it.id },
            incomes = (orderDto.incomes.filterNot { it.id in deletedIncomeIds || localIncomes.any { localIncome -> localIncome.id == it.id } } +
                localIncomes.filterNot { it.id in deletedIncomeIds }).distinctBy { it.id },
            liquidations = (orderDto.liquidations.filterNot { it.id in deletedLiquidationIds || localLiquidations.any { localLiquidation -> localLiquidation.id == it.id } } +
                localLiquidations.filterNot { it.id in deletedLiquidationIds }).distinctBy { it.id },
        ).toModel()
    }

    private companion object {
        const val LIST_PAGE_SIZE = 30
        const val CALENDAR_PAGE_SIZE = 100
    }

    private fun apiException(body: String, fallback: String): OrdenesException {
        val message = runCatching { ApiJson.instance.decodeFromString<ApiError>(body).message }
            .getOrNull()?.takeIf(String::isNotBlank)
        return OrdenesException(message ?: fallback)
    }
}

private fun OrdenTrabajo.toOfflineJson(clientId: Long? = null, unitId: Long? = null): JsonObject = buildJsonObject {
    put("id", id)
    put("code", codigo)
    fecha?.let { put("date", it) }
    put("client", buildJsonObject {
        clientId?.let { put("id", it) }
        cliente?.let { put("name", it) }
    })
    descripcion?.let { put("description", it) }
    pesoMineral?.let { put("mineral_weight", it) }
    unidadPeso?.let { put("weight_unit", it) }
    unitId?.let { put("weight_unit_id", it) }
    put("status", estado)
    put("status_code", codigoEstado)
}

private fun CostoProcesoTrabajo.toOfflineJson(processId: Long): JsonObject = buildJsonObject {
    put("id", id); put("process_id", processId); put("category_id", categoriaId); put("category", categoria)
    put("date", fecha); put("description", descripcion); put("quantity", cantidad); put("unit_cost", costoUnitario)
    put("total", total); put("currency_id", monedaId); put("currency", moneda); totalNio?.let { put("total_nio", it) }
    observaciones?.let { put("observations", it) }
}

private fun CostoOrdenTrabajo.toOfflineJson(orderId: Long): JsonObject = buildJsonObject {
    put("id", id); put("order_id", orderId); put("category_id", categoriaId); put("category", categoria.orEmpty())
    put("date", fecha.orEmpty()); put("description", descripcion.orEmpty())
    cantidadNumero?.let { put("quantity", it) }; costoUnitario?.let { put("unit_cost", it) }
    put("total", total?.toDoubleOrNull() ?: 0.0); put("currency_id", monedaId); put("currency", moneda.orEmpty())
    totalNio?.toDoubleOrNull()?.let { put("total_nio", it) }; observaciones?.let { put("observations", it) }
}

private fun MaterialProcesoTrabajo.toOfflineJson(processId: Long): JsonObject = buildJsonObject {
    put("id", id); put("process_id", processId); put("product_id", productoId); put("product", producto)
    unidad?.let { put("unit", it) }; put("date", fecha); put("quantity", cantidad)
    put("unit_cost", costoUnitario); totalNio?.let { put("total_nio", it) }
    observaciones?.let { put("observations", it) }
}

private fun Double.displayMaterial(): String = ((this * 1000).toLong() / 1000.0).toString()

private fun Double.roundMoney(): Double = (this * 100).roundToInt() / 100.0

@Serializable
private data class OfflineEmployeeRateDto(
    @SerialName("employee_id") val employeeId: Long,
    @SerialName("payment_type_id") val paymentTypeId: Long,
    val rate: Double,
    @SerialName("currency_id") val currencyId: Long,
    val currency: String? = null,
    @SerialName("is_base_currency") val isBaseCurrency: Boolean,
    @SerialName("calculation_method") val calculationMethod: String? = null,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    val active: Boolean,
)

@Serializable
private data class OfflineExchangeRateDto(
    val id: Long = 0,
    @SerialName("moneda_id") val currencyId: Long,
    val fecha: String,
    @SerialName("valor") val rate: Double,
)

@Serializable
private data class CategoriaCostoDto(val id: Long, val name: String)

@Serializable
private data class MonedaCostoDto(
    val id: Long,
    val code: String,
    val name: String,
    @SerialName("is_base_currency") val isBaseCurrency: Boolean = false,
)

@Serializable
private data class IncomeTypeDto(val id: Long, val name: String)

@Serializable
private data class IncomeOptionsResponseDto(val data: IncomeOptionsDto)

@Serializable
private data class IncomeOptionsDto(
    val date: String,
    val types: List<IncomeTypeDto> = emptyList(),
    val units: List<CategoriaCostoDto> = emptyList(),
    val currencies: List<MonedaCostoDto> = emptyList(),
)

private fun IncomeOptionsDto.toModel() = OpcionesIngresoOrden(
    fecha = date,
    tipos = types.map { TipoIngresoOpcion(it.id, it.name) },
    unidades = units.map { UnidadPesoOpcion(it.id, it.name) },
    monedas = currencies.map { MonedaCostoOpcion(it.id, it.code, it.name, it.isBaseCurrency) },
)

@Serializable
private data class OpcionesCostoResponseDto(val data: OpcionesCostoDto)

@Serializable
private data class OpcionesCostoDto(
    val categories: List<CategoriaCostoDto> = emptyList(),
    val currencies: List<MonedaCostoDto> = emptyList(),
    val date: String,
)

private fun OpcionesCostoDto.toModel() = OpcionesCostoProceso(
    categorias = categories.map { CategoriaCostoOpcion(it.id, it.name) },
    monedas = currencies.map { MonedaCostoOpcion(it.id, it.code, it.name, it.isBaseCurrency) },
    fecha = date,
)

private fun OpcionesCostoDto.toOrderModel() = OpcionesCostoOrden(
    categorias = categories.map { CategoriaCostoOpcion(it.id, it.name) },
    monedas = currencies.map { MonedaCostoOpcion(it.id, it.code, it.name, it.isBaseCurrency) },
    fecha = date,
)

@Serializable
private data class CostosOrdenResponseDto(val data: CostosOrdenDto)

@Serializable
private data class CostosOrdenDto(val costs: List<CostoOrdenDto> = emptyList())

@Serializable
private data class OrderCostResponseDto(val data: CostoOrdenDto)

private fun CostoOrdenDto.toOrderModel() = CostoOrdenTrabajo(
    id = id,
    fecha = date,
    proceso = process,
    categoria = category,
    descripcion = description,
    cantidad = quantity?.contentOrNull,
    moneda = currency,
    total = total?.contentOrNull,
    totalNio = totalNio?.contentOrNull,
    categoriaId = categoryId,
    cantidadNumero = quantity?.contentOrNull?.toDoubleOrNull(),
    costoUnitario = unitCost?.contentOrNull?.toDoubleOrNull(),
    monedaId = currencyId,
    observaciones = observations,
)

@Serializable
private data class CostosProcesoResponseDto(val data: CostosProcesoDto)

@Serializable
private data class CostosProcesoDto(val costs: List<ProcessCostDto> = emptyList())

@Serializable
private data class ProcessCostResponseDto(val data: ProcessCostDto)

@Serializable
private data class OpcionesMaterialesResponseDto(val data: OpcionesMaterialesDto)

@Serializable
private data class OpcionesMaterialesDto(
    val products: List<ProductoMateriaDto> = emptyList(),
    val date: String,
)

private fun OpcionesMaterialesDto.toModel() = OpcionesMaterialProceso(products.map { it.toModel() }, date)

@Serializable
private data class MaterialesProcesoResponseDto(val data: MaterialesProcesoDto)

@Serializable
private data class MaterialesProcesoDto(val materials: List<ProcessMaterialDto> = emptyList())

@Serializable
private data class MaterialProcesoResponseDto(val data: ProcessMaterialDto)

@Serializable
private data class ProductoMateriaDto(
    val id: Long,
    val code: String? = null,
    val name: String,
    val unit: String? = null,
    val stock: Double = 0.0,
    @SerialName("average_cost") val averageCost: Double = 0.0,
)

private fun ProductoMateriaDto.toModel() = ProductoMateriaPrima(id, code.orEmpty(), name, unit.orEmpty(), stock, averageCost)

@Serializable
internal data class ProcessMaterialDto(
    val id: Long,
    @SerialName("process_id") val processId: Long = 0,
    @SerialName("product_id") val productId: Long,
    val product: String,
    val unit: String? = null,
    val date: String,
    val quantity: Double,
    @SerialName("unit_cost") val unitCost: Double,
    @SerialName("total_nio") val totalNio: Double? = null,
    val observations: String? = null,
)

private fun ProcessMaterialDto.toModel() = MaterialProcesoTrabajo(
    id, productId, product, unit, date, quantity, unitCost, totalNio, observations,
)

@Serializable
internal data class ProcessCostDto(
    val id: Long,
    @SerialName("process_id") val processId: Long = 0,
    @SerialName("category_id") val categoryId: Long,
    val category: String,
    @SerialName("date") val date: String,
    val description: String,
    val quantity: Double,
    @SerialName("unit_cost") val unitCost: Double,
    val total: Double,
    @SerialName("currency_id") val currencyId: Long,
    val currency: String,
    @SerialName("total_nio") val totalNio: Double? = null,
    val observations: String? = null,
)

private fun ProcessCostDto.toModel() = CostoProcesoTrabajo(
    id, categoryId, category, date, description, quantity, unitCost, total, currencyId, currency, totalNio, observations,
)

@Serializable
private data class CrearOrdenRequestDto(
    @SerialName("cliente_id") val clientId: Long,
    @SerialName("fecha") val date: String,
    @SerialName("descripcion") val description: String?,
    @SerialName("peso_mineral") val mineralWeight: Double,
    @SerialName("unidad_peso_id") val weightUnitId: Long,
)

@Serializable
private data class ActualizarOrdenRequestDto(
    @SerialName("cliente_id") val clientId: Long,
    @SerialName("fecha") val date: String?,
    @SerialName("descripcion") val description: String?,
    @SerialName("peso_mineral") val mineralWeight: Double?,
    @SerialName("unidad_peso_id") val weightUnitId: Long?,
)

@Serializable
private data class OpcionesOrdenResponseDto(val data: OpcionesOrdenDto)

@Serializable
private data class EdicionOrdenResponseDto(val data: EdicionOrdenDto)

@Serializable
private data class EdicionOrdenDto(
    val order: OrdenEditableDto,
    @SerialName("has_processes") val hasProcesses: Boolean,
    val clients: List<ClienteEditableDto>,
    val units: List<OpcionUnidadPesoDto>,
)

@Serializable
private data class OrdenEditableDto(
    val id: Long,
    val code: String,
    @SerialName("client_id") val clientId: Long,
    @SerialName("client_name") val clientName: String? = null,
    val date: String,
    val description: String? = null,
    @SerialName("mineral_weight") val mineralWeight: Double,
    @SerialName("weight_unit_id") val weightUnitId: Long? = null,
    @SerialName("weight_unit") val weightUnit: String? = null,
    val status: String,
    @SerialName("status_code") val statusCode: Int,
)

@Serializable
private data class ClienteEditableDto(val id: Long, val name: String, @SerialName("is_active") val isActive: Boolean)

@Serializable
private data class OpcionesOrdenDto(
    val clients: List<OpcionClienteOrdenDto>,
    val units: List<OpcionUnidadPesoDto>,
)

@Serializable
private data class OpcionClienteOrdenDto(val id: Long, val name: String)

@Serializable
private data class OpcionUnidadPesoDto(val id: Long, val name: String)

private fun EdicionOrdenDto.toModel() = FormularioEdicionOrden(
    orden = OrdenTrabajo(
        id = order.id,
        codigo = order.code,
        fecha = order.date,
        cliente = order.clientName,
        descripcion = order.description,
        pesoMineral = order.mineralWeight,
        unidadPeso = order.weightUnit,
        estado = order.status,
        codigoEstado = order.statusCode,
    ),
    clienteId = order.clientId,
    unidadPesoId = order.weightUnitId,
    tieneProcesos = hasProcesses,
    clientes = clients.map { ClienteOpcionOrden(it.id, it.name) },
    unidades = units.map { UnidadPesoOpcion(it.id, it.name) },
)

private fun OpcionesOrdenDto.toModel() = OpcionesFormularioOrden(
    clientes = clients.map { ClienteOpcionOrden(it.id, it.name) },
    unidades = units.map { UnidadPesoOpcion(it.id, it.name) },
)

@Serializable
private data class OrdenCreadaResponseDto(val data: OrdenTrabajoDto)

@Serializable
private data class EtapasProcesoResponseDto(val data: EtapasProcesoDto)

@Serializable
private data class EtapasProcesoDto(val stages: List<EtapaProcesoDto>)

@Serializable
private data class EtapaProcesoDto(val id: Long, val name: String, val order: Int)

private fun EtapaProcesoDto.toModel() = EtapaOpcionProceso(id, name, order)

@Serializable
private data class CrearProcesoRequestDto(
    @SerialName("etapa_id") val etapaId: Long,
    @SerialName("fecha_inicio") val fechaInicio: String?,
    @SerialName("fecha_fin") val fechaFin: String?,
    @SerialName("peso_entrada") val pesoEntrada: Double?,
    @SerialName("peso_salida") val pesoSalida: Double?,
    @SerialName("observaciones") val observaciones: String?,
)

data class OpcionTrabajoEmpleado(val id: Long, val codigo: String?, val nombre: String, val metodoCalculo: String? = null)
data class OpcionUnidadTrabajo(val id: Long, val nombre: String)
data class OpcionesTrabajoEmpleado(
    val empleados: List<OpcionTrabajoEmpleado>,
    val tiposPago: List<OpcionTrabajoEmpleado>,
    val unidades: List<OpcionUnidadTrabajo>,
)
data class TarifaTrabajoEmpleado(
    val tarifa: Double,
    val moneda: String,
    val tipoCambio: Double,
    val tarifaNio: Double,
    val metodoCalculo: String,
    val vigenciaInicio: String?,
    val vigenciaFin: String?,
)

@Serializable
private data class OpcionesTrabajoResponseDto(val data: OpcionesTrabajoDto)

@Serializable
private data class OpcionesTrabajoDto(
    val employees: List<OpcionTrabajoDto>,
    @SerialName("payment_types") val paymentTypes: List<OpcionTipoPagoDto>,
    val units: List<OpcionUnidadDto>,
)

@Serializable
private data class OpcionTrabajoDto(val id: Long, val code: String, val name: String)

@Serializable
private data class OpcionTipoPagoDto(val id: Long, val name: String, @SerialName("calculation_method") val calculationMethod: String)

@Serializable
private data class OpcionUnidadDto(val id: Long, val name: String)

private fun OpcionesTrabajoDto.toModel() = OpcionesTrabajoEmpleado(
    empleados = employees.map { OpcionTrabajoEmpleado(it.id, it.code, it.name) },
    tiposPago = paymentTypes.map { OpcionTrabajoEmpleado(it.id, null, it.name, it.calculationMethod) },
    unidades = units.map { OpcionUnidadTrabajo(it.id, it.name) },
)

@Serializable
private data class TarifaTrabajoResponseDto(val data: TarifaTrabajoDto)

@Serializable
private data class TarifaTrabajoDto(
    val rate: Double,
    val currency: String,
    @SerialName("exchange_rate") val exchangeRate: Double,
    @SerialName("rate_nio") val rateNio: Double,
    @SerialName("calculation_method") val calculationMethod: String,
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
)

private fun TarifaTrabajoDto.toModel() = TarifaTrabajoEmpleado(rate, currency, exchangeRate, rateNio, calculationMethod, validFrom, validUntil)

@Serializable
private data class CrearTrabajoEmpleadoRequestDto(
    @SerialName("empleado_id") val employeeId: Long,
    @SerialName("tipo_pago_id") val paymentTypeId: Long,
    @SerialName("fecha") val date: String,
    @SerialName("hora_inicio") val startTime: String?,
    @SerialName("hora_fin") val endTime: String?,
    @SerialName("cantidad") val quantity: Double,
    @SerialName("unidad_id") val unitId: Long,
    @SerialName("descripcion") val description: String?,
    @SerialName("observaciones") val observations: String?,
)

@Serializable
private data class OpcionesEquipoResponseDto(val data: OpcionesEquipoDto)

@Serializable
private data class OpcionesEquipoDto(
    val equipment: List<EquipoOpcionDto>,
    @SerialName("default_start") val defaultStart: String,
)

@Serializable
private data class EquipoOpcionDto(
    val id: Long,
    val code: String,
    val name: String,
    @SerialName("acquisition_value") val acquisitionValue: Double,
    @SerialName("residual_value") val residualValue: Double,
    @SerialName("useful_life_months") val usefulLifeMonths: Int,
    @SerialName("remaining_value") val remainingValue: Double = 0.0,
)

private fun OpcionesEquipoDto.toModel() = OpcionesEquipoProceso(
    equipos = equipment.map {
        EquipoOpcionProceso(it.id, it.code, it.name, it.acquisitionValue, it.residualValue, it.usefulLifeMonths)
    },
    inicioPredeterminado = defaultStart,
)

@Serializable
private data class DepreciacionEquipoResponseDto(val data: DepreciacionEquipoDto)

@Serializable
private data class DepreciacionEquipoDto(
    val code: String,
    @SerialName("acquisition_value") val acquisitionValue: Double,
    @SerialName("residual_value") val residualValue: Double,
    @SerialName("useful_life_months") val usefulLifeMonths: Int,
    val days: Double,
    @SerialName("daily_depreciation") val dailyDepreciation: Double,
    @SerialName("total_depreciation") val totalDepreciation: Double,
    @SerialName("available_value") val availableValue: Boolean,
    val overlap: String? = null,
)

private fun DepreciacionEquipoDto.toModel() = DepreciacionEquipoProceso(
    code, acquisitionValue, residualValue, usefulLifeMonths, days,
    dailyDepreciation, totalDepreciation, availableValue, overlap,
)

@Serializable
private data class AsignarEquipoRequestDto(
    @SerialName("equipo_id") val equipoId: Long,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_fin") val fechaFin: String?,
    @SerialName("observaciones") val observaciones: String?,
)

class OrdenesException(message: String) : Exception(message)

internal fun parseOrdersPage(body: String): PaginaOrdenes {
    val normalizedBody = body.removePrefix("\uFEFF").trimStart()
    val payload = runCatching { ApiJson.instance.decodeFromString<OrdenesResponseDto>(normalizedBody) }
        .getOrElse { error ->
            val reason = if (normalizedBody.startsWith("<")) {
                "el servidor devolvió HTML en lugar de JSON"
            } else {
                "el formato de la respuesta no es válido (${error::class.simpleName})"
            }
            throw OrdenesException("No se pudieron interpretar las órdenes: $reason.")
        }

    return PaginaOrdenes(
        ordenes = payload.data.map(OrdenTrabajoDto::toModel),
        paginaActual = payload.meta.currentPage,
        ultimaPagina = payload.meta.lastPage,
        total = payload.meta.total,
    )
}

internal fun parseOrderDetail(body: String): DetalleOrdenTrabajo {
    val normalizedBody = body.removePrefix("\uFEFF").trimStart()
    val payload = runCatching { ApiJson.instance.decodeFromString<OrdenDetalleResponseDto>(normalizedBody) }
        .getOrElse { error ->
            val reason = if (normalizedBody.startsWith("<")) {
                "el servidor devolvió HTML en lugar de JSON"
            } else {
                "el formato de la respuesta no es válido (${error::class.simpleName})"
            }
            throw OrdenesException("No se pudo interpretar el detalle de la orden: $reason.")
        }
    return payload.data.toModel()
}

private fun OrdenTrabajoDetalleDto.toModel() = DetalleOrdenTrabajo(
    orden = OrdenTrabajoDto(
        id = id,
        code = code,
        date = date,
        client = client,
        description = description,
        mineralWeight = mineralWeight,
        weightUnit = weightUnit,
        status = status,
        statusCode = statusCode,
    ).toModel(),
    procesos = processes.map { process ->
        ProcesoOrdenTrabajo(
            id = process.id,
            codigo = process.code,
            etapa = process.stage,
            estado = process.status,
            fechaInicio = process.startDate,
            fechaFin = process.endDate,
            pesoEntrada = process.inputWeight?.contentOrNull?.toDoubleOrNull(),
            pesoSalida = process.outputWeight?.contentOrNull?.toDoubleOrNull(),
            observaciones = process.observations,
            costos = process.costs.map { it.toModel() },
            materiales = process.materials.map { it.toModel() },
        )
    },
    costos = costs.map { cost ->
        CostoOrdenTrabajo(
            id = cost.id,
            fecha = cost.date,
            proceso = cost.process,
            categoria = cost.category,
            descripcion = cost.description,
            cantidad = cost.quantity?.contentOrNull,
            moneda = cost.currency,
            total = cost.total?.contentOrNull,
            totalNio = cost.totalNio?.contentOrNull,
            categoriaId = cost.categoryId,
            cantidadNumero = cost.quantity?.contentOrNull?.toDoubleOrNull(),
            costoUnitario = cost.unitCost?.contentOrNull?.toDoubleOrNull(),
            monedaId = cost.currencyId,
            observaciones = cost.observations,
        )
    },
    ingresos = incomes.map { income ->
        IngresoOrdenTrabajo(
            id = income.id,
            fecha = income.date,
            tipo = income.type,
            descripcion = income.description,
            moneda = income.currency,
            total = income.total?.contentOrNull,
            totalNio = income.totalNio?.contentOrNull,
            cantidad = income.quantity?.contentOrNull,
            unidad = income.unit,
            precioUnitario = income.unitPrice?.contentOrNull,
            tipoId = income.typeId,
            unidadId = income.unitId,
            monedaId = income.currencyId,
            observaciones = income.observations,
        )
    },
    acuerdos = agreements.map { agreement ->
        AcuerdoOrdenTrabajo(
            id = agreement.id,
            tipoParticipacionId = agreement.participationTypeId,
            tipoParticipacion = agreement.participationType,
            modalidad = agreement.modality,
            porcentajeCliente = agreement.clientPercentage?.contentOrNull,
            porcentajeEmpresa = agreement.companyPercentage?.contentOrNull,
            tarifaServicio = agreement.serviceFee?.contentOrNull,
            moneda = agreement.currency,
            monedaId = agreement.currencyId,
            observaciones = agreement.observations,
            activo = agreement.active,
        )
    },
    liquidaciones = liquidations.map { liquidation ->
        LiquidacionOrdenTrabajo(
            id = liquidation.id,
            fecha = liquidation.date,
            gramosRecuperados = liquidation.recoveredGrams?.contentOrNull,
            gramosCliente = liquidation.clientGrams?.contentOrNull,
            gramosEmpresa = liquidation.companyGrams?.contentOrNull,
            porcentajeCliente = liquidation.clientPercentage?.contentOrNull,
            porcentajeEmpresa = liquidation.companyPercentage?.contentOrNull,
            observaciones = liquidation.observations,
        )
    },
    trabajosEmpleados = employeeWork.map { work ->
        TrabajoEmpleadoOrden(
            id = work.id,
            codigoProceso = work.process,
            empleado = work.employee,
            tipoPago = work.paymentType,
            fecha = work.date,
            descripcion = work.description,
            cantidad = work.quantity?.contentOrNull,
            unidad = work.unit,
            moneda = work.currency,
            total = work.total?.contentOrNull,
            totalNio = work.totalNio?.contentOrNull,
        )
    },
    equipos = equipment.map { assignment ->
        EquipoProcesoOrden(
            id = assignment.id,
            codigoProceso = assignment.process,
            equipoId = assignment.equipmentId,
            codigoEquipo = assignment.equipmentCode,
            nombreEquipo = assignment.equipmentName,
            inicio = assignment.startDate,
            fin = assignment.endDate,
            dias = assignment.days,
            depreciacionDiaria = assignment.dailyDepreciation,
            depreciacionTotal = assignment.totalDepreciation,
            observaciones = assignment.observations,
        )
    },
)

@Serializable
internal data class OrdenesResponseDto(
    val data: List<OrdenTrabajoDto>,
    val meta: OrdenesMetaDto,
)

@Serializable
internal data class OrdenDetalleResponseDto(val data: OrdenTrabajoDetalleDto)

@Serializable
internal data class OrdenTrabajoDetalleDto(
    val id: Long,
    val code: String,
    val date: String? = null,
    val client: OrdenClienteDto? = null,
    val description: String? = null,
    @SerialName("mineral_weight") val mineralWeight: JsonPrimitive? = null,
    @SerialName("weight_unit") val weightUnit: String? = null,
    val status: String,
    @SerialName("status_code") val statusCode: Int,
    val processes: List<ProcesoOrdenDto> = emptyList(),
    val costs: List<CostoOrdenDto> = emptyList(),
    val incomes: List<IngresoOrdenDto> = emptyList(),
    val agreements: List<AcuerdoOrdenDto> = emptyList(),
    val liquidations: List<LiquidacionOrdenDto> = emptyList(),
    @SerialName("employee_work") val employeeWork: List<TrabajoEmpleadoDto> = emptyList(),
    val equipment: List<EquipoProcesoDto> = emptyList(),
    @SerialName("liquidation_agreements") val liquidationAgreements: List<LiquidationAgreementDto> = emptyList(),
    @SerialName("weight_unit_id") val weightUnitId: Long? = null,
)

@Serializable
internal data class EquipoProcesoDto(
    val id: Long,
    val process: String? = null,
    @SerialName("equipment_id") val equipmentId: Long,
    @SerialName("equipment_code") val equipmentCode: String? = null,
    @SerialName("equipment_name") val equipmentName: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val days: Double = 0.0,
    @SerialName("daily_depreciation") val dailyDepreciation: Double = 0.0,
    @SerialName("total_depreciation") val totalDepreciation: Double = 0.0,
    val observations: String? = null,
)

@Serializable
internal data class TrabajoEmpleadoDto(
    val id: Long,
    val date: String? = null,
    val employee: String? = null,
    val process: String? = null,
    @SerialName("payment_type") val paymentType: String? = null,
    val description: String? = null,
    val quantity: JsonPrimitive? = null,
    val unit: String? = null,
    val currency: String? = null,
    val total: JsonPrimitive? = null,
    @SerialName("total_nio") val totalNio: JsonPrimitive? = null,
)

@Serializable
internal data class AcuerdoOrdenDto(
    val id: Long,
    @SerialName("participation_type_id") val participationTypeId: Long = 0,
    @SerialName("participation_type") val participationType: String? = null,
    val modality: String,
    @SerialName("client_percentage") val clientPercentage: JsonPrimitive? = null,
    @SerialName("company_percentage") val companyPercentage: JsonPrimitive? = null,
    @SerialName("service_fee") val serviceFee: JsonPrimitive? = null,
    val currency: String? = null,
    @SerialName("currency_id") val currencyId: Long = 0,
    val observations: String? = null,
    val active: Boolean = false,
)

@Serializable
internal data class LiquidationAgreementDto(
    val id: Long,
    @SerialName("participation_type") val participationType: String? = null,
    @SerialName("client_percentage") val clientPercentage: JsonPrimitive,
    @SerialName("company_percentage") val companyPercentage: JsonPrimitive,
)

private fun LiquidationAgreementDto.toModel() = AcuerdoLiquidacionOpcion(
    id = id,
    tipoParticipacion = participationType,
    porcentajeCliente = clientPercentage.contentOrNull?.toDoubleOrNull() ?: 0.0,
    porcentajeEmpresa = companyPercentage.contentOrNull?.toDoubleOrNull() ?: 0.0,
)

@Serializable
internal data class LiquidationOptionsDataDto(
    val date: String,
    val agreements: List<LiquidationAgreementDto> = emptyList(),
)

@Serializable
internal data class LiquidationOptionsResponseDto(val data: LiquidationOptionsDataDto)

private fun LiquidationOptionsDataDto.toModel() = OpcionesLiquidacionOrden(date, agreements.map { it.toModel() })

private fun roundFour(value: Double): Double = (value * 10_000.0).roundToInt() / 10_000.0

@Serializable
internal data class ParticipationTypeDto(val id: Long, val name: String)

@Serializable
internal data class AgreementCurrencyDto(
    val id: Long,
    val code: String,
    val name: String,
    @SerialName("is_base_currency") val isBaseCurrency: Boolean = false,
)

@Serializable
internal data class AgreementOptionsDataDto(
    @SerialName("participation_types") val participationTypes: List<ParticipationTypeDto> = emptyList(),
    val currencies: List<AgreementCurrencyDto> = emptyList(),
    @SerialName("base_currency_id") val baseCurrencyId: Long? = null,
)

@Serializable
internal data class AgreementOptionsResponseDto(val data: AgreementOptionsDataDto)

private fun AgreementOptionsDataDto.toModel() = OpcionesAcuerdoOrden(
    tipos = participationTypes.map { TipoParticipacionOpcion(it.id, it.name) },
    monedas = currencies.map { MonedaAcuerdoOpcion(it.id, it.code, it.name, it.isBaseCurrency) },
    monedaBaseId = baseCurrencyId ?: currencies.firstOrNull(AgreementCurrencyDto::isBaseCurrency)?.id,
)

@Serializable
internal data class LiquidacionOrdenDto(
    val id: Long,
    val date: String? = null,
    @SerialName("recovered_grams") val recoveredGrams: JsonPrimitive? = null,
    @SerialName("client_grams") val clientGrams: JsonPrimitive? = null,
    @SerialName("company_grams") val companyGrams: JsonPrimitive? = null,
    @SerialName("client_percentage") val clientPercentage: JsonPrimitive? = null,
    @SerialName("company_percentage") val companyPercentage: JsonPrimitive? = null,
    val observations: String? = null,
)

@Serializable
internal data class CostoOrdenDto(
    val id: Long,
    val date: String? = null,
    val process: String? = null,
    @SerialName("category_id") val categoryId: Long = 0,
    val category: String? = null,
    val description: String? = null,
    val quantity: JsonPrimitive? = null,
    @SerialName("unit_cost") val unitCost: JsonPrimitive? = null,
    @SerialName("currency_id") val currencyId: Long = 0,
    val currency: String? = null,
    val total: JsonPrimitive? = null,
    @SerialName("unit_cost_nio") val unitCostNio: JsonPrimitive? = null,
    @SerialName("total_nio") val totalNio: JsonPrimitive? = null,
    val observations: String? = null,
)

@Serializable
internal data class IngresoOrdenDto(
    val id: Long,
    val date: String? = null,
    @SerialName("type_id") val typeId: Long = 0,
    val type: String? = null,
    val description: String? = null,
    val quantity: JsonPrimitive? = null,
    @SerialName("unit_id") val unitId: Long? = null,
    val unit: String? = null,
    @SerialName("unit_price") val unitPrice: JsonPrimitive? = null,
    @SerialName("currency_id") val currencyId: Long = 0,
    val currency: String? = null,
    val total: JsonPrimitive? = null,
    @SerialName("total_nio") val totalNio: JsonPrimitive? = null,
    val observations: String? = null,
)

@Serializable
internal data class ProcesoOrdenDto(
    val id: Long,
    val code: String,
    val stage: String? = null,
    val status: String,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("input_weight") val inputWeight: JsonPrimitive? = null,
    @SerialName("output_weight") val outputWeight: JsonPrimitive? = null,
    val observations: String? = null,
    val costs: List<ProcessCostDto> = emptyList(),
    val materials: List<ProcessMaterialDto> = emptyList(),
)

@Serializable
internal data class OrdenTrabajoDto(
    val id: Long,
    val code: String,
    val date: String? = null,
    val client: OrdenClienteDto? = null,
    val description: String? = null,
    @SerialName("mineral_weight") val mineralWeight: JsonPrimitive? = null,
    @SerialName("weight_unit") val weightUnit: String? = null,
    val status: String,
    @SerialName("status_code") val statusCode: Int,
) {
    fun toModel() = OrdenTrabajo(
        id = id,
        codigo = code,
        fecha = date,
        cliente = client?.name,
        descripcion = description,
        pesoMineral = mineralWeight?.contentOrNull?.toDoubleOrNull(),
        unidadPeso = weightUnit,
        estado = status,
        codigoEstado = statusCode,
    )
}

@Serializable
internal data class OrdenClienteDto(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
internal data class OrdenesMetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    @SerialName("per_page") val perPage: Int,
    val total: Int,
)
