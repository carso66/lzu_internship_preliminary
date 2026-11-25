-- 支付编号使用数据库序列生成，保证并发请求拿到的序号不重复。
-- 回滚会浪费序号，但支付编号只要求唯一，不要求连续。
CREATE SEQUENCE payment_no_seq START WITH 1 INCREMENT BY 1;
