(() => {
  if (window.__officialLoginMemory || window !== top || location.protocol !== 'https:') return;
  let background = false;
  const visible = e => {
    const rect = e.getBoundingClientRect();
    // An application-owned, unattached WebView has no drawn viewport. Only the
    // exact school adapter may opt in; CSS-hidden alternative forms stay excluded.
    if (!background && (rect.width <= 0 || rect.height <= 0)) return false;
    for (let node = e; node instanceof Element; node = node.parentElement) {
      const style = getComputedStyle(node);
      if (style.display === 'none' || ['hidden','collapse'].includes(style.visibility) ||
          Number(style.opacity) === 0) return false;
      // The official iView modal remains drawn during its leave transition.
      // Its v-model value already records that the school closed the notice.
      if (casPage && node.matches('.ivu-modal')) {
        let component=node.__vue__;
        for(let i=0;component&&component.$options?.name!=='Modal'&&i<8;i++)component=component.$parent;
        if(component?.$options?.name==='Modal'&&component.value===false)return false;
      }
    }
    return true;
  };
  const challenge = e => /captcha|verifycode|verification|otp|one-time-code|验证码/i.test(e.name+' '+e.id+' '+e.autocomplete+' '+e.placeholder);
  // iView uses .ivu-modal for notices as well as challenges. A notice is not a CAPTCHA.
  const humanChallenge = () =>
    [...document.querySelectorAll('iframe[src*="captcha"],img[src*="captcha"],input[autocomplete="one-time-code"],.vue-auth-box_,.auth-control_')].some(visible) ||
    [...document.querySelectorAll('.ivu-modal')].some(e => visible(e) &&
      /验证码|安全验证|二次认证|人机验证|拖动滑块/.test(e.textContent || ''));
  const casPage = location.origin === 'https://cas.scvtc.edu.cn' &&
    ['/cas/WEB/index.html','/cas/H5/index.html'].includes(location.pathname);
  const allowedService = () => {
    const service = new URL(location.href).searchParams.get('service');
    if (!service) return true;
    try {
      const u = new URL(service);
      return u.protocol === 'https:' && !u.username && !u.password &&
        (u.hostname === 'jwxt.scvtc.edu.cn' && ['', '443'].includes(u.port) && u.pathname === '/api/cas/login' ||
         u.hostname === 'www.shulin-soft.com' && u.port === '8267' && u.pathname === '/casLogin.html');
    } catch { return false; }
  };
  const optionalNoticeAction = () => {
    if (!casPage || !allowedService() || humanChallenge()) return null;
    const modals = [...document.querySelectorAll('.ivu-modal')].filter(visible);
    if (modals.length !== 1) return null;
    const modal=modals[0], body=modal.querySelector('.ivu-modal-body');
    let owner=modal.__vue__;
    for(let i=0;owner&&owner.$options?.name!=='login'&&i<8;i++)owner=owner.$parent;
    if(owner?.$options?.name!=='login'||owner.mustBind!==false||owner.needAutoLock===true)return null;
    const cancel=modal.querySelector('.ivu-modal-footer .footer-left');
    const bind=modal.querySelector('.ivu-modal-footer .footer-right');
    if (!body || !cancel || !bind ||
        !visible(cancel) || modal.querySelector('input,iframe') ||
        cancel.getAttribute('aria-disabled') === 'true') return null;
    const left=cancel.textContent.trim(), right=bind.textContent.trim();
    const accountReminder=owner.noticeModalTitle==='账号安全提醒' && owner.twoBtn===true &&
      typeof owner.twoBtnLeftClick==='function' &&
      ((left==='稍后修改'&&right==='立即修改')||(left==='稍后绑定'&&right==='立即绑定'));
    const phoneReminder=body.textContent.replace(/\s+/g,'')==='未查询到手机号，请先绑定账号。' &&
      left==='取消'&&right==='去绑定'&&typeof owner.twoBtnLeftClick==='function';
    if (!accountReminder && !phoneReminder) return null;
    return {button:cancel,callback:owner.twoBtnLeftClick};
  };
  const dismissedNotices = new WeakMap();
  let noticeAttempts = 0;
  const identify = (allowHuman = false) => {
    if (casPage && (!allowedService() || !['','#','#/'].includes(location.hash))) return null;
    const passwords = [...document.querySelectorAll('input[type=password]')].filter(visible);
    if (passwords.length !== 1) return null;
    const password = passwords[0], form = password.form;
    // Actual H5 Vue/iView DOM has no form: the official button's handler owns
    // encryption and submission. Scope this exception to the observed panel.
    const h5 = casPage && location.pathname === '/cas/H5/index.html';
    const panels = [...document.querySelectorAll('.login-panel > .content-info')].filter(visible);
    const scope = form || (h5 && panels.length === 1 ? panels[0] : null);
    if (!scope || !scope.contains(password)) return null;
    if (!allowHuman && [...document.querySelectorAll('.ivu-modal')].some(e => visible(e) && !e.contains(password))) return null;
    if (h5 && (!password.matches('.account-input--pwd input.ivu-input') || password.placeholder !== '请输入密码')) return null;
    const action = new URL(form?.getAttribute('action') || location.href, location.href);
    if (action.origin !== location.origin || action.protocol !== 'https:') return null;
    const fields = [...scope.querySelectorAll('input')].filter(visible);
    const human=fields.some(e => visible(e) && challenge(e)) || humanChallenge();
    if(human&&!allowHuman)return null;
    const users = fields.filter(e => ['text','email','tel'].includes(e.type) && e !== password && !challenge(e));
    if (users.length !== 1 || fields.some(e => e.required && ![users[0],password].includes(e) && !challenge(e))) return null;
    if (h5 && (!users[0].matches('.account-input:not(.account-input--pwd) input.ivu-input') || users[0].placeholder !== '请输入账号')) return null;
    // Observed CAS Vue login: inputs have no name/id and .login-button is outside
    // the form. Use that exact verified page adapter, preserving its own handler.
    const schoolCas=location.origin==='https://cas.scvtc.edu.cn'&&location.pathname==='/cas/WEB/index.html';
    const buttons = [...scope.querySelectorAll(h5 ? '.info-button > button.button-item' : 'button,input[type=submit]'),...(schoolCas?[...document.querySelectorAll('.login-button')]:[])].filter(e => visible(e) && (allowHuman || (!e.disabled && e.getAttribute('aria-disabled') !== 'true' && !e.classList.contains('ivu-btn-loading'))) &&
      (e.type === 'submit' || /^(登\s*录|login|sign\s*in)$/i.test((e.textContent || e.value || '').trim())));
    if (buttons.length !== 1) return null;
    const button = buttons[0], username = users[0];
    const binding = { origin:location.origin, path:location.pathname, action:action.origin+action.pathname,
      user:username.name+'|'+username.id+'|'+username.placeholder, password:password.name+'|'+password.id+'|'+password.placeholder,
      button:(button.textContent || button.value || '').trim()+'|'+(button.type||button.tagName) };
    return { username,password,button,binding,scope };
  };
  const capture = found => {
    if (!found || !found.password.value) return;
    window.OfficialLoginBridge?.postMessage(JSON.stringify({page:location.href,binding:found.binding,
      username:found.username.value,password:found.password.value}));
  };
  document.addEventListener('click', event => {
    if (!event.isTrusted) return;
    const found = identify(true);
    if (found?.button.contains(event.target)) capture(found);
  }, true);
  document.addEventListener('keydown', event => {
    if (!event.isTrusted || event.key !== 'Enter' || event.isComposing) return;
    const found=identify(true);
    if (found && ([found.username,found.password].includes(event.target)||(event.target instanceof HTMLInputElement&&found.scope.contains(event.target)&&challenge(event.target)))) capture(found);
  }, true);
  const matches = (found,binding) => found && Object.keys(found.binding).length === Object.keys(binding).length &&
    Object.keys(found.binding).every(key => found.binding[key] === binding[key]);
  const fill=(found,username,password)=>{
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
    for (const [input,value] of [[found.username,username],[found.password,password]]) {
      setter.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));
    }
  };
  // The official CAS bundle uses personCenter as its default applications page.
  // This requests the protected JWXT entry, never proof of an authenticated student.
  const isLanding = () => casPage && location.pathname === '/cas/H5/index.html' &&
    location.hash.slice(1).split('?')[0].replace(/\/$/, '') === '/personCenter' &&
    !humanChallenge() && ![...document.querySelectorAll('.ivu-modal')].some(visible);
  let lastLanding = '';
  const reportLanding = () => {
    if (!isLanding() || lastLanding === location.href ||
        typeof window.OfficialLoginBridge?.postMessage !== 'function') return;
    lastLanding = location.href;
    window.OfficialLoginBridge.postMessage(JSON.stringify({kind:'cas-landing',page:location.href}));
  };
  if (casPage) {
    window.addEventListener('hashchange', reportLanding);
    window.addEventListener('popstate', reportLanding);
    document.addEventListener('DOMContentLoaded', reportLanding);
    new MutationObserver(reportLanding).observe(document,{childList:true,subtree:true,attributes:true,attributeFilter:['style','class']});
  }
  window.__officialLoginMemory = { describe:() => identify(true)?.binding || null,
    reportLanding,
    setBackgroundMode:() => { background = casPage && allowedService(); },
    continueOptionalNotice:() => {
      const action=optionalNoticeAction();
      if (!action || noticeAttempts >= 3) return false;
      let seen=dismissedNotices.get(action.button);
      if (!seen) { seen=new WeakSet();dismissedNotices.set(action.button,seen); }
      if (seen.has(action.callback)) return false;
      seen.add(action.callback);noticeAttempts++;action.button.click();return true;
    },
    state:() => {
      if (!casPage) return 'outside';
      if (humanChallenge() || [...document.querySelectorAll('input')].some(e => visible(e) && challenge(e))) return 'challenge';
      if (isLanding()) return 'handoff';
      const errors = [...document.querySelectorAll('.ivu-message-error')].filter(visible);
      if (errors.some(e => /(?:账号|帐号|账户|密码|用户名).*(?:错误|不正确|不存在|锁定|禁用|停用)/.test(e.textContent || ''))) return 'rejected';
      if (errors.length) return 'unavailable';
      if (optionalNoticeAction()) return 'optional-notice';
      if ([...document.querySelectorAll('.ivu-modal')].some(visible) ||
          !['','#','#/'].includes(location.hash)) return 'notice';
      return identify(true) ? 'form' : 'loading';
    },
    challengeRequired:() => humanChallenge() || [...document.querySelectorAll('input')].some(e => visible(e) && challenge(e)),
    matches:binding => !!matches(identify(),binding), matchesForInput:binding=>!!matches(identify(true),binding),
    configure(binding,username,password,submit=true){
      const found=identify(true);if(!matches(found,binding))return false;
      fill(found,username,password);if(submit&&matches(identify(),binding))found.button.click();return true;
    },restore(binding,username,password) {
    const found = identify();
    if (!matches(found,binding)) return false;
    fill(found,username,password);
    found.button.click(); return true;
  }};
})();
