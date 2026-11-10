<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { claimCoupon, fetchAvailableCoupons, fetchMyCoupons } from '@/api/coupons'
import { toUiError } from '@/api/http'
import { useCartStore } from '@/stores/cart'
import { useCheckoutStore } from '@/stores/checkout'
import type { CouponTemplate, UserCoupon } from '@/types/coupon'
import type { UiError } from '@/types/system'
import { formatPrice } from '@/utils/format'

const router = useRouter()
const cart = useCartStore()
const checkout = useCheckoutStore()
const availableCoupons = ref<CouponTemplate[]>([])
const myCoupons = ref<UserCoupon[]>([])
const selectedCouponId = ref<number | null>(null)
const claimingId = ref<number | null>(null)
const couponError = ref<UiError | null>(null)

const unusedCoupons = computed(() =>
  myCoupons.value.filter((coupon) => coupon.status === 'UNUSED'),
)
const selectedCoupon = computed(
  () => unusedCoupons.value.find((coupon) => coupon.id === selectedCouponId.value) ?? null,
)
const discountAmount = computed(() => {
  if (!selectedCoupon.value || !canUse(selectedCoupon.value)) return '0.00'
  return Math.min(
    Number(selectedCoupon.value.discountAmount),
    Number(cart.subtotal),
  ).toFixed(2)
})
const payable = computed(() =>
  Math.max(0, Number(cart.subtotal) - Number(discountAmount.value)).toFixed(2),
)

function canUse(coupon: UserCoupon): boolean {
  return Number(cart.subtotal) >= Number(coupon.minOrderAmount)
}

async function loadCoupons(): Promise<void> {
  couponError.value = null
  try {
    const [available, mine] = await Promise.all([
      fetchAvailableCoupons(),
      fetchMyCoupons(),
    ])
    availableCoupons.value = available
    myCoupons.value = mine
    if (selectedCouponId.value !== null
      && !mine.some((coupon) => coupon.id === selectedCouponId.value && coupon.status === 'UNUSED')) {
      selectedCouponId.value = null
    }
  } catch (cause) {
    couponError.value = toUiError(cause)
  }
}

async function claim(template: CouponTemplate): Promise<void> {
  claimingId.value = template.id
  couponError.value = null
  try {
    const coupon = await claimCoupon(template.id)
    await loadCoupons()
    if (canUse(coupon)) {
      selectedCouponId.value = coupon.id
    }
    ElMessage.success('优惠券已领取')
  } catch (cause) {
    couponError.value = toUiError(cause)
  } finally {
    claimingId.value = null
  }
}

function toggleCoupon(coupon: UserCoupon): void {
  if (!canUse(coupon)) return
  selectedCouponId.value = selectedCouponId.value === coupon.id ? null : coupon.id
}

async function submitOrder(): Promise<void> {
  if (!cart.hasItems || checkout.submitting) return
  try {
    const order = await checkout.submit(cart, selectedCouponId.value)
    await router.push({ name: 'order-detail', params: { orderId: order.id } })
  } catch {
    // checkout.error carries the server code and traceId.
  }
}

watch(() => cart.subtotal, () => {
  if (selectedCoupon.value && !canUse(selectedCoupon.value)) {
    selectedCouponId.value = null
  }
})

onMounted(() => {
  void loadCoupons()
})

async function clearCart(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '清空后需要重新从菜单加入商品。此操作只影响本地购物车。',
      '清空购物车？',
      {
        confirmButtonText: '确定清空',
        cancelButtonText: '保留',
        type: 'warning',
      },
    )
    cart.clear()
  } catch {
    // Cancel is not an error.
  }
}
</script>

