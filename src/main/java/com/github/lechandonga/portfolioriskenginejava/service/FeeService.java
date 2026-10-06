package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.config.EngineProperties;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FeeService {
    private final EngineProperties props;

    public FeeService(EngineProperties props) {
        this.props = props;
    }

    public BigDecimal fee(Side side, BigDecimal price, long quantity) {
        BigDecimal notional = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal commission = notional.multiply(props.getFee().getCommissionRate())
                .max(props.getFee().getMinCommission());
        BigDecimal duty = side == Side.SELL
                ? notional.multiply(props.getFee().getSellDutyRate())
                : BigDecimal.ZERO;
        return commission.add(duty).setScale(2, RoundingMode.HALF_UP);
    }
}
