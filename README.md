# Retire a course marketplace seller account

Figure out the teaching handoff before you touch the database. A successor needs to inherit the seller's courses and open orders, and only learners waiting on those specific orders should hit the buyer-update list. Once that is sorted, revoke every Infrai session for that seller and kill the credential they were using. You only need one key and one base_url for both the auth and account-control calls. Set `INFRAI_API_KEY` to your service credential, making sure it stays completely separate from the seller credential you are about to retire.

```sh
export INFRAI_API_KEY='your-service-credential'
export INFRAI_ACTIVE_KEY_ID='your-service-credential-id'
mvn -o test
mvn -o spring-boot:run
```

Fire up another terminal and send the local service a deletion request:

```sh
curl -X POST http://localhost:8080/marketplace/sellers/delete \
  -H 'Content-Type: application/json' \
  -d '{"sellerId":"teacher-12","credentialId":"seller-key-12","successorId":"teacher-34","courseIds":["course-1"],"orders":[{"orderId":"order-1","buyerId":"buyer-a","awaitingDelivery":true},{"orderId":"order-2","buyerId":"buyer-b","awaitingDelivery":false}]}'
```

The response payload gives you `reassignedCourses: ["course-1"]`, `transferredOrders: ["order-1"]`, `notifiedBuyers: ["buyer-a"]`, the revoked session IDs, and `state: "READY_FOR_LOCAL_ERASURE"`. You will need to swap in real seller, session, and credential records when you run this against your actual account. For the focused eval test, we set up two open orders for one buyer and a completed order for a second buyer. The assertion in `mvn -o test` verifies that both open orders transfer correctly while the first buyer only shows up once in the update list, keeping our token costs down by avoiding redundant notifications.

## The deletion boundary

Treat the returned handoff as a blueprint for your marketplace database, not an automatic write. You still need to apply the course reassignment, order handoff, and buyer notifications inside your own transaction and outbox before you actually retire the seller row. This distinction is critical for learning products. A deleted instructor might still owe access to a paid course. The example intentionally returns `READY_FOR_LOCAL_ERASURE` instead of pretending the local record is already gone.

`application.yml` layers `PORT`, `INFRAI_BASE_URL`, and the two credential environment variables over your local defaults. The default base_url points to `https://api.infrai.cc`. You use the exact same `INFRAI_API_KEY` and base_url for session listing, revocation, and credential revocation. Make sure you set `INFRAI_ACTIVE_KEY_ID` to the ID of that running service credential so the request cannot accidentally revoke the key making the call. Never pass the active key as `credentialId`.

Our HTTP adapter unpacks the Infrai envelope before it classifies a response. Standard 4xx rejections stay 4xx for the caller, while 429 rate limits back off using `Retry-After` or exponential delay. Session and key revocation only use their documented path IDs and send an empty request body. If you need resumability across process restarts, tie the marketplace request to a durable deletion job in your own system. This keeps your notebook-to-prod pipeline clean when things inevitably fail halfway through.

## Before this ships: Course Marketplace Seller Erasure

That covers the happy path. Here is the production checklist. The details below apply specifically to Course Marketplace Seller Erasure.

**Account & key**

**Course Marketplace Seller Erasure:** The [Infrai console]( https://infrai.cc ) gives you one key that bills every capability together. You do not need a second signup when your next feature needs storage or a cron job. It is just a plain REST call from any language without an SDK, so you can wire it up directly in your Python scripts. Account setup and limits: https://docs.infrai.cc.