<template>
  <div>
    <div class="page-head">
      <h2>课程资源</h2>
      <p class="subtitle">选课报名 · 课件下载 · 课程管理</p>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="所有课程" name="all" v-if="role === 'student'">
        <div class="filter-bar">
          <el-input
            v-model="courseQuery.courseName"
            placeholder="按课程名称搜索"
            clearable
            style="width: 220px"
            @keyup.enter="searchCourses"
            @clear="searchCourses"
          />
          <el-button type="primary" @click="searchCourses">搜索</el-button>
          <el-button @click="resetCourseSearch">重置</el-button>
        </div>
        <el-table :data="courses" v-loading="courseLoading" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="培训周期"></el-table-column>
          <el-table-column prop="publishTime" label="发布时间">
            <template #default="scope">{{ formatDate(scope.row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button size="small" type="primary" @click="applyCourse(scope.row)">报名</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, prev, pager, next, sizes"
            :pager-count="5"
            :total="courseTotal"
            :page-sizes="[5, 10, 20, 50]"
            v-model:current-page="courseQuery.pageNum"
            v-model:page-size="courseQuery.pageSize"
            @current-change="fetchCourses"
            @size-change="searchCourses"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="我已报名的课程" name="enrolled" v-if="role === 'student'">
        <el-table :data="enrolledCourses" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="培训周期"></el-table-column>
          <el-table-column label="报名状态" width="110">
            <template #default="scope">
              <el-tag :type="enrollStatusTagType(scope.row)">{{ enrollStatusText(scope.row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="applyRemark" label="审核意见" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.applyRemark || '—' }}</template>
          </el-table-column>
          <el-table-column prop="publishTime" label="发布时间">
            <template #default="scope">{{ formatDate(scope.row.publishTime) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <!-- 只有审核通过后才能下载课件资源 -->
              <el-button size="small" type="primary" v-if="scope.row.applyStatus === 1" @click="viewResources(scope.row)">资源下载</el-button>
              <span v-else style="color: #909399; font-size: 12px">审核通过后可下载</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我发布的课程" name="my" v-if="role === 'teacher'">
        <div class="filter-bar">
          <el-input
            v-model="courseQuery.courseName"
            placeholder="按课程名称搜索"
            clearable
            style="width: 220px"
            @keyup.enter="searchCourses"
            @clear="searchCourses"
          />
          <el-select v-model="courseQuery.auditStatus" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
          <el-button type="primary" @click="searchCourses">搜索</el-button>
          <el-button @click="resetCourseSearch">重置</el-button>
          <div class="spacer"></div>
          <!-- 删除课程、驳回报名都要管理员点头，所以给教师一个回看申请进度的地方 -->
          <el-button type="warning" plain @click="openMyRequests">
            我的审批申请{{ pendingRequestCount > 0 ? '（待审批 ' + pendingRequestCount + '）' : '' }}
          </el-button>
          <el-button type="success" @click="showAddDialog = true">发布新课程</el-button>
        </div>
        <el-table :data="courses" v-loading="courseLoading" style="width: 100%">
          <el-table-column prop="courseName" label="课程名称"></el-table-column>
          <el-table-column prop="trainCycle" label="培训周期"></el-table-column>
          <el-table-column prop="auditStatus" label="状态">
            <template #default="scope">
              <el-tag :type="scope.row.auditStatus === 1 ? 'success' : (scope.row.auditStatus === 2 ? 'danger' : 'info')">
                {{ scope.row.auditStatus === 1 ? '已通过' : (scope.row.auditStatus === 2 ? '已驳回' : '待审核') }}
              </el-tag>
            </template>
          </el-table-column>
          <!-- 被驳回时展示管理员填写的原因，教师据此修改后重新提交 -->
          <el-table-column prop="auditRemark" label="审核意见" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.auditRemark || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300">
            <template #default="scope">
              <el-button size="small" @click="viewResources(scope.row)">管理资源</el-button>
              <el-button size="small" type="info" @click="viewStudents(scope.row)">查看学员</el-button>
              <!-- 删除是不可恢复的级联操作，教师只能「申请」——管理员同意后才真正执行 -->
              <el-button size="small" type="danger" @click="applyDeleteCourse(scope.row)">申请删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, prev, pager, next, sizes"
            :pager-count="5"
            :total="courseTotal"
            :page-sizes="[5, 10, 20, 50]"
            v-model:current-page="courseQuery.pageNum"
            v-model:page-size="courseQuery.pageSize"
            @current-change="fetchCourses"
            @size-change="searchCourses"
          />
        </div>
      </el-tab-pane>
      <el-tab-pane label="报名审核" name="apply" v-if="role === 'teacher'">
        <!-- 待办优先：统计卡把"还要处理多少"提到最前，点一下即筛选，默认停在待审核。
             第一张「全部」卡是退出筛选的显式出口 —— 靠"再点一次同一张卡"来取消筛选太隐蔽，
             用户根本发现不了。 -->
        <div class="stat-cards">
          <div
            v-for="item in applyStats"
            :key="item.label"
            class="stat-card"
            :class="{ 'is-active': applyQuery.status === item.status }"
            :style="{ '--stat-color': item.color }"
            @click="pickApplyStatus(item.status)"
          >
            <div class="stat-card-label">{{ item.label }}</div>
            <div class="stat-card-value">{{ item.count }}</div>
          </div>
        </div>

        <div class="filter-bar">
          <el-select
            v-model="applyQuery.courseId"
            placeholder="全部课程"
            clearable
            style="width: 200px"
            @change="searchApplies"
          >
            <el-option
              v-for="course in myCourses"
              :key="course.courseId"
              :label="course.courseName"
              :value="course.courseId"
            />
          </el-select>
          <el-input
            v-model="applyQuery.studentName"
            placeholder="按学员姓名搜索"
            clearable
            style="width: 200px"
            @keyup.enter="searchApplies"
            @clear="searchApplies"
          />
          <el-button type="primary" @click="searchApplies">搜索</el-button>
          <el-button @click="resetApplySearch">重置</el-button>
          <div class="spacer"></div>
          <span class="apply-tip">当前条件下 {{ filteredApplies.length }} 条</span>
        </div>

        <!-- max-height 让表头固定：学生多的时候不必滚回顶部才知道哪列是什么 -->
        <el-table :data="pagedApplies" style="width: 100%" max-height="520" empty-text="当前筛选条件下没有报名记录">
          <el-table-column prop="courseName" label="课程名称" min-width="160"></el-table-column>
          <el-table-column prop="studentName" label="学员姓名" min-width="110"></el-table-column>
          <el-table-column prop="applyTime" label="申请时间" min-width="170">
            <template #default="scope">{{ formatDate(scope.row.applyTime) }}</template>
          </el-table-column>
          <el-table-column prop="auditStatus" label="状态" width="110">
            <template #default="scope">
              <el-tag :type="auditStatusTagType(scope.row)">{{ auditStatusText(scope.row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="auditRemark" label="审核意见" min-width="140" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.auditRemark || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="scope">
              <el-button size="small" type="success" v-if="scope.row.auditStatus === 0" @click="auditApply(scope.row, 1)">通过</el-button>
              <!-- 驳回影响学员能否上课，教师只能提交申请，管理员同意后才驳回；
                   申请已提交（rejectPending）时隐藏按钮并提示，避免教师以为没提交成功而反复点 -->
              <el-button
                size="small"
                type="danger"
                v-if="scope.row.auditStatus === 0 && !scope.row.rejectPending"
                @click="applyRejectApply(scope.row)"
              >驳回</el-button>
              <el-tooltip
                v-if="scope.row.rejectPending"
                content="驳回申请已提交，等待管理员审批"
                placement="top"
              >
                <el-button size="small" disabled>驳回审批中</el-button>
              </el-tooltip>
              <el-button size="small" type="warning" v-if="scope.row.auditStatus === 2" @click="auditApply(scope.row, 0)">撤回驳回</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar" v-if="filteredApplies.length > 0">
          <el-pagination
            background
            layout="total, prev, pager, next, sizes"
            :pager-count="5"
            :total="filteredApplies.length"
            :page-sizes="[5, 10, 20, 50]"
            v-model:current-page="applyQuery.pageNum"
            v-model:page-size="applyQuery.pageSize"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showAddDialog" title="发布新课程">
      <el-form :model="newCourse">
        <el-form-item label="课程名称"><el-input v-model="newCourse.courseName"></el-input></el-form-item>
        <el-form-item label="培训周期"><el-input v-model="newCourse.trainCycle"></el-input></el-form-item>
        <el-form-item label="课程介绍"><el-input type="textarea" v-model="newCourse.courseIntro"></el-input></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddCourse">确定</el-button>
      </template>
    </el-dialog>

    <!-- 资源弹窗：加宽到 960px，上方是预览区、下方是资源列表。
         原来 780px 里只塞了"元数据 + 下载按钮"，没有真正的预览，所以显得又小又空。 -->
    <el-dialog v-model="showResourceDialog" :title="resourceDialogTitle" width="960px" class="res-dialog">
      <!-- ==================== 预览区（选中某个资源后才出现） ==================== -->
      <div v-if="previewRes" class="res-preview">
        <div class="res-preview-head">
          <span class="res-preview-title">{{ previewRes.resourceName }}</span>
          <el-tag size="small" effect="plain" :type="resourceTypeTag(previewRes.resourceType)">
            {{ previewRes.resourceType || '其他' }}
          </el-tag>
          <div class="spacer"></div>
          <el-button size="small" @click="closePreview">收起预览</el-button>
        </div>

        <div v-loading="previewLoading" class="res-preview-body">
          <!-- 外链不能内嵌（对方站点通常禁止被 iframe 嵌套），只给一个跳转入口 -->
          <div v-if="isExternalPath(previewRes.resourcePath)" class="res-preview-tip">
            这是外部链接，无法在页面内预览
            <el-button size="small" type="primary" @click="openExternal(previewRes)">
              在新标签页打开
            </el-button>
          </div>

          <!-- PDF / 图片都交给浏览器内嵌渲染 -->
          <iframe
            v-else-if="previewUrl && (previewKind === 'pdf' || previewKind === 'image')"
            class="res-preview-frame"
            :src="previewUrl"
            title="资源预览"
          ></iframe>

          <video
            v-else-if="previewUrl && previewKind === 'video'"
            class="res-preview-video"
            :src="previewUrl"
            controls
          ></video>

          <div v-else-if="previewKind === 'other'" class="res-preview-tip">
            该格式不支持在线预览，请下载后查看
          </div>
        </div>

        <div class="res-preview-foot">
          <el-button
            v-if="!isExternalPath(previewRes.resourcePath)"
            type="primary"
            @click="downloadResource(previewRes)"
          >
            下载
          </el-button>
          <!-- 「另存为」用 File System Access API 弹出系统保存对话框，
               只有 Chromium 系浏览器支持，不支持时整个按钮不出现 -->
          <el-button
            v-if="canPickSavePath && !isExternalPath(previewRes.resourcePath)"
            @click="downloadResourceAs(previewRes)"
          >
            另存为…
          </el-button>
          <span v-if="!canPickSavePath && !isExternalPath(previewRes.resourcePath)" class="res-hint">
            当前浏览器不支持自选保存位置，可在浏览器设置里开启「下载前询问保存位置」
          </span>
        </div>
      </div>

      <div class="res-toolbar">
        <span class="res-count">共 {{ resources.length }} 个资源</span>
        <el-button type="primary" v-if="role === 'teacher'" @click="showAddResourceDialog = true">
          上传资源
        </el-button>
      </div>
      <el-table :data="resources" style="width: 100%" empty-text="这门课程还没有上传课件">
        <el-table-column prop="resourceName" label="资源名称" min-width="200"></el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="scope">
            <el-tag size="small" effect="plain" :type="resourceTypeTag(scope.row.resourceType)">
              {{ scope.row.resourceType || '其他' }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 来源列：本站文件和外部链接的处理方式完全不同，先说清楚再给按钮 -->
        <el-table-column label="来源" width="105">
          <template #default="scope">
            <el-tag
              size="small"
              effect="plain"
              :type="isExternalPath(scope.row.resourcePath) ? 'warning' : 'success'"
            >
              {{ isExternalPath(scope.row.resourcePath) ? '外部链接' : '本站文件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploadTime" label="上传时间" width="170">
          <template #default="scope">{{ formatDate(scope.row.uploadTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="scope">
            <el-button
              v-if="!isExternalPath(scope.row.resourcePath)"
              size="small"
              :disabled="!scope.row.resourcePath"
              @click="togglePreview(scope.row)"
            >
              {{ previewRes?.resourceId === scope.row.resourceId ? '收起' : '预览' }}
            </el-button>
            <el-button
              v-if="isExternalPath(scope.row.resourcePath)"
              size="small"
              type="primary"
              @click="openExternal(scope.row)"
            >
              打开链接
            </el-button>
            <template v-else>
              <el-button
                size="small"
                type="primary"
                :disabled="!scope.row.resourcePath"
                @click="downloadResource(scope.row)"
              >
                下载
              </el-button>
              <!-- 「另存为…」只在支持 File System Access API 的浏览器上出现 -->
              <el-button
                v-if="canPickSavePath"
                size="small"
                :disabled="!scope.row.resourcePath"
                @click="downloadResourceAs(scope.row)"
              >
                另存为…
              </el-button>
            </template>
            <el-button v-if="role === 'teacher'" size="small" type="danger" @click="deleteResource(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="showAddResourceDialog" title="上传资源">
      <el-form :model="newResource">
        <el-form-item label="资源名称"><el-input v-model="newResource.resourceName"></el-input></el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="newResource.resourceType" placeholder="请选择资源类型">
            <el-option label="视频 (Video)" value="Video"></el-option>
            <el-option label="课件 (PDF)" value="PDF"></el-option>
            <el-option label="图片 (Image)" value="Image"></el-option>
            <el-option label="其他 (Other)" value="Other"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="上传文件">
          <el-upload
            class="upload-demo"
            action="/api/file/upload"
            :headers="uploadHeaders"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :limit="1"
            ref="uploadRef"
          >
            <el-button type="primary">点击上传</el-button>
            <template #tip>
              <div class="el-upload__tip">支持图片、PDF、视频等文件</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddResourceDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddResource">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showStudentsDialog" title="课程学员列表">
      <el-table :data="courseStudents" style="width: 100%">
        <el-table-column prop="studentAccount" label="学员账号"></el-table-column>
        <el-table-column prop="studentName" label="学员姓名"></el-table-column>
        <el-table-column prop="studentPhone" label="联系电话"></el-table-column>
      </el-table>
    </el-dialog>

    <!-- 教师的审批申请进度：删课程 / 驳回报名都提交到这里，管理员给了结果教师自己能看到 -->
    <el-dialog v-model="showRequestsDialog" title="我的审批申请" width="900px">
      <el-table :data="myRequests" v-loading="requestLoading" empty-text="还没有提交过审批申请">
        <el-table-column prop="requestTypeText" label="类型" width="95" />
        <el-table-column prop="targetDesc" label="申请对象" min-width="150" show-overflow-tooltip />
        <el-table-column prop="reason" label="申请理由" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="95">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : (scope.row.status === 2 ? 'danger' : 'info')">
              {{ scope.row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批意见" min-width="180" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.auditComment || '—' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="165">
          <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, reactive, computed } from 'vue'
import { useRoute } from 'vue-router'
import request from '../utils/request'
import { getRole, getToken, getUser } from '../utils/auth'
import { ElMessage, ElMessageBox } from 'element-plus'

const courses = ref([])
const courseTotal = ref(0)
const courseLoading = ref(false)
/** 课程列表的分页与搜索条件（服务端分页，见 fetchCourses） */
const courseQuery = reactive({ pageNum: 1, pageSize: 10, courseName: '', auditStatus: null })
const enrolledCourses = ref([])
const applies = ref([])
/**
 * 报名审核的筛选与分页。
 *
 * <p>数据是一次性全量取回的（教师自己那几门课的报名总量有限），
 * 所以筛选和分页都在浏览器里做，不必再加服务端分页接口。
 * {@code status} 默认 0 = 待审核 —— 「待办优先」：进页面先看到需要处理的。
 */
const applyQuery = reactive({ status: 0, courseId: null, studentName: '', pageNum: 1, pageSize: 10 })
/** 教师自己发布的课程，供「报名审核」的课程筛选下拉使用 */
const myCourses = ref([])
const resources = ref([])
const role = ref(getRole())
const user = ref(getUser())
const activeTab = ref(role.value === 'teacher' ? 'my' : 'all')

const route = useRoute()

/** 本页允许的页签名（用于校验 URL 上的 ?tab= 参数） */
const TAB_NAMES = ['all', 'enrolled', 'my', 'apply']

/**
 * 支持从首页数字卡片带着 ?tab=xxx 直接定位到指定页签，例如：
 *   /courses?tab=enrolled  学员「我已报名的课程」
 *   /courses?tab=apply     教师「报名审核」
 *
 * 只接受白名单内的值 —— URL 是用户随手就能改的，
 * 不能让它把页面切到一个不存在的页签上。
 */
const applyQueryTab = () => {
  const tab = route.query.tab
  if (typeof tab === 'string' && TAB_NAMES.includes(tab)) {
    activeTab.value = tab
  }
}

const showAddDialog = ref(false)
const showResourceDialog = ref(false)

/** 弹窗关掉时释放预览占用的 objectURL，别把 Blob 留在内存里 */
watch(showResourceDialog, (open) => {
  if (!open) {
    closePreview()
  }
})
const showAddResourceDialog = ref(false)
const showStudentsDialog = ref(false)
const courseStudents = ref([])
const currentCourse = ref(null)
const uploadRef = ref(null)
const newCourse = ref({
  courseName: '',
  trainCycle: '',
  courseIntro: ''
})
const newResource = ref({
  resourceName: '',
  resourceType: '',
  resourcePath: ''
})

/**
 * el-upload 用的是它内置的 XMLHttpRequest，**不经过** request.js 拦截器，
 * 所以这里拿到的是后端返回的原始 JSON { code, message, data }，要自己判断。
 * 同理 headers 必须显式补上 token —— 否则上传请求会被鉴权拦成 401。
 */
const uploadHeaders = {
  Authorization: `Bearer ${getToken()}`
}

const handleUploadSuccess = (res) => {
  if (res.code === 200 && res.data) {
    newResource.value.resourcePath = res.data.url
    ElMessage.success('文件上传成功')
  } else {
    ElMessage.error(res.message || '文件上传失败')
  }
}

const handleUploadError = () => {
  ElMessage.error('文件上传失败')
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString()
}

const fetchCourses = async () => {
  courseLoading.value = true
  try {
    const params = { pageNum: courseQuery.pageNum, pageSize: courseQuery.pageSize }
    // 搜索用 courseNameLike：QueryUtil 约定「条件名以 Like 结尾」即模糊匹配
    if (courseQuery.courseName) params.courseNameLike = courseQuery.courseName.trim()

    if (role.value === 'teacher') {
      params.publishTeacherId = user.value.teacherId
      if (courseQuery.auditStatus !== null && courseQuery.auditStatus !== '') {
        params.auditStatus = courseQuery.auditStatus
      }
    } else {
      // 学员只能看到审核通过的课程
      params.auditStatus = 1
    }

    const res = await request.post('/course/page', params)
    courses.value = res?.records || []
    courseTotal.value = res?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    courseLoading.value = false
  }
}

/** 条件变化后从第一页重新查 */
const searchCourses = () => {
  courseQuery.pageNum = 1
  fetchCourses()
}

/**
 * 注意：分页器的 @size-change 也接到 searchCourses（而不是直接 fetchCourses）。
 * 因为改了每页条数后总页数会变，如果还停在原来的页码上就会请求到空数据：
 * 例如原本在第 5 页（每页 5 条），改成每页 50 条后总页数只剩 1 页，
 * 却仍请求第 5 页 → 表格显示"暂无数据"，用户会以为数据丢了。
 */

const resetCourseSearch = () => {
  courseQuery.courseName = ''
  courseQuery.auditStatus = null
  courseQuery.pageNum = 1
  fetchCourses()
}

const fetchEnrolledCourses = async () => {
  try {
    // 查该学员的全部报名（不过滤状态）：待审核、已驳回的也要能看到进度与驳回原因，
    // 否则学员报名后不知道卡在哪一步
    const appliesRes = await request.post('/course-apply/query', { studentId: user.value.studentId })
    if (!appliesRes || appliesRes.length === 0) {
      enrolledCourses.value = []
      return
    }
    const allCourses = await request.post('/course/query', { auditStatus: 1 })
    const courseMap = new Map(allCourses.map(c => [c.courseId, c]))
    // 把报名状态 / 审核意见合并到课程行上，供表格展示
    enrolledCourses.value = appliesRes
      .filter(a => courseMap.has(a.courseId))
      .map(a => ({
        ...courseMap.get(a.courseId),
        applyStatus: a.auditStatus,
        applyRemark: a.auditRemark,
        applyTime: a.applyTime,
        // 教师已提交驳回申请、正等管理员审批 —— 学员也该看到进度，否则一直显示「待审核」
        rejectPending: a.rejectPending
      }))
  } catch (e) {
    console.error(e)
  }
}

/**
 * 报名状态的展示文案与配色。
 *
 * <p>「驳回审批中」是一个<b>派生状态</b>：报名本身仍然是「待审核」
 * （audit_status = 0），只是教师已经提交了驳回申请、还等管理员签字。
 * 如果只显示「待审核」，教师会以为申请没提交成功而反复点驳回，
 * 学员也会以为自己被晾着 —— 所以两个端都把它显式显示出来。
 */
const auditStatusText = (row) => {
  if (row.rejectPending) return '驳回审批中'
  if (row.auditStatus === 1) return '已通过'
  if (row.auditStatus === 2) return '已驳回'
  return '待审核'
}

const auditStatusTagType = (row) => {
  if (row.rejectPending) return 'warning'
  if (row.auditStatus === 1) return 'success'
  if (row.auditStatus === 2) return 'danger'
  return 'info'
}

/** 学员端「我已报名的课程」用的是合并后的 applyStatus 字段 */
const enrollStatusText = (row) => {
  if (row.rejectPending) return '驳回审批中'
  if (row.applyStatus === 1) return '已通过'
  if (row.applyStatus === 2) return '已驳回'
  return '待审核'
}

const enrollStatusTagType = (row) => {
  if (row.rejectPending) return 'warning'
  if (row.applyStatus === 1) return 'success'
  if (row.applyStatus === 2) return 'danger'
  return 'warning'
}

const fetchApplies = async () => {
  try {
    const res = await request.get('/course-apply/list')
    const ownCourses = await request.post('/course/query', { publishTeacherId: user.value.teacherId })
    // 存到 ref 上：以前它只是个局部变量，所以「报名审核」的课程筛选下拉拿不到课程列表
    myCourses.value = ownCourses || []
    const myCourseIds = myCourses.value.map(c => c.courseId)
    const filteredApplies = (res || []).filter(apply => myCourseIds.includes(apply.courseId))

    const allStudents = await request.get('/student/list')
    const studentMap = new Map((allStudents || []).map(s => [s.studentId, s]))
    const courseMap = new Map(myCourses.value.map(c => [c.courseId, c]))

    applies.value = filteredApplies.map(apply => {
      const student = studentMap.get(apply.studentId)
      const course = courseMap.get(apply.courseId)
      return {
        ...apply,
        studentName: student ? student.studentName : '未知学员',
        courseName: course ? course.courseName : '未知课程'
      }
    })
  } catch (e) {
    console.error(e)
  }
}

/**
 * 审核状态统计卡。
 *
 * <p>第一张「全部」卡（status = null）是显式的退出筛选出口：
 * 以前只能靠"再点一次同一张卡"取消筛选 —— 这个交互太隐蔽，几乎没人能发现，
 * 点完「已通过」就再也回不去看全部了。
 * 放在最前面，总数一目了然，点它 = 清掉状态筛选（课程 / 姓名条件保留）。
 */
const applyStats = computed(() => {
  const countOf = (status) => applies.value.filter(a => a.auditStatus === status).length
  return [
    { status: null, label: '全部', count: applies.value.length, color: '#409eff' },
    { status: 0, label: '待审核', count: countOf(0), color: '#e6a23c' },
    { status: 1, label: '已通过', count: countOf(1), color: '#67c23a' },
    { status: 2, label: '已驳回', count: countOf(2), color: '#f56c6c' }
  ]
})

/** 应用「状态 + 课程 + 姓名」三个条件后的报名列表 */
const filteredApplies = computed(() =>
  applies.value.filter(apply => {
    if (applyQuery.status !== null && apply.auditStatus !== applyQuery.status) return false
    if (applyQuery.courseId && apply.courseId !== applyQuery.courseId) return false
    const keyword = applyQuery.studentName.trim()
    if (keyword && !(apply.studentName || '').includes(keyword)) return false
    return true
  })
)

/** 前端分页：从筛完的结果里切当前页，避免把上千行一次性塞进 DOM */
const pagedApplies = computed(() => {
  const start = (applyQuery.pageNum - 1) * applyQuery.pageSize
  return filteredApplies.value.slice(start, start + applyQuery.pageSize)
})

/**
 * 页码越界自动拉回。
 *
 * <p>两种场景都会让当前页码"悬空"：
 *   · 把每页条数从 10 调到 50，第 3 页的内容已经被第 1 页装下了；
 *   · 在「待审核」里把最后一条点了通过，列表变空了但页码还停在原来那页。
 * 两种情况的共同表现都是"表格突然空了"，像出了 bug，其实只是页码超界。
 */
watch(filteredApplies, (list) => {
  const maxPage = Math.max(1, Math.ceil(list.length / applyQuery.pageSize))
  if (applyQuery.pageNum > maxPage) {
    applyQuery.pageNum = maxPage
  }
})

/**
 * 点统计卡切换筛选。
 *
 * <p>「全部」卡（null）点了就是清除状态筛选，不再做二次点击的反选 ——
 * 现在有显式的「全部」出口，反选这个隐藏交互就不需要了；
 * 但保留它也不会出错，于是只对非空状态做切换（点已选中的状态卡 = 取消该筛选）。
 */
const pickApplyStatus = (status) => {
  applyQuery.status = status
  applyQuery.pageNum = 1
}

/** 条件变了必须回到第 1 页，否则会停在一个筛完不存在的页码上 */
const searchApplies = () => {
  applyQuery.pageNum = 1
}

const resetApplySearch = () => {
  applyQuery.status = 0
  applyQuery.courseId = null
  applyQuery.studentName = ''
  applyQuery.pageNum = 1
}

const handleTabChange = () => {
  if (activeTab.value === 'apply') {
    fetchApplies()
  } else if (activeTab.value === 'enrolled') {
    fetchEnrolledCourses()
  } else {
    fetchCourses()
  }
}

const auditApply = async (apply, status) => {
  try {
    // 只处理「通过(1)」与「撤回驳回(0)」。
    // 驳回(2) 走 applyRejectApply 提交审批申请 —— 服务端也会拒绝教师的直接驳回请求。
    await request.post('/course-apply/audit', {
      applyId: apply.applyId,
      auditStatus: status,
      auditRemark: status === 0 ? '恢复为待审核，重新排队' : null,
    })
    ElMessage.success(status === 1 ? '报名已通过' : '已撤回驳回，报名恢复为待审核')
    fetchApplies()
  } catch (e) {
    // 例如「课程名额已满，无法恢复该报名」
    ElMessage.error(e?.message || '审核失败')
  }
}

const handleAddCourse = async () => {
  if (!newCourse.value.courseName?.trim() || !newCourse.value.trainCycle?.trim()) {
    ElMessage.warning('课程名称和培训周期不能为空')
    return
  }
  newCourse.value.publishTeacherId = user.value.teacherId
  newCourse.value.publishTime = new Date()
  newCourse.value.auditStatus = 0 // 待审核
  await request.post('/course/save', newCourse.value)
  ElMessage.success('发布成功，等待管理员审核')
  showAddDialog.value = false
  newCourse.value = {
    courseName: '',
    trainCycle: '',
    courseIntro: ''
  }
  fetchCourses()
}

const applyCourse = async (course) => {
  try {
    // 报名改由服务端「原子占位」接口处理：
    //   服务端在一条 UPDATE 里同时完成「名额是否已满」的判断与占用，
    //   并用唯一索引兜底重复报名；名额满 / 已报名会以业务错误返回。
    // 前端不再做「先查是否已报名、再插入」的预检查 ——
    // 那种检查在并发下无效（两个请求会同时通过检查），而且多一次网络往返。
    await request.post('/course-apply/apply', { courseId: course.courseId })
    ElMessage.success('报名申请已提交，等待教师审核')
    fetchCourses()
  } catch (e) {
    // 服务端业务提示（如「课程名额已满（名额上限 5 人）」）优先展示
    ElMessage.error(e?.message || '报名失败')
  }
}

/**
 * 教师申请删除课程。
 *
 * <p>「删除课程」会级联清掉资源、报名、成绩、作业与全部提交记录，不可恢复，
 * 所以教师端只提交<b>申请</b>：服务端会拒绝对 /course/{id} 的教师删除请求，
 * 真正执行发生在管理员于「系统管理 → 审批中心」点同意的那一刻。
 * 理由必填 —— 管理员得知道为什么要删。
 */
const applyDeleteCourse = async (course) => {
  try {
    const { value } = await ElMessageBox.prompt(
      '删除课程会连带删除课程资源、报名记录、成绩以及全部作业提交，且不可恢复。\n请填写申请理由，管理员同意后才会执行删除。',
      '申请删除课程',
      {
        confirmButtonText: '提交申请',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：课程内容已并入另一门课 / 课程结课不再开设',
        inputValidator: (v) => (v && v.trim() ? true : '申请理由不能为空'),
        type: 'warning',
      }
    )
    await request.post('/approval-request/submit', {
      requestType: 'COURSE_DELETE',
      targetId: course.courseId,
      reason: value.trim(),
    })
    ElMessage.success('删除申请已提交，等待管理员审批')
    fetchMyRequests()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '提交申请失败')
  }
}

/**
 * 教师申请驳回报名。
 *
 * <p>驳回直接决定学员能不能上这门课，所以同样要先申请。
 * 这里填的理由有两个去处：管理员据此判断是否同意；
 * 同意后它会作为驳回原因写入报名记录，学员端能看到。
 */
const applyRejectApply = async (apply) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `驳回 ${apply.studentName || '该学员'} 的报名需要管理员审批。\n请填写驳回理由，理由会同步展示给学员。`,
      '申请驳回报名',
      {
        confirmButtonText: '提交申请',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：不符合报名条件 / 未提交前置作业',
        inputValidator: (v) => (v && v.trim() ? true : '驳回理由不能为空'),
        type: 'warning',
      }
    )
    await request.post('/approval-request/submit', {
      requestType: 'APPLY_REJECT',
      targetId: apply.applyId,
      reason: value.trim(),
    })
    ElMessage.success('驳回申请已提交，等待管理员审批')
    // 刷新报名列表：这一行的状态要变成「驳回审批中」，驳回按钮也要收起来
    fetchApplies()
    fetchMyRequests()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '提交申请失败')
  }
}

/* ------------------------- 我的审批申请 ------------------------- */
const showRequestsDialog = ref(false)
const requestLoading = ref(false)
const myRequests = ref([])
/** 待审批条数，直接显示在按钮上，省得教师反复点开看 */
const pendingRequestCount = ref(0)

const fetchMyRequests = async () => {
  // 只在教师端有意义（管理员在系统管理里看全部申请）
  if (role.value !== 'teacher') return
  try {
    const res = await request.get('/approval-request/list')
    myRequests.value = res || []
    pendingRequestCount.value = myRequests.value.filter((r) => r.status === 0).length
  } catch (e) {
    console.error(e)
  }
}

const openMyRequests = async () => {
  showRequestsDialog.value = true
  requestLoading.value = true
  try {
    await fetchMyRequests()
  } finally {
    requestLoading.value = false
  }
}

const viewResources = async (course) => {
  currentCourse.value = course
  try {
    const res = await request.post('/course-resource/query', { courseId: course.courseId })
    resources.value = res
    showResourceDialog.value = true
  } catch (e) {
    console.error(e)
  }
}

const handleAddResource = async () => {
  if (!newResource.value.resourceName?.trim() || !newResource.value.resourceType) {
    ElMessage.warning('资源名称和资源类型不能为空')
    return
  }
  if (!newResource.value.resourcePath) {
    ElMessage.warning('请先上传文件')
    return
  }
  newResource.value.courseId = currentCourse.value.courseId
  newResource.value.uploadTeacherId = user.value.teacherId
  newResource.value.uploadTime = new Date()
  try {
    await request.post('/course-resource/save', newResource.value)
    ElMessage.success('保存成功')
    showAddResourceDialog.value = false
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
    const res = await request.post('/course-resource/query', { courseId: currentCourse.value.courseId })
    resources.value = res
    newResource.value = { resourceName: '', resourceType: '', resourcePath: '' }
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

/**
 * 是否是「外部链接」。
 *
 * <p>判断依据是路径本身而不是 resource_type —— 那个字段存的是**格式**
 * （PDF / Video / Image），跟文件放在哪没有关系。本站上传的文件后端统一
 * 返回 {@code /uploads/xxx}，其余一律按外链处理。
 */
const isExternalPath = (path) => /^https?:\/\//i.test(String(path || ''))

/** 资源类型标签配色：纯区分用，没有业务含义 */
const RES_TYPE_TAG_TYPES = { PDF: 'danger', Video: 'warning', Image: 'success' }
const resourceTypeTag = (type) => RES_TYPE_TAG_TYPES[type] || 'info'

/** 弹窗标题带上课程名：教师可能同时开好几个弹窗，不写清楚容易看串 */
const resourceDialogTitle = computed(() =>
  currentCourse.value ? `课程资源 · ${currentCourse.value.courseName}` : '课程资源'
)

/**
 * 预览区状态。
 *
 * <p>预览内容走的是**鉴权下载接口**拿到的 Blob，再用 createObjectURL 生成临时地址
 * —— 而不是直接把 /uploads/xxx 塞进 iframe。后者在 SecurityConfig 里是 permitAll，
 * 等于预览也绕过了"是否选了这门课"的检查。
 */
const previewRes = ref(null)
const previewUrl = ref('')
const previewLoading = ref(false)
/** pdf / image / video / other —— 决定预览区用哪个标签渲染 */
const previewKind = ref('other')

/** 按扩展名（优先）或资源类型判断文件形态，决定内嵌渲染方式 */
const fileKind = (resource) => {
  const path = String(resource?.resourcePath || '').toLowerCase()
  const type = String(resource?.resourceType || '').toLowerCase()
  if (/\.(png|jpe?g|gif|webp|bmp|svg)$/.test(path) || type === 'image') return 'image'
  if (/\.(mp4|webm|ogg|mov|m4v)$/.test(path) || type === 'video') return 'video'
  if (/\.pdf$/.test(path) || type === 'pdf') return 'pdf'
  return 'other'
}

/** 收起预览：必须释放 objectURL，否则 Blob 会一直占着内存直到刷新页面 */
const closePreview = () => {
  if (previewUrl.value) {
    window.URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  previewRes.value = null
  previewKind.value = 'other'
}

/** 点「预览」展开、再点一次收起 */
const togglePreview = async (resource) => {
  if (previewRes.value?.resourceId === resource.resourceId) {
    closePreview()
    return
  }
  closePreview()

  const kind = fileKind(resource)
  previewRes.value = resource
  previewKind.value = kind
  // 外链不内嵌（对方站点通常禁止被 iframe 嵌套），不支持的格式也不用白跑一次请求
  if (isExternalPath(resource.resourcePath) || kind === 'other') {
    return
  }

  previewLoading.value = true
  try {
    // inline=true：让服务端用真实 MIME（application/pdf、image/png…）+ inline 返回。
    // 拿到的 Blob 类型决定了 iframe 能不能渲染 —— 类型是 octet-stream 的话
    // 浏览器只会把它当"要下载的东西"，预览区就是一片空白 + 多一条下载记录。
    const blob = await fetchResourceBlob(resource, true)
    if (blob) {
      previewUrl.value = window.URL.createObjectURL(withPreviewMime(blob, resource))
    } else {
      previewRes.value = null
    }
  } finally {
    previewLoading.value = false
  }
}

/** 按扩展名推断预览用的 MIME；认不出返回空串 */
const mimeForPreview = (resource) => {
  const path = String(resource?.resourcePath || '').toLowerCase()
  if (path.endsWith('.pdf')) return 'application/pdf'
  if (path.endsWith('.png')) return 'image/png'
  if (/\.jpe?g$/.test(path)) return 'image/jpeg'
  if (path.endsWith('.gif')) return 'image/gif'
  if (path.endsWith('.webp')) return 'image/webp'
  if (path.endsWith('.bmp')) return 'image/bmp'
  if (path.endsWith('.mp4')) return 'video/mp4'
  if (path.endsWith('.webm')) return 'video/webm'
  return ''
}

/**
 * 兜底修正 Blob 的 MIME 类型。
 *
 * <p>正常情况下服务端已经给了正确的 Content-Type；但只要有任意一层（反向代理、
 * 开发服务器）改写了响应头，octet-stream 又会回来，预览就再次失灵。
 * 这里按扩展名再包一层，成本几乎为零，换来的是预览不再依赖传输链路。
 */
const withPreviewMime = (blob, resource) => {
  // 服务端给了可信类型（非 octet-stream）就不动它
  if (blob.type && blob.type !== 'application/octet-stream') return blob
  const mime = mimeForPreview(resource)
  return mime ? new Blob([blob], { type: mime }) : blob
}

const openExternal = (resource) => {
  window.open(resource.resourcePath, '_blank', 'noopener')
}

/**
 * 下载资源。
 *
 * <p>本站文件**必须走服务端的鉴权下载接口**，不能让浏览器直接打开
 * {@code /uploads/xxx}：那条路径在 SecurityConfig 里是 permitAll 的
 * （浏览器用 img / a 标签访问时不带 Authorization 头，只能放行），
 * 结果是「谁知道文件名，谁就能下载」。列表里那句「审核通过后可下载」
 * 只是把按钮藏起来，属于 UI 遮蔽，不是权限控制。
 * 走接口之后，服务端会用 JWT 里的身份复核「你是否真的选了这门课」。
 *
 * <p>外部链接没法由本站代理下载，只能交给浏览器新开标签页打开
 * —— 这也解释了为什么原来外链点「直接下载」会跳到一个陌生页面：
 * HTML5 的 download 属性对跨域地址是无效的，浏览器只能"导航过去"。
 */
/**
 * 取资源的二进制内容。
 *
 * <p>抽出来是因为「下载」「另存为」「预览」三处都要用同一段逻辑，
 * 尤其是这个坑必须只写一遍：后端业务失败（无权限 / 文件不存在）时
 * 返回的仍是 HTTP 200 + JSON（项目的统一约定），必须识别出来，
 * 否则会把一段错误 JSON 原样当成文件存到本地。
 *
 * @param inline true 表示"这是预览"：请求头会带上 inline=1，
 *               服务端改用真实的 MIME 类型 + Content-Disposition: inline 返回。
 *               不传这个参数，浏览器拿到的是 octet-stream，iframe 渲染不了，
 *               表现就是"点预览却开始下载"。
 * @return 成功返回 Blob；失败返回 null（提示已在内部弹出）
 */
const fetchResourceBlob = async (resource, inline = false) => {
  try {
    const blob = await request.get(`/course-resource/${resource.resourceId}/download`, {
      responseType: 'blob',
      params: inline ? { inline: true } : undefined
    })
    if (blob?.type && blob.type.includes('json')) {
      const text = await blob.text()
      let message = '下载失败'
      try {
        message = JSON.parse(text).message || message
      } catch (e) {
        // 解析不了就用默认文案
      }
      ElMessage.error(message)
      return null
    }
    return blob
  } catch (e) {
    // 拦截器已提示
    return null
  }
}

const downloadResource = async (resource) => {
  if (!resource?.resourcePath) {
    ElMessage.warning('该资源没有文件')
    return
  }
  if (isExternalPath(resource.resourcePath)) {
    openExternal(resource)
    return
  }
  const blob = await fetchResourceBlob(resource)
  if (blob) {
    saveBlob(blob, buildFileName(resource))
  }
}

/**
 * 「另存为…」：弹出系统保存对话框，让用户自己选目录和文件名。
 *
 * <p>用的是 File System Access API 的 showSaveFilePicker。
 * 这里必须说明一件事：**普通网页不能指定下载路径**，这是浏览器的安全沙箱
 * 决定的（否则恶意站点可以往你硬盘任意位置写文件）。百度网盘能选路径，
 * 是因为它装了本地客户端，不是纯网页做到的。
 * showSaveFilePicker 是浏览器主动把选择权交给用户，所以才被允许。
 *
 * <p>支持范围：Chrome / Edge 86+。Firefox、Safari、手机浏览器不支持，
 * 因此按钮在不支持的浏览器上直接不显示（渐进增强，而不是报错）。
 */
const canPickSavePath = typeof window !== 'undefined' && 'showSaveFilePicker' in window

const downloadResourceAs = async (resource) => {
  if (!resource?.resourcePath) {
    ElMessage.warning('该资源没有文件')
    return
  }
  if (isExternalPath(resource.resourcePath)) {
    openExternal(resource)
    return
  }

  const blob = await fetchResourceBlob(resource)
  if (!blob) return

  const fileName = buildFileName(resource)
  const matched = /\.([a-z0-9]+)$/i.exec(fileName)
  const ext = matched ? '.' + matched[1].toLowerCase() : ''
  try {
    const handle = await window.showSaveFilePicker({
      suggestedName: fileName,
      types: ext
        ? [{ description: '资源文件', accept: { 'application/octet-stream': [ext] } }]
        : undefined
    })
    const writable = await handle.createWritable()
    await writable.write(blob)
    await writable.close()
    ElMessage.success('已保存')
  } catch (e) {
    // 用户在系统对话框里点了取消 —— 不是错误，静默返回
    if (e?.name === 'AbortError') {
      return
    }
    // 其他失败（浏览器策略限制等）退回普通下载，保证功能不中断
    saveBlob(blob, fileName)
  }
}

/** 把二进制内容存成文件 */
const saveBlob = (blob, fileName) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/** 下载后的文件名 = 资源名称 + 原始扩展名（名称里已带同名扩展名就不重复叠加） */
const buildFileName = (resource) => {
  const name = (resource.resourceName || '资源').trim()
  const matched = /\.([a-z0-9]+)$/i.exec(resource.resourcePath || '')
  const ext = matched ? matched[1] : ''
  if (!ext || name.toLowerCase().endsWith('.' + ext.toLowerCase())) {
    return name
  }
  return `${name}.${ext}`
}

const deleteResource = async (resource) => {
  try {
    await ElMessageBox.confirm('确定要删除该资源吗？', '提示', { type: 'warning' })
    await request.delete(`/course-resource/${resource.resourceId}`)
    ElMessage.success('删除成功')
    const res = await request.post('/course-resource/query', { courseId: currentCourse.value.courseId })
    resources.value = res
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const viewStudents = async (course) => {
  try {
    const appliesRes = await request.post('/course-apply/query', { courseId: course.courseId, auditStatus: 1 })
    const allStudents = await request.get('/student/list')
    
    const studentIds = appliesRes.map(a => a.studentId)
    courseStudents.value = allStudents.filter(s => studentIds.includes(s.studentId))
    showStudentsDialog.value = true
  } catch (e) {
    console.error(e)
    ElMessage.error('获取学员列表失败')
  }
}

onMounted(() => {
  // 先按 URL 参数定位页签，再拉数据（这样从首页点卡片过来能直接落在对应页签上）
  applyQueryTab()
  fetchCourses()
  if (role.value === 'teacher') {
    fetchApplies()
    // 顺带把「我的审批申请」待办数取回来，按钮上直接显示还有几条在等管理员
    fetchMyRequests()
  } else if (role.value === 'student') {
    fetchEnrolledCourses()
  }
})

watch(activeTab, () => {
  handleTabChange()
})
</script>
