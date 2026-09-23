<template>
  <t-table
    :data="courseList"
    :columns="columns"
    row-key="id"
    :pagination="{ defaultPageSize: 10, total: courseList.length }"
    stripe
    hover
  >
    <template #dayOfWeek="{ row }">
      <t-tag variant="light" theme="primary">{{ weekDays[row.dayOfWeek - 1] }}</t-tag>
    </template>
    <template #status="{ row }">
      <t-tag v-if="row.source === 'jw'" theme="primary" variant="light">教务课程</t-tag>
      <t-tag v-else :theme="row.status === 1 ? 'success' : 'danger'">
        {{ row.status === 1 ? '启用' : '停用' }}
      </t-tag>
    </template>
    <template #operation="{ row }">
      <!-- 教务课程由排课管理统一维护，此处只读 -->
      <t-space v-if="row.source === 'jw'">
        <t-tooltip content="教务排课课程，请在「排课管理」中调整">
          <t-link theme="default" disabled>教务维护</t-link>
        </t-tooltip>
      </t-space>
      <t-space v-else>
        <t-link theme="primary" @click="$emit('edit', row)">编辑</t-link>
        <t-popconfirm content="确认删除此课程？" @confirm="$emit('delete', row)">
          <t-link theme="danger">删除</t-link>
        </t-popconfirm>
      </t-space>
    </template>
  </t-table>
</template>

<script setup>
defineProps({
  courseList: {
    type: Array,
    default: () => []
  }
})

defineEmits(['edit', 'delete'])

const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const columns = [
  { colKey: 'name', title: '课程名称', width: 160 },
  { colKey: 'teacher', title: '教师', width: 100 },
  { colKey: 'classroom', title: '教室', width: 140 },
  { colKey: 'dayOfWeek', title: '星期', width: 80 },
  { colKey: 'startTime', title: '开始', width: 80 },
  { colKey: 'endTime', title: '结束', width: 80 },
  { colKey: 'status', title: '状态', width: 70 },
  { colKey: 'operation', title: '操作', width: 120 }
]
</script>
