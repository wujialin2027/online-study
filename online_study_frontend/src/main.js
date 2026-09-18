import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 中文语言包：不引入的话 Element Plus 组件默认走英文
// （分页会显示 "Total 422 / 10/page"、表格空数据是 "No Data"、确认框按钮是 OK/Cancel）
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// 全局视觉规范：必须放在 Element Plus 的样式之后引入，否则会被它覆盖
import './styles/theme.css'

const app = createApp(App)

app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')