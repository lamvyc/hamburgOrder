import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { ElAlert, ElButton, ElDialog, ElEmpty, ElInput, ElSwitch, ElTag } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/tag/style/css'
import App from './App.vue'
import router from './router'
import './styles/base.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElAlert)
app.use(ElButton)
app.use(ElDialog)
app.use(ElEmpty)
app.use(ElInput)
app.use(ElSwitch)
app.use(ElTag)
app.mount('#app')
