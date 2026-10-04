# Secuencia — Consumo de tarjeta con control de línea (`POST /credit-cards/{id}/charges`)

Implementado en `ChargeCreditCardUseCaseImpl` (R3), `CreditCard.charge` (R2),
`MongoUnitOfWorkAdapter` (R4), `MovementRecorderRestAdapter` (R7) y `CreditCardController` (R5).

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente HTTP
    participant Ctrl as CreditCardController
    participant UC as ChargeCreditCardUseCaseImpl
    participant Log as OperationLogPort
    participant Repo as CreditCardRepositoryPort
    participant Dom as CreditCard
    participant UOW as UnitOfWorkPort (Mongo tx)
    participant Evt as CreditEventPublisherPort
    participant HR as HistoryRecorder
    participant Tx as transaction-service
    participant EH as GlobalExceptionHandler

    C->>Ctrl: POST /api/v1/credit-cards/{id}/charges (operationId, amount, description)
    Ctrl->>UC: execute(ChargeCommand)
    UC->>Log: find(operationId)

    alt operationId ya usado
        Log-->>UC: CreditOperation existente
        alt otra tarjeta, tipo o monto
            UC-->>EH: OPERATION_ID_REUSED
            EH-->>C: 409 OPERATION_ID_REUSED
        else coincide
            UC->>HR: recordQuietly(existente)
            UC-->>Ctrl: existente.toChargeResult()
            Ctrl-->>C: 200 ChargeResult (sin consumir otra vez)
        end
    else operationId nuevo
        UC->>Repo: findById(cardId)
        alt no existe
            UC-->>EH: CreditCardNotFoundException
            EH-->>C: 404 CREDIT_CARD_NOT_FOUND
        end
        Repo-->>UC: CreditCard
        UC->>Dom: charge(operationId, amount, paymentTermDays, clock)
        alt tarjeta CLOSED
            Dom-->>EH: INVALID_STATE
            EH-->>C: 422 INVALID_STATE
        else amount > línea − usado
            Dom-->>EH: CREDIT_LIMIT_EXCEEDED
            EH-->>C: 422 CREDIT_LIMIT_EXCEEDED
        end
        Note over Dom: usado += amount<br/>si usado era 0: paymentDueDate = hoy + 30 días<br/>si no: se conserva la fecha<br/>OVERDUE sigue pudiendo consumir
        Dom-->>UC: ChargeOutcome(card, result)
        UC->>UOW: saveCardAndOperation(card, CreditOperation.ofCharge)
        alt conflicto de versión (otro consumo a la vez)
            UOW-->>EH: CONCURRENT_MODIFICATION
            EH-->>C: 409 CONCURRENT_MODIFICATION
        end
        UOW-->>UC: CreditCard guardada
        UC->>Evt: publish(CreditCardUpdated)
        UC->>Evt: publish(ChargeRegistered)
        UC->>HR: recordQuietly(operation)
        HR->>Tx: POST /transactions/records (CARD_CHARGE)
        Note over HR,Tx: si falla: recorded = false, se reintenta después
        UC-->>Ctrl: ChargeResult
        Ctrl-->>C: 200 ChargeResult (usedAmount, availableCredit, paymentDueDate, status)
    end
```

## Notas

- **El control de línea es del agregado.** `CreditCard.charge` compara contra `availableCredit()`
  (`creditLimit − usedAmount`); el constructor del `record` además impide que `usedAmount` supere la
  línea, así que no hay forma de construir una tarjeta sobregirada.
- **Dos consumos simultáneos** sobre la misma tarjeta leen la misma `version`; el segundo en guardar
  falla con bloqueo optimista → 409 `CONCURRENT_MODIFICATION`. Así no se puede pasar la línea por
  carrera: el cliente reintenta con el mismo `operationId` y se evalúa contra el saldo nuevo.
- **Un rechazo no deja registro** (solo se guardan operaciones aplicadas), así que repetir un consumo
  rechazado vuelve a evaluarse; útil si mientras tanto se pagó o se subió la línea.
- **Fecha de pago.** Se fija solo en el primer consumo con usado 0 (`credit.card.payment-term-days`,
  30 por defecto) y se borra cuando un pago deja usado 0. Es la fecha que mira la revisión de vencidos.
- **Saldo.** `GET /credit-cards/{id}/balance` devuelve línea, usado, disponible y fecha de pago
  (`CardBalanceView`).
