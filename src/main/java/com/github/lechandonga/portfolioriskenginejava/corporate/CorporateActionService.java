package com.github.lechandonga.portfolioriskenginejava.corporate;

import com.github.lechandonga.portfolioriskenginejava.domain.Account;
import com.github.lechandonga.portfolioriskenginejava.domain.CorporateAction;
import com.github.lechandonga.portfolioriskenginejava.domain.Order;
import com.github.lechandonga.portfolioriskenginejava.domain.OrderStatus;
import com.github.lechandonga.portfolioriskenginejava.domain.Position;
import com.github.lechandonga.portfolioriskenginejava.domain.Side;
import com.github.lechandonga.portfolioriskenginejava.error.ErrorCode;
import com.github.lechandonga.portfolioriskenginejava.error.ServiceException;
import com.github.lechandonga.portfolioriskenginejava.money.Money;
import com.github.lechandonga.portfolioriskenginejava.store.AccountEntry;
import com.github.lechandonga.portfolioriskenginejava.store.AccountStore;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 公司行为服务（按账户幂等）：
 * - SPLIT：新数量 = 旧数量 × factor；新成本 = 旧成本（总成本不变，单位成本按比例下降）；
 * - DIVIDEND：现金增加 = 持股 × 每股分红；数量与成本不变；分红计入已实现盈亏；
 * - 同 actionId 重复应用返回首次结果（duplicated=true），绝不二次调整；
 * - 存在占用该标的数量的未成交卖单时拒绝，防止拆分后预留数量失真。
 *
 * 守恒性：拆股不改变现金与总成本；分红等额增加现金与已实现盈亏；
 * 应用后按相同行情计算的市值中，拆股前后相等（价格尚未调整时会按比例反映）。
 */
public class CorporateActionService {

    private final AccountStore store;

    public CorporateActionService(AccountStore store) {
        this.store = store;
    }

    public CorporateActionResult apply(String accountId, CorporateAction action) {
        validate(action);
        return store.withAccount(accountId, entry -> {
            if (entry.getAppliedCorporateActions().contains(action.getActionId())) {
                Position position = entry.getAccount().getPositions().get(action.getSymbol());
                BigDecimal qty = position == null ? BigDecimal.ZERO : position.getQuantity();
                return new CorporateActionResult(action.getActionId(), accountId,
                        action.getSymbol(), action.getType(), qty, qty,
                        position == null ? BigDecimal.ZERO : position.getCostBasis(),
                        position == null ? BigDecimal.ZERO : position.getCostBasis(),
                        BigDecimal.ZERO.setScale(Money.MONEY_SCALE), true,
                        ErrorCode.CORPORATE_ACTION_DUPLICATE.getDefaultMessage());
            }

            for (Order order : entry.getOrders().values()) {
                if (order.getStatus() == OrderStatus.NEW
                        && order.getSide() == Side.SELL
                        && order.getSymbol().equals(action.getSymbol())) {
                    throw new ServiceException(ErrorCode.CORPORATE_ACTION_OPEN_ORDERS,
                            "orderId=" + order.getId());
                }
            }

            Account account = entry.getAccount();
            Position position = account.getPositions().get(action.getSymbol());
            BigDecimal beforeQty = position == null
                    ? BigDecimal.ZERO.setScale(Money.QTY_SCALE)
                    : position.getQuantity();
            BigDecimal beforeCost = position == null
                    ? BigDecimal.ZERO.setScale(Money.MONEY_SCALE)
                    : position.getCostBasis();
            BigDecimal cashPaid = BigDecimal.ZERO.setScale(Money.MONEY_SCALE);

            if (action.getType() == CorporateAction.Type.SPLIT) {
                if (position != null && beforeQty.signum() > 0) {
                    BigDecimal afterQty = Money.qty(
                            beforeQty.multiply(action.getFactor()));
                    position.setQuantity(afterQty);
                    position.setAvailableQty(afterQty);
                    position.setCostBasis(Money.money(beforeCost));
                }
            } else {
                if (position != null && beforeQty.signum() > 0) {
                    cashPaid = Money.money(beforeQty.multiply(action.getCashPerShare()));
                    account.setCash(Money.money(account.getCash().add(cashPaid)));
                    account.setAvailableCash(Money.money(
                            account.getAvailableCash().add(cashPaid)));
                    account.setRealizedPnl(Money.money(
                            account.getRealizedPnl().add(cashPaid)));
                }
            }

            entry.getAppliedCorporateActions().add(action.getActionId());
            BigDecimal afterQty = position == null ? beforeQty : position.getQuantity();
            BigDecimal afterCost = position == null ? beforeCost : position.getCostBasis();
            return new CorporateActionResult(action.getActionId(), accountId,
                    action.getSymbol(), action.getType(),
                    beforeQty, afterQty, beforeCost, afterCost, cashPaid, false,
                    "公司行为已应用");
        });
    }

    private void validate(CorporateAction action) {
        if (action == null
                || action.getActionId() == null || action.getActionId().isBlank()
                || action.getSymbol() == null || action.getSymbol().isBlank()
                || action.getType() == null) {
            throw new ServiceException(ErrorCode.CORPORATE_ACTION_INVALID, "缺少必填字段");
        }
        switch (action.getType()) {
            case SPLIT -> {
                if (action.getFactor() == null
                        || action.getFactor().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ServiceException(ErrorCode.CORPORATE_ACTION_INVALID,
                            "拆股 factor 必须为正数");
                }
            }
            case DIVIDEND -> {
                if (action.getCashPerShare() == null
                        || action.getCashPerShare().compareTo(BigDecimal.ZERO) < 0) {
                    throw new ServiceException(ErrorCode.CORPORATE_ACTION_INVALID,
                            "每股分红必须 >= 0");
                }
            }
            default -> throw new ServiceException(ErrorCode.CORPORATE_ACTION_UNKNOWN_TYPE,
                    String.valueOf(action.getType()));
        }
    }

    static BigDecimal scaleFactor(BigDecimal factor) {
        return factor.setScale(Money.QTY_SCALE, RoundingMode.HALF_UP);
    }
}
