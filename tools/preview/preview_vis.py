"""Preview de los tres visualizadores (Espectro, Onda, Halo) con datos de audio sintéticos."""
import os, math, random
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),"..",".."))
HERE=os.path.dirname(os.path.abspath(__file__))
src=open(os.path.join(HERE,"preview_gen.py")).read()
exec(src[src.index("import math"):src.index("# ---------- 1.")])
def blend(a,b,f): return tuple(int(a[i]*(1-f)+b[i]*f) for i in range(3))
c1=(255,196,90); c2=(120,210,255)
art=Image.new("RGB",(400,400)); ad=ImageDraw.Draw(art)
for y in range(400): ad.line([(0,y),(400,y)],fill=blend((120,200,230),(40,60,150),y/400))
ad.ellipse((190,70,350,230),fill=(250,170,70)); art=art.filter(ImageFilter.GaussianBlur(6))
small=art.resize((16,16),Image.BILINEAR)
bands=48; r=random.Random(3)
level=[max(0.05,min(1,(1-b/bands)**0.6*0.95*(0.55+0.45*r.random()))) for b in range(bands)]
BG1=(22,34,86); BG2=(78,40,12)
def base():
    stops=[BG1,blend(BG1,BG2,0.6),blend(BG2,(0,0,0),0.35)]
    im=Image.new("RGB",(W,H)); g=ImageDraw.Draw(im)
    for k in range(0,W+H,2):
        t=k/(W+H); col=blend(stops[0],stops[1],t*2) if t<0.5 else blend(stops[1],stops[2],(t-0.5)*2)
        g.line([(k,0),(k-H,H)],fill=col,width=3)
    return im
def overlay(im,style):
    ov=Image.new("RGBA",im.size,(0,0,0,0)); od=ImageDraw.Draw(ov)
    t1="Teardrop — Massive Attack"; f1=F("m",18)
    od.text((d(48+56+14),d(32+28)),t1,font=f1,fill=(255,255,255),anchor="lm")
    sym=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",int(14*S))
    t2=f"{style}   "; f2=F("r",14); w2=od.textlength(t2,font=f2)+od.textlength("◀ ▶",font=sym)+od.textlength(" cambiar estilo · OK pausa",font=f2)
    y=H-d(32)-d(32); od.rounded_rectangle((d(48),y,d(48)+w2+d(28),y+d(32)),radius=d(16),fill=(0,0,0,102))
    x=d(62); od.text((x,y+d(16)),t2,font=f2,fill=(221,221,221),anchor="lm"); x+=od.textlength(t2,font=f2)
    od.text((x,y+d(16)),"◀ ▶",font=sym,fill=(221,221,221),anchor="lm"); x+=od.textlength("◀ ▶",font=sym)
    od.text((x,y+d(16))," cambiar estilo · OK pausa",font=f2,fill=(221,221,221),anchor="lm")
    out=Image.alpha_composite(im.convert("RGBA"),ov)
    th=art.resize((d(56),d(56)),Image.LANCZOS); m=Image.new("L",th.size,0); ImageDraw.Draw(m).rounded_rectangle((0,0,th.size[0]-1,th.size[1]-1),radius=d(8),fill=255)
    out.paste(th,(d(48),d(32)),m)
    return out.convert("RGB")
# Espectro
im=base(); dr=ImageDraw.Draw(im,"RGBA")
total=bands*2; areaW=W*0.78; left=(W-areaW)/2; slot=areaW/total; bar=slot*0.56; bl=H*0.72; maxH=H*0.5
for i in range(total):
    b=bands-1-i if i<bands else i-bands; x=left+i*slot+(slot-bar)/2; bh=max(bar,level[b]*maxH)
    steps=24
    for k in range(steps):
        y0=bl-bh+bh*k/steps; y1=bl-bh+bh*(k+1)/steps; t=(y0-(bl-maxH))/maxH
        dr.rectangle((x,y0,x+bar,y1),fill=blend(c2,c1,max(0,min(1,t)))+(255,))
    dr.rounded_rectangle((x,bl+6,x+bar,bl+6+bh*0.25),radius=bar/2,fill=c1+(50,))
    py=bl-max(bar,min(1,level[b]+0.06)*maxH)-bar*1.6; dr.ellipse((x+bar/2-bar*0.32,py-bar*0.32,x+bar/2+bar*0.32,py+bar*0.32),fill=(255,255,255,200))
overlay(im,"Espectro").save("7_vis_espectro.png")
# Onda
im=base(); dr=ImageDraw.Draw(im,"RGBA"); mid=H*0.5; amp=H*0.32
wav=[]; v=0
for i in range(160): v=v*0.8+r.uniform(-1,1)*0.25; wav.append(v)
for li,(col,sc,al) in enumerate([(c2,1.0,0.35),(c1,0.7,0.6),((255,255,255),0.45,0.9)]):
    pts=[]
    for i in range(160):
        x=W*0.06+W*0.88*i/159; e=math.sin(math.pi*i/159); y=mid+wav[i]*1.4*amp*sc*e
        pts.append((x,y))
    dr.line(pts,fill=col+(int(al*70),),width=d(7),joint="curve"); dr.line(pts,fill=col+(int(al*255),),width=d(2),joint="curve")
overlay(im,"Onda").save("8_vis_onda.png")
# Halo
im=base(); dr=ImageDraw.Draw(im,"RGBA"); cx,cy=W/2,H/2; rad=min(W,H)*0.2*1.05; rays=bands*2
for i in range(rays):
    b=i if i<bands else rays-1-i; ang=2*math.pi*i/rays-math.pi/2; ln=rad*(0.06+level[b]*0.7); r0=rad*1.12
    t=(math.sin(ang)+1)/2; col=blend(c1,c2,t)
    dr.line((cx+math.cos(ang)*r0,cy+math.sin(ang)*r0,cx+math.cos(ang)*(r0+ln),cy+math.sin(ang)*(r0+ln)),fill=col+(255,),width=max(2,int(2*math.pi*rad/rays*0.5)))
dr.ellipse((cx-rad*1.06,cy-rad*1.06,cx+rad*1.06,cy+rad*1.06),fill=c1+(110,))
a=art.resize((int(rad*2),int(rad*2)),Image.LANCZOS); m=Image.new("L",a.size,0); ImageDraw.Draw(m).ellipse((0,0,a.size[0]-1,a.size[1]-1),fill=255)
im.paste(a,(int(cx-rad),int(cy-rad)),m)
overlay(im,"Halo").save("9_vis_halo.png"); print("ok")
