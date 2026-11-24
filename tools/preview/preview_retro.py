"""Maquetas del estilo LCD retro (radio de auto fines de los 90) para Espectro, Onda y Halo."""
import os, math, random
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),"..",".."))
HERE=os.path.dirname(os.path.abspath(__file__))
src=open(os.path.join(HERE,"preview_gen.py")).read()
exec(src[src.index("import math"):src.index("# ---------- 1.")])
def blend(a,b,f): return tuple(int(a[i]*(1-f)+b[i]*f) for i in range(3))
BG1=(22,34,86); BG2=(78,40,12); C1=(255,196,90); C2=(120,210,255)
GHOST=(255,255,255,22)
def grad():
    stops=[BG1,blend(BG1,BG2,0.6),blend(BG2,(0,0,0),0.35)]
    im=Image.new("RGB",(W,H)); g=ImageDraw.Draw(im)
    for k in range(0,W+H,2):
        t=k/(W+H); col=blend(stops[0],stops[1],t*2) if t<0.5 else blend(stops[1],stops[2],(t-0.5)*2)
        g.line([(k,0),(k-H,H)],fill=col,width=3)
    return im.convert("RGBA")
art=Image.new("RGB",(400,400)); ad=ImageDraw.Draw(art)
for y in range(400): ad.line([(0,y),(400,y)],fill=blend((120,200,230),(40,60,150),y/400))
ad.ellipse((190,70,350,230),fill=(250,170,70)); art=art.filter(ImageFilter.GaussianBlur(6))
def overlay(im,style):
    ov=Image.new("RGBA",im.size,(0,0,0,0)); od=ImageDraw.Draw(ov)
    od.text((d(48+56+14),d(32+28)),"Teardrop — Massive Attack",font=F("m",18),fill=(255,255,255),anchor="lm")
    sym=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",int(14*S)); f2=F("r",14)
    parts=[(f"{style}   ",f2),("◀ ▶",sym),(" cambiar estilo · OK pausa",f2)]
    w2=sum(od.textlength(t,font=f) for t,f in parts); y=H-d(64)
    od.rounded_rectangle((d(48),y,d(48)+w2+d(28),y+d(32)),radius=d(16),fill=(0,0,0,102))
    x=d(62)
    for t,f in parts: od.text((x,y+d(16)),t,font=f,fill=(221,221,221),anchor="lm"); x+=od.textlength(t,font=f)
    out=Image.alpha_composite(im,ov)
    th=art.resize((d(56),d(56)),Image.LANCZOS); m=Image.new("L",th.size,0); ImageDraw.Draw(m).rounded_rectangle((0,0,th.size[0]-1,th.size[1]-1),radius=d(8),fill=255)
    out.paste(th,(d(48),d(32)),m); return out.convert("RGB")
r=random.Random(3)
BANDS=24
level=[max(0.06,min(1,(1-b/BANDS)**0.55*0.95*(0.55+0.45*r.random()))) for b in range(BANDS)]
peak=[min(1,l+r.uniform(0.04,0.15)) for l in level]
def seg_color(f):  # abajo color 1 → arriba color 2, como los VU de la época
    return blend(C1,C2,f)

# ---------- Espectro LCD ----------
im=grad(); lay=Image.new("RGBA",im.size,(0,0,0,0)); dr=ImageDraw.Draw(lay)
SB=16  # menos barras y más anchas, como el ecualizador de una radio
lv=[level[int(k*BANDS/SB)] for k in range(SB)]; pkv=[peak[int(k*BANDS/SB)] for k in range(SB)]
total=SB*2; segs=22
areaW=W*0.74; left=(W-areaW)/2; slot=areaW/total; bw=slot*0.8
base=H*0.76; maxH=H*0.52; sh=maxH/segs; sgap=sh*0.38
level_,peak_=level,peak; level,peak=lv,pkv
for i in range(total):
    b=SB-1-i if i<SB else i-SB
    x=left+i*slot+(slot-bw)/2
    lit=int(round(level[b]*segs)); pk=min(segs-1,int(round(peak[b]*segs)))
    for k in range(segs):
        y1=base-k*sh; y0=y1-(sh-sgap)
        if k<lit: dr.rectangle((x,y0,x+bw,y1),fill=seg_color(k/(segs-1))+(255,))
        elif k==pk: dr.rectangle((x,y0,x+bw,y1),fill=(255,255,255,230))
        else: dr.rectangle((x,y0,x+bw,y1),fill=GHOST)
