# Secuencia — Revisión de deuda vencida (`detected` y `cleared`)

Implementado en `CheckOverdueUseCaseImpl` y `OverdueClearance` (R3), `markOverdueIfDue` de `Credit` y
`CreditCard` (R2), `OverdueCheckScheduler` / `OverdueCheckController` (R5–R6) y
`OverdueQueryAdapter` (R4). Flujo de negocio completo en `bank-docs/flows/04-overdue-debt.md`.

## Detección (`credit.overdue.detected`)

```mermaid
sequenceDiagram
    autonumber
    participant Sch as OverdueCheckScheduler<br/>(credit.overdue.cron, 01:00)
    actor Adm as ADMIN (Postman)
    participant Ctrl as OverdueCheckController
    participant UC as CheckOverdueUseCaseImpl
    participant CR as CreditRepositoryPort
    participant KR as CreditCardRepositoryPort
    participant Dom as Credit / CreditCard
    participant Evt as CreditEventPublisherPort

    alt proceso diario
        Sch->>UC: execute(null) → asOf = hoy (bank.zone)
    else ejecución manual
        Adm->>Ctrl: POST /api/v1/overdue-checks?asOf=2027-12-31
        Ctrl->>UC: execute(asOf)
    end

    UC->>CR: findActiveDueBefore(asOf)
    loop cada crédito ACTIVE con dueDate < asOf (aislado: onErrorComplete)
        CR-->>UC: Credit
        UC->>Dom: markOverdueIfDue(asOf)
        alt saldo 0 o ya no ACTIVE
            Dom-->>UC: vacío (no cambia nada)
        else vencido
            Dom-->>UC: Credit OVERDUE
            UC->>CR: save(credit)
            alt CONCURRENT_MODIFICATION (un pago a la vez)
                UC->>CR: findById → reevaluar (hasta 3 intentos)
            end
            UC->>Evt: publish(CreditUpdated)
            UC->>Evt: publish(ProductBecameOverdue)
        end
    end

    UC->>KR: findActiveDueBefore(asOf)
    loop cada tarjeta ACTIVE con paymentDueDate < asOf
        KR-->>UC: CreditCard
        UC->>Dom: markOverdueIfDue(asOf)
        Note over UC,Evt: mismo tratamiento: save → CreditCardUpdated + ProductBecameOverdue
    end

    UC-->>Ctrl: OverdueCheckResult(asOf, creditsMarked, cardsMarked, customersAffected)
    Ctrl-->>Adm: 200 OverdueCheckResult
```

## Limpieza (`credit.overdue.cleared`)

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant UC as PayCreditUseCaseImpl /<br/>PayCreditCardUseCaseImpl /<br/>RescheduleCreditUseCaseImpl
    participant Dom as Credit / CreditCard
    participant UOW as UnitOfWorkPort / RepositoryPort
    participant OC as OverdueClearance
    participant OQ as OverdueQueryPort
    participant Evt as CreditEventPublisherPort

    C->>UC: pago total o reprogramación (fecha ≥ hoy)
    UC->>Dom: registerPayment / reschedule
    Dom-->>UC: producto fuera de OVERDUE (leftOverdue = true)
    UC->>UOW: guardar producto (+ operación si es pago)
    UC->>Evt: publish(CreditUpdated / CreditCardUpdated)
    UC->>OC: afterLeavingOverdue(customerId)
    OC->>OQ: existsOverdueByCustomer(customerId)
    alt aún tiene otro producto OVERDUE
        OQ-->>OC: true
        Note over OC: no publica nada
    else ya no le queda ninguno
        OQ-->>OC: false
        OC->>Evt: publish(CustomerOverdueCleared)
    end
```

## Notas

- **Idempotente.** Solo se leen productos `ACTIVE` con fecha vencida, y `markOverdueIfDue` devuelve
  vacío si no aplica; repetir la revisión con el mismo `asOf` no cambia ni publica nada.
- **Aislamiento por producto.** Un error en uno (`onErrorComplete`) no detiene a los demás; queda para
  la siguiente corrida. Un conflicto de versión (un pago que llegó a la vez) se resuelve recargando y
  reevaluando, hasta `MAX_ATTEMPTS = 3`: si el pago dejó saldo 0, ya no se marca.
- **Fuente de verdad.** Este servicio es el dueño de la deuda vencida: `OverdueDebtValidator` la
  consulta (`OverdueQueryAdapter`, créditos y tarjetas `OVERDUE`) antes de otorgar un producto, y
  `account-service` la consultará al abrir cuentas en P2.
- **`detected` por producto, `cleared` por cliente.** Se publica un `ProductBecameOverdue` por cada
  producto marcado, pero `CustomerOverdueCleared` solo una vez, cuando sale el último.
- **`asOf` manual y modo demo.** `POST /overdue-checks?asOf=...` permite simular otro día desde
  Postman; con `credit.demo-mode` se crean productos con vencimiento pasado para disparar la detección.
- **Varias instancias.** Hoy el cron corre en cada instancia; sigue pendiente un bloqueo distribuido
  (ficha §12).
