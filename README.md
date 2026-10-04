# credit-service

Dueño de los productos de crédito (créditos y tarjetas de crédito), sus pagos y consumos, y fuente
de verdad de la deuda vencida. Ficha completa: `bank-docs/services/credit-service.md`. Contrato:
`bank-docs/contracts/credit-service/`.

Construido a partir de `bank-service-template`, siguiendo la receta R1–R10 de
`bank-docs/implementation-plan.md` (sección 3, paso 1.4 del plan). Depende de `customer-service`
(tipo y estado del cliente) y de `transaction-service` (registra cada pago y consumo en el
historial vía `POST /transactions/records`) — REST en P1/P2, eventos en P3.

## Estado (receta R1–R10)
- [x] R1. Esqueleto: proyecto creado desde la plantilla (paquete `com.bank.credit`, `CreditServiceApplication`), contrato copiado a `src/main/resources/openapi/` (`service/openapi.yaml` + `common/common-schemas.yaml`), `PingController` eliminado, `bank-config/credit-service.yml` (puerto 8083, base `bank_credit`)
- [ ] R2. Dominio
- [ ] R3. Casos de uso y puertos
- [ ] R4. Persistencia
- [ ] R5. Adaptadores de entrada
- [ ] R6. Configuración y arranque real
- [ ] R7. Clientes salientes (`customer-service`, `transaction-service`)
- [ ] R8. Calidad (Checkstyle/Jacoco)
- [ ] R9. Postman
- [ ] R10. Cierre (README, diagramas, etiqueta)

## Comandos
- Compilar, estilo, tests y cobertura: `./mvnw verify` (reporte en `target/site/jacoco/index.html`)
- Arrancar (necesita `config-server` arriba): `./mvnw spring-boot:run`

## Resincronizar el contrato
Si `bank-docs/contracts/credit-service/openapi.yaml` cambia:
```powershell
.\copy-contracts.ps1 -ServiceName credit-service
```
Asume que `bank-docs` es una carpeta hermana; si no, pasa `-DocsRepo <ruta>`.
