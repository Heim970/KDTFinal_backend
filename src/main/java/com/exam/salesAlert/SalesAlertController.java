package com.exam.salesAlert;

import com.exam.salesAnalysis.SalesAnalysisService;
import com.exam.salesAnalysis.SalesProductDTO;
import com.exam.statistics.SalesDailyDTO;
import com.exam.statistics.SalesDailyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/salesAlert")
public class SalesAlertController {

    SalesAlertService alertService;
    SalesDailyService dailyService;
    SalesAnalysisService analysisService;

    public SalesAlertController(SalesAlertService alertService, SalesDailyService dailyService, SalesAnalysisService analysisService) {
        this.alertService = alertService;
        this.dailyService = dailyService;
        this.analysisService = analysisService;
    }

    @GetMapping("/searchList/byDate/{date}")
    public ResponseEntity<List<SalesAlertDTO>> findByAlertDate(@PathVariable String date) {
        // 특정 날짜의 이상치 알림기록 조회
        log.info("LOGGER: 일간 이상치 알림기록 조회를 요청함");

        // 날짜 포매팅
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            // 날짜 데이터 타입 변환
            LocalDate searchDate = LocalDate.parse(date, formatter);

            log.info("LOGGER: 조회할 날짜: {}", searchDate);

            List<SalesAlertDTO> alertList = alertService.findByAlertDate(searchDate);
            log.info("LOGGER: 해당하는 날짜의 알림 정보 획득 성공");

            return ResponseEntity.status(200).body(alertList);
        } catch (DateTimeException e) {
            log.error("날짜 형식이 올바르지 않습니다.", e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/searchList/byDate/{date1}/{date2}")
    public ResponseEntity<List<SalesAlertDTO>> findByAlertDate(@PathVariable String date1, @PathVariable String date2) {
        // 특정 기간의 이상치 알림기록 조회
        log.info("LOGGER: 특정 기간의 이상치 알림기록 조회를 요청함");

        // 날짜 포매팅
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            // 날짜 데이터 타입 변환
            LocalDate searchDate1 = LocalDate.parse(date1, formatter);
            LocalDate searchDate2 = LocalDate.parse(date2, formatter);

            log.info("LOGGER: 조회할 기간: {} ~ {}", date1, date2);

            List<SalesAlertDTO> alertList = alertService.findByAlertDateBetween(searchDate1, searchDate2);
            log.info("LOGGER: 해당하는 기간의 알림 정보 획득 성공");

            return ResponseEntity.status(200).body(alertList);
        } catch (DateTimeException e) {
            log.error("날짜 형식이 올바르지 않습니다.", e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/searchList/byTrend/{date}/{trendBasis}")
    public ResponseEntity<List<SalesAlertDTO>> findByTrendBasis(@PathVariable String date, @PathVariable int trendBasis) {
        /*
        date: 분석 데이터를 조회할 날짜
        trendBasis: 트렌드의 타입 | 7: 일주일 전 같은 요일, 30: 1개월 전 같은 날짜(일), 365: 1년 전 같은 날짜(월-일)
         */
        // 특정 날짜의 이상치 알림기록 조회
        log.info("LOGGER: 트렌드 타입에 해당하는 일간 이상치 알림기록 조회를 요청함");

        // 날짜 포매팅
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            // 날짜 데이터 타입 변환
            LocalDate searchDate = LocalDate.parse(date, formatter);

            log.info("LOGGER: 조회할 날짜: {}, 트렌드 타입: {}", searchDate, trendBasis);

            List<SalesAlertDTO> alertList = alertService.findByTrendBasis(searchDate, trendBasis);
            log.info("LOGGER: 해당하는 날짜와 트렌드 타입의 알림 정보 획득 성공");

            return ResponseEntity.status(200).body(alertList);
        } catch (DateTimeException e) {
            log.error("날짜 형식이 올바르지 않습니다.", e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/updateComment")
    public ResponseEntity<String> updateUserComment(@RequestBody Map<String, Object> requestBody) {
        // 사용자(점주)가 직접 기록한 이상치 매출 데이터의 코멘트를 업데이트
        log.info("사용자가 이상치 매출 데이터의 코멘트를 수정함");

        Long alertId = ((Number) requestBody.get("alertId")).longValue();
        String userComment = (String) requestBody.get("userComment");
        log.info("수정할 기록 번호: {}, 수정할 코멘트: {}", alertId, userComment);

        try {
            alertService.updateUserComment(alertId, userComment);
            return ResponseEntity.status(201).body("코멘트가 수정되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("수정에 실패했습니다.");
        }
    }

    @DeleteMapping("/deleteAlert/{alertId}")
    public ResponseEntity<String> deleteByAlertId(@PathVariable Long alertId) {
        // 이상치 기록을 삭제
        log.info("사용자가 이상치 기록의 삭제를 요청함. 삭제할 기록 id: {}", alertId);
        try {
            alertService.deleteByAlertId(alertId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body("삭제에 실패했습니다.");
        }
    }

    @GetMapping("/salesRecordHourly/{salesDate}/{salesHour}")
    public ResponseEntity<List<SalesProductDTO>> getSalesDailyByDateAndHour(@PathVariable String salesDate, @PathVariable int salesHour) {
        // 해당하는 날짜와 시간대의 매출 기록을 조회(상품별 수량과 매출액의 합계)
        log.info("LOGGER: 날짜와 시간대를 기반으로 한 매출 기록 조회를 요청함");
        // 날짜 포매팅
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        try {
            // 날짜 데이터 타입 변환
            LocalDate searchDate = LocalDate.parse(salesDate, formatter);

            log.info("LOGGER: 조회할 날짜: {}, 조회할 시간: {}", searchDate, salesHour);

            List<SalesProductDTO> list = analysisService.getSoldProductsByDateAndHour(searchDate, salesHour);
            log.info("매출 데이터 조회에 성공함. 매출 데이터 반환");

            return ResponseEntity.status(200).body(list);
        } catch (DateTimeException e) {
            log.error("날짜 형식이 올바르지 않습니다.", e);
            return ResponseEntity.badRequest().body(null);
        }
    }
    
}
