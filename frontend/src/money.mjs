export function yuanToCents(value) {
  const input = value.trim();

  if (!/^\d+(?:\.\d{1,2})?$/.test(input)) {
    throw new Error('请输入大于 0、最多两位小数的价格，例如 19.90。');
  }

  const [yuan, fraction = ''] = input.split('.');
  const cents = Number(`${yuan}${fraction.padEnd(2, '0')}`);

  if (!Number.isSafeInteger(cents) || cents <= 0) {
    throw new Error('请输入有效的正数价格。');
  }

  return cents;
}