<template>
  <main class="page">
    <section class="hero">
      <div>
        <p class="eyebrow">B1 / CART + COUPON</p>
        <h1>购物车</h1>
        <p class="lede">
          这个购物车只保存在当前浏览器。价格是加入时的展示快照，
          服务端会在 B1 下单时重新校验商品、价格、库存与优惠券。
        </p>
        <div v-if="cart.hasItems" class="cart-facts">
          <span>{{ cart.totalQuantity }} 件商品</span>
          <span>{{ cart.storeName }}</span>
        </div>
      </div>
      <aside class="cart-stamp">
        <span>LOCAL ONLY</span>
        <strong>{{ cart.totalQuantity }}</strong>
        <small>ITEMS</small>
      </aside>
    </section>

    <section v-if="cart.hasItems" class="cart-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">YOUR SELECTION</p>
          <h2>{{ cart.storeName }}</h2>
        </div>
        <el-button class="clear-button" size="small" @click="clearCart">
          清空购物车
        </el-button>
      </div>

      <div class="cart-table">
        <article v-for="item in cart.items" :key="item.productId" class="cart-row">
          <div class="item-copy">
            <div class="item-head">
              <h3>{{ item.name }}</h3>
              <code>{{ item.code }}</code>
            </div>
            <p>加入时价格 {{ formatPrice(item.priceAtAdd) }}</p>
          </div>

          <div class="quantity-control" :aria-label="`${item.name}数量`">
            <button type="button" aria-label="减少数量" @click="cart.decrease(item.productId)">
              −
            </button>
            <span>{{ item.quantity }}</span>
            <button type="button" aria-label="增加数量" @click="cart.increase(item.productId)">
              +
            </button>
          </div>

          <div class="line-total">{{ formatPrice((Number(item.priceAtAdd) * item.quantity).toFixed(2)) }}</div>

          <button class="remove-button" type="button" @click="cart.remove(item.productId)">
            移除
          </button>
        </article>
      </div>

      <section class="coupon-section">
        <div class="section-heading coupon-heading">
          <div>
            <p class="eyebrow">B1 / COUPON LEDGER</p>
            <h2>优惠券</h2>
          </div>
          <span class="coupon-note">领取与核销都以数据库状态为准</span>
        </div>

        <div v-if="availableCoupons.length > 0" class="available-coupons">
          <article v-for="template in availableCoupons" :key="template.id" class="coupon-template">
            <div>
              <span class="coupon-code">{{ template.code }}</span>
              <strong>{{ template.name }}</strong>
              <p>满 {{ formatPrice(template.minOrderAmount) }} 减 {{ formatPrice(template.discountAmount) }}</p>
            </div>
            <div class="coupon-claim">
              <small>剩余 {{ template.remainingQuantity }}</small>
              <el-button
                size="small"
                :loading="claimingId === template.id"
                :disabled="template.claimed || template.remainingQuantity <= 0"
                @click="claim(template)"
              >
                {{ template.claimed ? '已领取' : '领取' }}
              </el-button>
            </div>
          </article>
        </div>

        <div v-if="unusedCoupons.length > 0" class="my-coupons">
          <button
            v-for="coupon in unusedCoupons"
            :key="coupon.id"
            type="button"
            class="coupon-choice"
            :class="{ active: selectedCouponId === coupon.id, disabled: !canUse(coupon) }"
            :disabled="!canUse(coupon)"
            @click="toggleCoupon(coupon)"
          >
            <span>{{ coupon.code }}</span>
            <strong>{{ coupon.name }}</strong>
            <small>{{ canUse(coupon) ? '点击使用' : `满 ${formatPrice(coupon.minOrderAmount)} 可用` }}</small>
          </button>
        </div>
        <p v-else class="coupon-empty">还没有可用的优惠券，先领取一张。</p>

        <el-alert
          v-if="couponError"
          class="coupon-alert"
          type="error"
          :closable="false"
          show-icon
          title="优惠券操作失败"
        >
          <p>{{ couponError.message }}</p>
          <p class="alert-meta">
            错误码 <code>{{ couponError.code }}</code>
            <span v-if="couponError.traceId">· traceId <code>{{ couponError.traceId }}</code></span>
          </p>
        </el-alert>
      </section>

      <el-alert
        v-if="checkout.error"
        class="checkout-alert"
        type="error"
        :closable="false"
        show-icon
        title="订单创建失败"
      >
        <p>{{ checkout.error.message }}</p>
        <p class="alert-meta">
          错误码 <code>{{ checkout.error.code }}</code>
          <span v-if="checkout.error.traceId">· traceId <code>{{ checkout.error.traceId }}</code></span>
        </p>
      </el-alert>

      <div class="summary">
        <div class="summary-copy">
          <span>B1 / SERVER PRICING</span>
          <p>
            本地小计只用于展示。服务端按商品 ID 重新查询价格、上架状态与库存，
            并在同一事务内创建订单、明细、库存预占、事件与幂等记录。
          </p>
        </div>
        <div class="summary-total">
          <span>商品小计 {{ formatPrice(cart.subtotal) }}</span>
          <span v-if="discountAmount !== '0.00'" class="discount-line">
            优惠券 − {{ formatPrice(discountAmount) }}
          </span>
          <span>应付</span>
          <strong>{{ formatPrice(payable) }}</strong>
        </div>
      </div>

      <div class="checkout-bar">
        <div>
          <span>WAIT_PAY / 30 MIN</span>
          <p>服务端复核优惠券门槛与状态；提交后进入待支付。</p>
        </div>
        <el-button
          class="checkout-button"
          type="primary"
          :loading="checkout.submitting"
          :disabled="checkout.submitting"
          @click="submitOrder"
        >
          提交订单
        </el-button>
      </div>

      <div class="cart-actions">
        <RouterLink
          v-if="cart.storeId"
          class="back-link"
          :to="{ name: 'store-menu', params: { storeId: cart.storeId } }"
        >
          ← 继续逛 {{ cart.storeName }}
        </RouterLink>
        <RouterLink class="back-link" to="/">返回全部门店</RouterLink>
      </div>
    </section>

    <section v-else class="empty-section">
      <el-empty description="购物车还是空的">
        <RouterLink class="back-link" to="/">去选一家门店</RouterLink>
      </el-empty>
    </section>

    <aside class="scope-note">
      <span>B1 / CART BOUNDARY</span>
      <p>
        购物车按门店整体保存，跨门店加入会询问是否清空旧购物车。
        它按当前登录顾客分属，退出或切换账号会清空。
      </p>
    </aside>
  </main>
