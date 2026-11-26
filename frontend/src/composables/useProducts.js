import { computed, reactive, ref } from 'vue'
import { listProducts } from '../api/products.js'

/**
 * 商品列表状态：加载态、空态、筛选、错误。
 *
 * 关键点：
 * - 每次请求带自增 `requestId`，只用最新一次的结果回写，
 *   避免快速改筛选时旧响应后到把新数据覆盖掉；
 * - `loading` 与「列表为空」严格分离，交给模板决定「加载中 / 暂无数据」互斥显示。
 */
export function useProducts() {
  const items = ref([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref('')

  const filters = reactive({ keyword: '', status: '', category: '' })
  const hasFilters = computed(
    () => Boolean(filters.keyword.trim() || filters.status || filters.category),
  )

  let requestId = 0

  async function reload() {
    const current = ++requestId
    loading.value = true
    error.value = ''
    try {
      const data = await listProducts({
        keyword: filters.keyword.trim(),
        status: filters.status,
        category: filters.category,
      })
      if (current !== requestId) return
      items.value = Array.isArray(data?.items) ? data.items : []
      total.value = Number.isFinite(Number(data?.total)) ? Number(data.total) : items.value.length
    } catch (err) {
      if (current !== requestId) return
      items.value = []
      total.value = 0
      error.value = err?.message || '加载商品失败'
    } finally {
      // 有更新的请求在飞时保持 loading，交给它自己收尾
      if (current === requestId) loading.value = false
    }
  }

  function clearFilters() {
    filters.keyword = ''
    filters.status = ''
    filters.category = ''
    return reload()
  }

  /** 上下架成功后只改这一行，不做整表刷新 */
  function patchItemStatus(id, status) {
    const target = items.value.find((item) => item.id === id)
    if (target) target.status = status
  }

  return { items, total, loading, error, filters, hasFilters, reload, clearFilters, patchItemStatus }
}
