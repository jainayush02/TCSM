package com.amdocs.telecom.dao.impl;

import com.amdocs.telecom.dao.AddOnDAO;
import com.amdocs.telecom.model.AddOn;
import com.amdocs.telecom.util.DBConnection;
import java.sql.*;
import java.util.*;

public class AddOnDAOImpl implements AddOnDAO {
    @Override public List<AddOn> findActive() throws SQLException {
        return query("SELECT * FROM add_on_services WHERE status='ACTIVE' ORDER BY add_on_id",null);
    }
    @Override public List<AddOn> findBySubscription(int subscriptionId) throws SQLException {
        return query("SELECT a.* FROM add_on_services a JOIN subscription_add_ons sa ON sa.add_on_id=a.add_on_id WHERE sa.subscription_id=? AND sa.status='ACTIVE' ORDER BY a.add_on_id",subscriptionId);
    }
    private List<AddOn> query(String sql,Integer id) throws SQLException {
        List<AddOn> result=new ArrayList<>();
        try(Connection c=DBConnection.getInstance().getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            if(id!=null) p.setInt(1,id);
            try(ResultSet r=p.executeQuery()) { while(r.next()) result.add(new AddOn(r.getInt("add_on_id"),r.getString("code"),r.getString("name"),r.getBigDecimal("monthly_price"),r.getString("status"))); }
        }
        return result;
    }
}
