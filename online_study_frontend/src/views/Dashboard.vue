<template>
  <div>
    <h2>数据概览</h2>
    <div class="charts">
      <div id="courseChart" style="width: 400px; height: 300px;"></div>
      <div id="userChart" style="width: 400px; height: 300px;"></div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import request from '../utils/request'

onMounted(async () => {
  try {
    const students = await request.get('/student/list')
    const teachers = await request.get('/teacher/list')
    const courses = await request.get('/course/list')
    const applies = await request.get('/course-apply/list')

    // Prepare course data
    const courseNames = []
    const applyCounts = []
    courses.forEach(course => {
      courseNames.push(course.courseName)
      const count = applies.filter(a => a.courseId === course.courseId).length
      applyCounts.push(count)
    })

    const courseChart = echarts.init(document.getElementById('courseChart'))
    courseChart.setOption({
      title: { text: '课程报名统计', left: 'center' },
      tooltip: {},
      xAxis: { 
        data: courseNames.length > 0 ? courseNames : ['暂无课程'],
        axisLabel: {
          interval: 0,
          rotate: 30 // 旋转文字避免重叠错位
        }
      },
      yAxis: {},
      grid: {
        bottom: '20%' // 给底部留出空间显示旋转的文字
      },
      series: [{ type: 'bar', data: applyCounts.length > 0 ? applyCounts : [0] }]
    })

    const userChart = echarts.init(document.getElementById('userChart'))
    userChart.setOption({
      title: { text: '系统用户分布', left: 'center' },
      tooltip: {
        trigger: 'item'
      },
      legend: {
        orient: 'vertical',
        left: 'left'
      },
      series: [
        {
          type: 'pie',
          radius: '50%',
          data: [
            { value: students.length, name: '学员' },
            { value: teachers.length, name: '教师' },
            { value: 1, name: '管理员' } // 假设至少1个超级管理员
          ],
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }
      ]
    })
    
    // 自适应窗口大小
    window.addEventListener('resize', () => {
      courseChart.resize()
      userChart.resize()
    })
  } catch (e) {
    console.error(e)
  }
})
</script>

<style scoped>
.charts {
  display: flex;
  gap: 20px;
  margin-top: 20px;
}
</style>