im=Image.alpha_composite(im,lay)
level,peak=level_,peak_
overlay(im,"Espectro").save("15_retro_espectro.png")

# ---------- Onda LCD (matriz de puntos) ----------
im=grad(); lay=Image.new("RGBA",im.size,(0,0,0,0)); dr=ImageDraw.Draw(lay)
px=d(9); gap=max(2,int(px*0.22))
cols=int(W*0.86/px); rows=int(H*0.5/px); x0=(W-cols*px)/2; y0=H*0.25
wav=[]; v=0
for i in range(cols): v=v*0.82+r.uniform(-1,1)*0.3; wav.append(v)
mid=rows/2
prev=None
litcells=set()
for c in range(cols):
    e=math.sin(math.pi*c/(cols-1))
    rr=int(round(mid+wav[c]*rows*0.42*e)); rr=max(0,min(rows-1,rr))
    if prev is None: prev=rr
    lo,hi=sorted((prev,rr))
    for k in range(lo,hi+1): litcells.add((c,k))
    prev=rr
for c in range(cols):
    for k in range(rows):
        x=x0+c*px; y=y0+k*px
        if (c,k) in litcells:
            t=abs(k-mid)/mid
            dr.rectangle((x,y,x+px-gap,y+px-gap),fill=blend(C1,C2,min(1,t*1.4))+(255,))
        else:
            dr.rectangle((x,y,x+px-gap,y+px-gap),fill=(255,255,255,14))
im=Image.alpha_composite(im,lay)
overlay(im,"Onda").save("16_retro_onda.png")

# ---------- Halo LCD ----------
im=grad(); lay=Image.new("RGBA",im.size,(0,0,0,0)); dr=ImageDraw.Draw(lay)
cx,cy=W/2,H/2; rad=min(W,H)*0.2; rays=BANDS*2*2  # 96 rayos
segsR=9; r0=rad*1.14; segL=rad*0.075; segG=segL*0.32
for i in range(rays):
    b=(i if i<rays//2 else rays-1-i)*BANDS//(rays//2)
    ang=2*math.pi*i/rays-math.pi/2; ca,sa=math.cos(ang),math.sin(ang)
    lit=int(round(level[b]*segsR)); wdt=2*math.pi*r0/rays*0.62
    for k in range(segsR):
        ra=r0+k*segL; rb=ra+segL-segG
        # segmento como cuadrilátero orientado al centro
        px_,py_=-sa*wdt/2,ca*wdt/2
        poly=[(cx+ca*ra+px_,cy+sa*ra+py_),(cx+ca*rb+px_,cy+sa*rb+py_),(cx+ca*rb-px_,cy+sa*rb-py_),(cx+ca*ra-px_,cy+sa*ra-py_)]
        dr.polygon(poly,fill=(seg_color(k/(segsR-1))+(255,)) if k<lit else GHOST)
im=Image.alpha_composite(im,lay)
a=art.resize((int(rad*2),int(rad*2)),Image.LANCZOS); m=Image.new("L",a.size,0); ImageDraw.Draw(m).ellipse((0,0,a.size[0]-1,a.size[1]-1),fill=255)
ring=Image.new("RGBA",im.size,(0,0,0,0)); ImageDraw.Draw(ring).ellipse((cx-rad*1.05,cy-rad*1.05,cx+rad*1.05,cy+rad*1.05),fill=C1+(90,))
im=Image.alpha_composite(im,ring); im.paste(a,(int(cx-rad),int(cy-rad)),m)
overlay(im,"Halo").save("17_retro_halo.png"); print("ok")
