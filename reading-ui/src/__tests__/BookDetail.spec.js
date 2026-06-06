import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' } }),
  useRouter: () => ({ back: vi.fn(), push: vi.fn() })
}))

vi.mock('../utils/request', () => ({
  default: { get: vi.fn() }
}))

import request from '../utils/request'
import BookDetail from '../views/BookDetail.vue'

const commonStubs = {
  'el-icon': { template: '<i />' },
  'el-empty': { template: '<div class="el-empty"><slot /></div>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-rate': { template: '<div />' },
  'el-avatar': { template: '<div />' },
  'el-input': { template: '<input />' },
  'el-dialog': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-image': { template: '<img />' },
  'el-tag': { template: '<span><slot /></span>' }
}

function mockGet(url) {
  if (url.includes('/comment/') || url.includes('/bookshelf/') || url.includes('/tag/list') || url.includes('/sysNote/list'))
    return { data: { code: '200', data: [] } }
  // book detail
  return { data: { code: '200', data: { id: 1, title: '测试书籍', author: '作者', description: '', coverUrl: null, category: '', tags: '', filePath: '', uploaderId: 1, status: 2 } } }
}

describe('BookDetail', () => {
  let pinia

  beforeEach(() => {
    vi.clearAllMocks()
    pinia = createPinia()
    setActivePinia(pinia)
    request.get.mockImplementation(mockGet)
  })

  it('should show error state when book not found', async () => {
    request.get.mockImplementation((url) => {
      if (url.includes('/comment/') || url.includes('/bookshelf/') || url.includes('/tag/list') || url.includes('/sysNote/list')) return { data: { code: '200', data: [] } }
      return { data: { code: '404', data: [] } }
    })

    const wrapper = mount(BookDetail, {
      global: { plugins: [pinia], stubs: commonStubs }
    })

    await new Promise(r => setTimeout(r, 50))
    await nextTick()

    expect(wrapper.find('.error-state').exists()).toBe(true)
  })

  it('should show book detail when loaded successfully', async () => {
    const wrapper = mount(BookDetail, {
      global: { plugins: [pinia], stubs: commonStubs }
    })

    await new Promise(r => setTimeout(r, 50))
    await nextTick()

    expect(wrapper.find('.error-state').exists()).toBe(false)
    expect(wrapper.find('.el-card').exists()).toBe(true)
  })
})
