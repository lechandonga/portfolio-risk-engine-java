package com.github.lechandonga.portfolioriskenginejava.error;

/**
 * 稳定的、可被调用方程序化区分的错误码。每个码都对应一个明确的业务含义，
 * 不会把不同的失败原因合并成一个含糊错误。
 */
public enum ErrorCode {

    ACCOUNT_NOT_FOUND("账户不存在"),
    SYMBOL_NOT_FOUND("标的不存在"),
    ORDER_NOT_FOUND("订单不存在"),
    ORDER_DUPLICATE("重复提交：相同 clientRequestId 的指令已处理"),
    ILLEGAL_AMOUNT("数量或价格非法：必须为正数"),
    INSUFFICIENT_CASH("资金不足：可用现金无法覆盖买入金额与费用"),
    INSUFFICIENT_POSITION("可用数量不足：持仓或可卖数量不足"),
    ORDER_NOT_CANCELLABLE("订单当前状态不可撤单"),
    ORDER_PRICE_UNAVAILABLE("行情缺失：无法按最新行情成交或估值"),
    MARKET_DATA_TIMEOUT("行情来源超时"),
    MARKET_DATA_DIRTY("行情来源返回脏数据"),
    MARKET_DATA_UNAVAILABLE("行情来源不可用"),
    PRICE_STALE("行情已过期：超过允许的陈旧阈值"),
    VALUATION_IMPOSSIBLE("无法计算估值：持仓存在但缺少最新有效行情"),
    RISK_HISTORY_INSUFFICIENT("无法计算风险：历史样本不足"),
    RISK_PRICE_UNAVAILABLE("无法计算风险：当前价或历史行情缺失"),
    RISK_INVALID_CONFIDENCE("置信水平非法：必须位于 (0,1) 开区间"),
    CORPORATE_ACTION_DUPLICATE("公司行为重复应用"),
    CORPORATE_ACTION_OPEN_ORDERS("存在占用持仓的未成交卖单，拒绝应用公司行为"),
    CORPORATE_ACTION_UNKNOWN_TYPE("未知公司行为类型"),
    CORPORATE_ACTION_INVALID("公司行为参数非法"),
    REPLAY_BAD_TIMESTAMP("行情回放失败：时间戳必须严格递增"),
    REPLAY_UNKNOWN_SYMBOL("行情回放失败：包含未知标的"),
    BAD_REQUEST("请求参数非法");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
