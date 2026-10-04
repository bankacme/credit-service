# Secuencia — Pago de crédito de un tercero (`POST /credits/{id}/payments`)

Implementado en `GetPaymentInfoUseCaseImpl` y `PayCreditUseCaseImpl` (R3), `HistoryRecorder` y
`OverdueClearance` (R3), `MongoUnitOfWorkAdapter` / `OperationLogPersistenceAdapter` (R4),
`MovementRecorderRestAdapter` (R7) y `CreditController` (R5). El pago de tarjeta
(`POST /credit-cards/{id}/payments`) sigue el mismo flujo con `CreditCard.registerPayment`.

```mermaid
sequenceDiagram
    autonumber
    actor P as Pagador (otro cliente)
    participant Ctrl as CreditController
    participant PI as GetPaymentInfoUseCaseImpl
    participant UC as PayCreditUseCaseImpl
    participant Log as OperationLogPort
    participant Repo as CreditRepositoryPort
    participant Dom as Credit
    participant UOW as UnitOfWorkPort (Mongo tx)
    participant Evt as CreditEventPublisherPort
    participant OC as OverdueClearance
    participant HR as HistoryRecorder
    participant Tx as transaction-service
    participant EH as GlobalExceptionHandler

    P->>Ctrl: GET /api/v1/credits/{id}/payment-info
    Ctrl->>PI: execute(CREDIT, id)
    PI-->>Ctrl: PaymentInfoView (estado, monto adeudado, vencimiento)
    Ctrl-->>P: 200 PaymentInfo (vista limitada, sin datos del dueño)

    P->>Ctrl: POST /api/v1/credits/{id}/payments (operationId, amount, payerCustomerId)
    Ctrl->>UC: execute(PaymentCommand)
    UC->>Log: find(operationId)

    alt operationId ya usado
        Log-->>UC: CreditOperation existente
        alt otro producto, tipo o monto
            UC-->>EH: OPERATION_ID_REUSED
            EH-->>P: 409 OPERATION_ID_REUSED
        else coincide (repetición)
            UC->>HR: recordQuietly(existente) — reintenta si recorded = false
            UC-->>Ctrl: existente.toPaymentResult()
            Ctrl-->>P: 200 PaymentResult (el mismo, sin aplicar otra vez)
        end
    else operationId nuevo
        UC->>Repo: findById(creditId)
        alt no existe
            Repo-->>UC: vacío
            UC-->>EH: CreditNotFoundException
            EH-->>P: 404 CREDIT_NOT_FOUND
        end
        Repo-->>UC: Credit
        UC->>Dom: registerPayment(operationId, amount, payerCustomerId, clock)
        Note over Dom: PAID/CLOSED → 422 INVALID_STATE<br/>amount > saldo → 422 OVERPAYMENT<br/>saldo 0 → PAID<br/>payerCustomerId ≠ dueño → se guarda como tercero
        Dom-->>UC: PaymentOutcome(credit, result, leftOverdue)
        UC->>UOW: saveCreditAndOperation(credit, CreditOperation.ofPayment)
        Note over UOW: una transacción de Mongo:<br/>crédito (@Version) + operación (insert)
        alt conflicto de versión u operationId insertado a la vez
            UOW-->>EH: CONCURRENT_MODIFICATION
            EH-->>P: 409 CONCURRENT_MODIFICATION
        end
        UOW-->>UC: Credit guardado
        UC->>Evt: publish(CreditUpdated)
        UC->>Evt: publish(PaymentRegistered)
        opt leftOverdue (OVERDUE → PAID)
            UC->>OC: afterLeavingOverdue(customerId)
            OC->>OC: existsOverdueByCustomer?
            opt ya no le queda nada vencido
                OC->>Evt: publish(CustomerOverdueCleared)
            end
        end
        UC->>HR: recordQuietly(operation)
        HR->>Tx: POST /transactions/records (CREDIT_PAYMENT, payerCustomerId, occurredAt)
        alt registrado
            Tx-->>HR: 201
            HR->>Log: save(operation.markRecorded())
        else falla (timeout, 5xx, circuito abierto)
            Tx--xHR: error
            Note over HR: se traga el error:<br/>la operación queda recorded = false
        end
        UC-->>Ctrl: PaymentResult
        Ctrl-->>P: 200 PaymentResult (resultingBalance, status, payerCustomerId)
    end
```

## Notas

- **Idempotencia por `operationId`.** `credit_operations` tiene índice único por `operationId`; solo
  se guardan operaciones aplicadas, así que un pago rechazado (422) no deja registro y repetirlo se
  evalúa de nuevo. Una repetición idéntica devuelve el resultado guardado sin tocar el saldo.
- **Producto y operación se guardan juntos** (`MongoUnitOfWorkAdapter`, `TransactionalOperator`):
  nunca queda un saldo descontado sin su operación ni al revés. Necesita el replica set `rs0`.
- **El historial no cambia la respuesta.** El pago ya ocurrió cuando se llama a `transaction-service`;
  si esa llamada falla la respuesta sigue siendo 200 y la operación queda `recorded = false`. La
  retoman `RecordRecoveryScheduler` (cada minuto, operaciones con más de
  `credit.recorder.pending-minutes`), `POST /credit-recovery-runs` o la repetición del mismo
  `operationId`. `transaction-service` es idempotente por `operationId`, así que registrar dos veces
  es seguro.
- **Tercero.** `payerCustomerId` solo se guarda en el resultado si es distinto del dueño; el pago no
  debita ninguna cuenta (ficha §12). La vista `payment-info` es lo único que el tercero ve del
  producto.
- **Limpieza de deuda vencida.** `CustomerOverdueCleared` se publica una sola vez por cliente: solo
  cuando el producto pagado era el último `OVERDUE` que le quedaba (ver `overdue-check.md`).
