-- Seed data for TCSMS

-- Default Admin (Username: admin, Password: admin@123)
-- BCrypt hash generated at runtime; using a pre-computed hash here
INSERT INTO administrators (admin_number, username, password_hash, email, full_name, account_status)
SELECT 'ADM1001', 'admin', '$2a$10$In4VlbyKe7FC4DpR.3wnEOg8lCO/31lKQ1ayCTHfWuE4YwTeGkoqW', 'admin@telecom.com', 'System Administrator', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM administrators WHERE username = 'admin');

-- Initial Tariff Plans
INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status)
SELECT 'PLAN-101', '5G Premium', 'POSTPAID', 999.0, 100, -1, 1000, 30, TRUE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM telecom_plans WHERE plan_code = 'PLAN-101');

INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status)
SELECT 'PLAN-102', '5G Standard', 'POSTPAID', 699.0, 50, 1500, 500, 30, FALSE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM telecom_plans WHERE plan_code = 'PLAN-102');

INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status)
SELECT 'PLAN-103', 'Business Pro', 'POSTPAID', 1499.0, 200, -1, 2000, 30, TRUE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM telecom_plans WHERE plan_code = 'PLAN-103');

INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status)
SELECT 'PLAN-104', 'Basic', 'PREPAID', 399.0, 20, 500, 100, 28, FALSE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM telecom_plans WHERE plan_code = 'PLAN-104');

-- Sample SIM Cards (available for new subscriptions)
INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678901', 'ESIM', '404010123456789', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678901');

INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678902', 'PHYSICAL_SIM', '404010123456790', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678902');

INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678903', 'ESIM', '404010123456791', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678903');

INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678904', 'PHYSICAL_SIM', '404010123456792', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678904');

INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678905', 'ESIM', '404010123456793', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678905');

-- Sample Customers (Password: Customer@123)
INSERT INTO customers (customer_number, first_name, last_name, date_of_birth, email, mobile_number, address, city, country, username, password_hash, account_status)
SELECT 'CUST100245', 'Arjun', 'Mehta', '1995-05-15', 'arjun.mehta@example.com', '9876543210', '101 Marine Drive', 'Mumbai', 'India', 'arjunm', '$2a$10$sIDl.6SBUnH9IG23TmFc/eHItikRx8YqdxxCeEGOvrlyA3mcNPKbO', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE customer_number = 'CUST100245');

INSERT INTO customers (customer_number, first_name, last_name, date_of_birth, email, mobile_number, address, city, country, username, password_hash, account_status)
SELECT 'CUST100378', 'Sarah', 'Wilson', '1992-08-20', 'sarah.wilson@example.com', '9876543211', '42 Baker Street', 'London', 'UK', 'sarahw', '$2a$10$sIDl.6SBUnH9IG23TmFc/eHItikRx8YqdxxCeEGOvrlyA3mcNPKbO', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE customer_number = 'CUST100378');

INSERT INTO customers (customer_number, first_name, last_name, date_of_birth, email, mobile_number, address, city, country, username, password_hash, account_status)
SELECT 'CUST100412', 'Omar', 'Hassan', '1988-12-10', 'omar.hassan@example.com', '9876543212', '12 King Fahd Rd', 'Riyadh', 'Saudi Arabia', 'omarh', '$2a$10$sIDl.6SBUnH9IG23TmFc/eHItikRx8YqdxxCeEGOvrlyA3mcNPKbO', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE customer_number = 'CUST100412');

-- Sample Mobile Subscription
INSERT INTO mobile_subscriptions (subscription_number, customer_id, plan_id, mobile_number, sim_id, activation_date, subscription_type, status)
SELECT 'SUB10001', c.customer_id, p.plan_id, '+91-9876543210', s.sim_id, '2025-01-01', 'POSTPAID', 'ACTIVE'
FROM customers c, telecom_plans p, sim_cards s
WHERE c.customer_number = 'CUST100245' AND p.plan_code = 'PLAN-102' AND s.sim_number = '89910012345678901'
AND NOT EXISTS (SELECT 1 FROM mobile_subscriptions WHERE subscription_number = 'SUB10001' OR sim_id = s.sim_id OR mobile_number = '+91-9876543210');

-- Sample Bill for Customer 1
INSERT INTO bills (bill_number, subscription_id, billing_month, invoice_key, plan_rental, usage_charges, tax_amount, discount, total_amount, due_date, bill_status)
SELECT 'INV-2026-08-10245', s.subscription_id, '2026-08', CONCAT('MONTHLY:',s.subscription_id,':2026-08'), 699.0, 320.0, 183.42, 50.0, 1152.42, '2026-08-20', 'UNPAID'
FROM mobile_subscriptions s WHERE s.subscription_number = 'SUB10001'
AND NOT EXISTS (SELECT 1 FROM bills WHERE bill_number = 'INV-2026-08-10245' OR invoice_key = CONCAT('MONTHLY:',s.subscription_id,':2026-08'));

