package com.exam.salesAlert;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class SalesAlertDTO {

    Long alertId;

    @NotNull
    int trendBasis;

    @NotNull
    LocalDate alertDate;

    @NotNull
    int alertHour;

    @NotNull
    Long previousSales;

    @NotNull
    Long currentSales;

    @NotNull
    Long difference;

    @NotNull
    Double percentageDifference;

    @NotNull
    String alertMessage;

    String userComment;
    
}
