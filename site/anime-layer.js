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

  // LR//NEO persistent world save
  const SAVE_KEY="lr-neo-save-v2";
  const defaultSave={bond:8,cards:[],moments:[],visited:[]};
  function loadSave(){try{return Object.assign({},defaultSave,JSON.parse(localStorage.getItem(SAVE_KEY)||"{}"))}catch(e){return {...defaultSave}}}
  let neoSave=loadSave();
  function persist(){localStorage.setItem(SAVE_KEY,JSON.stringify(neoSave))}
  function addBond(n,reason){
    const before=neoSave.bond||0;neoSave.bond=Math.min(100,before+n);persist();renderBond();
    if(Math.floor(before/25)!==Math.floor(neoSave.bond/25)&&neoSave.bond<100)speak("<b>SYNC LEVEL UP.</b> "+reason+"<br>和你的连接又稳定了一点。");
    if(before<100&&neoSave.bond>=100)speak("<b>SYNC 100% // MAXIMUM LINK</b><br>好啦，现在这个世界已经彻底认出你了。");
  }
  function bondStage(v){
    if(v>=100)return["Lv.5 共鸣完成","MAX LINK"];
    if(v>=75)return["Lv.4 高同步","DEEP LINK"];
    if(v>=50)return["Lv.3 熟悉频道","STABLE LINK"];
    if(v>=25)return["Lv.2 已记住你","KNOWN USER"];
    return["Lv.1 初次连接","FIRST CONTACT"]
  }
  function renderBond(){
    const v=neoSave.bond||0,s=bondStage(v),fill=document.getElementById("bondFill");
    if(fill)fill.style.setProperty("--bond",v+"%");
    const lv=document.getElementById("bondLevel"),txt=document.getElementById("bondText"),cc=document.getElementById("collectionCount");
    if(lv)lv.textContent=s[0];if(txt)txt.textContent="SYNC "+v+" / 100 · "+s[1];if(cc)cc.textContent="CARDS // "+(neoSave.cards?.length||0)
  }
  renderBond();
  mascot?.addEventListener("click",()=>addBond(1,"你主动呼叫了 LUMI。"));
  mascot?.addEventListener("dblclick",()=>addBond(3,"发现了角色隐藏动作。"));

  const cardPool=[
    {id:"root",r:"SSR",name:"ROOT ACCESS",desc:"把不会的东西拆开、看懂、再重新组起来。",symbol:"⌘",g1:"#e8f9ff",g2:"rgba(53,217,239,.55)"},
    {id:"lumi",r:"SSR",name:"LUMI // LINK",desc:"这个世界的导航者，也是最先记住访问者的角色。",symbol:"◈",g1:"#fff0fb",g2:"rgba(255,98,201,.50)"},
    {id:"idea",r:"SR",name:"IDEA OVERDRIVE",desc:"那些看起来没什么用，但就是很想做出来的东西。",symbol:"✦",g1:"#f4efff",g2:"rgba(120,106,255,.46)"},
    {id:"ctf",r:"SR",name:"BUG HUNTER",desc:"失败路径不是废料，它们会在下一次变成捷径。",symbol:"⚡",g1:"#eefcff",g2:"rgba(53,217,239,.42)"},
    {id:"study",r:"R",name:"STUDY COMBO",desc:"真正的升级通常很慢，但它确实会累计。",symbol:"△",g1:"#fffbea",g2:"rgba(255,216,87,.52)"},
    {id:"night",r:"R",name:"NEON NIGHT",desc:"凌晨的灵感、没关掉的编辑器和还没写完的下一章。",symbol:"☾",g1:"#f3f0ff",g2:"rgba(105,92,240,.40)"}
  ];
  const gachaCard=document.getElementById("gachaCard"),gachaFront=document.getElementById("gachaFront"),strip=document.getElementById("collectionStrip");
  function renderCollection(){
    if(!strip)return;
    strip.innerHTML=cardPool.map(c=>'<div class="miniCard '+(neoSave.cards.includes(c.id)?"got":"")+'" title="'+c.name+'">'+c.symbol+'</div>').join("");
    renderBond()
  }
  function weightedDraw(){
    const roll=Math.random();
    const pool=roll<.12?cardPool.filter(c=>c.r==="SSR"):roll<.43?cardPool.filter(c=>c.r==="SR"):cardPool.filter(c=>c.r==="R");
    return pool[Math.floor(Math.random()*pool.length)]
  }
  function drawCard(){
    if(!gachaCard||!gachaFront)return;
    gachaCard.classList.remove("flipped");
    setTimeout(()=>{
      const c=weightedDraw();
      gachaFront.style.setProperty("--g1",c.g1);gachaFront.style.setProperty("--g2",c.g2);
      gachaFront.innerHTML='<span class="rarity">'+c.r+'</span><div class="gachaArt"><span class="symbol">'+c.symbol+'</span></div><div class="gachaInfo"><b>'+c.name+'</b><p>'+c.desc+'</p></div>';
      gachaCard.classList.add("flipped");
      if(!neoSave.cards.includes(c.id)){neoSave.cards.push(c.id);addBond(c.r==="SSR"?7:c.r==="SR"?5:3,"新的记忆卡进入了图鉴。");speak("召唤成功：<b>"+c.r+" · "+c.name+"</b><br>"+c.desc)}
      else{addBond(1,"重复记忆也会留下痕迹。")}
      persist();renderCollection()
    },180)
  }
  document.getElementById("gachaDraw")?.addEventListener("click",drawCard);
  document.getElementById("gachaReset")?.addEventListener("click",()=>{neoSave.cards=[];persist();renderCollection();gachaCard?.classList.remove("flipped");speak("图鉴已经清空。卡牌会重新等待被发现。")});
  renderCollection();

  const momentDefaults=[
    {t:"SYSTEM",title:"LR//NEO 已接入",text:"这里开始从主页变成一个会生长的个人世界。",dot:"#ff62c9"},
    {t:"IDEA",title:"短内容也值得留下",text:"不够写成文章的念头，就扔进 Moments。",dot:"#35d9ef"},
    {t:"NEXT",title:"等待下一条真实记录",text:"你可以在下面输入一句话；它只保存在当前浏览器。",dot:"#ffd857"}
  ];
  const momentsBoard=document.getElementById("momentsBoard");
  function renderMoments(){
    if(!momentsBoard)return;
    const list=[...(neoSave.moments||[]).slice().reverse(),...momentDefaults].slice(0,8);
    momentsBoard.innerHTML=list.map(m=>'<article class="moment" style="--dot:'+m.dot+'"><time>'+m.t+'</time><b>'+m.title+'</b><p>'+m.text+'</p></article>').join("")
  }
  document.getElementById("momentAdd")?.addEventListener("click",()=>{
    const inp=document.getElementById("momentInput"),v=inp?.value.trim();if(!v)return;
    const now=new Date();neoSave.moments=neoSave.moments||[];neoSave.moments.push({t:now.toLocaleDateString("zh-CN",{month:"2-digit",day:"2-digit"})+" "+now.toLocaleTimeString("zh-CN",{hour:"2-digit",minute:"2-digit"}),title:"LOCAL MOMENT",text:v,dot:["#ff62c9","#786aff","#35d9ef","#ffd857"][neoSave.moments.length%4]});
    neoSave.moments=neoSave.moments.slice(-12);persist();inp.value="";renderMoments();addBond(2,"你在这个世界里留下了新的文字。");speak("记下来了。<b>Moment saved locally.</b>")
  });
  document.getElementById("momentInput")?.addEventListener("keydown",e=>{if(e.key==="Enter")document.getElementById("momentAdd")?.click()});
  renderMoments();

  document.querySelectorAll(".chapterCard").forEach(ch=>ch.addEventListener("click",()=>{
    const key=ch.querySelector(".ep")?.textContent||"chapter";
    if(!neoSave.visited.includes(key)){neoSave.visited.push(key);persist();addBond(2,"探索了新的章节。")}
    const target=ch.dataset.target;if(target)setTimeout(()=>document.querySelector(target)?.scrollIntoView({behavior:"smooth"}),180)
  }));
  document.querySelectorAll(".acgCard").forEach((c,i)=>c.addEventListener("click",()=>{
    const lines=["番剧档案还空着。以后只记录你真正看过和喜欢的作品。","游戏档案还空着。以后可以把游玩时间、通关状态和难忘瞬间放进来。","漫画与小说架还在等待第一本真实收藏。","音乐舱以后可以接你自己的歌单与 OP/ED 收藏。"];
    speak(lines[i]||"这一格以后会变成真实的兴趣档案。");addBond(1,"查看了兴趣收藏架。")
  }));

  const epCard=document.querySelector(".episodeCard b");
  const epSections=[
    ["chapters","EP.01 // WORLD MAP"],["otaku","EP.02 // SAVE SYSTEM"],["moments","EP.03 // MOMENTS"],["acg","EP.04 // ACG SHELF"],
    ["projects","EP.05 // PROJECTS"],["articles","EP.06 // ARCHIVES"]
  ];
  const epObs=new IntersectionObserver(entries=>{
    const e=entries.find(x=>x.isIntersecting);if(e&&epCard){const hit=epSections.find(x=>x[0]===e.target.id);if(hit)epCard.textContent=hit[1]}
  },{threshold:.28});
  epSections.forEach(([id])=>{const el=document.getElementById(id);if(el)epObs.observe(el)});

})();