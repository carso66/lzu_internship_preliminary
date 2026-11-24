-- 订单号使用数据库序列生成，避免并发下 MAX(id)+1 产生重复订单号。
-- 回滚会浪费序号，但订单号只要求唯一，不要求连续。
CREATE SEQUENCE order_no_seq START WITH 1 INCREMENT BY 1;
