package com.amdocs.telecom.service.impl;

import com.amdocs.telecom.dao.PlanDAO;
import com.amdocs.telecom.dao.impl.PlanDAOImpl;
import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.TelecomPlan;
import com.amdocs.telecom.service.PlanService;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PlanServiceImpl implements PlanService {

    private final PlanDAO planDAO;

    public PlanServiceImpl() {
        this.planDAO = new PlanDAOImpl();
    }

    @Override
    public List<TelecomPlan> getAllActivePlans() {
        try {
            return planDAO.findAllActive();
        } catch (SQLException e) {
            return List.of();
        }
    }

    @Override
    public List<TelecomPlan> searchPlansByName(String keyword) {
        List<TelecomPlan> plans = getAllActivePlans();
        Predicate<TelecomPlan> nameContains = p -> p.getPlanName().toLowerCase().contains(keyword.toLowerCase());
        return plans.stream()
                .filter(nameContains)
                .collect(Collectors.toList());
    }

    @Override
    public List<TelecomPlan> filterPlansByMaxPrice(double maxPrice) {
        List<TelecomPlan> plans = getAllActivePlans();
        Predicate<TelecomPlan> withinBudget = p -> p.getMonthlyRental() <= maxPrice;
        return plans.stream()
                .filter(withinBudget)
                .collect(Collectors.toList());
    }

    @Override
    public List<TelecomPlan> filterPlansByMinData(int minDataGB) {
        List<TelecomPlan> plans = getAllActivePlans();
        Predicate<TelecomPlan> enoughData = p -> p.getDataAllowanceGB() >= minDataGB;
        return plans.stream()
                .filter(enoughData)
                .collect(Collectors.toList());
    }

    @Override
    public List<TelecomPlan> sortPlansByPrice(boolean ascending) {
        List<TelecomPlan> plans = getAllActivePlans();
        Comparator<TelecomPlan> priceComparator = Comparator.comparingDouble(TelecomPlan::getMonthlyRental);
        
        if (!ascending) {
            priceComparator = priceComparator.reversed();
        }
        
        return plans.stream()
                .sorted(priceComparator)
                .collect(Collectors.toList());
    }

    @Override
    public TelecomPlan getPlanById(int planId) throws TelecomException {
        try {
            Optional<TelecomPlan> opt = planDAO.findById(planId);
            return opt.orElseThrow(() -> new TelecomException("Plan not found with ID: " + planId));
        } catch (SQLException e) {
            throw new TelecomException("Database error: " + e.getMessage());
        }
    }

    @Override
    public List<TelecomPlan> comparePlans(int planId1, int planId2) throws TelecomException {
        TelecomPlan plan1 = getPlanById(planId1);
        TelecomPlan plan2 = getPlanById(planId2);
        return List.of(plan1, plan2);
    }
}
