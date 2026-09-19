
const $=(s,r=document)=>r.querySelector(s), $$=(s,r=document)=>[...r.querySelectorAll(s)];

const hero=$(".hero"), bg=$(".heroBg");
if(hero&&bg){
  hero.addEventListener("pointermove",e=>{
    if(innerWidth<760)return;
    const r=hero.getBoundingClientRect();
    const x=(e.clientX-r.left)/r.width-.5, y=(e.clientY-r.top)/r.height-.5;
    bg.style.transform=`scale(1.035) translate(${-x*8}px,${-y*5}px)`;
  });
  hero.addEventListener("pointerleave",()=>bg.style.transform="scale(1.015)");
}

const io=new IntersectionObserver(es=>{
  es.forEach(e=>{if(e.isIntersecting)e.target.classList.add("on")})
},{threshold:.12});
$$(".fade").forEach(x=>io.observe(x));

const defaults=[
  {t:"SYSTEM",title:"LR//NEO v4",text:"删掉多余的 HUD、窗口和卡通头，回到更克制的二次元个人博客。"},
  {t:"NOTE",title:"有些内容不值得单独写文章",text:"但一句话、一个念头、一张图，也值得留下。"},
  {t:"NEXT",title:"这里会慢慢长",text:"番剧、游戏、技术、学习和生活，不急着一次填满。"}
];
const KEY="lr-neo-moments-v4";
let local=[];
try{local=JSON.parse(localStorage.getItem(KEY)||"[]")}catch(e){}
const board=$("#momentBoard");
function esc(v){return String(v).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]))}
function render(){
  if(!board)return;
  const rows=[...local.slice().reverse(),...defaults].slice(0,8);
  board.innerHTML=rows.map(m=>`<article class="moment"><time>${esc(m.t)}</time><b>${esc(m.title)}</b><p>${esc(m.text)}</p></article>`).join("");
}
render();
$("#momentAdd")?.addEventListener("click",()=>{
  const input=$("#momentInput"),v=input.value.trim();
  if(!v)return;
  const d=new Date();
  local.push({t:d.toLocaleDateString("zh-CN",{month:"2-digit",day:"2-digit"})+" "+d.toLocaleTimeString("zh-CN",{hour:"2-digit",minute:"2-digit"}),title:"LOCAL MOMENT",text:v});
  local=local.slice(-12);
  localStorage.setItem(KEY,JSON.stringify(local));
  input.value="";render();
});
$("#momentInput")?.addEventListener("keydown",e=>{if(e.key==="Enter")$("#momentAdd")?.click()});

$("#year").textContent=new Date().getFullYear();


// MIT-licensed APlayer integration.
// Audio stays local to the browser: selected files are played through Blob URLs and are never uploaded.
const musicToggle=$("#musicToggle"),musicPanel=$("#musicPanel"),musicClose=$("#musicClose"),musicFiles=$("#musicFiles");
let localPlayer=null,localAudioUrls=[];
function closeMusic(){musicPanel?.classList.remove("open")}
musicToggle?.addEventListener("click",()=>musicPanel?.classList.toggle("open"));
musicClose?.addEventListener("click",closeMusic);

musicFiles?.addEventListener("change",()=>{
  const files=[...(musicFiles.files||[])];
  if(!files.length)return;
  localAudioUrls.forEach(URL.revokeObjectURL);
  localAudioUrls=[];
  const audio=files.map(file=>{
    const url=URL.createObjectURL(file);localAudioUrls.push(url);
    return {
      name:file.name.replace(/.[^.]+$/,""),
      artist:"LOCAL FILE",
      url,
      cover:"./assets/anime-boy.webp",
      theme:"#9bd8ff"
    };
  });
  try{localPlayer?.destroy()}catch(e){}
  $("#aplayer").innerHTML="";
  localPlayer=new APlayer({
    container:$("#aplayer"),
    autoplay:false,
    theme:"#9bd8ff",
    loop:"all",
    order:"list",
    preload:"metadata",
    volume:.65,
    listFolded:false,
    audio
  });
});
addEventListener("beforeunload",()=>localAudioUrls.forEach(URL.revokeObjectURL));


// Seasonal anime schedule — data snapshot from bangumi-data (CC BY 4.0)
let onairPool=[];
const dayNames=["SUN","MON","TUE","WED","THU","FRI","SAT"];
function getNextBroadcast(item,now=new Date()){
  const base=new Date(item.begin);
  if(Number.isNaN(base.getTime()))return null;
  const week=7*24*60*60*1000;
  let nextTime=base.getTime();
  if(nextTime<now.getTime()){
    const n=Math.ceil((now.getTime()-nextTime)/week);
    nextTime+=n*week;
  }
  const end=item.end?new Date(item.end).getTime():Infinity;
  if(nextTime>end)return null;
  return new Date(nextTime);
}
function animeName(item){
  return item.titleTranslate?.["zh-Hans"]?.[0]||item.titleTranslate?.["zh-Hant"]?.[0]||item.title;
}
function animeBangumiUrl(item){
  const hit=(item.sites||[]).find(x=>x.site==="bangumi");
  return hit?("https://bangumi.tv/subject/"+hit.id):item.officialSite||"#";
}
function renderOnair(list){
  const grid=$("#onairGrid");if(!grid)return;
  if(!list.length){grid.innerHTML='<div class="onairLoading">暂时没有可计算的本季放送数据。</div>';return}
  grid.innerHTML=list.map(({item,next})=>{
    const name=animeName(item),day=dayNames[next.getDay()];
    const time=next.toLocaleString("zh-CN",{month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit"});
    return `<a class="onairCard" data-day="${day}" href="${esc(animeBangumiUrl(item))}" target="_blank" rel="noreferrer">
      <div class="onairTop"><span class="onairBadge">${day} / TV</span><span class="onairTime">NEXT BROADCAST</span></div>
      <h3>${esc(name)}</h3>
      <div class="onairJp">${esc(item.title)}</div>
      <div class="onairMeta"><span class="onairNext">${esc(time)}</span><span class="onairLink">BANGUMI ↗</span></div>
    </a>`;
  }).join("");
}
async function initOnair(){
  const label=$("#onairNow");
  const stamp=()=>{if(label)label.textContent="LOCAL TIME / "+new Date().toLocaleString("zh-CN",{month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",second:"2-digit"})};
  stamp();setInterval(stamp,1000);
  try{
    const res=await fetch("./data/anime-2026-07.json",{cache:"no-store"});
    if(!res.ok)throw new Error("HTTP "+res.status);
    const data=await res.json(),now=new Date();
    onairPool=data.filter(x=>x.type==="tv").map(item=>({item,next:getNextBroadcast(item,now)})).filter(x=>x.next).sort((a,b)=>a.next-b.next);
    renderOnair(onairPool.slice(0,8));
  }catch(err){
    const grid=$("#onairGrid");if(grid)grid.innerHTML='<div class="onairLoading">本季番组数据读取失败，稍后刷新再试。</div>';
  }
}
$("#onairShuffle")?.addEventListener("click",()=>{
  if(onairPool.length<=8){renderOnair(onairPool);return}
  const copy=[...onairPool];
  for(let i=copy.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[copy[i],copy[j]]=[copy[j],copy[i]]}
  renderOnair(copy.slice(0,8).sort((a,b)=>a.next-b.next));
});
initOnair();
