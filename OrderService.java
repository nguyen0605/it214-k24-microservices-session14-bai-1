package com.example.orderservice.service;

import com.example.orderservice.client.InventoryClient;
import com.example.orderservice.model.Order;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @Autowired
    private InventoryClient inventoryClient;

    @Autowired
    private OrderRepository orderRepository;

    public ResponseEntity<Order> createOrder(OrderRequest request) {
        // 1. Gọi Inventory Service để trừ kho
        boolean stockDecreased = inventoryClient.decreaseStock(request.getProductId(), request.getQuantity());
        if (!stockDecreased) {
            return ResponseEntity.badRequest().body(null); // Hết hàng
        }

        // 2. Tạo đơn hàng
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        // 3. Xử lý lưu đơn hàng và bù trừ nếu thất bại
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            // KHẮC PHỤC: Gọi bù trừ cộng lại kho khi lưu đơn thất bại
            try {
                inventoryClient.increaseStock(request.getProductId(), request.getQuantity());
            } catch (Exception ex) {
                // Log lỗi nghiêm trọng: Cần cơ chế Background Job retry lại việc hoàn kho
                // Hoặc ghi log vào bảng Saga Log để xử lý sau
            }
            return ResponseEntity.status(500).build();
        }

        return ResponseEntity.ok(order);
    }
}