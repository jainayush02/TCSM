-- ====================================================
-- TELECOM CUSTOMER & SUBSCRIPTION MANAGEMENT SYSTEM (TCSMS)
-- Database Schema Definition (Normalized Relational DB)
-- ====================================================

-- 1. Customers Table
CREATE TABLE IF NOT EXISTS customers (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_number VARCHAR(20) NOT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    date_of_birth DATE NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    mobile_number VARCHAR(15) NOT NULL UNIQUE,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(50) NOT NULL,
    country VARCHAR(50) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    account_status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- 2. Administrators Table
CREATE TABLE IF NOT EXISTS administrators (
    admin_id INT AUTO_INCREMENT PRIMARY KEY,
    admin_number VARCHAR(20) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Telecom Tariff Plans Table
CREATE TABLE IF NOT EXISTS telecom_plans (
    plan_id INT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(30) NOT NULL UNIQUE,
    plan_name VARCHAR(100) NOT NULL,
    plan_type VARCHAR(20) NOT NULL,
    monthly_rental DOUBLE NOT NULL,
    data_allowance_gb INT NOT NULL,
    voice_minutes INT NOT NULL,
    sms_allowance INT NOT NULL,
    validity_days INT NOT NULL,
    international_roaming BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. SIM Cards Table
CREATE TABLE IF NOT EXISTS sim_cards (
    sim_id INT AUTO_INCREMENT PRIMARY KEY,
    sim_number VARCHAR(30) NOT NULL UNIQUE,
    sim_type VARCHAR(20) NOT NULL,
    imsi VARCHAR(30) NOT NULL UNIQUE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Mobile Subscriptions Table
CREATE TABLE IF NOT EXISTS mobile_subscriptions (
    subscription_id INT AUTO_INCREMENT PRIMARY KEY,
    subscription_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id INT NOT NULL,
    plan_id INT NOT NULL,
    mobile_number VARCHAR(15) NOT NULL UNIQUE,
    sim_id INT NOT NULL UNIQUE,
    activation_date DATE NOT NULL,
    subscription_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id) ON DELETE RESTRICT,
    FOREIGN KEY (plan_id) REFERENCES telecom_plans(plan_id) ON DELETE RESTRICT,
    FOREIGN KEY (sim_id) REFERENCES sim_cards(sim_id) ON DELETE RESTRICT
);

-- 6. Subscription History Table
CREATE TABLE IF NOT EXISTS subscription_history (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    old_plan_id INT,
    new_plan_id INT NOT NULL,
    change_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    change_reason VARCHAR(255) NOT NULL,
    changed_by VARCHAR(50) NOT NULL,
    FOREIGN KEY (subscription_id) REFERENCES mobile_subscriptions(subscription_id) ON DELETE CASCADE,
    FOREIGN KEY (old_plan_id) REFERENCES telecom_plans(plan_id) ON DELETE SET NULL,
    FOREIGN KEY (new_plan_id) REFERENCES telecom_plans(plan_id) ON DELETE RESTRICT
);

-- 7. Usage Records Table
CREATE TABLE IF NOT EXISTS usage_records (
    usage_id INT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    usage_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usage_type VARCHAR(20) NOT NULL,
    quantity DOUBLE NOT NULL,
    unit VARCHAR(20) NOT NULL,
    charge DOUBLE NOT NULL DEFAULT 0.0,
    FOREIGN KEY (subscription_id) REFERENCES mobile_subscriptions(subscription_id) ON DELETE CASCADE
);

-- 8. Bills Table
CREATE TABLE IF NOT EXISTS bills (
    bill_id INT AUTO_INCREMENT PRIMARY KEY,
    bill_number VARCHAR(50) NOT NULL UNIQUE,
    subscription_id INT NOT NULL,
    billing_month VARCHAR(10) NOT NULL,
    plan_rental DOUBLE NOT NULL,
    usage_charges DOUBLE NOT NULL,
    tax_amount DOUBLE NOT NULL,
    discount DOUBLE NOT NULL DEFAULT 0.0,
    total_amount DOUBLE NOT NULL,
    due_date DATE NOT NULL,
    bill_status VARCHAR(20) DEFAULT 'UNPAID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (subscription_id) REFERENCES mobile_subscriptions(subscription_id) ON DELETE CASCADE
);

-- 9. Payments Table
CREATE TABLE IF NOT EXISTS payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    transaction_reference VARCHAR(50) NOT NULL UNIQUE,
    bill_id INT NOT NULL,
    customer_id INT NOT NULL,
    amount DOUBLE NOT NULL,
    payment_mode VARCHAR(20) NOT NULL,
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    payment_status VARCHAR(20) NOT NULL,
    FOREIGN KEY (bill_id) REFERENCES bills(bill_id) ON DELETE RESTRICT,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id) ON DELETE RESTRICT
);

-- 10. Customer Complaints Table
CREATE TABLE IF NOT EXISTS complaints (
    complaint_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_number VARCHAR(30) NOT NULL UNIQUE,
    customer_id INT NOT NULL,
    subscription_id INT,
    category VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'OPEN',
    resolution TEXT,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id) ON DELETE RESTRICT,
    FOREIGN KEY (subscription_id) REFERENCES mobile_subscriptions(subscription_id) ON DELETE SET NULL
);

-- 11. Login History & Security Table
CREATE TABLE IF NOT EXISTS login_history (
    login_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    user_role VARCHAR(20) NOT NULL,
    login_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50) DEFAULT '127.0.0.1',
    status VARCHAR(20) NOT NULL
);

-- 12. Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    entity_name VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    details TEXT,
    performed_by VARCHAR(50) NOT NULL,
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 13. Notifications Table
CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    title VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'UNREAD',
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_customer_email ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customer_mobile ON customers(mobile_number);
CREATE INDEX IF NOT EXISTS idx_sub_customer ON mobile_subscriptions(customer_id);
CREATE INDEX IF NOT EXISTS idx_usage_sub ON usage_records(subscription_id);
CREATE INDEX IF NOT EXISTS idx_bill_sub ON bills(subscription_id);
CREATE INDEX IF NOT EXISTS idx_bill_status ON bills(bill_status);
CREATE INDEX IF NOT EXISTS idx_complaint_status ON complaints(status);
