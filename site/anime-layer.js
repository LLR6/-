// LR//NEO anime interaction layer
(()=>{
  const body=document.body;
  const btn=document.getElementById("animeModeToggle");
  const saved=localStorage.getItem("lr-anime-mode");
  const enabled=saved===null?true:saved==="1";
  function setAnime(on){body.classList.toggle("animeMode",on);btn?.classList.toggle("on",on);if(btn)btn.textContent=on?"ア":"A";localStorage.setItem("lr-anime-mode",on?"1":"0")}
  setAnime(enabled);btn?.addEventListener("click",()=>setAnime(!body.classList.contains("animeMode")));

  const mascot=document.getElementById("animeMascot"),vn=document.getElementById("vnBox"),vnText=document.getElementById("vnText");
  const lines=[
    "欢迎来到 <b>LR//NEO</b>。这里不是主页，是你的个人世界观入口。",
    "今天的状态：<b>Creative Core = ACTIVE</b>。要不要去项目宇宙看看？",
    "我把技术、CTF、学习和生活做成了四块“记忆碎片”。以后每一块都可以继续长。",
    "如果普通网站是文件夹，那这里更像一部会持续更新的番剧。下一集，由你今天做的事决定。"
  ];
  let li=0;
  function speak(text){if(vnText)vnText.innerHTML=text||lines[li++%lines.length];vn?.classList.add("open")}
  mascot?.addEventListener("click",()=>speak());
  document.getElementById("vnClose")?.addEventListener("click",()=>vn.classList.remove("open"));
  document.querySelectorAll("[data-vn]").forEach(x=>x.addEventListener("click",()=>speak(x.dataset.vn)));

  document.getElementById("vnProjects")?.addEventListener("click",()=>{vn.classList.remove("open");window.openWin?.("universeWin")});
  document.getElementById("vnIdea")?.addEventListener("click",()=>{if(window.shuffleIdea)window.shuffleIdea();speak("<b>灵感协议：</b>"+(window.ideaTitle?.textContent||"去做一点只属于你自己的东西。"))});
  document.getElementById("vnAnime")?.addEventListener("click",()=>setAnime(true));

  const root=document.getElementById("petalRoot");
  function petal(){
    if(!body.classList.contains("animeMode")||document.hidden)return;
    const p=document.createElement("i");p.className="neoPetal";
    p.style.left=(Math.random()*100)+"vw";p.style.animationDuration=(6+Math.random()*7)+"s";p.style.setProperty("--drift",(Math.random()*220-110)+"px");p.style.transform="rotate("+(Math.random()*180)+"deg)";
    root?.appendChild(p);setTimeout(()=>p.remove(),14000)
  }
  setInterval(petal,520);

  document.querySelectorAll(".mangaPanel").forEach(p=>p.addEventListener("click",()=>{
    const target=p.dataset.target;
    if(target)document.querySelector(target)?.scrollIntoView({behavior:"smooth"});
    p.animate([{transform:"scale(1)"},{transform:"scale(.985) rotate(-.4deg)"},{transform:"scale(1)"}],{duration:260})
  }));

  let secret=0;mascot?.addEventListener("dblclick",()=>{
    secret++; speak(secret%2?"你发现了隐藏动作。<b>ルミ // OVERDRIVE</b> 已启动。":"别一直戳我啦……不过，系统同步率 +1%。");
    document.documentElement.animate([{filter:"hue-rotate(0deg)"},{filter:"hue-rotate(22deg)"},{filter:"hue-rotate(0deg)"}],{duration:650});
  });
})();