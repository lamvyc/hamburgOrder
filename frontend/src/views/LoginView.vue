<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { homeForRole, safeRedirectTarget } from '@/utils/roleHome'

interface DemoAccount {
  label: string
  username: string
  role: string
  note: string
}

const demoAccounts: DemoAccount[] = [
  {
    label: '顾客',
    username: 'demo_customer',
    role: 'CUSTOMER',
    note: '选店、浏览菜单、本地购物车',
  },
  {
    label: '员工',
    username: 'demo_staff',
    role: 'STAFF',
    note: '仅所属中央车站店',
  },
  {
    label: '管理员',
    username: 'demo_admin',
    role: 'ADMIN',
    note: '基础管理入口',
  },
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const submitError = ref<string | null>(null)

function fillAccount(account: DemoAccount): void {
  username.value = account.username
  password.value = 'Burger@123'
  submitError.value = null
}

async function submit(): Promise<void> {
  submitError.value = null
  try {
    const user = await auth.login(username.value.trim(), password.value)
    const redirect = safeRedirectTarget(route.query.redirect)
    await router.replace(redirect ?? homeForRole(user.role))
  } catch {
    submitError.value = auth.error?.message ?? '登录失败，请稍后重试'
  }
}
</script>

<template>
  <main class="page">
    <section class="login-layout">
      <div class="intro">
        <p class="eyebrow">STAGE A2 / SESSION</p>
        <h1>先确认<br /><span>你是谁。</span></h1>
        <p class="lede">
          顾客、门店员工、管理员共用一套登录，但后端会分别校验身份和角色。
          前端路由守卫只负责体验，真正的权限边界在 Spring Security。
        </p>

        <div class="demo-block">
          <div class="demo-heading">
            <span>固定示例账号</span>
            <code>统一密码 Burger@123</code>
          </div>
          <button
            v-for="account in demoAccounts"
            :key="account.username"
            class="demo-account"
            type="button"
            @click="fillAccount(account)"
          >
            <span class="demo-role">{{ account.label }}</span>
            <span class="demo-main">
              <strong>{{ account.username }}</strong>
              <small>{{ account.note }}</small>
            </span>
            <span class="demo-arrow" aria-hidden="true">→</span>
          </button>
          <p class="demo-foot">
            仅本地开发演示。密码以 BCrypt 散列保存在 V5 迁移中，不存明文。
          </p>
        </div>
      </div>

      <aside class="login-card">
        <div class="card-head">
          <span>SESSION LOGIN</span>
          <span>CSRF ON</span>
        </div>
        <h2>登录你的入口</h2>
        <p class="card-copy">
          登录成功后使用 HttpOnly Session Cookie；所有写请求需要 CSRF Token。
        </p>

        <el-alert
          v-if="submitError || auth.error"
          class="login-alert"
          type="error"
          :closable="false"
          show-icon
          title="登录失败"
        >
          <p>{{ submitError ?? auth.error?.message }}</p>
          <p v-if="auth.error" class="alert-meta">
            错误码 <code>{{ auth.error.code }}</code>
            <span v-if="auth.error.traceId">· traceId <code>{{ auth.error.traceId }}</code></span>
          </p>
        </el-alert>

        <form class="login-form" @submit.prevent="submit">
          <label class="field">
            <span>用户名</span>
            <el-input
              v-model="username"
              autocomplete="username"
              placeholder="demo_customer"
              :disabled="auth.loading"
            />
          </label>
          <label class="field">
            <span>密码</span>
            <el-input
              v-model="password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="Burger@123"
              :disabled="auth.loading"
            />
          </label>
          <el-button
            class="submit-button"
            type="primary"
            native-type="submit"
            :loading="auth.loading"
          >
            登录
          </el-button>
        </form>

        <div class="csrf-note">
          <span>请求顺序</span>
          <p>
            <code>GET /api/auth/csrf</code> 设置 XSRF Cookie →
            <code>POST /api/auth/login</code> 带 Session Cookie 与
            <code>X-XSRF-TOKEN</code>。
          </p>
        </div>
      </aside>
    </section>
  </main>
</template>

<style scoped>
.page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 0 32px 48px;
}

