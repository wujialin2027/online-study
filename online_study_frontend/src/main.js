import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 全局视觉规范：必须放在 Element Plus 的样式之后引入，否则会被它覆盖
import './styles/theme.css'

const app = createApp(App)

app.use(router)
app.use(ElementPlus)

app.mount('#app')