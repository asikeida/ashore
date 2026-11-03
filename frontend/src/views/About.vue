<template>
  <div class="container-wide about-page">
    <section class="about-hero">
      <span class="eyebrow">ASHORE / 上岸</span>
      <h1>把面试经验变成<br>下一次更好的回答</h1>
      <p>Ashore 是一个面经分享与 AI 辅助复盘的技术社区。阅读真实经历，记录自己的面试过程，再从具体面经进入模拟练习。</p>
      <div class="hero-actions"><RouterLink to="/" class="primary-link">浏览面经 →</RouterLink><RouterLink to="/write" class="secondary-link">写下我的面经</RouterLink></div>
    </section>
    <div class="steps">
      <section><span>01 / 发现</span><h2>阅读面经</h2><p>从列表进入详情，了解面试问题和作者复盘。</p></section>
      <section><span>02 / 分享</span><h2>发布经历</h2><p>用 Markdown 写下经历与标签，提交后进入审核。</p></section>
      <section><span>03 / 练习</span><h2>针对性模拟</h2><p>从面经详情启动 AI 面试官，在个人中心回看回答。</p></section>
    </div>
  </div>
</template>

<style scoped>
.about-page{max-width:1190px;padding-top:45px}.about-hero{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:52px;box-shadow:var(--shadow-card)}.eyebrow,.steps span{font-size:11px;font-weight:800;letter-spacing:2px;color:var(--accent)}h1{font-size:42px;line-height:1.25;margin:15px 0;color:var(--text)}.about-hero p{font-size:16px;color:var(--muted);max-width:680px;line-height:1.8}.hero-actions{display:flex;gap:10px;margin-top:28px}.hero-actions a{padding:10px 16px;border-radius:8px;text-decoration:none}.primary-link{background:var(--accent);color:#fff}.secondary-link{border:1px solid var(--border);color:var(--text)}.steps{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:18px}.steps section{background:var(--card);border:1px solid var(--border);border-radius:10px;padding:24px}.steps h2{font-size:18px;margin:8px 0}.steps p{color:var(--muted);margin:0;font-size:13px}
@media(max-width:700px){.about-page{padding-top:25px}.about-hero{padding:27px}h1{font-size:30px}.steps{grid-template-columns:1fr}}
</style>
