<template>
  <footer class="site-footer">
    <div class="footer-inner">
      <span>Burger Order Lab · Stage D1</span>
      <span>功能存在只是起点；能解释、能复现、能排错，才计入学习进度。</span>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  border-top: 1.5px solid var(--ink);
}

.footer-inner {
  display: flex;
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 24px 32px 32px;
  justify-content: space-between;
  gap: 24px;
  color: var(--muted);
  font-size: 0.7rem;
  line-height: 1.6;
}

.footer-inner span:last-child {
  text-align: right;
}

@media (max-width: 620px) {
  .footer-inner {
    padding: 20px 18px 28px;
    flex-direction: column;
    gap: 8px;
  }

  .footer-inner span:last-child {
    text-align: left;
  }
}
</style>