</template>

<style scoped>
.page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 0 32px 48px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(220px, 0.7fr);
  gap: clamp(30px, 6vw, 72px);
  align-items: start;
  padding: clamp(50px, 7vw, 78px) 0 40px;
  border-bottom: 1.5px solid var(--ink);
}

.eyebrow {
  margin: 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.66rem;
  font-weight: 600;
  letter-spacing: 0.13em;
}

h1 {
  margin: 14px 0 18px;
  font-family: var(--font-display);
  font-size: clamp(3.4rem, 7vw, 5.4rem);
  font-weight: 500;
  line-height: 0.95;
  letter-spacing: -0.06em;
}

.lede {
  max-width: 58ch;
  margin: 0;
  color: var(--muted-strong);
  font-size: 0.94rem;
  line-height: 1.85;
}

.cart-facts {
  display: flex;
  gap: 22px;
  margin-top: 24px;
  padding-top: 14px;
  border-top: 1px solid var(--line-strong);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.66rem;
}

.cart-stamp {
  display: grid;
  justify-items: start;
  padding: 24px;
  border: 1.5px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: 6px 6px 0 var(--mustard);
}

.cart-stamp span,
.cart-stamp small {
  color: rgba(244, 239, 228, 0.58);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.12em;
}

.cart-stamp strong {
  margin: 15px 0 4px;
  font-family: var(--font-display);
  font-size: 4rem;
  font-weight: 500;
  line-height: 1;
}

.cart-section {
  padding: 42px 0;
  border-bottom: 1.5px solid var(--ink);
}

.section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}

h2 {
  margin: 8px 0 0;
  font-family: var(--font-display);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 500;
  letter-spacing: -0.045em;
}

.clear-button {
  border-color: var(--line-strong);
}

.cart-table {
  margin-top: 26px;
  border-top: 1.5px solid var(--ink);
}

.cart-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 110px 50px;
  gap: 18px;
  align-items: center;
  padding: 20px 6px;
  border-bottom: 1px solid var(--line);
}

.item-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.item-head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.35rem;
  font-weight: 500;
  letter-spacing: -0.03em;
}

.item-head code {
  color: var(--muted);
  font-size: 0.58rem;
}

.item-copy p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 0.68rem;
}

.quantity-control {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--line-strong);
}

.quantity-control button {
  width: 30px;
  height: 30px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 1rem;
  cursor: pointer;
}

.quantity-control button:hover {
  background: var(--ink);
  color: var(--paper);
}

.quantity-control span {
  min-width: 34px;
  font-family: var(--font-mono);
  font-size: 0.75rem;
  text-align: center;
}

.line-total {
  font-family: var(--font-display);
  font-size: 1.25rem;
  text-align: right;
  white-space: nowrap;
}

.remove-button {
  padding: 0;
  border: 0;
  background: none;
  color: var(--muted);
  font-size: 0.65rem;
  text-decoration: underline;
  cursor: pointer;
}