.login-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(340px, 0.8fr);
  gap: clamp(36px, 7vw, 90px);
  align-items: start;
  padding: clamp(54px, 8vw, 92px) 0 40px;
}

.eyebrow {
  margin: 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.14em;
}

h1 {
  margin: 14px 0 26px;
  font-family: var(--font-display);
  font-size: clamp(3.6rem, 7.4vw, 6.2rem);
  font-weight: 500;
  line-height: 0.9;
  letter-spacing: -0.065em;
}

h1 span {
  color: var(--ketchup);
  font-style: italic;
}

.lede {
  max-width: 54ch;
  margin: 0 0 34px;
  color: var(--muted-strong);
  font-size: 1.02rem;
  line-height: 1.85;
}

.demo-block {
  max-width: 580px;
  border-top: 1.5px solid var(--ink);
}

.demo-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.08em;
}

.demo-heading code {
  color: var(--ketchup);
  font-size: 0.62rem;
}

.demo-account {
  display: grid;
  width: 100%;
  grid-template-columns: 64px minmax(0, 1fr) 20px;
  gap: 14px;
  align-items: center;
  padding: 15px 4px;
  border: 0;
  border-top: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.demo-account:hover,
.demo-account:focus-visible {
  background: rgba(255, 253, 248, 0.72);
}

.demo-role {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  font-weight: 700;
}

.demo-main {
  display: grid;
  gap: 3px;
}

.demo-main strong {
  font-family: var(--font-mono);
  font-size: 0.76rem;
}

.demo-main small {
  color: var(--muted);
  font-size: 0.68rem;
}

.demo-arrow {
  color: var(--ketchup);
  font-size: 1.2rem;
}

.demo-foot {
  margin: 0;
  padding: 12px 0 0;
  border-top: 1px dashed var(--line-strong);
  color: var(--muted);
  font-size: 0.68rem;
  line-height: 1.6;
}

.login-card {
  padding: 24px;
  border: 1.5px solid var(--ink);
  background: #fffdf7;
  box-shadow: 7px 7px 0 var(--mustard);
}

.card-head {
  display: flex;
  justify-content: space-between;
  padding-bottom: 13px;
  border-bottom: 1px dashed var(--line-strong);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.12em;
}

.login-card h2 {
  margin: 24px 0 8px;
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 500;
  letter-spacing: -0.04em;
}

.card-copy {
  margin: 0 0 22px;
  color: var(--muted-strong);
  font-size: 0.77rem;
  line-height: 1.75;
}

.login-alert {
  margin-bottom: 18px;
  border: 1px solid var(--ink);
  border-radius: 2px;
}

.login-alert p {
  margin: 0;
}

.alert-meta {
  margin-top: 4px !important;
  font-size: 0.7rem;
}

.login-form {
  display: grid;
  gap: 16px;
}

.field {
  display: grid;
  gap: 7px;
}

.field > span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.08em;
}

.submit-button {
  width: 100%;
  margin-top: 4px;
  border: 1.5px solid var(--ink);
  border-radius: 4px;
  background: var(--ink);
  box-shadow: 3px 3px 0 var(--mustard);
  font-weight: 700;
}

.submit-button:hover,
.submit-button:focus-visible {
  background: var(--ketchup);
  border-color: var(--ink);
}

.csrf-note {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px dashed var(--line-strong);
}

.csrf-note span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.08em;
}

.csrf-note p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 0.68rem;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .login-layout {
    grid-template-columns: 1fr;
  }

  .login-card {
    max-width: 540px;
  }
}

@media (max-width: 620px) {
  .page {
    padding: 0 18px 34px;
  }

  h1 {
    font-size: clamp(3.2rem, 16vw, 4.6rem);
  }

  .demo-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
