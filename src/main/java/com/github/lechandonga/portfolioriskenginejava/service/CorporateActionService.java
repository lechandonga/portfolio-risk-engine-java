package com.github.lechandonga.portfolioriskenginejava.service;

import com.github.lechandonga.portfolioriskenginejava.domain.AccountLockManager;
import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import com.github.lechandonga.portfolioriskenginejava.domain.Instrument;
import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import com.github.lechandonga.portfolioriskenginejava.dto.Requests.CorporateActionRequest;
import com.github.lechandonga.portfolioriskenginejava.error.BusinessException;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.LongSupplier;

@Service
public class CorporateActionService {
    private static final Logger log = LoggerFactory.getLogger(CorporateActionService.class);

    private final CorporateActionRepository actions;
    private final InstrumentRepository instruments;
    private final PositionRepository positions;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final AccountLockManager locks;
    private final LongSupplier clock;

    public CorporateActionService(CorporateActionRepository actions, InstrumentRepository instruments,
                                  PositionRepository positions, AccountRepository accounts,
                                  LedgerRepository ledger, AccountLockManager locks, LongSupplier clock) {
        this.actions = actions;
        this.instruments = instruments;
        this.positions = positions;
        this.accounts = accounts;
        this.ledger = ledger;
        this.locks = locks;
        this.clock = clock;
    }

    public CorporateAction register(CorporateActionRequest req) {
        Instrument instrument = instruments.get(req.symbol());
        if (instrument == null) {
            throw new BusinessException(ErrorCode.CORPORATE_ACTION_UNKNOWN_INSTRUMENT,
                    "cannot register corporate action for unknown instrument: " + req.symbol());
        }
        if (req.type() == CorporateAction.Type.SPLIT
                && (req.ratio() == null || req.ratio().signum() <= 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "split requires a positive ratio");
        }
        if (req.type() == CorporateAction.Type.CASH_DIVIDEND && req.cashPerShare() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "cash dividend requires cashPerShare");
        }
        CorporateAction action = new CorporateAction(req.actionId(), req.symbol(), req.type(),
                req.ratio(), req.cashPerShare(),
                req.exDate() != null ? req.exDate() : clock.getAsLong());
        actions.save(action);
        log.info("corporate action registered id={} symbol={} type={}", req.actionId(),
                req.symbol(), req.type());
        return action;
    }

    /**
     * Apply the action to one account. The (actionId, accountId) claim is atomic, so retrying
     * the same request — even concurrently — adjusts holdings at most once.
     */
    public boolean apply(String accountId, String actionId) {
        CorporateAction action = actions.get(actionId);
        if (action == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "corporate action not registered: " + actionId);
        }
        var account = accounts.get(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found: " + accountId);
        }
        synchronized (locks.lockFor(accountId)) {
            if (!actions.claim(actionId, accountId)) {
                log.info("corporate action {} already applied to {}", actionId, accountId);
                throw new BusinessException(ErrorCode.CORPORATE_ACTION_DUPLICATE,
                        "corporate action " + actionId + " has already been applied to account "
                                + accountId);
            }
            Position position = positions.get(accountId, action.getSymbol());
            if (position == null || position.getQuantity() == 0) {
                log.info("corporate action {} no holdings for {} on {}", actionId,
                        action.getSymbol(), accountId);
                return false;
            }
            if (action.getType() == CorporateAction.Type.SPLIT) {
                BigDecimal scaled = BigDecimal.valueOf(position.getQuantity())
                        .multiply(action.getRatio());
                if (scaled.stripTrailingZeros().scale() > 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "split ratio " + action.getRatio() + " does not produce whole shares for "
                                    + position.getQuantity() + " holdings");
                }
                long newQty = scaled.longValueExact();
                long newReserved = BigDecimal.valueOf(position.getReservedQuantity())
                        .multiply(action.getRatio()).longValueExact();
                position.setQuantity(newQty);
                position.setReservedQuantity(newReserved);
                // Total cost basis is unchanged; average cost drops in proportion to the ratio.
                log.info("split applied action={} account={} newQty={}", actionId, accountId, newQty);
            } else {
                BigDecimal payout = action.getCashPerShare()
                        .multiply(BigDecimal.valueOf(position.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP);
                account.setCash(account.getCash().add(payout));
                ledger.append(accountId, "DIVIDEND", actionId, payout, clock.getAsLong());
                log.info("dividend applied action={} account={} payout={}", actionId, accountId, payout);
            }
            return true;
        }
    }
}