.remove-button:hover {
  color: var(--ketchup);
}

.coupon-section {
  margin-top: 30px;
  padding: 24px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.coupon-heading {
  align-items: baseline;
}

.coupon-note {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.6rem;
}

.available-coupons {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.coupon-template {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 13px 15px;
  border: 1px dashed var(--line-strong);
  background: #fffdf8;
}

.coupon-template p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 0.68rem;
}

.coupon-code {
  margin-right: 8px;
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.08em;
}

.coupon-template strong {
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 500;
}

.coupon-claim {
  display: flex;
  align-items: center;
  gap: 10px;
}

.coupon-claim small {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.58rem;
}

.my-coupons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.coupon-choice {
  display: grid;
  gap: 3px;
  min-width: 180px;
  padding: 11px 13px;
  border: 1.5px solid var(--line-strong);
  background: #fffdf8;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.coupon-choice span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.56rem;
}

.coupon-choice strong {
  font-size: 0.78rem;
}

.coupon-choice small {
  color: var(--muted);
  font-size: 0.62rem;
}

.coupon-choice.active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: 3px 3px 0 var(--mustard);
}

.coupon-choice.active small {
  color: rgba(244, 239, 228, 0.65);
}

.coupon-choice.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.coupon-empty {
  margin: 14px 0 0;
  color: var(--muted);
  font-size: 0.7rem;
}

.coupon-alert {
  margin-top: 16px;
  border: 1.5px solid var(--ink);
  border-radius: 2px;
}

.coupon-alert p {
  margin: 0 0 3px;
}

.discount-line {
  color: var(--ketchup);
}

.checkout-alert {
  margin-top: 20px;
  border: 1.5px solid var(--ink);
  border-radius: 2px;
}

.checkout-alert p {
  margin: 0 0 4px;
}

.alert-meta {
  font-size: 0.72rem;
}

.checkout-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 18px;
  padding: 16px 20px;
  border: 1.5px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
  box-shadow: 4px 4px 0 var(--mustard);
}

.checkout-bar span {
  color: var(--mustard);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.1em;
}

.checkout-bar p {
  margin: 4px 0 0;
  color: rgba(244, 239, 228, 0.66);
  font-size: 0.68rem;
}

.checkout-button {
  min-width: 124px;
  border: 1px solid var(--paper);
  border-radius: 4px;
  background: var(--mustard);
  color: var(--ink);
  font-weight: 700;
}

.checkout-button:hover,
.checkout-button:focus-visible {
  background: var(--ketchup);
  border-color: var(--ketchup);
  color: #fff8ec;
}

.summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-top: 24px;
  padding: 20px;
  border: 1.5px solid var(--ink);
  background: #fffdf8;
  box-shadow: 4px 4px 0 var(--mustard);
}

.summary-copy span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.1em;
}

.summary-copy p {
  max-width: 60ch;
  margin: 7px 0 0;
  color: var(--muted);
  font-size: 0.7rem;
  line-height: 1.7;
}

.summary-total {
  display: grid;
  gap: 4px;
  text-align: right;
}

.summary-total span {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.6rem;
}

.summary-total strong {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 500;
}

.cart-actions {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-top: 22px;
}

.back-link {
  color: var(--ink);
  font-size: 0.74rem;
  font-weight: 700;
  text-decoration: none;
  border-bottom: 1px solid var(--ink);
}

.back-link:hover {
  color: var(--ketchup);
  border-color: var(--ketchup);
}

.empty-section {
  display: grid;
  min-height: 320px;
  place-items: center;
  border-bottom: 1.5px solid var(--ink);
}

.scope-note {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 20px;
  padding-top: 24px;
}

.scope-note span {
  color: var(--ketchup);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
}

.scope-note p {
  max-width: 80ch;
  margin: 0;
  color: var(--muted);
  font-size: 0.75rem;
  line-height: 1.8;
}

@media (max-width: 840px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .cart-stamp {
    max-width: 260px;
  }

  .cart-row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 12px;
  }

  .line-total {
    text-align: left;
  }

  .remove-button {
    justify-self: start;
  }
}

@media (max-width: 620px) {
  .page {
    padding: 0 18px 34px;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .summary {
    flex-direction: column;
  }

  .summary-total {
    text-align: left;
  }

  .checkout-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .checkout-button {
    width: 100%;
  }

  .cart-actions {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .scope-note {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>
