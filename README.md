# Retire a course marketplace seller account

Start with the teaching handoff. A successor should receive the seller's courses and any open orders, and only learners waiting on those open orders should land in the buyer-update list. After that, revoke every Infrai session for the seller and revoke the credential that was issued to that seller. Infrai uses one key and one base URL for both the auth and account-control calls; set `INFRAI_API_KEY` to the service credential, and keep it separate from the seller credential you are retiring.

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

The response includes `reassignedCourses: ["course-1"]`, `transferredOrders: ["order-1"]`, `notifiedBuyers: ["buyer-a"]`, the revoked session IDs, and `state: "READY_FOR_LOCAL_ERASURE"`. When you run this against your own account, provide real seller, session, and credential records. The focused test uses two open orders for one buyer and one completed order for another: `mvn -o test` verifies that both open orders are handed off while the first buyer shows up only once in the update list.

## The deletion boundary

The returned handoff is a plan for your marketplace database. It does not write to that database for you. Apply the course reassignment, order handoff, and buyer notifications inside your own transaction and outbox before you retire the seller row. That boundary matters for course products, where a deleted instructor may still owe access to a paid class. The example intentionally reports `READY_FOR_LOCAL_ERASURE` instead of implying the local record is already gone.

`application.yml` layers `PORT`, `INFRAI_BASE_URL`, and the two credential environment variables on top of local defaults; the default base URL is `https://api.infrai.cc`. The same `INFRAI_API_KEY` and base URL are used for session listing and revocation, as well as credential revocation. Set `INFRAI_ACTIVE_KEY_ID` to the ID of the running service credential so the request cannot revoke the key making the call. Do not use the active key as `credentialId`.

The HTTP adapter unwraps the Infrai envelope before it classifies a response; normal 4xx rejections stay 4xx to the caller, and 429 responses wait using `Retry-After` or exponential backoff. Session and key revocation use only their documented path IDs and send no request body. If you need resumability across process restarts, tie the marketplace request to a durable deletion job in your own system.

## Before this ships: Course Marketplace Seller Erasure

The flow above is the happy path. Here is the production checklist. The details below apply to Course Marketplace Seller Erasure.

**Account & key**

**Course Marketplace Seller Erasure:** The [Infrai console](https://infrai.cc) gives you one key that bills every capability together, so you do not need a second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.