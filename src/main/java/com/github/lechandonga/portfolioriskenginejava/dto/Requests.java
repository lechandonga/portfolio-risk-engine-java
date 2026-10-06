package com.github.lechandonga.portfolioriskenginejava.dto;

import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public final class Requests {
    private Requests() {}

    public record CreateAccountRequest(@NotBlank String accountId, String name,
                                       @NotNull @PositiveOrZero BigDecimal initialCash) {}

    public record CreateInstrumentRequest(@NotBlank String symbol, String name,
                                          @Positive int lotSize,
                                          @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal maxPriceDeviation,
                                          Boolean active) {}

    public record OrderRequest(
            @NotBlank String clientRequestId,
            @NotNull Side side,
            @NotBlank String symbol,
            long quantity,
            /** Optional; when present the fill/quote price must stay within the instrument band. */
            BigDecimal limitPrice) {}

    public record CorporateActionRequest(
            @NotBlank String actionId,
            @NotBlank String symbol,
            @NotNull CorporateAction.Type type,
            /** Required for SPLIT, e.g. 2 for a 2-for-1 split. */
            @Positive BigDecimal ratio,
            /** Required for CASH_DIVIDEND. */
            @PositiveOrZero BigDecimal cashPerShare,
            Long exDate) {}

    public record PublishQuoteRequest(@NotBlank String symbol,
                                      @NotNull @Positive BigDecimal price,
                                      Long asOf) {}
}