-- Sample Usage Records
INSERT INTO usage_records (subscription_id, usage_date, usage_type, quantity, unit, charge)
SELECT s.subscription_id, '2026-08-10 12:00:00', 'VOICE', 120.0, 'Minutes', 0.0
FROM mobile_subscriptions s WHERE s.subscription_number = 'SUB10001'
AND NOT EXISTS (SELECT 1 FROM usage_records WHERE subscription_id=s.subscription_id AND usage_date='2026-08-10 12:00:00' AND usage_type='VOICE');
INSERT INTO usage_records (subscription_id, usage_date, usage_type, quantity, unit, charge)
SELECT s.subscription_id, '2026-08-10 12:00:00', 'SMS', 45.0, 'Count', 0.0
FROM mobile_subscriptions s WHERE s.subscription_number = 'SUB10001'
AND NOT EXISTS (SELECT 1 FROM usage_records WHERE subscription_id=s.subscription_id AND usage_date='2026-08-10 12:00:00' AND usage_type='SMS');
INSERT INTO usage_records (subscription_id, usage_date, usage_type, quantity, unit, charge)
SELECT s.subscription_id, '2026-08-10 12:00:00', 'DATA', 2048.0, 'MB', 150.0
FROM mobile_subscriptions s WHERE s.subscription_number = 'SUB10001'
AND NOT EXISTS (SELECT 1 FROM usage_records WHERE subscription_id=s.subscription_id AND usage_date='2026-08-10 12:00:00' AND usage_type='DATA');
INSERT INTO usage_records (subscription_id, usage_date, usage_type, quantity, unit, charge)
SELECT s.subscription_id, '2026-08-10 12:00:00', 'ROAMING', 30.0, 'MB', 170.0
FROM mobile_subscriptions s WHERE s.subscription_number = 'SUB10001'
AND NOT EXISTS (SELECT 1 FROM usage_records WHERE subscription_id=s.subscription_id AND usage_date='2026-08-10 12:00:00' AND usage_type='ROAMING');

-- Sample Complaints
INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1001A1', c.customer_id, sub.subscription_id, 'NETWORK', 'Slow 5G internet speeds and call drops during peak hours in South Mumbai.', 'HIGH', CURRENT_TIMESTAMP, 'RESOLVED', 'Network cell tower 4B in South Mumbai optimized and antenna reoriented. Speeds verified above 150 Mbps.'
FROM customers c JOIN mobile_subscriptions sub ON sub.customer_id=c.customer_id AND sub.subscription_number='SUB10001'
WHERE c.customer_number='CUST100245' AND NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1001A1');

INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1002B2', c.customer_id, sub.subscription_id, 'BILLING', 'Charged extra INR 170 for roaming when roaming was included.', 'MEDIUM', CURRENT_TIMESTAMP, 'OPEN', NULL
FROM customers c JOIN mobile_subscriptions sub ON sub.customer_id=c.customer_id AND sub.subscription_number='SUB10001'
WHERE c.customer_number='CUST100245' AND NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1002B2');

INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1003C3', c.customer_id, NULL, 'NETWORK', 'Cannot connect to 4G LTE while traveling on underground commute routes.', 'LOW', CURRENT_TIMESTAMP, 'OPEN', NULL
FROM customers c WHERE c.customer_number='CUST100378'
AND NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1003C3');

INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1004D4', c.customer_id, NULL, 'SIM', 'eSIM profile download QR code failed scanning twice.', 'HIGH', CURRENT_TIMESTAMP, 'RESOLVED', 'Fresh eSIM activation QR profile regenerated and emailed to customer. Profile successfully installed.'
FROM customers c WHERE c.customer_number='CUST100412'
AND NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1004D4');

INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1005E5', c.customer_id, sub.subscription_id, 'PAYMENT', 'UPI transaction deducted twice during bill payment.', 'CRITICAL', CURRENT_TIMESTAMP, 'RESOLVED', 'Duplicate transaction INR 1152.42 verified. Automatic refund initiated to original bank account via PG gateway.'
FROM customers c JOIN mobile_subscriptions sub ON sub.customer_id=c.customer_id AND sub.subscription_number='SUB10001'
WHERE c.customer_number='CUST100245' AND NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1005E5');

INSERT INTO add_on_services(code,name,monthly_price,status)
SELECT 'DATA-BOOST','Extra data pack',99,'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM add_on_services WHERE code='DATA-BOOST');
INSERT INTO add_on_services(code,name,monthly_price,status)
SELECT 'ROAMING-PACK','Roaming pack',199,'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM add_on_services WHERE code='ROAMING-PACK');
