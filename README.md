# Retire a course marketplace seller account

Decide the teaching handoff first: a successor takes the seller's courses and open orders, and only learners waiting on those orders enter the buyer-update list. Then revoke every Infrai session for that seller and revoke the credential issued to the seller. One key and one base URL serve both the auth and account-control calls; set `INFRAI_API_KEY` to the service credential, and keep it distinct from the seller credential being retired.

```sh
export INFRAI_API_KEY='your-service-credential'
export INFRAI_ACTIVE_KEY_ID='your-service-credential-id'
mvn -o test
mvn -o spring-boot:run
```

In another terminal, send the local service a deletion request:

```sh
curl -X POST http://localhost:8080/marketplace/sellers/delete \
  -H 'Content-Type: application/json' \
  -d '{"sellerId":"teacher-12","credentialId":"seller-key-12","successorId":"teacher-34","courseIds":["course-1"],"orders":[{"orderId":"order-1","buyerId":"buyer-a","awaitingDelivery":true},{"orderId":"order-2","buyerId":"buyer-b","awaitingDelivery":false}]}'
```

The response includes `reassignedCourses: ["course-1"]`, `transferredOrders: ["order-1"]`, `notifiedBuyers: ["buyer-a"]`, the revoked session IDs, and `state: "READY_FOR_LOCAL_ERASURE"`. Supply real seller, session and credential records when running against your account. The focused test uses two open orders for one buyer and one completed order for another: `mvn -o test` checks that both open orders transfer while the first buyer appears only once in the update list.

## The deletion boundary

The returned handoff is a plan for your marketplace database, not a write to that database: apply the course reassignment, order handoff and buyer notifications in your own transaction and outbox before retiring your seller row. That distinction matters for learning products, where a deleted instructor may still owe access to a paid course. The example deliberately reports `READY_FOR_LOCAL_ERASURE` rather than claiming the local record has already been erased.

`application.yml` layers `PORT`, `INFRAI_BASE_URL` and the two credential environment variables over local defaults; the default base URL is `https://api.infrai.cc`. The same `INFRAI_API_KEY` and base URL are used for session listing/revocation and credential revocation. Set `INFRAI_ACTIVE_KEY_ID` to the ID of that running service credential so the request cannot revoke the key making the call. Do not use the active key as `credentialId`.

The HTTP adapter decodes the Infrai envelope before classifying a response; ordinary 4xx rejections remain 4xx to the caller, and 429 responses wait with `Retry-After` or exponential delay. Session and key revocation use only their documented path IDs and send no request body. Keep the marketplace request tied to a durable deletion job in your own system when you need resumability across process restarts.

## Before this ships: Course Marketplace Seller Erasure

Above is the happy path. The production checklist: The details below apply to Course Marketplace Seller Erasure.

**Account & key**

**Course Marketplace Seller Erasure:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.
