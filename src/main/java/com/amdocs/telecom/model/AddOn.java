package com.amdocs.telecom.model;

import java.math.BigDecimal;

public record AddOn(int id, String code, String name, BigDecimal monthlyPrice, String status) {}
