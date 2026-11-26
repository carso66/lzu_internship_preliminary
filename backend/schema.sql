CREATE TABLE IF NOT EXISTS products (
    id BIGINT NOT NULL COMMENT '商品ID，主键，由应用生成雪花ID',
    title VARCHAR(200) NOT NULL COMMENT '商品标题',
    price INT NOT NULL COMMENT '商品单价，单位：分（整数，禁止用小数存金额）',
    stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '商品库存数量',
    category VARCHAR(50) NOT NULL COMMENT '商品分类',
    description TEXT NULL COMMENT '商品描述',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '商品状态：0-下架，1-上架',
    seller_name VARCHAR(64) NOT NULL COMMENT '卖家名称',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '更新人',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id),
    KEY idx_products_category (category),
    KEY idx_products_status_deleted (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT NOT NULL COMMENT '订单ID，主键，由应用生成雪花ID',
    order_no VARCHAR(32) NOT NULL COMMENT '订单编号，全局唯一',
    product_id BIGINT NOT NULL COMMENT '商品ID，对应products.id',
    product_title VARCHAR(200) NOT NULL COMMENT '下单时的商品标题快照',
    quantity INT UNSIGNED NOT NULL COMMENT '购买数量',
    unit_price INT NOT NULL COMMENT '下单时的商品单价，单位：分',
    total_amount BIGINT NOT NULL COMMENT '订单总金额，单位：分（unit_price × quantity）',
    buyer_name VARCHAR(64) NOT NULL COMMENT '买家姓名',
    buyer_phone VARCHAR(20) NOT NULL COMMENT '买家联系电话',
    address VARCHAR(255) NOT NULL COMMENT '收货地址',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付，1-已支付，2-已取消',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    paid_at DATETIME NULL COMMENT '支付时间，未支付时为NULL',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '更新人',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_product_id (product_id),
    KEY idx_orders_status_deleted (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT NOT NULL COMMENT '支付单ID，主键，由应用生成雪花ID',
    payment_no VARCHAR(32) NOT NULL COMMENT '支付流水号，形如PAYxxx，全局唯一',
    order_id BIGINT NOT NULL COMMENT '订单ID，对应orders.id',
    order_no VARCHAR(32) NOT NULL COMMENT '订单编号快照，便于对账',
    amount BIGINT NOT NULL COMMENT '支付金额，单位：分，等于订单总金额',
    method VARCHAR(16) NOT NULL COMMENT '支付方式：alipay-支付宝，wechat-微信',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '支付状态：0-处理中，1-已支付，2-失败',
    paid_at DATETIME NULL COMMENT '支付成功时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '更新人',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_payment_no (payment_no),
    UNIQUE KEY uk_payments_order_id (order_id),
    KEY idx_payments_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付表';

-- 商品表 products 5条测试数据
INSERT INTO products (id, title, price, stock, category, description, status, seller_name, created_at, updated_at, created_by, updated_by, is_deleted)
VALUES
    (1680010000000000001, '无线蓝牙耳机', 8990, 236, '数码配件', '降噪蓝牙耳机，续航24小时，支持蓝牙5.3', 1, '数码小店', NOW(), NOW(), 'admin', 'admin', 0),
    (1680010000000000002, '机械键盘青轴', 12900, 78, '电脑外设', '87键机械键盘，青轴手感，RGB背光', 1, '外设优选', NOW(), NOW(), 'admin', 'admin', 0),
    (1680010000000000003, '纯棉圆领T恤', 3990, 420, '服饰', '夏季纯棉短袖，多色可选，透气舒适', 1, '服饰工坊', NOW(), NOW(), 'admin', 'admin', 0),
    (1680010000000000004, '不锈钢保温杯', 4550, 155, '家居用品', '316不锈钢保温杯，500ml，长效保温12小时', 0, '居家百货', NOW(), NOW(), 'admin', 'admin', 0),
    (1680010000000000005, '桌面小型加湿器', 6600, 92, '家居用品', '静音迷你加湿器，适合办公室桌面使用', 1, '居家百货', NOW(), NOW(), 'admin', 'admin', 0);

-- 订单表 orders 5条测试数据（关联上面商品id）
INSERT INTO orders (id, order_no, product_id, product_title, quantity, unit_price, total_amount, buyer_name, buyer_phone, address, status, created_at, paid_at, updated_at, created_by, updated_by, is_deleted)
VALUES
    (1690010000000000001, 'ORD202611250001', 1680010000000000001, '无线蓝牙耳机', 1, 8990, 8990, '张三', '13800138001', '甘肃省兰州市城关区XX小区1号楼', 1, NOW(), NOW(), NOW(), 'admin', 'admin', 0),
    (1690010000000000002, 'ORD202611250002', 1680010000000000002, '机械键盘青轴', 1, 12900, 12900, '李四', '13800138002', '重庆市石柱县XX街道XX大厦', 0, NOW(), NULL, NOW(), 'admin', 'admin', 0),
    (1690010000000000003, 'ORD202611250003', 1680010000000000003, '纯棉圆领T恤', 2, 3990, 7980, '王五', '13800138003', '兰州市安宁区XX大学宿舍', 1, NOW(), NOW(), NOW(), 'admin', 'admin', 0),
    (1690010000000000004, 'ORD202611250004', 1680010000000000005, '桌面小型加湿器', 1, 6600, 6600, '赵六', '13800138004', '兰州市七里河区XX小区', 2, NOW(), NULL, NOW(), 'admin', 'admin', 0),
    (1690010000000000005, 'ORD202611250005', 1680010000000000001, '无线蓝牙耳机', 2, 8990, 17980, '孙七', '13800138005', '兰州市西固区XX园区', 1, NOW(), NOW(), NOW(), 'admin', 'admin', 0);
