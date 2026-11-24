import os
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),"..",".."))
HERE=os.path.dirname(os.path.abspath(__file__))
import math, random, re
from PIL import Image, ImageDraw, ImageFont, ImageFilter
S=2  # 1 dp = 2 px (1920x1080)
W,H=960*S,540*S
FD=REPO+"/app/src/main/res/font/"
def F(w,sp): return ImageFont.truetype(FD+{"r":"dmsans_regular","m":"dmsans_medium","b":"dmsans_bold"}[w]+".ttf", int(sp*S))
BG=(18,18,18); ACC=(229,160,13); WHITE=(255,255,255); SUB=(170,170,170)
def d(v): return int(v*S)
def cover(size, seed):
    r=random.Random(seed); c1=tuple(r.randint(30,230) for _ in range(3)); c2=tuple(r.randint(10,180) for _ in range(3))
    im=Image.new("RGB",(size,size),c1); dr=ImageDraw.Draw(im)
    for y in range(size):
        t=y/size; dr.line([(0,y),(size,y)],fill=tuple(int(c1[i]*(1-t)+c2[i]*t) for i in range(3)))
    for _ in range(3):
        x,y,rr=r.randint(0,size),r.randint(0,size),r.randint(size//6,size//2)
        dr.ellipse((x-rr,y-rr,x+rr,y+rr),fill=tuple(min(255,int(v*1.25)) for v in c2))
    return im.filter(ImageFilter.GaussianBlur(size/30)), c1, c2
def pill(dr,x,y,text,focused=False,selected=False,font=None):
    font=font or F("m",16); tw=dr.textlength(text,font=font); w=tw+d(40); h=d(42)
    fill=ACC if focused else ((229,160,13,51) if selected else (255,255,255,38))
    col=(0,0,0) if focused else (ACC if selected else WHITE)
    dr.rounded_rectangle((x,y,x+w,y+h),radius=d(21),fill=fill)
    if text.startswith("▶ "):
        sym=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",int(14*S))
        dr.text((x+d(20),y+h/2),"▶",font=sym,fill=col,anchor="lm"); dr.text((x+d(38),y+h/2),text[2:],font=font,fill=col,anchor="lm")
    else:
        dr.text((x+d(20),y+h/2),text,font=font,fill=col,anchor="lm")
    return x+w+d(12)

# ---------- 1. Pantalla principal ----------
im=Image.new("RGBA",(W,H),BG+(255,)); ov=Image.new("RGBA",(W,H),(0,0,0,0)); dr=ImageDraw.Draw(ov)
x=d(54); y=d(33)
for t,f,s in [("Artistas",False,True),("Álbumes",True,False),("Playlists",0,0),("Buscar",0,0),("Orden: A–Z",0,0),("▶ Reproduciendo",0,0),("Cerrar sesión",0,0)]:
    x=pill(dr,x,y,t,f,s)
letters=["#","A","B","C","D","E","F","G","H","J","K","L","M","N","O","P","R","S","T","U","V","W","Y"]
x=d(54); y=d(85)
for i,l in enumerate(letters):
    f=F("b",15); w=max(d(30),dr.textlength(l,font=f)+d(16))
    dr.rounded_rectangle((x,y,x+w,y+d(36)),radius=d(18),fill=ACC if l=="M" else (255,255,255,38))
    dr.text((x+w/2,y+d(18)),l,font=f,fill=(0,0,0) if l=="M" else WHITE,anchor="mm"); x+=w+d(4)
im=Image.alpha_composite(im,ov); dr=ImageDraw.Draw(im)
names=[("Massive Attack","Mezzanine · 1998"),("Mazzy Star","So Tonight That I Might See"),("Metronomy","The English Riviera · 2011"),("MGMT","Oracular Spectacular · 2007"),("Mica Levi","Under the Skin · 2014"),("Miles Davis","Kind of Blue · 1959"),("Moby","Play · 1999"),
       ("Moderat","II · 2013"),("Mogwai","Young Team · 1997"),("Molchat Doma","Etazhi · 2018"),("Mon Laferte","Norma · 2018"),("Morcheeba","Big Calm · 1998"),("Mount Kimbie","Love What Survives"),("Mulatu Astatke","Ethio Jazz")]
cols=7; gx0=d(48); cw=(W-d(96))/cols; y0=d(133)
for i,(t,s) in enumerate(names):
    r,c=divmod(i,cols); x=gx0+c*cw; y=y0+r*d(170)
    foc=(i==2); sc=1.1 if foc else 1
    pad=d(6); inner=cw-2*d(6)-2*pad
    card=Image.new("RGBA",(int(cw-2*d(6)),d(158)),(0,0,0,0)); cd=ImageDraw.Draw(card)
    if foc: cd.rounded_rectangle((0,0,card.width-1,card.height-1),radius=d(10),fill=(42,42,42),outline=ACC,width=d(3))
    cv,_,_=cover(int(inner),i+7); card.paste(cv,(pad,pad))
    cd.text((pad,pad+inner+d(5)),t,font=F("m",13),fill=WHITE)
    cd.text((pad,pad+inner+d(23)),s if len(s)<20 else s[:18]+"…",font=F("r",11),fill=SUB)
    if foc: card=card.resize((int(card.width*sc),int(card.height*sc)),Image.LANCZOS)
    ox=int(x+d(6)-(card.width-(cw-2*d(6)))/2); oy=int(y-(card.height-d(158))/2)
    im.alpha_composite(card,(ox,oy))
im.convert("RGB").save("1_principal.png")

# ---------- 2. Reproduciendo ----------
cv,c1,c2=cover(d(320),42)
c1,c2=(120,200,230),(40,60,150)
cv=Image.new("RGB",(d(320),d(320))); cdr=ImageDraw.Draw(cv)
for yy in range(d(320)):
    t=yy/d(320); cdr.line([(0,yy),(d(320),yy)],fill=tuple(int(c1[i]*(1-t)+c2[i]*t) for i in range(3)))
cdr.ellipse((d(150),d(60),d(290),d(200)),fill=(250,190,90)); cv=cv.filter(ImageFilter.GaussianBlur(d(6)))
import colorsys
def darken(c,f): return tuple(int(v*f) for v in c)
top=darken(c2,0.55); bot=darken(top,0.3)
im=Image.new("RGB",(W,H)); dr=ImageDraw.Draw(im)
for yy in range(H):
    for_t=yy/H
for k in range(0,W+H,2):
    t=k/(W+H); col=tuple(int(top[i]*(1-t)+bot[i]*t) for i in range(3)); dr.line([(k,0),(k-H,H)],fill=col,width=3)
accent=tuple(min(255,int(v*0.4+255*0.6)) for v in c1)
im.paste(cv,(d(64),(H-d(320))//2))
x0=d(64+320+48); xr=W-d(64); y=d(138)
dr.text((x0,y),"Teardrop",font=F("b",30),fill=WHITE); y+=d(46)
dr.text((x0,y),"Massive Attack",font=F("r",20),fill=accent); y+=d(30)
dr.text((x0,y),"Mezzanine",font=F("r",18),fill=SUB); y+=d(52)
# waveform
n=96; r=random.Random(5); lv=[]; v=0.5
for i in range(n):
    v=max(0.08,min(1,v+r.uniform(-0.18,0.18))); env=0.35+0.65*math.sin(math.pi*min(1,(i+4)/n))
    lv.append(max(0.08,v*env))
slot=(xr-x0)/n; bar=max(3,slot*0.55); prog=0.38; wh=d(44)
for i,l in enumerate(lv):
    bx=x0+i*slot+(slot-bar)/2; bh=max(bar,l*wh); ty=y+(wh-bh)/2
    dr.rounded_rectangle((bx,ty,bx+bar,ty+bh),radius=bar/2,fill=accent if (i+0.5)/n<=prog else tuple(int(top[j]*0.75+255*0.25) for j in range(3)))
y+=wh+d(8)
dr.rectangle((x0,y,xr,y+d(4)),fill=tuple(int(top[j]*0.67+255*0.33) for j in range(3))); dr.rectangle((x0,y,x0+(xr-x0)*prog,y+d(4)),fill=accent)
y+=d(10); dr.text((x0,y),"2:01",font=F("r",16),fill=SUB); dr.text((xr,y),"5:30",font=F("r",16),fill=SUB,anchor="ra"); y+=d(48)
ICONS={"prev":"M6,6h2v12H6zM9.5,12l8.5,6V6z"}
bx=x0
def circle(cx,cy,rad,foc):
    dr.ellipse((cx-rad,cy-rad,cx+rad,cy+rad),fill=ACC if foc else tuple(int(top[j]*0.85+255*0.15) for j in range(3)))
def glyph(kind,cx,cy,s,col):
    if kind=="play": dr.polygon([(cx-s*0.35,cy-s*0.45),(cx-s*0.35,cy+s*0.45),(cx+s*0.45,cy)],fill=col)
    if kind=="pause": dr.rectangle((cx-s*0.35,cy-s*0.42,cx-s*0.1,cy+s*0.42),fill=col); dr.rectangle((cx+s*0.1,cy-s*0.42,cx+s*0.35,cy+s*0.42),fill=col)
    if kind=="next": dr.polygon([(cx-s*0.4,cy-s*0.4),(cx-s*0.4,cy+s*0.4),(cx+s*0.2,cy)],fill=col); dr.rectangle((cx+s*0.22,cy-s*0.4,cx+s*0.36,cy+s*0.4),fill=col)
    if kind=="prev": dr.polygon([(cx+s*0.4,cy-s*0.4),(cx+s*0.4,cy+s*0.4),(cx-s*0.2,cy)],fill=col); dr.rectangle((cx-s*0.36,cy-s*0.4,cx-s*0.22,cy+s*0.4),fill=col)
    if kind=="shuffle":
        w=d(3); dr.line([(cx-s*0.4,cy-s*0.3),(cx+s*0.4,cy+s*0.3)],fill=col,width=w); dr.line([(cx-s*0.4,cy+s*0.3),(cx+s*0.4,cy-s*0.3)],fill=col,width=w)
    if kind=="queue":
        w=d(3)
        for k in range(3): dr.line([(cx-s*0.42,cy-s*0.3+k*s*0.25),(cx+s*0.05,cy-s*0.3+k*s*0.25)],fill=col,width=w)
        dr.ellipse((cx+s*0.05,cy+s*0.1,cx+s*0.35,cy+s*0.4),fill=col); dr.line([(cx+s*0.33,cy+s*0.25),(cx+s*0.33,cy-s*0.42)],fill=col,width=w)
import subprocess, io
RES=REPO+"/app/src/main/res/drawable/"
def icon(name,px,col):
    xml=open(RES+name+".xml").read(); p=re.search(r'pathData="([^"]+)"',xml).group(1)
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" viewBox="0 0 24 24"><path fill="rgb{col}" d="{p}"/></svg>'
    png=subprocess.run(["rsvg-convert"],input=svg.encode(),capture_output=True).stdout
    return Image.open(io.BytesIO(png)).convert("RGBA")
cy=y+d(40)
for kind,rad,foc in [("prev",32,0),("pause",40,1),("next",32,0),("shuffle",32,0),("queue",32,0)]:
    cx=bx+d(rad); circle(cx,cy,d(rad),foc)
    ic=icon({"prev":"ic_prev","pause":"ic_pause","next":"ic_next","shuffle":"ic_shuffle","queue":"ic_queue"}[kind], int(d(rad*2-28)), (0,0,0) if foc else WHITE)
    im.paste(ic,(int(cx-ic.width/2),int(cy-ic.height/2)),ic); bx+=d(rad*2+20)
y=cy+d(40)+d(24)
dr.text((x0,y),"A continuación: Angel — Massive Attack",font=F("r",16),fill=SUB)
im.save("2_reproduciendo.png")

# ---------- 3. Cola ----------
im=Image.new("RGBA",(W,H),BG+(255,)); dr=ImageDraw.Draw(im)
dr.text((d(48),d(33)),"Cola de reproducción",font=F("b",26),fill=WHITE)
ov=Image.new("RGBA",(W,H),(0,0,0,0)); od=ImageDraw.Draw(ov)
f=F("m",16); tw=od.textlength("Vaciar lo que sigue",font=f); x2=W-d(48); x1=x2-tw-d(40)
od.rounded_rectangle((x1,d(30),x2,d(72)),radius=d(21),fill=(255,255,255,38)); od.text((x1+d(20),d(51)),"Vaciar lo que sigue",font=f,fill=WHITE,anchor="lm")
im=Image.alpha_composite(im,ov); dr=ImageDraw.Draw(im)
dr.text((d(48),d(78)),"6 canciones a continuación   ·   OK: reproducir · Mantén OK: más opciones",font=F("r",14),fill=SUB)
rows=[("Teardrop","Massive Attack · Mezzanine","5:30",42),("Angel","Massive Attack · Mezzanine","6:18",42),("Fade Into You","Mazzy Star · So Tonight That I Might See","4:55",3),("The Bay","Metronomy · The English Riviera","5:00",4),("Time to Pretend","MGMT · Oracular Spectacular","4:21",5),("So What","Miles Davis · Kind of Blue","9:22",8),("Porcelain","Moby · Play","4:01",9)]
y=d(108)
for i,(t,s,du,seed) in enumerate(rows):
    h=d(56); foc=(i==2)
    ov=Image.new("RGBA",(W,H),(0,0,0,0)); od=ImageDraw.Draw(ov)
    if foc: od.rounded_rectangle((d(50),y,W-d(50),y+h),radius=d(8),fill=(229,160,13,51),outline=ACC,width=d(2))
    im=Image.alpha_composite(im,ov); dr=ImageDraw.Draw(im)
    cv,_,_=cover(d(44),seed); im.paste(cv,(d(60),y+d(6)))
    dr.text((d(118),y+d(9)),t,font=F("m",15),fill=ACC if i==0 else WHITE)
    dr.text((d(118),y+d(30)),s,font=F("r",12),fill=SUB)
    dr.text((W-d(62),y+h/2),du,font=F("r",14),fill=SUB,anchor="rm")
    if i==0: dr.text((W-d(110),y+h/2),"SONANDO",font=F("b",12),fill=ACC,anchor="rm")
    y+=h+d(4)
im.convert("RGB").save("3_cola.png")
print("ok")
