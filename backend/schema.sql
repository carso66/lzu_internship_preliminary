CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    seller_name VARCHAR(80) NOT NULL,
    category VARCHAR(64) NOT NULL,
    description VARCHAR(1000),
    price BIGINT NOT NULL,
    stock INT NOT NULL,
    status VARCHAR(3) DEFAULT 'on' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_stock CHECK (stock >= 0),
    CONSTRAINT ck_products_status CHECK (status IN ('on', 'off'))
);

CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL,
    product_id BIGINT NOT NULL,
    product_title VARCHAR(120) NOT NULL,
    quantity INT NOT NULL,
    unit_price BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    buyer_name VARCHAR(80) NOT NULL,
    buyer_phone VARCHAR(32) NOT NULL,
    address VARCHAR(255) NOT NULL,
    status VARCHAR(7) DEFAULT 'created' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_orders_order_no UNIQUE (order_no),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_orders_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_orders_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount = unit_price * quantity),
    CONSTRAINT ck_orders_status CHECK (status IN ('created', 'paid')),
    CONSTRAINT ck_orders_paid_at CHECK (
        (status = 'created' AND paid_at IS NULL) OR
        (status = 'paid' AND paid_at IS NOT NULL)
    )
);

CREATE TABLE payments (
    payment_id VARCHAR(40) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    method VARCHAR(6) NOT NULL,
    amount BIGINT NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_payments_order UNIQUE (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payments_method CHECK (method IN ('alipay', 'wechat')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0)
);
