<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ApiError } from '../api/client'
import { publishPost } from '../api/post'
import { uploadPostImage } from '../api/me'

const title = ref('')
const content = ref('')
const tagList = ref<string[]>([])
const error = ref('')
const submitting = ref(false)
const contentRef = ref<InstanceType<typeof import('element-plus')['ElInput']>>()
const uploadingImage = ref(false)

/** 在光标处插入上传图片的 Markdown 语法 */
async function onPickImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('配图不能超过 5MB')
    input.value = ''
    return
  }
  uploadingImage.value = true
  try {
    const url = await uploadPostImage(file)
    const md = `![](${url})`
    const el = contentRef.value?.$el?.querySelector('textarea') as HTMLTextAreaElement | undefined
    if (el && el.selectionStart != null) {
      const start = el.selectionStart
      content.value = content.value.slice(0, start) + md + content.value.slice(el.selectionEnd)
      requestAnimationFrame(() => {
        el.focus()
        el.selectionStart = el.selectionEnd = start + md.length
      })
    } else {
      content.value += md
    }
    ElMessage.success('图片已上传，已插入 Markdown')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '上传失败')
  } finally {
    uploadingImage.value = false
    input.value = ''
  }
}
const router = useRouter()

const CONTENT_HINT =
  '支持 Markdown 语法：## 二级标题、**加粗**、`行内代码`、``` 代码块、> 引用…'

// Element Plus 动态创建标签前允许的最大数量（留 8 个防滥用）
const MAX_TAGS = 8
const canAddTag = computed(() => tagList.value.length < MAX_TAGS)

async function submit() {
  if (title.value.trim().length < 5) {
    error.value = '标题至少 5 个字'
    return
  }
  if (content.value.trim().length < 10) {
    error.value = '正文太短了，至少写 10 个字'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    await publishPost(title.value.trim(), content.value, tagList.value)
    // 发布进 REVIEW 审核态，提示后回列表
    ElMessage.success('发布成功，等待审核通过后公开可见')
    router.push('/')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '发布失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="container">
    <div class="panel">
      <h2 style="margin: 0 0 4px">写面经</h2>
      <p class="muted" style="margin: 0 0 20px">
        发布后进入审核队列，管理员通过后公开可见——真实经验更受欢迎
      </p>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input
            v-model="title"
            size="large"
            maxlength="128"
            show-word-limit
            placeholder="一句话概括：公司 + 岗位 + 轮次（如：字节跳动后端一面复盘）"
          />
        </el-form-item>

        <el-form-item label="正文（Markdown）">
          <label class="img-upload" style="display: inline-block; margin-bottom: 6px; font-size: 13px; color: var(--el-color-primary); cursor: pointer">
            <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden :disabled="uploadingImage" @change="onPickImage" />
            {{ uploadingImage ? '上传中…' : '🖼 插入图片（≤5MB）' }}
          </label>
          <el-input
            ref="contentRef"
            v-model="content"
            type="textarea"
            :autosize="{ minRows: 14, maxRows: 30 }"
            :placeholder="CONTENT_HINT"
          />
        </el-form-item>

        <el-form-item label="标签（回车添加，最多 8 个）">
          <el-select
            v-model="tagList"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            :disabled="!canAddTag && tagList.length >= MAX_TAGS"
            placeholder="如：字节跳动、Redis、后端、二面"
            style="width: 100%"
          >
            <el-option v-for="t in tagList" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          @click="submit"
        >
          {{ submitting ? '发布中…' : '发布（进审核）' }}
        </el-button>
      </el-form>
    </div>
  </div>
</template>
