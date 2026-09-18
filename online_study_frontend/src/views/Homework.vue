<template>
  <div>
    <div class="page-head">
      <h2>作业管理</h2>
      <p class="subtitle">{{ role === 'student' ? '提交作业 · 查看成绩' : '发布作业 · 批改评分' }}</p>
    </div>

    <el-tabs v-if="role === 'student'" v-model="studentActiveTab">
      <el-tab-pane label="我的作业" name="homework">

        <!-- ========== 第一级：先选课程 ==========
             作业是挂在课程下面的，所以入口是课程而不是作业列表。
             卡片上直接给出「几个作业 / 我还有几个没交」，不点进去也能看出该先处理哪门课。 -->
        <template v-if="hwView === 'courses'">
          <div class="course-cards" v-loading="courseLoading">
            <div
              v-for="course in courseOptions"
              :key="course.courseId"
              class="course-card"
              :style="{ borderLeft: '3px solid ' + courseColor(course.courseId) }"
              @click="enterCourse(course)"
            >
              <div class="course-card-name">{{ course.courseName }}</div>
              <div class="course-card-meta">
                <span>{{ courseHwTotal(course.courseId) }} 个作业</span>
                <span :style="{ color: courseHwUndone(course.courseId) > 0 ? '#f56c6c' : '' }">
                  未交 {{ courseHwUndone(course.courseId) }}
                </span>
              </div>
            </div>
          </div>
          <el-empty
            v-if="!courseLoading && courseOptions.length === 0"
            description="还没有审核通过的课程，先去「课程资源」报名"
          />
        </template>

        <!-- ========== 第二级：这门课的作业 ========== -->
        <template v-else>
          <!-- 二级页头：返回入口做成按钮（原来是一行 13px 文字链接，太小看不见） -->
          <div class="subpage-head">
            <div class="subpage-back" @click="backToCourses">
              <el-icon><ArrowLeft /></el-icon>
              <span>返回课程列表</span>
            </div>
            <div class="subpage-title">
              <div class="subpage-name">{{ activeCourse?.courseName }}</div>
              <div class="subpage-meta">共 {{ hwTotal }} 项作业 · 可点左侧按钮切换其他课程</div>
            </div>
          </div>

          <div class="filter-bar">
            <el-input
              v-model="hwQuery.homeworkName"
              placeholder="按作业名称搜索"
              clearable
              style="width: 220px"
              @keyup.enter="searchHomework"
              @clear="searchHomework"
            />
            <el-button type="primary" @click="searchHomework">搜索</el-button>
            <el-button @click="resetHomeworkSearch">重置</el-button>
          </div>

          <!-- 状态统计：进来先看到"还有几个没交"，点一下即筛选 -->
          <div class="stat-cards">
            <div
              v-for="item in studentHwStats"
              :key="item.key || 'all'"
              class="stat-card"
              :class="{ 'is-active': hwStatusFilter === item.key }"
              :style="{ '--stat-color': item.color }"
              @click="pickHwStatus(item.key)"
            >
              <div class="stat-card-label">{{ item.label }}</div>
              <div class="stat-card-value">{{ item.count }}</div>
            </div>
          </div>

          <el-table
            :data="homeworkList"
            v-loading="hwLoading"
            style="width: 100%"
            empty-text="这门课程下还没有作业"
          >
            <el-table-column prop="homeworkName" label="作业名称" min-width="180"></el-table-column>
            <el-table-column prop="deadline" label="截止时间" min-width="170">
              <template #default="scope">{{ formatDate(scope.row.deadline) }}</template>
            </el-table-column>
            <!-- 提交状态摆在列表行上：原来必须点开弹窗才知道交没交 -->
            <el-table-column label="提交状态" width="110">
              <template #default="scope">
                <el-tag size="small" :type="submitStatus(scope.row).type">
                  {{ submitStatus(scope.row).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="scope">
                <el-button
                  size="small"
                  :type="submitStatus(scope.row).key === 'none' ? 'primary' : 'default'"
                  @click="openSubmitDialog(scope.row)"
                >
                  {{ submitActionText(scope.row) }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar" v-if="hwTotal > 0">
            <el-pagination
              background
              layout="total, prev, pager, next, sizes"
              :pager-count="5"
              :total="hwTotal"
              :page-sizes="[5, 10, 20, 50]"
              v-model:current-page="hwQuery.pageNum"
              v-model:page-size="hwQuery.pageSize"
              @current-change="fetchHomework"
              @size-change="searchHomework"
            />
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane label="我的成绩" name="score">

        <!-- ========== 第一级：课程卡片 ==========
             与「我的作业」同构：先看课程，点进去才看这门课每次作业的成绩。
             卡片直接给出「已批改 x/y · 平均分」，一眼看出哪门课成绩齐了、哪门还在等批改。 -->
        <template v-if="scoreView === 'courses'">
          <el-alert
            v-if="pendingTipCount > 0"
            class="score-tip"
            type="warning"
            :closable="false"
            show-icon
            :title="`还有 ${pendingTipCount} 次作业等老师批改，未批改的作业不计入平均分`"
          />

          <div class="course-cards" v-loading="courseScoreLoading">
            <div
              v-for="course in courseScoreList"
              :key="course.courseId"
              class="course-card"
              :style="{ borderLeft: '3px solid ' + courseColor(course.courseId) }"
              @click="enterScoreCourse(course)"
            >
              <div class="course-card-name">{{ course.courseName }}</div>
              <div class="course-card-meta">
                <span>已批改 {{ course.gradedCount }} / {{ course.totalCount }}</span>
                <span v-if="course.avgScore !== null && course.avgScore !== undefined">
                  平均 {{ course.avgScore }} 分
                </span>
                <span v-else>暂无成绩</span>
              </div>
              <div class="course-card-meta">
                <span v-if="course.pendingCount > 0" style="color: #e6a23c">
                  待批改 {{ course.pendingCount }}
                </span>
                <span v-if="course.notSubmitCount > 0" style="color: #f56c6c">
                  未交 {{ course.notSubmitCount }}
                </span>
                <span v-if="course.pendingCount === 0 && course.notSubmitCount === 0">
                  全部完成
                </span>
              </div>
            </div>
          </div>
          <el-empty
            v-if="!courseScoreLoading && courseScoreList.length === 0"
            description="还没有审核通过的课程，先去「课程资源」报名"
          />
        </template>

        <!-- ========== 第二级：这门课每次作业的成绩 ========== -->
        <template v-else>
          <div class="subpage-head">
            <div class="subpage-back" @click="backToScoreCourses">
              <el-icon><ArrowLeft /></el-icon>
              <span>返回课程列表</span>
            </div>
            <div class="subpage-title">
              <div class="subpage-name">{{ scoreActiveCourse?.courseName }}</div>
              <div class="subpage-meta">
                已批改 {{ scoreActiveCourse?.gradedCount }} / {{ scoreActiveCourse?.totalCount }} 次 ·
                平均 {{ scoreActiveCourse?.avgScore ?? '—' }} 分
              </div>
            </div>
          </div>

          <el-table
            :data="scoreDetailList"
            v-loading="scoreDetailLoading"
            style="width: 100%"
            empty-text="这门课程下还没有作业"
          >
            <el-table-column prop="homeworkName" label="作业名称" min-width="180"></el-table-column>
            <el-table-column label="截止时间" min-width="160">
              <template #default="scope">{{ formatDate(scope.row.deadline) }}</template>
            </el-table-column>
            <el-table-column label="提交时间" min-width="160">
              <template #default="scope">{{ formatDate(scope.row.submitTime) || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag size="small" :type="scoreStatusTag(scope.row.status)">
                  {{ scope.row.statusText }}
                </el-tag>
              </template>
            </el-table-column>
            <!-- 分数列：未批改显示破折号而不是 0 分 -->
            <el-table-column label="分数" width="90" align="center">
              <template #default="scope">
                <span
                  v-if="scope.row.score !== null && scope.row.score !== undefined"
                  class="score-value"
                  :class="{ 'is-fail': scope.row.score < 60 }"
                >
                  {{ scope.row.score }}
                </span>
                <span v-else class="score-empty">—</span>
              </template>
            </el-table-column>
            <el-table-column label="教师评语" min-width="180">
              <template #default="scope">{{ scope.row.scoreComment || '—' }}</template>
            </el-table-column>
            <el-table-column label="评分教师" width="110">
              <template #default="scope">{{ scope.row.teacherName || '—' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>
    </el-tabs>

    <template v-else>

      <!-- ========== 第一级：我发布的课程（管理员为全部课程）========== -->
      <template v-if="hwView === 'courses'">
        <div class="filter-bar">
          <span style="font-size: 13px; color: var(--app-text-secondary)">
            共 {{ courseOptions.length }} 门课程，点一门进入查看该课程下的作业；发布新作业在课程内操作
          </span>
        </div>
        <div class="course-cards" v-loading="courseLoading">
          <div
            v-for="course in courseOptions"
            :key="course.courseId"
            class="course-card"
            :style="{ borderLeft: '3px solid ' + courseColor(course.courseId) }"
            @click="enterCourse(course)"
          >
            <div class="course-card-name">{{ course.courseName }}</div>
            <div class="course-card-meta">
              <span>{{ courseHwTotal(course.courseId) }} 个作业</span>
            </div>
          </div>
        </div>
        <el-empty
          v-if="!courseLoading && courseOptions.length === 0"
          description="还没有课程，先去「课程资源」发布一门课程"
        />
      </template>

      <!-- ========== 第二级：该课程下的作业 ========== -->
      <template v-else>
        <!-- 二级页头：教师端与学员端用同一套样式，保证两边的返回入口一样醒目 -->
        <div class="subpage-head">
          <div class="subpage-back" @click="backToCourses">
            <el-icon><ArrowLeft /></el-icon>
            <span>返回课程列表</span>
          </div>
          <div class="subpage-title">
            <div class="subpage-name">{{ activeCourse?.courseName }}</div>
            <div class="subpage-meta">共 {{ hwTotal }} 项作业 · 可点左侧按钮切换其他课程</div>
          </div>
        </div>
        <div class="filter-bar">
          <el-input
            v-model="hwQuery.homeworkName"
            placeholder="按作业名称搜索"
            clearable
            style="width: 220px"
            @keyup.enter="searchHomework"
            @clear="searchHomework"
          />
          <el-button type="primary" @click="searchHomework">搜索</el-button>
          <el-button @click="resetHomeworkSearch">重置</el-button>
          <div class="spacer"></div>
          <el-button type="success" @click="openAddDialog(activeCourse)">发布新作业</el-button>
        </div>
        <!-- 进到第二级后课程已固定，「所属课程」列就没有信息量了，去掉让表格更宽 -->
        <el-table
          :data="homeworkList"
          v-loading="hwLoading"
          style="width: 100%"
          empty-text="这门课程下还没有作业"
        >
          <el-table-column prop="homeworkName" label="作业名称" min-width="200"></el-table-column>
          <el-table-column prop="deadline" label="截止时间" min-width="180">
            <template #default="scope">{{ formatDate(scope.row.deadline) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="scope">
              <el-button size="small" type="success" @click="gradeHomework(scope.row)">查看提交并批改</el-button>
              <el-button size="small" type="danger" @click="deleteHomework(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar" v-if="hwTotal > 0">
          <el-pagination
            background
            layout="total, prev, pager, next, sizes"
            :pager-count="5"
            :total="hwTotal"
            :page-sizes="[5, 10, 20, 50]"
            v-model:current-page="hwQuery.pageNum"
            v-model:page-size="hwQuery.pageSize"
            @current-change="fetchHomework"
            @size-change="searchHomework"
          />
        </div>
      </template>
    </template>

    <el-dialog v-model="showAddDialog" title="发布新作业">
      <el-form :model="newHomework">
        <el-form-item label="作业名称"><el-input v-model="newHomework.homeworkName"></el-input></el-form-item>
        <el-form-item label="作业内容"><el-input type="textarea" v-model="newHomework.homeworkContent"></el-input></el-form-item>
        <el-form-item label="所属课程">
          <el-select v-model="newHomework.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option v-for="c in teacherCourses" :key="c.courseId" :label="c.courseName" :value="c.courseId"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddHomework">确定</el-button>
      </template>
    </el-dialog>

    <!--
      文案上刻意把「教师出的题」和「学生的作答」分成两个不同的名字。
      原来两块都叫「作业内容」（下面还带个"（可选）"），同一个词指两样东西，
      学生自然看不懂"可选"到底是什么意思。
    -->
    <el-dialog v-model="showSubmitDialog" :title="hasSubmitted ? '查看作业' : '提交作业'">
      <div v-if="currentHomework" style="margin-bottom: 20px; padding: 15px; background: #f4f4f5; border-radius: 4px;">
        <p style="margin: 0 0 6px; font-size: 12px; color: #909399;">作业要求（教师发布 · 只读）</p>
        <h4 style="margin-top: 0; color: #303133;">{{ currentHomework.homeworkName }}</h4>
        <p style="color: #606266; white-space: pre-wrap; margin-bottom: 0;">{{ currentHomework.homeworkContent || '无详细说明' }}</p>
        <p style="margin: 8px 0 0; font-size: 12px; color: #909399;">
          截止时间：{{ formatDate(currentHomework.deadline) }}
        </p>
      </div>

      <div v-if="hasSubmitted" style="margin-bottom: 20px; padding: 15px; background: #f0f9eb; border-radius: 4px;">
        <p style="color: #67c23a; font-weight: bold; margin-top: 0;"><el-icon><Check /></el-icon> 你已提交过该作业</p>
        <p><strong>我的作答：</strong>{{ submitContent || '（只上传了附件）' }}</p>
        <p v-if="submitFile">
          <strong>附件：</strong>
          <el-button link type="primary" @click="downloadFile(submitFile)">下载附件</el-button>
        </p>
        <el-button type="warning" size="small" @click="handleRecallSubmit">撤回并重新提交</el-button>
      </div>

      <el-form v-else>
        <el-form-item label="我的作答">
          <el-input
            type="textarea"
            v-model="submitContent"
            rows="5"
            placeholder="填写你的答案，也可以只上传附件"
          ></el-input>
          <div style="font-size: 12px; color: #909399; line-height: 1.6;">
            文字和附件至少填一项，两者都填也可以
          </div>
        </el-form-item>
        <el-form-item label="上传照片/附件">
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
              <div class="el-upload__tip">支持上传各类作业照片或文档格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmitDialog = false">取消</el-button>
        <el-button type="primary" v-if="!hasSubmitted" @click="handleSubmitHomework">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGradeDialog" title="批改作业" width="900px" top="5vh">
      <!--
        作业信息常驻顶部：原来批改时看不到题目和截止时间，得退出去翻。
        顺带给出「已批改 X / 共 Y」，教师随时知道还剩多少。
      -->
      <div class="hw-info" v-if="currentHomework">
        <div class="hw-info-head">
          <span class="hw-info-name">{{ currentHomework.homeworkName }}</span>
          <span class="hw-info-meta">
            截止 {{ formatDate(currentHomework.deadline) }} ·
            共 {{ submitList.length }} 份提交 ·
            已批改 {{ gradedCount }} / {{ submitList.length }}
          </span>
        </div>
        <div class="hw-info-content">{{ currentHomework.homeworkContent || '无详细内容' }}</div>
      </div>

      <div class="grade-body">
        <!-- 左：学生提交列表（点一下切换，不用关弹窗重开） -->
        <div class="grade-list">
          <div
            v-for="item in submitList"
            :key="item.submitId"
            class="grade-item"
            :class="{ 'is-active': currentSubmit && currentSubmit.submitId === item.submitId }"
            @click="selectSubmit(item)"
          >
            <div class="grade-item-row">
              <span class="grade-item-name">{{ getStudentName(item.studentId) }}</span>
              <el-tag size="small" :type="item.correctStatus === 1 ? 'success' : 'info'">
                {{ item.correctStatus === 1 ? '已批改' : '待批改' }}
              </el-tag>
            </div>
            <div class="grade-item-row grade-item-sub">
              <span>{{ formatDate(item.submitTime) }}</span>
              <el-tag v-if="isLate(item)" size="small" type="danger">迟交</el-tag>
            </div>
          </div>
          <div v-if="submitList.length === 0" class="grade-empty">暂无学生提交</div>
        </div>

        <!-- 右：内容预览 + 打分表单（一屏完成，不再"下载 → 看 → 弹窗 → 弹窗"） -->
        <div class="grade-panel" v-if="currentSubmit">
          <div class="grade-block">
            <div class="grade-block-title">提交内容</div>
            <div class="grade-text">{{ currentSubmit.submitContent || '（无文字内容）' }}</div>

            <!-- 附件在线预览：图片直接显示、PDF 内嵌，其他格式才需要下载 -->
            <div v-if="currentSubmit.submitFile" class="grade-file">
              <img
                v-if="isImageFile(currentSubmit.submitFile)"
                :src="currentSubmit.submitFile"
                class="grade-image"
                alt="作业附件"
              />
              <iframe
                v-else-if="isPdfFile(currentSubmit.submitFile)"
                :src="currentSubmit.submitFile"
                class="grade-pdf"
              ></iframe>
              <div v-else class="grade-file-tip">该格式无法在线预览</div>
              <!-- 预览之外仍保留下载入口：图片、PDF 有时也需要另存到本地 -->
              <div class="grade-file-actions">
                <el-button link type="primary" @click="downloadFile(currentSubmit.submitFile)">
                  下载附件
                </el-button>
                <span class="grade-file-name">{{ fileNameOf(currentSubmit.submitFile) }}</span>
              </div>
            </div>
          </div>

          <div class="grade-block">
            <div class="grade-block-title">评分</div>
            <div class="grade-form">
              <span class="grade-label">分数</span>
              <el-input-number v-model="gradeForm.score" :min="0" :max="100" :step="1" controls-position="right" />
              <span class="grade-label">评语</span>
              <el-input
                v-model="gradeForm.comment"
                type="textarea"
                :rows="3"
                placeholder="给学生几句反馈（可选）"
              />
              <div class="grade-quick">
                <span class="grade-quick-label">快捷评语</span>
                <el-tag
                  v-for="q in QUICK_COMMENTS"
                  :key="q"
                  class="grade-quick-tag"
                  @click="gradeForm.comment = q"
                >{{ q }}</el-tag>
              </div>
            </div>
          </div>

          <div class="grade-actions">
            <el-button v-if="currentSubmit.correctStatus === 1" @click="goNextPending">下一份待批改</el-button>
            <el-button type="primary" @click="doGrade">保存批改</el-button>
          </div>
        </div>
        <div class="grade-panel grade-empty" v-else>请从左侧选择一份提交</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, reactive } from 'vue'
import { useRoute } from 'vue-router'
import request from '../utils/request'
import { getRole, getToken, getUser } from '../utils/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check } from '@element-plus/icons-vue'

const homeworkList = ref([])
const hwTotal = ref(0)
const hwLoading = ref(false)
/**
 * 作业列表的搜索与分页条件。
 *
 * <p>没有 {@code courseId} —— 课程不再是一个筛选条件，而是导航层级本身：
 * 先在第一级选中课程，第二级看到的作业天然就只有那一门课的。
 */
const hwQuery = reactive({ pageNum: 1, pageSize: 10, homeworkName: '' })

/**
 * 两级导航的层级状态。
 * <pre>
 *   courses = 第一级：课程卡片列表（教师=我发布的，学员=我已报名的）
 *   list    = 第二级：某一门课程下的作业列表
 * </pre>
 *
 * <p>参考成熟学习平台的做法 —— 超星学习通 / 雨课堂 / Moodle / Canvas 里，
 * 作业永远挂在课程下面，不存在"把全站作业堆在一张表里"的主入口。
 */
const hwView = ref('courses')
/** 当前进入的课程：第二级的标题、发布作业的默认课程都由它决定 */
const activeCourse = ref(null)
/** 第一级卡片的统计加载态（与第二级表格的 hwLoading 分开） */
const courseLoading = ref(false)
/**
 * 「我范围内」的全部作业，只查一次。
 * 第一级用它算每门课的作业数，学员端第二级直接复用它（不再重复请求）。
 */
const allHomeworks = ref([])
/**
 * homeworkId → 我的提交记录。
 *
 * <p>原来只有点开某条作业时才查一次状态（openSubmitDialog 里现查），
 * 导致列表上根本看不出交没交、而且点 N 次就发 N 次请求。
 * 现在进页面一次性批量查回，列表上的「提交状态」列直接从它算。
 */
const submitMap = ref({})
/** 学员端「我的作业」的状态筛选：'' = 全部 */
const hwStatusFilter = ref('')

// ==================== 我的成绩（两级） ====================
/**
 * 成绩页也是两级：先看到自己报名的每门课（卡片），点进去才看这门课
 * 每一次作业的成绩明细。
 *
 * 这样才和「我的作业」的结构对称 —— 原来是一张大表把所有课的成绩平铺，
 * 一门课上过 8 次作业时根本对不上哪一次是哪一次。
 */
const scoreView = ref('courses')
const scoreActiveCourse = ref(null)
const courseScoreList = ref([])
const courseScoreLoading = ref(false)
const scoreDetailList = ref([])
const scoreDetailLoading = ref(false)
/** 所有课程里「已交但还没批改」的次数合计，给顶部提示条用 */
const pendingTipCount = computed(() =>
  courseScoreList.value.reduce((sum, c) => sum + (c.pendingCount || 0), 0)
)
const teacherCourses = ref([])
const role = ref(getRole())
const user = ref(getUser())
const studentActiveTab = ref('homework')

const route = useRoute()

/**
 * 支持从首页卡片带着 ?tab=score 直接落到「我的成绩」页签。
 * 该页签只对学员存在（教师端没有 el-tabs），所以非学员时直接忽略。
 */
const applyQueryTab = () => {
  const tab = route.query.tab
  if (typeof tab === 'string' && ['homework', 'score'].includes(tab) && role.value === 'student') {
    studentActiveTab.value = tab
  }
}
const courseMap = ref({})
/**
 * 第一级课程卡片的数据源，同时也是"我有哪些课程"的唯一口径。
 * 教师 = 自己发布的课程；管理员 = 全部课程；学员 = 已通过审核的课程。
 */
const courseOptions = ref([])
/**
 * 学员「已通过审核」的课程 ID。
 * 由 {@link loadCourses} 拉一次存下来，作业列表直接复用 ——
 * 既少一次请求，也保证筛选校验用的是同一份数据。
 */
const myCourseIds = ref([])

const showAddDialog = ref(false)
const newHomework = ref({
  homeworkName: '',
  homeworkContent: '',
  courseId: null
})

/**
 * 打开发布作业弹窗。
 * @param course 可选。从某门课程的第二级进来时把课程预选好，省掉一次选择；
 *               从第一级（课程列表）进来时为 null，由用户在弹窗里选。
 */
const openAddDialog = async (course = null) => {
  if (teacherCourses.value.length === 0) {
    try {
      const res = await request.post('/course/query', { publishTeacherId: user.value.teacherId })
      teacherCourses.value = res
    } catch (e) {
      console.error(e)
    }
  }
  newHomework.value.courseId = course?.courseId || null
  showAddDialog.value = true
}

const deleteHomework = async (hw) => {
  try {
    await ElMessageBox.confirm('确定要删除该作业吗？删除后相关的提交记录也会被清除。', '提示', { type: 'warning' })
    await request.delete(`/homework/${hw.homeworkId}`)
    ElMessage.success('删除成功')
    // 第一级卡片上的「N 个作业」来自 allHomeworks，必须一起刷新才不会留下旧数字
    await loadAllHomeworks()
    fetchHomework()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const showSubmitDialog = ref(false)
const showGradeDialog = ref(false)
const submitList = ref([])
const currentHomework = ref(null)
const submitContent = ref('')
const submitFile = ref('')
const hasSubmitted = ref(false)
const currentSubmitId = ref(null)
const uploadRef = ref(null)

/**
 * el-upload 走的是内置 XMLHttpRequest，**不经过** request.js 拦截器，
 * 拿到的是后端原始响应 { code, message, data }，需自行判断；
 * headers 也必须补上 token，否则上传请求会被鉴权拦成 401。
 */
const uploadHeaders = {
  Authorization: `Bearer ${getToken()}`
}

const handleUploadSuccess = (res) => {
  if (res.code === 200 && res.data) {
    submitFile.value = res.data.url
    ElMessage.success('照片/附件上传成功')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

const handleUploadError = () => {
  ElMessage.error('上传失败')
}

const downloadFile = (url) => {
  if (!url) {
    ElMessage.warning('暂无可下载的附件')
    return
  }

  const link = document.createElement('a')
  link.href = url
  link.download = ''
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

// 原「下载内容」按钮已移除：批改面板支持附件在线预览，不再需要先把文件下载到本地

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString()
}

const getCourseName = (courseId) => {
  return courseMap.value[courseId] || `课程ID: ${courseId}`
}

/**
 * 课程配色：按 courseId 在几种颜色里循环取一个。
 *
 * <p>目的不是好看，是让「这门课」和「那门课」在第一级课程卡片上一眼能分开 ——
 * 一排纯文字的课程名在快速扫视时几乎看不出界限，左侧一道色条能形成视觉锚点。</p>
 *
 * <p>注：改成两级导航后，「所属课程」列在第二级里已经没有信息量（课程是固定的），
 * 所以这个配色从"表格里的标签色"改成了"课程卡片的左侧色条"。</p>
 */
const COURSE_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#909399', '#f56c6c']
const courseColor = (courseId) =>
  COURSE_COLORS[Math.abs(Number(courseId) || 0) % COURSE_COLORS.length]

/**
 * 学员视角的提交状态。
 *
 * <p>判据是 homework_submit.correct_status：0 = 老师还没批，1 = 已批改。
 * 没查到提交记录就是「未提交」—— 这正是原来列表上看不出来的信息。
 */
const submitStatus = (hw) => {
  const submit = submitMap.value[hw.homeworkId]
  if (!submit) return { key: 'none', label: '未提交', type: 'danger' }
  if (submit.correctStatus === 1) return { key: 'graded', label: '已批改', type: 'success' }
  return { key: 'pending', label: '待批改', type: 'warning' }
}

/** 按钮文案跟着状态走：没交是「去提交」，交了是「查看提交」，批完了是「查看批改」 */
const submitActionText = (hw) => {
  const key = submitStatus(hw).key
  if (key === 'none') return '去提交'
  if (key === 'graded') return '查看批改'
  return '查看提交'
}

/** 某门课的作业总数（第一级课程卡片） */
const courseHwTotal = (courseId) =>
  allHomeworks.value.filter((hw) => hw.courseId === courseId).length

/** 某门课里"我还没交"的作业数（学员专用，教师端不显示） */
const courseHwUndone = (courseId) =>
  allHomeworks.value.filter((hw) => hw.courseId === courseId && !submitMap.value[hw.homeworkId]).length

/** 第二级状态条的数字：当前课程下我各状态的作业数，点一下即筛选 */
const studentHwStats = computed(() => {
  const list = allHomeworks.value.filter((hw) => hw.courseId === activeCourse.value?.courseId)
  const countOf = (key) => list.filter((hw) => submitStatus(hw).key === key).length
  return [
    { key: '', label: '全部', count: list.length, color: '#409eff' },
    { key: 'none', label: '未提交', count: countOf('none'), color: '#f56c6c' },
    { key: 'pending', label: '待批改', count: countOf('pending'), color: '#e6a23c' },
    { key: 'graded', label: '已批改', count: countOf('graded'), color: '#67c23a' }
  ]
})

/**
 * 加载「所属课程」筛选下拉的选项，并顺手把 courseId → 课程名 的映射补全
 * （表格里的课程列靠它把 ID 换成名字）。
 *
 * <p>两种角色取数口径不同，必须和 {@link fetchHomework} 的过滤条件保持一致，
 * 否则会出现"下拉里有这门课、但选中后一条作业都查不到"的怪现象：
 * <pre>
 *   教师 / 管理员：自己发布的课程（管理员为全部）
 *   学员：已通过审核的课程 —— 审核没过的课本来也不会给他布置作业
 * </pre>
 */
const loadCourses = async () => {
  try {
    if (role.value === 'student') {
      const applies = await request.post('/course-apply/query', {
        studentId: user.value.studentId,
        auditStatus: 1
      })
      const ids = (applies || []).map((apply) => apply.courseId)
      myCourseIds.value = ids
      if (ids.length === 0) {
        courseOptions.value = []
        return
      }
      const all = await request.get('/course/list')
      const mine = (all || []).filter((course) => ids.includes(course.courseId))
      courseOptions.value = mine.map((course) => ({
        courseId: course.courseId,
        courseName: course.courseName
      }))
      mine.forEach((course) => {
        courseMap.value[course.courseId] = course.courseName
      })
      return
    }

    // 管理员不传条件 = 查全部课程，与 fetchHomework 的"不过滤"口径一致
    const params = role.value === 'admin' ? {} : { publishTeacherId: user.value.teacherId }
    const mine = await request.post('/course/query', params)
    courseOptions.value = (mine || []).map((course) => ({
      courseId: course.courseId,
      courseName: course.courseName
    }))
    courseOptions.value.forEach((course) => {
      courseMap.value[course.courseId] = course.courseName
    })
  } catch (e) {
    console.error(e)
  }
}

/**
 * 一次性把「我的全部提交记录」查回来，建成 homeworkId → 记录 的映射。
 *
 * <p>进页面查一次，替掉原来"点开某条作业才查一次"的 N+1 请求。
 * 学员的提交记录条数 = 已交作业数，量级很小，可以一次拿全。
 */
const loadMySubmissions = async () => {
  if (role.value !== 'student') {
    return
  }
  try {
    const res = await request.post('/homework-submit/query', { studentId: user.value.studentId })
    const map = {}
    ;(res || []).forEach((item) => {
      map[item.homeworkId] = item
    })
    submitMap.value = map
  } catch (e) {
    console.error(e)
  }
}

/**
 * 拉回「我范围内」的全部作业，供第一级课程卡片统计。
 *
 * <p>用 /homework/query（不分页）而不是 /homework/page：卡片上要显示
 * "这门课有几个作业、我还有几个没交"，需要的是全量，用分页接口反而要按课程循环请求 N 次。
 * 取数口径必须与 {@link loadCourses} 的课程范围一致，否则会出现"卡片上的作业数对不上"。
 */
const loadAllHomeworks = async () => {
  courseLoading.value = true
  try {
    const params = {}
    if (role.value === 'teacher') {
      params.publishTeacherId = user.value.teacherId
    } else if (role.value === 'student') {
      if (myCourseIds.value.length === 0) {
        allHomeworks.value = []
        return
      }
      params.courseIdIn = myCourseIds.value
    }
    const res = await request.post('/homework/query', params)
    allHomeworks.value = res || []
  } catch (e) {
    console.error(e)
  } finally {
    courseLoading.value = false
  }
}

/**
 * 成绩第一级：我报名通过的课程，每门课带「已批改 x/y · 平均分 · 待批改数」。
 *
 * 统计全部在后端算（一次请求固定 5 次查询），前端不做任何聚合 ——
 * 口径放在前端的话，每多一个展示位置就要复制一遍算法，迟早对不上。
 */
const fetchCourseScores = async () => {
  if (role.value !== 'student') {
    return
  }

  courseScoreLoading.value = true
  try {
    courseScoreList.value = (await request.get('/score/my-courses')) || []
  } catch (e) {
    console.error(e)
  } finally {
    courseScoreLoading.value = false
  }
}

/** 进入某门课的成绩明细 */
const enterScoreCourse = (course) => {
  scoreActiveCourse.value = course
  scoreView.value = 'detail'
  fetchScoreDetail(course.courseId)
}

/**
 * 成绩第二级：某门课每次作业的成绩。
 *
 * 后端会把「没提交的作业」也返回，状态标成未提交 —— 学员需要看到这门课
 * 还有几次没交；未批改的返回 pending 且不带分数，不参与平均分。
 */
const fetchScoreDetail = async (courseId) => {
  scoreDetailLoading.value = true
  try {
    scoreDetailList.value = (await request.get(`/score/my-course/${courseId}`)) || []
  } catch (e) {
    console.error(e)
  } finally {
    scoreDetailLoading.value = false
  }
}

/** 从明细返回课程列表，顺带刷新统计（可能刚被批改过） */
const backToScoreCourses = () => {
  scoreView.value = 'courses'
  scoreActiveCourse.value = null
  fetchCourseScores()
}

/** 成绩明细里的状态标签配色 */
const scoreStatusTag = (status) => {
  if (status === 'graded') {
    return 'success'
  }
  if (status === 'pending') {
    return 'warning'
  }
  return 'info'
}

/**
 * 第二级的作业列表。两种角色走了两条不同的取数路径：
 *
 * <pre>
 *   学员：当前课程的作业在浏览器里筛 + 分页。
 *         原因有二 —— ① 一门课的作业量很小（几到十几条）；
 *         ② 「提交状态」是前端拿 submitMap 现算的，交给服务端分页会出现
 *            "筛完剩 3 条、分页器却显示共 10 条"的矛盾。
 *         数据复用第一级已经拉回的 allHomeworks，不再重复请求。
 *   教师 / 管理员：保留服务端分页（作业可能很多，不该全量塞进浏览器）。
 * </pre>
 */
const fetchHomework = async () => {
  if (!activeCourse.value?.courseId) {
    homeworkList.value = []
    hwTotal.value = 0
    return
  }

  hwLoading.value = true
  try {
    if (role.value === 'student') {
      const keyword = hwQuery.homeworkName.trim()
      const rows = allHomeworks.value
        .filter((hw) => hw.courseId === activeCourse.value.courseId)
        .filter((hw) => (keyword ? (hw.homeworkName || '').includes(keyword) : true))
        .filter((hw) => (hwStatusFilter.value ? submitStatus(hw).key === hwStatusFilter.value : true))

      hwTotal.value = rows.length
      const start = (hwQuery.pageNum - 1) * hwQuery.pageSize
      homeworkList.value = rows.slice(start, start + hwQuery.pageSize)
      return
    }

    // 只在「自己看得见的课程」里查。课程 ID 来自前端，必须回到已加载的课程列表核对一次 ——
    // 否则手动把 courseId 改成别人的课，就能翻出别人课程的作业（越权）。
    if (!courseOptions.value.some((course) => course.courseId === activeCourse.value.courseId)) {
      homeworkList.value = []
      hwTotal.value = 0
      return
    }

    const params = {
      pageNum: hwQuery.pageNum,
      pageSize: hwQuery.pageSize,
      courseId: activeCourse.value.courseId
    }
    if (role.value === 'teacher') {
      params.publishTeacherId = user.value.teacherId
    }
    // 搜索用 homeworkNameLike：QueryUtil 约定「条件名以 Like 结尾」即模糊匹配
    if (hwQuery.homeworkName) params.homeworkNameLike = hwQuery.homeworkName.trim()

    const res = await request.post('/homework/page', params)
    homeworkList.value = res?.records || []
    hwTotal.value = res?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    hwLoading.value = false
  }
}

/** 进入第二级：把课程固定下来，原来的课程筛选条件一并清掉 */
const enterCourse = (course) => {
  activeCourse.value = course
  hwView.value = 'list'
  hwQuery.pageNum = 1
  hwQuery.homeworkName = ''
  hwStatusFilter.value = ''
  fetchHomework()
}

/** 回到第一级。不清空数据 —— 重新进入同一门课时不必再等一次请求 */
const backToCourses = () => {
  hwView.value = 'courses'
  activeCourse.value = null
  hwQuery.pageNum = 1
  hwQuery.homeworkName = ''
  hwStatusFilter.value = ''
}

/** 点状态卡切换筛选；再点一次同一张卡 = 取消筛选（看全部） */
const pickHwStatus = (status) => {
  hwStatusFilter.value = hwStatusFilter.value === status ? '' : status
  hwQuery.pageNum = 1
  fetchHomework()
}

/** 条件变化后回到第 1 页重新查 */
const searchHomework = () => {
  hwQuery.pageNum = 1
  fetchHomework()
}

const resetHomeworkSearch = () => {
  hwQuery.homeworkName = ''
  hwStatusFilter.value = ''
  hwQuery.pageNum = 1
  fetchHomework()
}

const handleAddHomework = async () => {
  if (!newHomework.value.homeworkName?.trim()) {
    ElMessage.warning('作业名称不能为空')
    return
  }
  // 课程是必填的：作业必须挂在某门课下面，否则它不会出现在任何一门课的列表里，
  // 学生也就永远看不到这份作业
  if (!newHomework.value.courseId) {
    ElMessage.warning('请选择所属课程')
    return
  }
  newHomework.value.publishTeacherId = user.value.teacherId
  newHomework.value.publishTime = new Date()
  newHomework.value.deadline = new Date(Date.now() + 7 * 24 * 3600 * 1000) // 默认 7 天后截止
  await request.post('/homework/save', newHomework.value)
  ElMessage.success('发布成功')
  showAddDialog.value = false
  newHomework.value = { homeworkName: '', homeworkContent: '', courseId: null }
  await loadAllHomeworks()
  fetchHomework()
}

const openSubmitDialog = async (hw) => {
  currentHomework.value = hw
  submitContent.value = ''
  submitFile.value = ''
  hasSubmitted.value = false
  currentSubmitId.value = null
  
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }

  // 立刻弹出：状态是异步查的，不该让弹窗等请求回来才出现
  showSubmitDialog.value = true

  // 优先用批量查回来的映射（进页面时就拉好了，本地命中 = 零延迟）；没有再发请求兜底
  const cached = submitMap.value[hw.homeworkId]
  if (cached) {
    fillSubmitForm(cached)
    return
  }

  try {
    const res = await request.post('/homework-submit/query', {
      homeworkId: hw.homeworkId,
      studentId: user.value.studentId
    })
    if (res && res.length > 0) {
      fillSubmitForm(res[0])
      submitMap.value = { ...submitMap.value, [hw.homeworkId]: res[0] }
    }
  } catch (e) {
    console.error(e)
  }
}

/** 把一条提交记录回填到弹窗上（已提交态） */
const fillSubmitForm = (record) => {
  hasSubmitted.value = true
  currentSubmitId.value = record.submitId
  submitContent.value = record.submitContent
  submitFile.value = record.submitFile
}

const handleRecallSubmit = async () => {
  try {
    await ElMessageBox.confirm('确定要撤回该次作业提交吗？撤回后需要重新提交。', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await request.delete(`/homework-submit/${currentSubmitId.value}`)
    ElMessage.success('撤回成功，请重新提交')
    hasSubmitted.value = false
    submitContent.value = ''
    submitFile.value = ''
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
    // 同步列表上的「提交状态」列：撤回后要立刻变回「未提交」
    await loadMySubmissions()
    fetchHomework()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('撤回失败')
    }
  }
}

const handleSubmitHomework = async () => {
  if (!submitContent.value && !submitFile.value) {
    ElMessage.warning('内容或附件至少填写一项')
    return
  }
  const data = {
    homeworkId: currentHomework.value.homeworkId,
    studentId: user.value.studentId,
    submitContent: submitContent.value,
    submitFile: submitFile.value,
    submitTime: new Date(),
    correctStatus: 0
  }
  await request.post('/homework-submit/save', data)
  ElMessage.success('作业提交成功')
  showSubmitDialog.value = false
  // 提交状态变了：重新拉一次映射并重算列表，行上的标签立刻从「未提交」变「待批改」
  await loadMySubmissions()
  fetchHomework()
  fetchCourseScores()
}

/* ==================== 批改面板 ==================== */

/** 当前选中的提交记录 */
const currentSubmit = ref(null)
/** 打分表单：分数 + 评语（一次提交，不再两次弹窗） */
const gradeForm = reactive({ score: null, comment: '' })
/** 学员 ID → 姓名，避免批改时只能看到一串 ID */
const studentMap = ref({})

/** 常用评语，点一下填进评语框，省去每次手打 */
const QUICK_COMMENTS = [
  '思路清晰，完成质量高。',
  '基本完成，注意边界情况的处理。',
  '代码可运行，建议补充注释与文档。',
  '问题较多，建议重做后重新提交。'
]

/** 已批改份数（弹窗顶部显示进度） */
const gradedCount = computed(
  () => submitList.value.filter((item) => item.correctStatus === 1).length
)

const getStudentName = (studentId) => studentMap.value[studentId] || `学员 ${studentId}`

/**
 * 是否迟交：提交时间晚于作业截止时间。
 *
 * 放在前端算是因为两个时间都已经在手上了（作业里有 deadline、提交记录里有 submitTime），
 * 不必为此多跑一个接口。
 */
const isLate = (record) => {
  const deadline = currentHomework.value?.deadline
  if (!deadline || !record?.submitTime) return false
  return new Date(record.submitTime) > new Date(deadline)
}

const isImageFile = (url) => /\.(png|jpe?g|gif|webp|bmp)$/i.test(url || '')
const isPdfFile = (url) => /\.pdf$/i.test(url || '')

/** 从附件地址里取文件名，显示在下载按钮旁边 */
const fileNameOf = (url) => (url || '').split('/').pop()

/** 选中一份提交，并把已打过的分数 / 评语回填到表单 */
const selectSubmit = async (record) => {
  currentSubmit.value = record
  gradeForm.score = null
  gradeForm.comment = ''
  try {
    const res = await request.post('/score/query', {
      studentId: record.studentId,
      courseId: currentHomework.value.courseId
    })
    const exist = res && res.length > 0 ? res[0] : null
    if (exist) {
      gradeForm.score = exist.homeworkScore ?? exist.totalScore ?? null
      gradeForm.comment = exist.scoreComment || ''
    }
  } catch (e) {
    console.error(e)
  }
}

/** 跳到下一份未批改的提交，支持连续批改 */
const goNextPending = () => {
  const list = submitList.value
  const index = list.findIndex((item) => item.submitId === currentSubmit.value?.submitId)
  const next = list.slice(index + 1).find((item) => item.correctStatus === 0)
    || list.find((item) => item.correctStatus === 0)
  if (next) {
    selectSubmit(next)
  } else {
    ElMessage.success('这份作业的提交都已批改完成')
  }
}

const gradeHomework = async (hw) => {
  currentHomework.value = hw
  currentSubmit.value = null
  gradeForm.score = null
  gradeForm.comment = ''
  try {
    const [submits, students] = await Promise.all([
      request.post('/homework-submit/query', { homeworkId: hw.homeworkId }),
      // 取学员姓名做映射；失败也不该挡住批改，所以单独兜底
      request.get('/student/list').catch(() => [])
    ])
    submitList.value = submits || []
    studentMap.value = (students || []).reduce((acc, student) => {
      acc[student.studentId] = student.studentName
      return acc
    }, {})
    // 默认选中第一份待批改的，打开弹窗就能直接干活
    const firstPending = submitList.value.find((item) => item.correctStatus === 0)
      || submitList.value[0]
    if (firstPending) {
      await selectSubmit(firstPending)
    }
    showGradeDialog.value = true
  } catch (e) {
    console.error(e)
  }
}

/**
 * 保存批改：分数、评语、提交状态一次写入。
 *
 * 与原实现的区别：原来分两次弹窗（先输分数、再输评语），中途点取消会丢掉已输入的内容；
 * 现在表单集中在一个面板里，点一次「保存批改」即可。
 */
const doGrade = async () => {
  if (!currentSubmit.value) {
    ElMessage.warning('请先从左侧选择一份提交')
    return
  }
  const score = Number(gradeForm.score)
  if (gradeForm.score === null || gradeForm.score === undefined || gradeForm.score === ''
      || !Number.isInteger(score) || score < 0 || score > 100) {
    ElMessage.warning('分数需要是 0~100 的整数')
    return
  }

  try {
    const submitId = currentSubmit.value.submitId
    const hwId = currentHomework.value.homeworkId

    // 批改整体交给服务端 /grade：
    //   ① 分数写进「这一条提交记录」—— 旧代码把分数写进 score 表（学员×课程一行），
    //      同一门课的第 2 次作业批改会把第 1 次的分数覆盖掉；
    //   ② 服务端重算这门课的作业平均分并回写课程总评。
    // 前端不再自己算 totalScore：旧代码是 (作业分 + 0) / 2，
    // 考试分没录入时会把总评算成一半（90 分显示成 45 分）。
    await request.post('/homework-submit/grade', {
      submitId,
      score,
      comment: gradeForm.comment || ''
    })

    ElMessage.success('批改已保存')

    // 刷新列表并保持当前选中，方便接着批下一份
    const res = await request.post('/homework-submit/query', { homeworkId: hwId })
    submitList.value = res
    const refreshed = res.find((item) => item.submitId === submitId)
    if (refreshed) {
      currentSubmit.value = refreshed
    }
    fetchCourseScores()
  } catch (e) {
    console.error(e)
    ElMessage.error('批改失败')
  }
}

onMounted(async () => {
  // 先按 URL 参数定位页签，再拉数据（从首页点卡片过来能直接落在对应页签上）
  applyQueryTab()
  // 课程列表必须最先拿到：它决定了作业的查询范围（学员=已通过的课 / 教师=自己发布的课），
  // 第一级课程卡片的选项、课程名映射也都来自它
  await loadCourses()
  // 提交记录与全部作业互不依赖，并行拉；两者都到位后第一级卡片上的
  // "N 个作业 / 未交 M" 才是准确数字
  await Promise.all([loadMySubmissions(), loadAllHomeworks()])
  // 进页面停在第一级（课程列表），所以这里不查作业明细；
  // 学员的「我的成绩」页签数据也在这一级，挂载时取一次
  if (role.value === 'student') {
    fetchCourseScores()
  }
})
</script>

<style scoped>
/* ==================== 批改面板 ==================== */

/* 顶部作业信息：批改时不必退出去翻题目和截止时间 */
.hw-info {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: #f5f7fa;
  border-radius: 6px;
}

.hw-info-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.hw-info-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.hw-info-meta {
  font-size: 12px;
  color: #909399;
}

.hw-info-content {
  max-height: 76px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
}

/* 左右两栏：左边选人、右边批改，一屏完成 */
.grade-body {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 14px;
  min-height: 420px;
}

.grade-list {
  max-height: 480px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.grade-item {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background 0.2s;
}

.grade-item:last-child {
  border-bottom: none;
}

.grade-item:hover {
  background: #f5f7fa;
}

/* 选中项用左侧色条标记，比整块变色更容易分辨 */
.grade-item.is-active {
  background: #ecf5ff;
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}

.grade-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.grade-item-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.grade-item-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.grade-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.grade-block {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.grade-block-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.grade-text {
  max-height: 120px;
  overflow-y: auto;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
  white-space: pre-wrap;
}

.grade-file {
  margin-top: 10px;
}

/* 预览之外的下载入口：图片 / PDF 有时也需要另存到本地 */
.grade-file-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.grade-file-name {
  font-size: 12px;
  color: #909399;
}

.grade-file-tip {
  font-size: 13px;
  color: #909399;
}

/* 图片直接铺开预览，省掉"下载再打开"这一步 */
.grade-image {
  max-width: 100%;
  max-height: 240px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.grade-pdf {
  width: 100%;
  height: 260px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.grade-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.grade-label {
  font-size: 13px;
  color: #606266;
}

.grade-quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.grade-quick-label {
  font-size: 12px;
  color: #909399;
}

.grade-quick-tag {
  cursor: pointer;
}

.grade-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.grade-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  font-size: 13px;
  color: #b1b3b8;
}

@media (max-width: 768px) {
  .grade-body {
    grid-template-columns: 1fr;
  }
}
</style>
