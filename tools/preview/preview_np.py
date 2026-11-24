import os
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),"..",".."))
HERE=os.path.dirname(os.path.abspath(__file__))
src=open(os.path.join(HERE,"preview_gen.py")).read()
exec(src[src.index("import math"):src.index("# ---------- 1.")])
import subprocess, io
RES=REPO+"/app/src/main/res/drawable/"
def icon(name,px,col):
    xml=open(RES+name+".xml").read(); p=re.search(r'pathData="([^"]+)"',xml).group(1)
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" viewBox="0 0 24 24"><path fill="rgb{col}" d="{p}"/></svg>'
    return Image.open(io.BytesIO(subprocess.run(["rsvg-convert"],input=svg.encode(),capture_output=True).stdout)).convert("RGBA")
def blend(a,b,f): return tuple(int(a[i]*(1-f)+b[i]*f) for i in range(3))
# cover: blue sky + orange sun
c1,c2=(120,200,230),(40,60,150)
cv=Image.new("RGB",(d(320),d(320))); cdr=ImageDraw.Draw(cv)
for yy in range(d(320)):
    t=yy/d(320); cdr.line([(0,yy),(d(320),yy)],fill=blend(c1,c2,t))
cdr.ellipse((d(150),d(60),d(290),d(200)),fill=(250,170,70)); cv=cv.filter(ImageFilter.GaussianBlur(d(6)))
bg1=(22,34,86); bg2=(78,40,12); stops=[bg1,blend(bg1,bg2,0.6),blend(bg2,(0,0,0),0.35)]
im=Image.new("RGB",(W,H)); dr=ImageDraw.Draw(im)
for k in range(0,W+H,2):
    t=k/(W+H); col=blend(stops[0],stops[1],t*2) if t<0.5 else blend(stops[1],stops[2],(t-0.5)*2)
    dr.line([(k,0),(k-H,H)],fill=col,width=3)
accent=(255,205,140)
im.paste(cv,(d(64),(H-d(320))//2))
x0=d(64+320+48); xr=W-d(64); y=d(130)
dr.text((x0,y),"Teardrop",font=F("b",30),fill=WHITE); y+=d(46)
dr.text((x0,y),"Massive Attack",font=F("r",20),fill=accent); y+=d(30)
dr.text((x0,y),"Mezzanine",font=F("r",18),fill=SUB); y+=d(42)
# focused seek area
ov=Image.new("RGBA",(W,H),(0,0,0,0)); od=ImageDraw.Draw(ov)
od.rounded_rectangle((x0-d(8),y,xr+d(8),y+d(8+44+8+4+8)),radius=d(10),fill=(255,255,255,26),outline=(255,255,255,153),width=d(2))
im=Image.alpha_composite(im.convert("RGBA"),ov); dr=ImageDraw.Draw(im)
y+=d(8)
n=72; r=random.Random(5); lv=[]; v=0.5
for i in range(n):
    v=max(0.08,min(1,v+r.uniform(-0.2,0.2))); env=0.35+0.65*math.sin(math.pi*min(1,(i+4)/n)); lv.append(max(0.08,v*env))
slot=(xr-x0)/n; bar=max(4,slot*0.42); prog=0.38; wh=d(44)
for i,l in enumerate(lv):
    pos=(i+0.5)/n; dd=abs(pos-prog)*n
    if dd<4: l*=1+0.18*(1-dd/4)*0.8
    bx=x0+i*slot+(slot-bar)/2; bh=max(bar,min(1,l)*wh); ty=y+(wh-bh)/2
    dr.rounded_rectangle((bx,ty,bx+bar,ty+bh),radius=bar/2,fill=accent if pos<=prog else blend(blend(bg1,bg2,0.5),(255,255,255),0.2))
y+=wh+d(8)
dr.rectangle((x0,y,xr,y+d(4)),fill=blend(blend(bg1,bg2,0.5),(255,255,255),0.33)); dr.rectangle((x0,y,x0+(xr-x0)*prog,y+d(4)),fill=accent)
y+=d(4+8+10)
dr.text((x0,y),"2:01",font=F("r",16),fill=SUB)
dr.text((xr-d(60),y+d(2)),"◀ ▶ retroceder / adelantar",font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",int(13*S)),fill=SUB,anchor="ra")
dr.text((xr,y),"5:30",font=F("r",16),fill=SUB,anchor="ra"); y+=d(48)
bx=x0; cy=y+d(40)
for name,rad in [("ic_prev",32),("ic_play",40),("ic_next",32),("ic_shuffle",32),("ic_queue",32)]:
    cx=bx+d(rad); dr.ellipse((cx-d(rad),cy-d(rad),cx+d(rad),cy+d(rad)),fill=blend(blend(bg1,bg2,0.5),(255,255,255),0.15))
    ic=icon(name,int(d(rad*2-28)),WHITE); im.paste(ic,(int(cx-ic.width/2),int(cy-ic.height/2)),ic); bx+=d(rad*2+20)
dr.text((x0,cy+d(64)),"A continuación: Angel — Massive Attack",font=F("r",16),fill=SUB)
im.convert("RGB").save("5_reproduciendo_v2.png"); print("ok")
