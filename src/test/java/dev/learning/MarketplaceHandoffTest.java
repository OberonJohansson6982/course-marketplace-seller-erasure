package dev.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketplaceHandoffTest {
    @Test
    void pendingOrdersTransferAndOnlyTheirBuyersReceiveUpdates() {
        var input = new MarketplaceHandoff.Request("teacher-12", "seller-key-12", "teacher-34",
                List.of("course-1"), List.of(
                    new MarketplaceHandoff.Order("order-1", "buyer-a", true),
                    new MarketplaceHandoff.Order("order-2", "buyer-a", true),
                    new MarketplaceHandoff.Order("order-3", "buyer-b", false)));
        var plan = MarketplaceHandoff.plan(input);
        assertEquals(List.of("course-1"), plan.reassignedCourses());
        assertEquals(List.of("order-1", "order-2"), plan.transferredOrders());
        assertEquals(List.of("buyer-a"), plan.notifiedBuyers());
    }
}
