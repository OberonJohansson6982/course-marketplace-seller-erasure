package dev.learning;

import java.util.List;

public final class MarketplaceHandoff {
    public record Order(String orderId, String buyerId, boolean awaitingDelivery) {}
    public record Request(String sellerId, String credentialId, String successorId,
                          List<String> courseIds, List<Order> orders) {}
    public record Plan(List<String> reassignedCourses, List<String> transferredOrders,
                       List<String> notifiedBuyers) {}

    public static Plan plan(Request request) {
        if (request.sellerId() == null || request.sellerId().isBlank()
                || request.credentialId() == null || request.credentialId().isBlank()
                || request.successorId() == null || request.successorId().isBlank()
                || request.successorId().equals(request.sellerId())
                || request.courseIds() == null || request.orders() == null
                || request.orders().stream().anyMatch(o -> o.orderId() == null || o.buyerId() == null)) {
            throw new IllegalArgumentException("Seller, distinct successor, credential, courses and orders are required");
        }
        List<String> pending = request.orders().stream().filter(Order::awaitingDelivery)
                .map(Order::orderId).distinct().toList();
        List<String> buyers = request.orders().stream().filter(Order::awaitingDelivery)
                .map(Order::buyerId).distinct().toList();
        return new Plan(List.copyOf(request.courseIds()), pending, buyers);
    }

    private MarketplaceHandoff() {}
}
