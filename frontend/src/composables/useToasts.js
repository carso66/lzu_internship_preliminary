import { reactive, ref } from 'vue'

/**
 * 极简 toast：页面上的成功 / 失败提示。业务错误也走这里，
 * 保证「错误 message 显示给用户」而不只是打 Console（README §4.3）。
 *
 * `toasts` 放在**模块作用域**，全体调用方共用同一份列表。
 * 拆成多页面后这点是必须的：toast 的渲染点在 `App.vue` 外壳上，
 * 而 `toast.success()` 可能在某个页面（下单、发布）里调用。
 * 如果每个组件各持一份 ref，push 到的是另一个数组，
 * 外壳那一条永远不更新，换页之后提示也会凭空消失。
 */
const toasts = ref([])
let seq = 0

function push(message, type = 'info') {
  const id = ++seq
  toasts.value.push({ id, message, type })
  setTimeout(() => dismiss(id), type === 'error' ? 6000 : 4000)
  return id
}

function dismiss(id) {
  toasts.value = toasts.value.filter((t) => t.id !== id)
}

const toast = reactive({
  success: (message) => push(message, 'success'),
  error: (message) => push(message, 'error'),
  info: (message) => push(message, 'info'),
})

export function useToasts() {
  return { toasts, push, dismiss, toast }
}
