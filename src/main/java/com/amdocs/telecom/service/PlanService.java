package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.TelecomPlan;

import java.util.List;

public interface PlanService {
    List<TelecomPlan> getAllActivePlans();
    List<TelecomPlan> searchPlansByName(String keyword);
    List<TelecomPlan> filterPlansByMaxPrice(double maxPrice);
    List<TelecomPlan> filterPlansByMinData(int minDataGB);
    List<TelecomPlan> sortPlansByPrice(boolean ascending);
    List<TelecomPlan> filterPlansByPriceRange(double minPrice, double maxPrice);
    TelecomPlan getPlanById(int planId) throws TelecomException;
    List<TelecomPlan> comparePlans(int planId1, int planId2) throws TelecomException;
}
