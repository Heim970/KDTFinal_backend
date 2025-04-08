package com.exam.payments;

import com.exam.Inventory.InventoryService;
import com.exam.cartAnalysis.dto.OrdersDTO;
import com.exam.cartAnalysis.entity.Orders;
import com.exam.cartAnalysis.repository.OrdersRepository;
import com.exam.cartAnalysis.service.OrdersService;
import com.exam.goods.Goods;
import com.exam.goods.GoodsRepository;
import com.exam.saleData.SaleData;
import com.exam.saleData.SaleDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentsController {

    private final PaymentsService paymentsService;
    private final OrdersService ordersService;
    private final OrdersRepository ordersRepository;
    private final GoodsRepository goodsRepository;
    private final SaleDataRepository saleDataRepository;
    private final InventoryService inventoryService;

    @PostMapping("/order")
    public ResponseEntity<Map<String, Long>> createOrder(@RequestBody OrdersDTO ordersDTO) {
        log.info("LOGGER: [PAYMENT] 주문 요청, orders 테이블에 주문내역 추가");
        Long orderId = ordersService.createOrder(ordersDTO);
        Map<String, Long> response = new HashMap<>();
        response.put("orderId", orderId);
        log.info("LOGGER: [PAYMENT] 새로운 주문내역 추가: {}", orderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<JSONObject> confirmPayment(@RequestBody String jsonBody) throws Exception {
        log.info("LOGGER: [PAYMENT] Toss Pay 요청함");

        JSONParser parser = new JSONParser();

        String orderId;
        String amount;
        String paymentKey;

        try {
            // 클라이언트에서 받은 JSON 요청 바디입니다.
            JSONObject requestData = (JSONObject) parser.parse(jsonBody);
            paymentKey = (String) requestData.get("paymentKey");
            orderId = (String) requestData.get("orderId");
            amount = (String) requestData.get("amount");
        } catch (ParseException e) {
            return ResponseEntity.badRequest().body(createErrorResponse("Invalid request data"));
        }

        // 주문 정보 조회 - 주문이 없으면 예외 발생
        OrdersDTO orders = ordersService.findById(Long.parseLong(orderId));

        // 중복 결제 방지 - 이미 결제된 주문이면 예외 발생
        if (orders.getPaymentStatus() == PaymentStatus.COMPLETED) {
            log.info("[TOSS PAY] 예외 발생 - 이미 결제된 주문");
            throw new IllegalArgumentException("이미 결제된 주문입니다.");
        }

        // 결제 승인 요청(Toss)
        JSONObject obj = new JSONObject();
        obj.put("orderId", orderId);
        obj.put("amount", Integer.parseInt(amount));
        obj.put("paymentKey", paymentKey);

        // 토스페이먼츠 API는 시크릿 키를 사용자 ID로 사용하고, 비밀번호는 사용하지 않습니다.
        // 비밀번호가 없다는 것을 알리기 위해 시크릿 키 뒤에 콜론을 추가합니다.
        String widgetSecretKey = "test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6";
        Base64.Encoder encoder = Base64.getEncoder();
        byte[] encodedBytes = encoder.encode((widgetSecretKey + ":").getBytes(StandardCharsets.UTF_8));
        String authorizations = "Basic " + new String(encodedBytes);

        // 결제를 승인하면 결제수단에서 금액이 차감돼요.
        URL url = new URL("https://api.tosspayments.com/v1/payments/confirm");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Authorization", authorizations);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(obj.toString().getBytes("UTF-8"));

        int code = connection.getResponseCode();
        boolean isSuccess = code == 200;

        InputStream responseStream = isSuccess ? connection.getInputStream() : connection.getErrorStream();

        Reader reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8);
        JSONObject jsonObject = (JSONObject) parser.parse(reader);

        // 결제 성공 및 실패 비즈니스 로직을 구현하세요.
        if (isSuccess) {
            // JSON에서 받은 approvedAt 문자열
            String approvedAtStr = (String) jsonObject.get("approvedAt");

            // OffsetDateTime으로 변환 후 LocalDateTime으로 변환
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(approvedAtStr);
            LocalDateTime approvedAt = offsetDateTime.toLocalDateTime();

            log.info("[TOSS PAY] 결제 성공함 payment 테이블에 저장");
            PaymentsDTO dto = PaymentsDTO.builder()
                    .ordersId(Long.parseLong(orderId))
                    .memberNo(orders.getMemberNo())
                    .finalPrice(orders.getFinalPrice())
                    .paymentAmount(Long.parseLong(amount))
                    .paymentMethod((String) jsonObject.get("method"))
                    .paymentApproved(approvedAt)
                    .build();

            paymentsService.savePayment(dto);

            log.info("[TOSS PAY] 결제 성공함 paymentStatus 업데이트");
            ordersService.updatePaymentStatus(Long.parseLong(orderId), PaymentStatus.COMPLETED);

//            // 재고 감소 처리
//            for (OrdersDTO.OrderItemDTO item : orders.getOrderItems()) {
//                inventoryService.reduceStock(item.getGoodsId(), item.getSaleAmount().longValue());
//            }
            // 재고 감소 처리
            if (orders.getOrderItems() == null) {
                log.warn("[TOSS PAY] 주문 상세(orderItems)가 null입니다.");
            } else {
                for (OrdersDTO.OrderItemDTO item : orders.getOrderItems()) {
                    log.info("[TOSS PAY] 재고 차감 대상 상품 - goodsId: {}, saleAmount: {}", item.getGoodsId(), item.getSaleAmount());
                    inventoryService.reduceStock(item.getGoodsId(), item.getSaleAmount().longValue());
                }
                log.info("[TOSS PAY] 결제 성공 → 재고 감소 처리 완료");
            }


            //log.info("[TOSS PAY] 결제 성공 → 재고 감소 처리 완료");

        }

        if(!isSuccess){
            saleDataRepository.deleteByOrders_OrdersId(Long.parseLong(orderId));
        }

        responseStream.close();

        return ResponseEntity.status(code).body(jsonObject);
    }

    // 에러 응답을 위한 메서드
    private JSONObject createErrorResponse(String message) {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("error", message);
        return errorResponse;
    }

    @DeleteMapping("/fail/{orderId}")
    public ResponseEntity<String> handlePaymentFail(@PathVariable Long orderId) {
        log.warn("[PAYMENT FAIL] TossPay 창 닫힘 → 결제 실패로 간주, sale_data 삭제");

        saleDataRepository.deleteByOrders_OrdersId(orderId);
        // 필요시 orders도 삭제하거나, payment_status = 2로 업데이트 가능

        return ResponseEntity.ok("결제 실패 처리 완료");
    }

}