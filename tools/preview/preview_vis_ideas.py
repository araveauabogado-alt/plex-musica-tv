"""Maquetas de propuestas de visualizador (no implementadas aún)."""
import os, math, random
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),"..",".."))
HERE=os.path.dirname(os.path.abspath(__file__))
src=open(os.path.join(HERE,"preview_gen.py")).read()
exec(src[src.index("import math"):src.index("# ---------- 1.")])
def blend(a,b,f): return tuple(int(a[i]*(1-f)+b[i]*f) for i in range(3))
BG1=(22,34,86); BG2=(78,40,12); C1=(255,196,90); C2=(120,210,255)
def grad():
    stops=[BG1,blend(BG1,BG2,0.6),blend(BG2,(0,0,0),0.35)]
    im=Image.new("RGB",(W,H)); g=ImageDraw.Draw(im)
    for k in range(0,W+H,2):
        t=k/(W+H); col=blend(stops[0],stops[1],t*2) if t<0.5 else blend(stops[1],stops[2],(t-0.5)*2)
        g.line([(k,0),(k-H,H)],fill=col,width=3)
    return im
def label(im,txt):
    d_=ImageDraw.Draw(im); d_.text((d(48),H-d(48)),txt,font=F("m",16),fill=(230,230,230),anchor="ls"); return im
r=random.Random(7)
# A) Pulsar: líneas apiladas tipo Joy Division (historia del espectro)
im=grad(); dr=ImageDraw.Draw(im)
rows=36; cols=120; top=H*0.18; bot=H*0.86
for k in range(rows):
    y0=top+(bot-top)*k/(rows-1); pts=[]
    amp=d(70)*(0.6+0.4*k/rows)
    peaks=[(r.uniform(0.35,0.65),r.uniform(0.02,0.07),r.uniform(0.3,1)) for _ in range(5)]
    for i in range(cols):
        x=W*0.22+W*0.56*i/(cols-1); u=i/(cols-1)
        env=math.exp(-((u-0.5)/0.16)**2)
        v=sum(p[2]*math.exp(-((u-p[0])/p[1])**2) for p in peaks)*env+r.uniform(0,0.05)*env
        pts.append((x,y0-v*amp))
    poly=pts+[(pts[-1][0],y0+d(4)),(pts[0][0],y0+d(4))]
    dr.polygon(poly,fill=blend(BG1,(0,0,0),0.5))
    col=blend(C2,C1,k/rows)
    dr.line(pts,fill=col,width=max(2,d(1.6)),joint="curve")
label(im,"A · Pulsar — inspirado en \"Pulsar Joy\" de Plexamp").save("12_idea_pulsar.png")
# B) Magnetósfera: partículas orbitando atractores (iTunes Magnetosphere)
im=grad().convert("RGBA"); glow=Image.new("RGBA",(W,H),(0,0,0,0)); g=ImageDraw.Draw(glow)
att=[(W*0.38,H*0.45,1.0),(W*0.6,H*0.52,0.8),(W*0.5,H*0.35,0.6)]
for n in range(5200):
    ax,ay,s=r.choice(att); ang=r.uniform(0,2*math.pi); rad=abs(r.gauss(0,1))*d(140)*s+d(10)
    x=ax+math.cos(ang)*rad*1.3; y=ay+math.sin(ang)*rad*0.8
    t=min(1,rad/(d(260)*s)); col=blend(C1,C2,t); a=int(220*(1-t*0.7))
    sz=r.choice([1,1,1,2])*S/2
    g.ellipse((x-sz,y-sz,x+sz,y+sz),fill=col+(a,))
halo=glow.filter(ImageFilter.GaussianBlur(d(6)))
im=Image.alpha_composite(im,halo); im=Image.alpha_composite(im,glow)
label(im.convert("RGB"),"B · Magnetósfera — inspirado en el visualizador clásico de iTunes").save("13_idea_magnetosfera.png")
# C) Ambiente: manchas de color de la carátula que se mueven lento (estilo Apple Music) + carátula
im=Image.new("RGB",(W,H),blend(BG1,(0,0,0),0.4)); bl=Image.new("RGB",(W,H),(0,0,0)); b=ImageDraw.Draw(bl)
for (x,y,rr,col) in [(0.25,0.3,0.35,C2),(0.75,0.35,0.3,C1),(0.55,0.8,0.38,(220,110,60)),(0.15,0.85,0.25,(60,80,200))]:
    b.ellipse((W*x-W*rr,H*y-W*rr,W*x+W*rr,H*y+W*rr),fill=blend(col,(0,0,0),0.35))
bl=bl.filter(ImageFilter.GaussianBlur(d(120)))
im=Image.blend(im,bl,0.85)
art=Image.new("RGB",(d(300),d(300))); ad=ImageDraw.Draw(art)
for y in range(art.height): ad.line([(0,y),(art.width,y)],fill=blend((120,200,230),(40,60,150),y/art.height))
ad.ellipse((d(140),d(55),d(270),d(185)),fill=(250,170,70)); art=art.filter(ImageFilter.GaussianBlur(d(5)))
m=Image.new("L",art.size,0); ImageDraw.Draw(m).rounded_rectangle((0,0,art.width-1,art.height-1),radius=d(14),fill=255)
sh=Image.new("RGBA",(W,H),(0,0,0,0)); ImageDraw.Draw(sh).rounded_rectangle((W/2-d(150)+d(8),H*0.42-d(150)+d(14),W/2+d(150)+d(8),H*0.42+d(150)+d(14)),radius=d(14),fill=(0,0,0,120))
im=Image.alpha_composite(im.convert("RGBA"),sh.filter(ImageFilter.GaussianBlur(d(14)))).convert("RGB")
im.paste(art,(int(W/2-d(150)),int(H*0.42-d(150))),m)
dr=ImageDraw.Draw(im); dr.text((W/2,H*0.42+d(185)),"Teardrop",font=F("b",26),fill=(255,255,255),anchor="mm"); dr.text((W/2,H*0.42+d(218)),"Massive Attack",font=F("r",17),fill=(230,230,230),anchor="mm")
label(im,"C · Ambiente — colores de la carátula en movimiento lento (estilo Apple Music)").save("14_idea_ambiente.png"); print("ok")
