# Secuencia — Otorgar crédito (`POST /credits`)

Implementado en `OpenCreditUseCaseImpl` (R3), `AcquisitionPolicy` y sus validadores (R2),
`CustomerRestAdapter` (R7), `CreditPersistenceAdapter` / `OverdueQueryAdapter` (R4) y
`CreditController` / `GlobalExceptionHandler` (R5). La emisión de tarjeta (`POST /credit-cards`) usa la
misma cadena con `ProductType.CREDIT_CARD` (el tope de crédito personal no aplica) y reintenta hasta 3
veces si el número generado choca con `uk_card_number`.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente HTTP
    participant Ctrl as CreditController
    participant UC as OpenCreditUseCaseImpl
    participant CL as CustomerLookupPort (CustomerRestAdapter)
    participant Cus as customer-service
    participant Repo as CreditRepositoryPort
    participant OQ as OverdueQueryPort
    participant Pol as AcquisitionPolicy
    participant Dom as Credit
    participant Evt as CreditEventPublisherPort
    participant EH as GlobalExceptionHandler

    C->>Ctrl: POST /api/v1/credits (customerId, amount, dueDate)
    Note over Ctrl: Bean Validation del DTO (amount > 0, campos requeridos)
    Ctrl->>UC: execute(OpenCreditCommand)

    UC->>CL: findById(customerId)
    CL->>Cus: GET /customers/{id} (circuit breaker + timeout 2 s)
    alt 200
        Cus-->>CL: Customer
        CL-->>UC: CustomerSnapshot(type, status)
    else 404
        Cus-->>CL: 404
        CL-->>UC: vacío
    else timeout / 5xx / circuito abierto
        CL-->>UC: error DownstreamServiceUnavailableException
        UC-->>EH: propaga
        EH-->>C: 503 SERVICE_UNAVAILABLE
    end

    UC->>Repo: existsUnpaidByCustomer(customerId)
    Repo-->>UC: hasUnpaid (crédito ACTIVE/OVERDUE)
    UC->>OQ: existsOverdueByCustomer(customerId)
    OQ-->>UC: hasOverdue (crédito o tarjeta OVERDUE)

    UC->>Pol: evaluate(AcquisitionContext)
    Note over Pol: 1 CustomerExists → 404 CUSTOMER_NOT_FOUND<br/>2 CustomerActive → 422 CUSTOMER_INACTIVE<br/>3 PersonalCreditLimit → 422 PERSONAL_CREDIT_LIMIT_REACHED<br/>4 OverdueDebt → 422 OVERDUE_DEBT
    alt alguna regla falla
        Pol-->>UC: excepción
        UC-->>EH: propaga
        EH-->>C: 404 / 422 con el código
    end
    Pol-->>UC: OwnerType (PERSONAL / BUSINESS, el del cliente)

    UC->>Dom: Credit.open(customerId, ownerType, amount, dueDate, demoMode, clock)
    alt dueDate ≤ hoy y demo-mode = false
        Dom-->>EH: INVALID_DUE_DATE
        EH-->>C: 422 INVALID_DUE_DATE
    end
    Dom-->>UC: Credit ACTIVE (saldo = monto)

    UC->>Repo: save(credit)
    alt otro crédito personal se guardó a la vez
        Repo-->>UC: duplicado en uk_credit_personal_unpaid
        UC-->>EH: PERSONAL_CREDIT_LIMIT_REACHED
        EH-->>C: 422 PERSONAL_CREDIT_LIMIT_REACHED
    end
    Repo-->>UC: Credit guardado (version 0)
    UC->>Evt: publish(CreditOpened)
    UC-->>Ctrl: Credit
    Ctrl-->>C: 201 Created (Credit)
```

## Notas

- **Los datos se reúnen antes de decidir.** El caso de uso hace las tres consultas (cliente, crédito
  no pagado, deuda vencida) y le pasa todo a `AcquisitionPolicy`, que es pura: no conoce puertos ni
  Reactor. Por eso se prueba sin mocks (`AcquisitionPolicyTest`).
- **Cliente inexistente = vacío, no error.** `CustomerRestAdapter` traduce el 404 a vacío y es el
  eslabón 1 de la cadena el que lo convierte en 404 `CUSTOMER_NOT_FOUND`; cualquier otra falla de
  `customer-service` es 503.
- **La carrera de la regla 3** (dos `POST /credits` simultáneos del mismo cliente personal) la cubre
  el índice único parcial `uk_credit_personal_unpaid` sobre `unpaidPersonal: true`;
  `PersistenceErrors` traduce el duplicado al mismo 422 que daría la validación.
- **Modo demo.** Con `credit.demo-mode: true` (Config Server) se acepta una fecha pasada, para poder
  probar la revisión de vencidos desde Postman sin esperar.
- **El evento sale por `NoOpCreditEventPublisher`** hasta que exista el broker (P3); solo deja log.
