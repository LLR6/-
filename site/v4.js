
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
