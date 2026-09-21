# Báo cáo phân tích và khắc phục lỗi "Kho treo" (Rollback Logic)

## 1. Phân tích nguyên nhân gây ra tình trạng "hàng ảo"

- **Hiện tượng:** Khách hàng đặt hàng thất bại ở bước lưu đơn hàng, nhưng số lượng tồn kho vẫn bị trừ.
- **Nguyên nhân cốt lõi:** 
  - Hệ thống thực hiện trừ kho ở Inventory Service **trước** khi tạo đơn hàng ở Order Service.
  - Khi xảy ra ngoại lệ (ví dụ: DB timeout) ở bước `orderRepository.save(order)`, hệ thống trả về mã lỗi 500 ngay lập tức mà **không thực hiện hành động bù trừ (Compensating Transaction)** là cộng trả lại số lượng kho đã trừ.
  - Sự thiếu vắng này phá vỡ tính nhất quán dữ liệu (ACID/Distributed Transactions), dẫn đến hiện tượng "hàng ảo" (Phantom Out-of-Stock).

## 2. Giải pháp sửa đổi mã nguồn

Bổ sung cơ chế bù trừ bằng cách gọi ngược lại `inventoryClient.increaseStock()` khi việc lưu đơn hàng gặp ngoại lệ.

## 3. Đề xuất giải pháp mạnh mẽ hơn (Saga Pattern & Retry Compensate)

Trong môi trường phân tán thực tế, việc gọi API bù trừ trực tiếp có thể thất bại do lỗi mạng hoặc timeout. Giải pháp tối ưu:
- Sử dụng **Saga Orchestration** hoặc **Choreography Pattern**.
- Lưu vết trạng thái Saga (State Store) với các trạng thái: `STOCK_DECREASED`, `ORDER_FAILED`, `COMPENSATING`, `COMPENSATED`.
- Sử dụng **Message Queue (RabbitMQ/Kafka)** kết hợp với **Outbox Pattern** và **Background Job (Retry mechanism)** để đảm bảo hành động bù trừ được thực thi thành công cuối cùng (Eventual Consistency).