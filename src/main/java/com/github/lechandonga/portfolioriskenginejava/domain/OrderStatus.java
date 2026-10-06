package com.github.lechandonga.portfolioriskenginejava.domain;

/**
 * Order lifecycle:
 * NEW      -> accepted, cash/quantity reserved, not yet filled
 * FILLED   -> executed against a market price, reservation consumed
 * CANCELLED-> reservation released
 * REJECTED -> never accepted (terminal; persisted only for audit if needed)
 */
public enum OrderStatus { NEW, FILLED, CANCELLED, REJECTED }
